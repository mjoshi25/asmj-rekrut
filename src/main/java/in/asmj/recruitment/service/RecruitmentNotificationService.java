package in.asmj.recruitment.service;

import in.asmj.recruitment.store.RecordStore;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
public class RecruitmentNotificationService {
  private final RecordStore store;
  public RecruitmentNotificationService(RecordStore store) { this.store = store; }

  public void notifyUser(String email, String applicationId, String type, String message) {
    if (email == null || email.isBlank() || "null".equalsIgnoreCase(email.trim())) return;
    String recipient = email.trim().toLowerCase(Locale.ROOT);
    String id = UUID.randomUUID().toString().replace("-", "");
    Map<String,Object> n = new LinkedHashMap<>();
    n.put("id", id); n.put("recipientEmail", recipient); n.put("candidateEmail", recipient);
    n.put("applicationId", Objects.requireNonNullElse(applicationId, "")); n.put("type", type);
    n.put("message", message); n.put("createdAt", Instant.now().toString()); n.put("read", false);
    store.save("notification", id, n);
  }

  public void notifyRole(String role, String applicationId, String type, String message) {
    String authority = role.startsWith("ROLE_") ? role : "ROLE_" + role;
    for (Map<String,Object> user : store.findAll("authUser")) {
      Object raw = user.get("authorities");
      boolean hasRole = raw instanceof Collection<?> c ? c.stream().anyMatch(a -> authority.equals(String.valueOf(a)))
          : raw != null && Arrays.asList(String.valueOf(raw).split(",")).contains(authority);
      if (hasRole) notifyUser(String.valueOf(user.getOrDefault("username", user.get("id"))), applicationId, type, message);
    }
  }

  public void notifyApplicationTeam(Map<String,Object> app, String type, String message) {
    String appId = String.valueOf(app.getOrDefault("id", ""));
    Set<String> recipients = new LinkedHashSet<>();
    add(recipients, app.get("employerEmail")); add(recipients, app.get("recruiterEmail"));
    String candidate = String.valueOf(app.getOrDefault("email", ""));
    recipients.removeIf(e -> e.equalsIgnoreCase(candidate));
    for (String email : recipients) notifyUser(email, appId, type, message);
    notifyRole("ADMIN", appId, type, message);
  }
  private void add(Set<String> recipients, Object raw) {
    if (raw != null && !String.valueOf(raw).isBlank() && !"null".equalsIgnoreCase(String.valueOf(raw))) recipients.add(String.valueOf(raw).trim().toLowerCase(Locale.ROOT));
  }
}

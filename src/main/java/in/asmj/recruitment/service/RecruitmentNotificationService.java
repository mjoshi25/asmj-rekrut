package in.asmj.recruitment.service;

import in.asmj.recruitment.store.RecordStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Central notification routing. Recruiters are included only when they have a
 * concrete relationship to the job/application and still have active employer
 * authorization. Recipients are normalized and de-duplicated before delivery.
 */
@Service
public class RecruitmentNotificationService {
  private final RecordStore store;

  public RecruitmentNotificationService(RecordStore store) { this.store = store; }

  public void notifyUser(String email, String applicationId, String type, String message) {
    if (email == null || email.isBlank() || "null".equalsIgnoreCase(email.trim())) return;
    String recipient = email.trim().toLowerCase(Locale.ROOT);
    String id = UUID.randomUUID().toString().replace("-", "");
    Map<String,Object> n = new LinkedHashMap<>();
    n.put("id", id);
    n.put("recipientEmail", recipient);
    // Keep the legacy field for existing candidate pages/data compatibility.
    n.put("candidateEmail", recipient);
    n.put("applicationId", Objects.requireNonNullElse(applicationId, ""));
    n.put("type", type);
    n.put("message", message);
    n.put("createdAt", Instant.now().toString());
    n.put("read", false);
    store.save("notification", id, n);
  }

  public void notifyRole(String role, String applicationId, String type, String message) {
    String authority = role.startsWith("ROLE_") ? role : "ROLE_" + role;
    for (Map<String,Object> user : store.findAll("authUser")) {
      if (hasAuthority(user, authority)) {
        Object username = user.getOrDefault("username", user.get("id"));
        if (username != null) notifyUser(String.valueOf(username), applicationId, type, message);
      }
    }
  }

  /** Notify the candidate's employer, directly involved/authorized recruiter(s), and admins. */
  public void notifyApplicationTeam(Map<String,Object> app, String type, String message) {
    if (app == null) return;
    String appId = text(app.get("id"));
    if (appId.isBlank()) appId = text(app.get("applicationId"));
    Set<String> recipients = new LinkedHashSet<>();
    add(recipients, app.get("employerEmail"));

    String jobId = text(app.get("jobId"));
    Map<String,Object> job = jobId.isBlank() ? Map.of() : store.find("job", jobId).orElse(Map.of());
    String employerId = firstNonBlank(app.get("employerId"), job.get("employerId"));
    if (employerId.isBlank()) employerId = employerIdForEmail(firstNonBlank(app.get("employerEmail"), job.get("employerEmail")));

    // A recruiter is involved if assigned on the application, assigned to or
    // posted the job, or they actually changed this application's hiring stage.
    Set<String> involvedRecruiters = new LinkedHashSet<>();
    add(involvedRecruiters, app.get("recruiterEmail"));
    add(involvedRecruiters, job.get("recruiterEmail"));
    if ("RECRUITER".equalsIgnoreCase(text(job.get("postedByRole")))) add(involvedRecruiters, job.get("postedBy"));
    collectRecruiterActors(involvedRecruiters, appId, app);

    for (String recruiterEmail : involvedRecruiters) {
      if (hasActiveEmployerAuthorization(recruiterEmail, employerId)) add(recipients, recruiterEmail);
    }

    String candidateEmail = text(app.get("email"));
    if (!candidateEmail.isBlank()) recipients.remove(candidateEmail.toLowerCase(Locale.ROOT));
    for (String email : recipients) notifyUser(email, appId, type, message);
    notifyRole("ADMIN", appId, type, message);
  }

  private void collectRecruiterActors(Set<String> recruiters, String applicationId, Map<String,Object> app) {
    Object history = app.get("statusHistory");
    if (history instanceof Collection<?> items) {
      for (Object item : items) {
        if (item instanceof Map<?,?> entry) {
          String actor = firstNonBlank(entry.get("changedBy"), entry.get("actorEmail"));
          if (isRecruiter(actor)) add(recruiters, actor);
        }
      }
    }
    if (applicationId.isBlank()) return;
    for (Map<String,Object> audit : store.findAll("applicationStatusAudit")) {
      if (!applicationId.equals(text(audit.get("applicationId")))) continue;
      String actor = firstNonBlank(audit.get("changedBy"), audit.get("actorEmail"));
      if (isRecruiter(actor)) add(recruiters, actor);
    }
  }

  private boolean hasActiveEmployerAuthorization(String recruiterEmail, String employerId) {
    if (recruiterEmail == null || recruiterEmail.isBlank() || employerId == null || employerId.isBlank()) return false;
    return store.findAll("recruiterAuthorization").stream().anyMatch(a ->
        recruiterEmail.equalsIgnoreCase(text(a.get("recruiterEmail")))
            && employerId.equals(text(a.get("employerId")))
            && "ACTIVE".equalsIgnoreCase(text(a.get("status")))
            && (Boolean.TRUE.equals(a.get("canManageCandidates")) || Boolean.TRUE.equals(a.get("canPostJobs"))));
  }

  private String employerIdForEmail(String employerEmail) {
    if (employerEmail == null || employerEmail.isBlank()) return "";
    for (Map<String,Object> employer : store.findAll("employerRegistration")) {
      if (employerEmail.equalsIgnoreCase(text(employer.get("email")))) return text(employer.get("id"));
    }
    for (Map<String,Object> employer : store.findAll("employer")) {
      if (employerEmail.equalsIgnoreCase(text(employer.get("email")))) return text(employer.get("id"));
    }
    return "";
  }

  private boolean isRecruiter(String email) {
    if (email == null || email.isBlank()) return false;
    for (Map<String,Object> user : store.findAll("authUser")) {
      Object username = user.getOrDefault("username", user.get("id"));
      if (username != null && email.equalsIgnoreCase(String.valueOf(username)) && hasAuthority(user, "ROLE_RECRUITER")) return true;
    }
    return false;
  }

  private boolean hasAuthority(Map<String,Object> user, String authority) {
    Object raw = user.get("authorities");
    return raw instanceof Collection<?> c
        ? c.stream().anyMatch(a -> authority.equals(String.valueOf(a)))
        : raw != null && Arrays.stream(String.valueOf(raw).split(",")).map(String::trim).anyMatch(authority::equals);
  }

  private void add(Set<String> recipients, Object raw) {
    String email = text(raw);
    if (!email.isBlank()) recipients.add(email.toLowerCase(Locale.ROOT));
  }

  private String firstNonBlank(Object first, Object second) {
    String a = text(first);
    return a.isBlank() ? text(second) : a;
  }

  private String text(Object value) {
    if (value == null) return "";
    String s = String.valueOf(value).trim();
    return "null".equalsIgnoreCase(s) ? "" : s;
  }
}

package in.asmj.recruitment.web;

import in.asmj.recruitment.store.RecordStore;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;
import java.util.Map;

@ControllerAdvice
public class NotificationModelAdvice {
  private final RecordStore store;
  public NotificationModelAdvice(RecordStore store) { this.store = store; }

  @ModelAttribute("unreadNotificationCount")
  public long unreadNotificationCount(Authentication auth) {
    if (auth == null || !auth.isAuthenticated() || auth.getName() == null) return 0;
    boolean candidate = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch("ROLE_CANDIDATE"::equals);
    if (!candidate) return 0;
    return store.findAll("notification").stream()
      .filter(n -> auth.getName().equalsIgnoreCase(String.valueOf(n.getOrDefault("recipientEmail", n.get("candidateEmail")))))
      .filter(n -> !Boolean.parseBoolean(String.valueOf(n.getOrDefault("read", false))))
      .count();
  }

  @ModelAttribute("navNotifications")
  public List<Map<String,Object>> navNotifications(Authentication auth) {
    if (auth == null || !auth.isAuthenticated() || auth.getName() == null) return List.of();
    boolean candidate = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch("ROLE_CANDIDATE"::equals);
    if (!candidate) return List.of();
    return store.findAll("notification").stream()
      .filter(n -> auth.getName().equalsIgnoreCase(String.valueOf(n.getOrDefault("recipientEmail", n.get("candidateEmail")))))
      .filter(n -> !Boolean.parseBoolean(String.valueOf(n.getOrDefault("read", false))))
      .toList();
  }
}

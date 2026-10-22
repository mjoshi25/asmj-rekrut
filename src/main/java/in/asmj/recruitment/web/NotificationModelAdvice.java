package in.asmj.recruitment.web;

import in.asmj.recruitment.store.RecordStore;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import java.util.*;

@ControllerAdvice
public class NotificationModelAdvice {
  private final RecordStore store;
  public NotificationModelAdvice(RecordStore store) { this.store = store; }

  @ModelAttribute("unreadNotificationCount")
  public long unreadNotificationCount(Authentication auth) {
    if (!valid(auth)) return 0;
    return ownNotifications(auth).stream().filter(n -> !Boolean.parseBoolean(String.valueOf(n.getOrDefault("read", false)))).count();
  }

  @ModelAttribute("navNotifications")
  public List<Map<String,Object>> navNotifications(Authentication auth) {
    if (!valid(auth)) return List.of();
    return ownNotifications(auth).stream()
      .sorted((a, b) -> {
        boolean aRead = Boolean.parseBoolean(String.valueOf(a.getOrDefault("read", false)));
        boolean bRead = Boolean.parseBoolean(String.valueOf(b.getOrDefault("read", false)));
        if (aRead != bRead) return aRead ? 1 : -1;
        return String.valueOf(b.getOrDefault("createdAt", "")).compareTo(String.valueOf(a.getOrDefault("createdAt", "")));
      }).limit(5).toList();
  }

  private List<Map<String,Object>> ownNotifications(Authentication auth) {
    String username = auth.getName();
    return store.findAll("notification").stream()
      .filter(n -> username.equalsIgnoreCase(String.valueOf(n.getOrDefault("recipientEmail", n.get("candidateEmail")))))
      .toList();
  }
  private boolean valid(Authentication auth) {
    return auth != null && auth.isAuthenticated() && auth.getName() != null && !auth.getName().equals("anonymousUser")
      && auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(a -> Set.of("ROLE_ADMIN","ROLE_EMPLOYER","ROLE_RECRUITER","ROLE_CANDIDATE").contains(a));
  }
}

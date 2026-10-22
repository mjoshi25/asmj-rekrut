package in.asmj.recruitment.web;

import in.asmj.recruitment.store.RecordStore;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.Instant;
import java.util.*;

@Controller
public class NotificationsController {
  private final RecordStore store;
  public NotificationsController(RecordStore store) { this.store = store; }

  @GetMapping("/notifications")
  public String history(Authentication auth, Model model) {
    List<Map<String,Object>> notifications = own(auth).stream()
      .sorted(Comparator.comparing((Map<String,Object> n) -> String.valueOf(n.getOrDefault("createdAt", ""))).reversed()).toList();
    model.addAttribute("notifications", notifications);
    model.addAttribute("unreadCount", notifications.stream().filter(n -> !Boolean.parseBoolean(String.valueOf(n.getOrDefault("read", false)))).count());
    return "notifications";
  }

  @PostMapping("/notifications/{id}/read")
  public String markRead(@PathVariable String id, Authentication auth, RedirectAttributes ra) {
    Map<String,Object> n = store.find("notification", id).orElse(null);
    if (n == null || !owns(n, auth)) { ra.addFlashAttribute("error", "Notification not found or access denied."); return "redirect:/notifications"; }
    n.put("read", true); n.put("readAt", Instant.now().toString()); store.save("notification", id, n);
    return "redirect:/notifications";
  }

  @PostMapping("/notifications/read-all")
  public String markAllRead(Authentication auth, RedirectAttributes ra) {
    int updated = 0;
    for (Map<String,Object> n : own(auth)) {
      if (!Boolean.parseBoolean(String.valueOf(n.getOrDefault("read", false)))) {
        n.put("read", true); n.put("readAt", Instant.now().toString()); store.save("notification", String.valueOf(n.get("id")), n); updated++;
      }
    }
    ra.addFlashAttribute("success", updated == 0 ? "You are all caught up." : updated + " notification(s) marked as read.");
    return "redirect:/notifications";
  }

  private List<Map<String,Object>> own(Authentication auth) {
    if (auth == null || auth.getName() == null) return List.of();
    return store.findAll("notification").stream().filter(n -> owns(n, auth)).toList();
  }
  private boolean owns(Map<String,Object> n, Authentication auth) {
    return auth != null && auth.getName() != null && auth.getName().equalsIgnoreCase(String.valueOf(n.getOrDefault("recipientEmail", n.get("candidateEmail"))));
  }
}

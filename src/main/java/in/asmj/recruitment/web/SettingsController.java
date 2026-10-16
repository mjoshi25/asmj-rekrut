package in.asmj.recruitment.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SettingsController {
  private final UserDetailsManager users;
  public SettingsController(UserDetailsManager users) { this.users = users; }

  @GetMapping("/settings")
  public String settings(Authentication auth, Model model) {
    model.addAttribute("accountEmail", auth.getName());
    model.addAttribute("roles", auth.getAuthorities().stream().map(a -> a.getAuthority().replace("ROLE_", "")).toList());
    model.addAttribute("candidate", auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CANDIDATE")));
    return "settings";
  }

  @PostMapping("/settings/password")
  public String changePassword(@RequestParam String currentPassword, @RequestParam String newPassword,
      @RequestParam String confirmPassword, RedirectAttributes ra) {
    if (newPassword == null || newPassword.length() < 8) {
      ra.addFlashAttribute("error", "Your new password must contain at least 8 characters.");
      return "redirect:/settings";
    }
    if (!newPassword.equals(confirmPassword)) {
      ra.addFlashAttribute("error", "The new password and confirmation do not match.");
      return "redirect:/settings";
    }
    if (newPassword.equals(currentPassword)) {
      ra.addFlashAttribute("error", "Choose a new password different from your current password.");
      return "redirect:/settings";
    }
    try {
      users.changePassword(currentPassword, newPassword);
      ra.addFlashAttribute("success", "Password changed successfully.");
    } catch (IllegalArgumentException | org.springframework.security.core.userdetails.UsernameNotFoundException e) {
      ra.addFlashAttribute("error", e.getMessage() == null ? "Unable to change password." : e.getMessage());
    }
    return "redirect:/settings";
  }
}

package in.asmj.recruitment.web;

import in.asmj.recruitment.store.RecordStore;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDate;
import java.util.*;

@Controller
public class RecruitmentAdminController {
 private final RecordStore store;
 public RecruitmentAdminController(RecordStore store){this.store=store;}
 @GetMapping("/admin/commission") public String commission(Model m){Map<String,Object> cfg=store.find("settings","commission").orElseGet(()->Map.of("type","PERCENT","value","8","currency","INR"));m.addAttribute("config",cfg);m.addAttribute("commissions",store.findAll("commission"));return "admin-commission";}
 @PostMapping("/admin/commission") public String saveCommission(@RequestParam String type,@RequestParam double value,RedirectAttributes ra){if(!Set.of("PERCENT","FIXED").contains(type)||value<0||value>100000000){ra.addFlashAttribute("error","Enter a valid commission model and amount.");return "redirect:/admin/commission";}Map<String,Object> c=new LinkedHashMap<>();c.put("id","commission");c.put("type",type);c.put("value",value);c.put("currency","INR");c.put("updatedAt",LocalDate.now().toString());store.save("settings","commission",c);ra.addFlashAttribute("success","Commission model saved. It applies to future candidate selections.");return "redirect:/admin/commission";}
 @PostMapping("/admin/commission/{id}/status") public String commissionStatus(@PathVariable String id,@RequestParam String status,RedirectAttributes ra){if(!Set.of("PENDING","INVOICED","PAID","WAIVED").contains(status)){ra.addFlashAttribute("error","Invalid commission status.");return "redirect:/admin/commission";}store.find("commission",id).ifPresent(c->{c.put("paymentStatus",status);c.put("updatedAt",LocalDate.now().toString());store.save("commission",id,c);});ra.addFlashAttribute("success","Commission status updated.");return "redirect:/admin/commission";}
 @GetMapping("/admin/relationships") public String relationships(Model m){m.addAttribute("applications",store.findAll("application"));m.addAttribute("jobs",store.findAll("job"));m.addAttribute("placements",store.findAll("placement"));m.addAttribute("commissions",store.findAll("commission"));return "admin-relationships";}
}

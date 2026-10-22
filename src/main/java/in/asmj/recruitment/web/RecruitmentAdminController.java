package in.asmj.recruitment.web;

import in.asmj.recruitment.store.RecordStore;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDate;
import java.time.Instant;
import java.util.*;

@Controller
public class RecruitmentAdminController {
 private final RecordStore store;
 public RecruitmentAdminController(RecordStore store){this.store=store;}
 @GetMapping("/admin/commission") public String commission(Model m){
   Map<String,Object> cfg=store.find("settings","commission").orElseGet(()->Map.of("type","PERCENT","value","8","currency","INR"));
   List<Map<String,Object>> rows=store.findAll("commission");
   double total=rows.stream().filter(c->!"WAIVED".equalsIgnoreCase(String.valueOf(c.getOrDefault("paymentStatus","PENDING")))).mapToDouble(c->number(c.get("amount"))).sum();
   double paid=rows.stream().filter(c->"PAID".equalsIgnoreCase(String.valueOf(c.get("paymentStatus")))).mapToDouble(c->number(c.get("amount"))).sum();
   double outstanding=rows.stream().filter(c->Set.of("PENDING","INVOICED").contains(String.valueOf(c.getOrDefault("paymentStatus","PENDING")).toUpperCase(Locale.ROOT))).mapToDouble(c->number(c.get("amount"))).sum();
   m.addAttribute("config",cfg);m.addAttribute("commissions",rows);m.addAttribute("totalCommission",total);m.addAttribute("paidCommission",paid);m.addAttribute("outstandingCommission",outstanding);return "admin-commission";
 }
 @PostMapping("/admin/commission") public String saveCommission(@RequestParam String type,@RequestParam double value,RedirectAttributes ra){if(!Set.of("PERCENT","FIXED").contains(type)||!Double.isFinite(value)||value<0||("PERCENT".equals(type)&&value>100)||("FIXED".equals(type)&&value>100000000)){ra.addFlashAttribute("error","Enter a valid commission model: percentage must be 0–100%, or fixed INR fee up to ₹10 crore.");return "redirect:/admin/commission";}Map<String,Object> c=new LinkedHashMap<>();c.put("id","commission");c.put("type",type);c.put("value",value);c.put("currency","INR");c.put("updatedAt",LocalDate.now().toString());store.save("settings","commission",c);ra.addFlashAttribute("success","Commission model saved. It applies to future candidate selections; existing ledger entries retain their saved model until recalculated.");return "redirect:/admin/commission";}
 @PostMapping("/admin/commission/{id}/status") public String commissionStatus(@PathVariable String id,@RequestParam String status,org.springframework.security.core.Authentication auth,RedirectAttributes ra){if(!Set.of("PENDING","INVOICED","PAID","WAIVED").contains(status)){ra.addFlashAttribute("error","Invalid commission status.");return "redirect:/admin/commission";}store.find("commission",id).ifPresent(c->{String previous=String.valueOf(c.getOrDefault("paymentStatus","PENDING"));c.put("paymentStatus",status);c.put("updatedAt",LocalDate.now().toString());store.save("commission",id,c);auditCommission(id,"STATUS_CHANGED",previous,status,auth==null?"system":auth.getName(),"Commission status changed");});ra.addFlashAttribute("success","Commission status updated and recorded in the audit trail.");return "redirect:/admin/commission";}
 @PostMapping("/admin/commission/{id}/salary") public String correctSalary(@PathVariable String id,@RequestParam String annualSalary,org.springframework.security.core.Authentication auth,RedirectAttributes ra){
   double salary;try{String raw=annualSalary==null?"":annualSalary.trim().replace(",","");if(!raw.matches("[0-9]+(?:\\.[0-9]{1,2})?")||raw.length()>14)throw new NumberFormatException();salary=Double.parseDouble(raw);if(!Double.isFinite(salary)||salary<=0||salary>100000000000d)throw new NumberFormatException();}catch(Exception ex){ra.addFlashAttribute("error","Enter one full annual salary amount in INR, e.g. 600000. Do not enter LPA, monthly pay, or a range.");return "redirect:/admin/commission";}
   Map<String,Object> c=store.find("commission",id).orElse(null);if(c==null){ra.addFlashAttribute("error","Commission record not found.");return "redirect:/admin/commission";}
   double oldSalary=number(c.get("salaryBase"));double oldAmount=number(c.get("amount"));String type=String.valueOf(c.getOrDefault("modelType","PERCENT"));double value=number(c.get("modelValue"));double newAmount="FIXED".equals(type)?value:Math.round((salary*value/100d)*100d)/100d;
   c.put("salaryBase",salary);c.put("amount",newAmount);c.put("calculationStatus","CALCULATED");c.put("salaryCorrectedAt",LocalDate.now().toString());c.put("salaryCorrectedBy",auth==null?"system":auth.getName());c.put("updatedAt",LocalDate.now().toString());store.save("commission",id,c);
   auditCommission(id,"SALARY_CORRECTED",String.valueOf(oldSalary)+" / commission "+oldAmount,String.valueOf(salary)+" / commission "+newAmount,auth==null?"system":auth.getName(),"Annual salary corrected in INR and commission recalculated");
   ra.addFlashAttribute("success","Annual salary saved and commission recalculated from the full annual INR amount.");return "redirect:/admin/commission";
 }
 private void auditCommission(String commissionId,String action,String before,String after,String actor,String note){Map<String,Object>a=new LinkedHashMap<>();String id=UUID.randomUUID().toString().replace("-","");a.put("id",id);a.put("commissionId",commissionId);a.put("action",action);a.put("before",before);a.put("after",after);a.put("actor",actor);a.put("note",note);a.put("createdAt",Instant.now().toString());store.save("commissionAudit",id,a);}
 private static double number(Object v){try{return Double.parseDouble(String.valueOf(v));}catch(Exception ex){return 0;}}
 @GetMapping("/admin/relationships") public String relationships(Model m){m.addAttribute("applications",store.findAll("application"));m.addAttribute("jobs",store.findAll("job"));m.addAttribute("placements",store.findAll("placement"));m.addAttribute("commissions",store.findAll("commission"));m.addAttribute("statusAudits",store.findAll("applicationStatusAudit").stream().sorted((a,b)->String.valueOf(b.getOrDefault("changedAt","")).compareTo(String.valueOf(a.getOrDefault("changedAt","")))).toList());m.addAttribute("commissionAudits",store.findAll("commissionAudit").stream().sorted((a,b)->String.valueOf(b.getOrDefault("createdAt","")).compareTo(String.valueOf(a.getOrDefault("createdAt","")))).toList());return "admin-relationships";}
}

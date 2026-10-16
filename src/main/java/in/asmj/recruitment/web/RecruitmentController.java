package in.asmj.recruitment.web;
import in.asmj.recruitment.store.RecordStore;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDate;
import java.util.*;
@Controller
public class RecruitmentController {
 private final RecordStore store;
 public RecruitmentController(RecordStore store){this.store=store;}
 @GetMapping("/") public String home(Model m){m.addAttribute("jobs",store.findAll("job").stream().filter(j->!"CLOSED".equals(j.get("status"))).limit(6).toList());return "home";}
 @GetMapping("/login") public String login(){return "login";}
 @GetMapping("/dashboard") public String dashboard(Model m, Authentication auth){
   m.addAttribute("candidateCount",store.count("candidate"));m.addAttribute("employerCount",store.count("employer"));m.addAttribute("jobCount",store.count("job"));m.addAttribute("applicationCount",store.count("application"));
   m.addAttribute("recentJobs",store.findAll("job").stream().limit(5).toList());m.addAttribute("name",auth.getName());return "dashboard";
 }
 @GetMapping("/jobs") public String jobs(@RequestParam(required=false) String q,Model m){
   List<Map<String,Object>> all=store.findAll("job").stream().filter(j->!"CLOSED".equals(j.get("status"))).toList();
   if(q!=null&&!q.isBlank()) all=all.stream().filter(j->String.valueOf(j.get("title")).toLowerCase().contains(q.toLowerCase())||String.valueOf(j.get("location")).toLowerCase().contains(q.toLowerCase())).toList();
   m.addAttribute("jobs",all);m.addAttribute("q",q==null?"":q);return "jobs";
 }
 @GetMapping("/opportunity/{id}") public String job(@PathVariable String id,Model m){m.addAttribute("job",store.find("job",id).orElseThrow());return "job-detail";}
 @GetMapping("/register/candidate") public String candidateRegister(Model m){m.addAttribute("record",new LinkedHashMap<>());return "candidate-register";}
 @PostMapping("/register/candidate") public String saveCandidate(@RequestParam Map<String,String> form,RedirectAttributes ra){
   String id=UUID.randomUUID().toString().substring(0,8).toUpperCase();Map<String,Object> r=new LinkedHashMap<>(form);r.put("id",id);r.put("status","ACTIVE");r.put("createdAt",LocalDate.now().toString());store.save("candidate",id,r);ra.addFlashAttribute("success","Registration received. Your candidate reference is "+id+". Sign in to access your portal.");return "redirect:/register/candidate";
 }
 @GetMapping("/candidates") public String candidates(Model m){m.addAttribute("records",store.findAll("candidate"));m.addAttribute("type","Candidates");m.addAttribute("listUrl","/candidates");return "records";}
 @GetMapping("/employers") public String employers(Model m){m.addAttribute("records",store.findAll("employer"));m.addAttribute("type","Employers");m.addAttribute("listUrl","/employers");return "records";}
 @GetMapping("/employers/new") public String employerForm(){return "employer-form";}
 @PostMapping("/employers/save") public String saveEmployer(@RequestParam Map<String,String> form,RedirectAttributes ra){String id=UUID.randomUUID().toString().substring(0,8).toUpperCase();Map<String,Object> r=new LinkedHashMap<>(form);r.put("id",id);r.put("createdAt",LocalDate.now().toString());store.save("employer",id,r);ra.addFlashAttribute("success","Employer saved.");return "redirect:/employers";}
 @GetMapping("/jobs/new") public String jobForm(Model m){m.addAttribute("employers",store.findAll("employer"));return "job-form";}
 @PostMapping("/jobs/save") public String saveJob(@RequestParam Map<String,String> form,RedirectAttributes ra){String id=UUID.randomUUID().toString().substring(0,8).toUpperCase();Map<String,Object> r=new LinkedHashMap<>(form);r.put("id",id);r.putIfAbsent("status","OPEN");r.put("createdAt",LocalDate.now().toString());store.save("job",id,r);ra.addFlashAttribute("success","Job opening published.");return "redirect:/jobs";}
 @PostMapping("/jobs/{id}/apply") public String apply(@PathVariable String id,@RequestParam String fullName,@RequestParam String email,@RequestParam(required=false) String phone,RedirectAttributes ra){
   Map<String,Object> job=store.find("job",id).orElseThrow();String appId=UUID.randomUUID().toString().substring(0,8).toUpperCase();Map<String,Object> a=new LinkedHashMap<>();a.put("id",appId);a.put("jobId",id);a.put("jobTitle",job.get("title"));a.put("fullName",fullName);a.put("email",email);a.put("phone",phone);a.put("status","APPLIED");a.put("appliedAt",LocalDate.now().toString());store.save("application",appId,a);ra.addFlashAttribute("success","Application submitted. Reference: "+appId);return "redirect:/jobs/"+id;
 }
 @GetMapping("/applications") public String applications(Model m){m.addAttribute("records",store.findAll("application"));return "applications";}
 @PostMapping("/applications/{id}/status") public String applicationStatus(@PathVariable String id,@RequestParam String status,RedirectAttributes ra){Map<String,Object> a=store.find("application",id).orElseThrow();a.put("status",status);store.save("application",id,a);ra.addFlashAttribute("success","Application status updated.");return "redirect:/applications";}
 @GetMapping("/interviews") public String interviews(Model m){m.addAttribute("records",store.findAll("interview"));m.addAttribute("applications",store.findAll("application"));return "interviews";}
 @PostMapping("/interviews/save") public String saveInterview(@RequestParam Map<String,String> form,RedirectAttributes ra){String id=UUID.randomUUID().toString().substring(0,8).toUpperCase();Map<String,Object> r=new LinkedHashMap<>(form);r.put("id",id);r.put("status","SCHEDULED");store.save("interview",id,r);ra.addFlashAttribute("success","Interview scheduled.");return "redirect:/interviews";}
 @GetMapping("/placements") public String placements(Model m){m.addAttribute("records",store.findAll("placement"));m.addAttribute("applications",store.findAll("application"));return "placements";}
 @PostMapping("/placements/save") public String savePlacement(@RequestParam Map<String,String> form,RedirectAttributes ra){String id=UUID.randomUUID().toString().substring(0,8).toUpperCase();Map<String,Object> r=new LinkedHashMap<>(form);r.put("id",id);r.put("status","PLACED");r.put("placedAt",LocalDate.now().toString());store.save("placement",id,r);ra.addFlashAttribute("success","Placement recorded.");return "redirect:/placements";}
 @GetMapping("/reports") public String reports(Model m){m.addAttribute("candidates",store.count("candidate"));m.addAttribute("employers",store.count("employer"));m.addAttribute("jobs",store.count("job"));m.addAttribute("applications",store.count("application"));m.addAttribute("interviews",store.count("interview"));m.addAttribute("placements",store.count("placement"));return "reports";}
 @GetMapping("/portal") public String portal(Authentication auth,Model m){m.addAttribute("username",auth.getName());m.addAttribute("applications",store.findAll("application").stream().filter(a->auth.getName().equals(a.get("email"))).toList());return "portal";}
}

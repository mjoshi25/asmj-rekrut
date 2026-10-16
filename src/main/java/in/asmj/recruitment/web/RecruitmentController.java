package in.asmj.recruitment.web;

import in.asmj.recruitment.store.RecordStore;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDate;
import java.util.*;

@Controller
public class RecruitmentController {
  private final RecordStore store;
  private final UserDetailsManager users;
  private final PasswordEncoder passwordEncoder;

  public RecruitmentController(RecordStore store, UserDetailsManager users, PasswordEncoder passwordEncoder) {
    this.store = store; this.users = users; this.passwordEncoder = passwordEncoder;
  }

  @GetMapping("/") public String home(Model m) {
    m.addAttribute("jobs", store.findAll("job").stream().filter(j -> !"CLOSED".equals(j.get("status"))).limit(6).toList()); return "home";
  }
  @GetMapping("/login") public String login() { return "login"; }
  @GetMapping("/dashboard") public String dashboard(Model m, Authentication auth) {
    m.addAttribute("candidateCount",store.count("candidate")); m.addAttribute("employerCount",store.count("employer"));
    m.addAttribute("jobCount",store.count("job")); m.addAttribute("applicationCount",store.count("application"));
    m.addAttribute("recentJobs",store.findAll("job").stream().limit(5).toList()); m.addAttribute("name",auth.getName()); m.addAttribute("isAdmin",auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))); return "dashboard";
  }
  @GetMapping("/jobs") public String jobs(@RequestParam(required=false) String q, Model m) {
    List<Map<String,Object>> all = store.findAll("job").stream().filter(j -> !"CLOSED".equals(j.get("status"))).toList();
    if (q != null && !q.isBlank()) all = all.stream().filter(j -> String.valueOf(j.get("title")).toLowerCase().contains(q.toLowerCase()) || String.valueOf(j.get("location")).toLowerCase().contains(q.toLowerCase())).toList();
    m.addAttribute("jobs",all); m.addAttribute("q",q==null?"":q); return "jobs";
  }
  @GetMapping("/opportunity/{id}") public String job(@PathVariable String id, Model m) { m.addAttribute("job",store.find("job",id).orElseThrow()); return "job-detail"; }

  @GetMapping("/register/candidate") public String candidateRegister(Model m) { m.addAttribute("record",new LinkedHashMap<>()); return "candidate-register"; }
  @PostMapping("/register/candidate") public String saveCandidate(@RequestParam Map<String,String> form, RedirectAttributes ra) {
    String email = normalizedEmail(form.get("email")); String password = form.get("password");
    if (email.isBlank() || password == null || password.length() < 8) { ra.addFlashAttribute("error","Enter a valid email and a password with at least 8 characters."); return "redirect:/register/candidate"; }
    if (!password.equals(form.get("confirmPassword"))) { ra.addFlashAttribute("error","Passwords do not match."); return "redirect:/register/candidate"; }
    if (users.userExists(email)) { ra.addFlashAttribute("error","An account already exists for this email. Please sign in."); return "redirect:/register/candidate"; }
    String id = newId(); Map<String,Object> r = new LinkedHashMap<>(form); r.remove("password"); r.remove("confirmPassword");
    r.put("id",id); r.put("email",email); r.put("status","ACTIVE"); r.put("createdAt",LocalDate.now().toString());
    try { users.createUser(User.withUsername(email).password(passwordEncoder.encode(password)).roles("CANDIDATE").build()); }
    catch (Exception ex) { ra.addFlashAttribute("error","Could not create your account. Please check the email and try again."); return "redirect:/register/candidate"; }
    store.save("candidate",id,r); ra.addFlashAttribute("success","Candidate account created successfully. You can now sign in. Reference: "+id); return "redirect:/login";
  }

  @GetMapping("/register/employer") public String employerRegister() { return "employer-register"; }
  @PostMapping("/register/employer") public String saveEmployerRegistration(@RequestParam Map<String,String> form, RedirectAttributes ra) {
    return registerPartner(form, "employerRegistration", "PENDING_EMPLOYER", "employer", ra);
  }
  @GetMapping("/register/recruiter") public String recruiterRegister() { return "recruiter-register"; }
  @PostMapping("/register/recruiter") public String saveRecruiterRegistration(@RequestParam Map<String,String> form, RedirectAttributes ra) {
    return registerPartner(form, "recruiterRegistration", "PENDING_RECRUITER", "recruiter", ra);
  }

  private String registerPartner(Map<String,String> form, String recordType, String pendingRole, String label, RedirectAttributes ra) {
    String email = normalizedEmail(form.get("email")); String password = form.get("password");
    if (email.isBlank() || password == null || password.length() < 8) { ra.addFlashAttribute("error","Enter a valid email and a password with at least 8 characters."); return "redirect:/register/"+label; }
    if (!password.equals(form.get("confirmPassword"))) { ra.addFlashAttribute("error","Passwords do not match."); return "redirect:/register/"+label; }
    if (users.userExists(email)) { ra.addFlashAttribute("error","An account already exists for this email. Please sign in."); return "redirect:/register/"+label; }
    String id = newId(); Map<String,Object> record = new LinkedHashMap<>(form); record.remove("password"); record.remove("confirmPassword");
    record.put("id",id); record.put("email",email); record.put("status","PENDING_APPROVAL"); record.put("createdAt",LocalDate.now().toString());
    try { users.createUser(User.withUsername(email).password(passwordEncoder.encode(password)).roles(pendingRole).build()); }
    catch (Exception ex) { ra.addFlashAttribute("error","Could not create this account. Please check the email and try again."); return "redirect:/register/"+label; }
    store.save(recordType,id,record);
    ra.addFlashAttribute("success","Registration submitted. Your account is pending ASMJ administrator approval. You can sign in after approval.");
    return "redirect:/login";
  }

  @GetMapping("/admin/registrations") public String pendingRegistrations(Model m) {
    m.addAttribute("employers",store.findAll("employerRegistration")); m.addAttribute("recruiters",store.findAll("recruiterRegistration")); return "admin-registrations";
  }
  @PostMapping("/admin/registrations/{type}/{id}/approve") public String approveRegistration(@PathVariable String type, @PathVariable String id, RedirectAttributes ra) {
    String recordType; String role;
    if ("employer".equals(type)) { recordType="employerRegistration"; role="EMPLOYER"; }
    else if ("recruiter".equals(type)) { recordType="recruiterRegistration"; role="RECRUITER"; }
    else { ra.addFlashAttribute("error","Unknown registration type."); return "redirect:/admin/registrations"; }
    Optional<Map<String,Object>> found = store.find(recordType,id);
    if (found.isEmpty()) { ra.addFlashAttribute("error","Registration not found."); return "redirect:/admin/registrations"; }
    Map<String,Object> record = found.get(); String email = String.valueOf(record.get("email"));
    if (!users.userExists(email)) { ra.addFlashAttribute("error","Login account not found for this registration."); return "redirect:/admin/registrations"; }
    UserDetails old = users.loadUserByUsername(email);
    users.updateUser(User.withUsername(old.getUsername()).password(old.getPassword()).authorities("ROLE_"+role).build());
    record.put("status","APPROVED"); record.put("approvedAt",LocalDate.now().toString()); store.save(recordType,id,record);
    if ("employer".equals(type)) store.save("employer",id,record);
    ra.addFlashAttribute("success",type.substring(0,1).toUpperCase()+type.substring(1)+" account approved."); return "redirect:/admin/registrations";
  }
  @PostMapping("/admin/registrations/{type}/{id}/reject") public String rejectRegistration(@PathVariable String type, @PathVariable String id, RedirectAttributes ra) {
    String recordType = "employer".equals(type) ? "employerRegistration" : "recruiter".equals(type) ? "recruiterRegistration" : null;
    if (recordType == null) { ra.addFlashAttribute("error","Unknown registration type."); return "redirect:/admin/registrations"; }
    Optional<Map<String,Object>> found = store.find(recordType,id);
    if (found.isPresent()) { Map<String,Object> r=found.get(); r.put("status","REJECTED"); r.put("reviewedAt",LocalDate.now().toString()); store.save(recordType,id,r); String email=String.valueOf(r.get("email")); if (users.userExists(email)) users.deleteUser(email); ra.addFlashAttribute("success","Registration marked as rejected. The pending login has been disabled."); }
    else ra.addFlashAttribute("error","Registration not found.");
    return "redirect:/admin/registrations";
  }

  @GetMapping("/candidates") public String candidates(Model m) { m.addAttribute("records",store.findAll("candidate")); m.addAttribute("type","Candidates"); m.addAttribute("listUrl","/candidates"); return "records"; }
  @GetMapping("/employers") public String employers(Model m) { m.addAttribute("records",store.findAll("employer")); m.addAttribute("type","Employers"); m.addAttribute("listUrl","/employers"); return "records"; }
  @GetMapping("/employers/new") public String employerForm() { return "employer-form"; }
  @PostMapping("/employers/save") public String saveEmployer(@RequestParam Map<String,String> form, RedirectAttributes ra) { String id=newId(); Map<String,Object> r=new LinkedHashMap<>(form); r.put("id",id); r.put("status","ACTIVE"); r.put("createdAt",LocalDate.now().toString()); store.save("employer",id,r); ra.addFlashAttribute("success","Employer saved."); return "redirect:/employers"; }
  @GetMapping("/jobs/new") public String jobForm(Model m) { m.addAttribute("employers",store.findAll("employer")); return "job-form"; }
  @PostMapping("/jobs/save") public String saveJob(@RequestParam Map<String,String> form, RedirectAttributes ra) { String id=newId(); Map<String,Object> r=new LinkedHashMap<>(form); r.put("id",id); r.putIfAbsent("status","OPEN"); r.put("createdAt",LocalDate.now().toString()); store.save("job",id,r); ra.addFlashAttribute("success","Job opening published."); return "redirect:/jobs"; }
  @PostMapping("/jobs/{id}/apply") public String apply(@PathVariable String id, @RequestParam String fullName, @RequestParam String email, @RequestParam(required=false) String phone, RedirectAttributes ra) {
    Map<String,Object> job=store.find("job",id).orElseThrow(); String appId=newId(); Map<String,Object> a=new LinkedHashMap<>(); a.put("id",appId); a.put("jobId",id); a.put("jobTitle",job.get("title")); a.put("fullName",fullName); a.put("email",normalizedEmail(email)); a.put("phone",phone); a.put("status","APPLIED"); a.put("appliedAt",LocalDate.now().toString()); store.save("application",appId,a); ra.addFlashAttribute("success","Application submitted. Reference: "+appId); return "redirect:/opportunity/"+id;
  }
  @GetMapping("/applications") public String applications(Model m) { m.addAttribute("records",store.findAll("application")); return "applications"; }
  @PostMapping("/applications/{id}/status") public String applicationStatus(@PathVariable String id,@RequestParam String status,RedirectAttributes ra) { Map<String,Object> a=store.find("application",id).orElseThrow(); a.put("status",status); store.save("application",id,a); ra.addFlashAttribute("success","Application status updated."); return "redirect:/applications"; }
  @GetMapping("/interviews") public String interviews(Model m) { m.addAttribute("records",store.findAll("interview")); m.addAttribute("applications",store.findAll("application")); return "interviews"; }
  @PostMapping("/interviews/save") public String saveInterview(@RequestParam Map<String,String> form,RedirectAttributes ra) { String id=newId(); Map<String,Object> r=new LinkedHashMap<>(form); r.put("id",id); r.put("status","SCHEDULED"); store.save("interview",id,r); ra.addFlashAttribute("success","Interview scheduled."); return "redirect:/interviews"; }
  @GetMapping("/placements") public String placements(Model m) { m.addAttribute("records",store.findAll("placement")); m.addAttribute("applications",store.findAll("application")); return "placements"; }
  @PostMapping("/placements/save") public String savePlacement(@RequestParam Map<String,String> form,RedirectAttributes ra) { String id=newId(); Map<String,Object> r=new LinkedHashMap<>(form); r.put("id",id); r.put("status","PLACED"); r.put("placedAt",LocalDate.now().toString()); store.save("placement",id,r); ra.addFlashAttribute("success","Placement recorded."); return "redirect:/placements"; }
  @GetMapping("/reports") public String reports(Model m) { m.addAttribute("candidates",store.count("candidate")); m.addAttribute("employers",store.count("employer")); m.addAttribute("jobs",store.count("job")); m.addAttribute("applications",store.count("application")); m.addAttribute("interviews",store.count("interview")); m.addAttribute("placements",store.count("placement")); return "reports"; }
  @GetMapping("/portal") public String portal(Authentication auth,Model m) { m.addAttribute("username",auth.getName()); m.addAttribute("applications",store.findAll("application").stream().filter(a -> auth.getName().equals(a.get("email"))).toList()); return "portal"; }
  private String newId() { return UUID.randomUUID().toString().substring(0,8).toUpperCase(); }
  private String normalizedEmail(String email) { return email == null ? "" : email.trim().toLowerCase(Locale.ROOT); }
}

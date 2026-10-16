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
  @GetMapping("/login") public String login(Authentication auth) {
    if (auth != null && auth.isAuthenticated() && !auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ANONYMOUS"))) {
      if (hasRole(auth, "ROLE_PENDING_EMPLOYER") || hasRole(auth, "ROLE_PENDING_RECRUITER")) return "redirect:/pending-approval";
      return "redirect:/dashboard";
    }
    return "login";
  }
  @GetMapping("/pending-approval") public String pendingApproval(Authentication auth) {
    if (auth == null || auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_PENDING_EMPLOYER") || a.getAuthority().equals("ROLE_PENDING_RECRUITER"))) return "redirect:/dashboard";
    return "pending-approval";
  }
  @GetMapping("/dashboard") public String dashboard(Authentication auth) {
    // Keep a role-aware fallback for direct visits to /dashboard.
    if (hasRole(auth, "ROLE_ADMIN")) return "redirect:/admin/dashboard";
    if (hasRole(auth, "ROLE_RECRUITER")) return "redirect:/recruiter/dashboard";
    if (hasRole(auth, "ROLE_EMPLOYER")) return "redirect:/employer/dashboard";
    if (hasRole(auth, "ROLE_CANDIDATE")) return "redirect:/portal";
    return "redirect:/login";
  }
  @GetMapping("/admin/dashboard") public String adminDashboard(Model m, Authentication auth) {
    populateDashboard(m, auth); m.addAttribute("isAdmin", true); return "dashboard";
  }
  @GetMapping("/recruiter/dashboard") public String recruiterDashboard(Model m, Authentication auth) {
    populateDashboard(m, auth); return "recruiter-dashboard";
  }
  @GetMapping("/employer/dashboard") public String employerDashboard(Model m, Authentication auth) {
    m.addAttribute("name", auth.getName());
    m.addAttribute("jobs", store.findAll("job").stream().filter(j -> auth.getName().equalsIgnoreCase(String.valueOf(j.get("employerEmail"))) || auth.getName().equalsIgnoreCase(String.valueOf(j.get("email")))).toList());
    m.addAttribute("jobCount", store.count("job"));
    m.addAttribute("applicationCount", store.count("application"));
    return "employer-dashboard";
  }
  private void populateDashboard(Model m, Authentication auth) {
    m.addAttribute("candidateCount",store.count("candidate")); m.addAttribute("employerCount",store.count("employer"));
    m.addAttribute("jobCount",store.count("job")); m.addAttribute("applicationCount",store.count("application"));
    m.addAttribute("recentJobs",store.findAll("job").stream().limit(5).toList()); m.addAttribute("name",auth.getName());
  }
  private boolean hasRole(Authentication auth, String role) {
    return auth != null && auth.getAuthorities().stream().anyMatch(a -> role.equals(a.getAuthority()));
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
  @GetMapping("/jobs/new") public String jobForm(Model m, Authentication auth) {
    List<Map<String,Object>> employers = store.findAll("employer").stream()
      .filter(e -> !"REJECTED".equalsIgnoreCase(String.valueOf(e.get("status"))))
      .filter(e -> !"PENDING_APPROVAL".equalsIgnoreCase(String.valueOf(e.get("status"))))
      .toList();
    // Employer accounts can only publish for their own approved company. Recruiters/admins can select any approved employer.
    if (hasRole(auth, "ROLE_EMPLOYER")) {
      employers = employers.stream().filter(e -> auth.getName().equalsIgnoreCase(String.valueOf(e.get("email")))).toList();
      if (employers.isEmpty()) {
        employers = store.findAll("employerRegistration").stream()
          .filter(e -> auth.getName().equalsIgnoreCase(String.valueOf(e.get("email"))) && "APPROVED".equalsIgnoreCase(String.valueOf(e.get("status"))))
          .toList();
      }
    }
    m.addAttribute("employers", employers);
    m.addAttribute("canSelectEmployer", hasRole(auth, "ROLE_ADMIN") || hasRole(auth, "ROLE_RECRUITER"));
    return "job-form";
  }
  @PostMapping("/jobs/save") public String saveJob(@RequestParam Map<String,String> form, Authentication auth, RedirectAttributes ra) {
    String employerId = form.getOrDefault("employerId", "").trim();
    Map<String,Object> employer = null;
    if (!employerId.isBlank()) employer = store.find("employer", employerId).orElse(null);
    if (employer == null && hasRole(auth, "ROLE_EMPLOYER")) {
      employer = store.findAll("employer").stream().filter(e -> auth.getName().equalsIgnoreCase(String.valueOf(e.get("email")))).findFirst().orElse(null);
      if (employer != null) employerId = String.valueOf(employer.get("id"));
    }
    if (employer == null) {
      ra.addFlashAttribute("error", "Select an approved employer from the dropdown before publishing this job."); return "redirect:/jobs/new";
    }
    if (hasRole(auth, "ROLE_EMPLOYER") && employer != null && !auth.getName().equalsIgnoreCase(String.valueOf(employer.get("email")))) {
      ra.addFlashAttribute("error", "Employer accounts can publish jobs only for their own company."); return "redirect:/jobs/new";
    }
    String id=newId(); Map<String,Object> r=new LinkedHashMap<>(form);
    if (employer != null) {
      r.put("employerId", employerId);
      r.put("company", firstNonBlank(employer.get("companyName"), employer.get("company"), employer.get("name")));
      r.put("employerEmail", employer.get("email"));
    } else {
      r.putIfAbsent("company", "ASMJ Recruitment");
    }
    r.put("id",id); r.putIfAbsent("status","OPEN"); r.put("createdAt",LocalDate.now().toString()); r.put("postedBy",auth.getName());
    store.save("job",id,r); ra.addFlashAttribute("success","Job opening published and linked to the selected employer."); return "redirect:/jobs";
  }
  @PostMapping("/jobs/{id}/apply") public String apply(@PathVariable String id, @RequestParam String fullName, @RequestParam String email, @RequestParam(required=false) String phone, RedirectAttributes ra) {
    Map<String,Object> job=store.find("job",id).orElseThrow(); String appId=newId(); Map<String,Object> a=new LinkedHashMap<>(); a.put("id",appId); a.put("jobId",id); a.put("jobTitle",job.get("title")); a.put("fullName",fullName); a.put("email",normalizedEmail(email)); a.put("phone",phone); a.put("status","APPLIED"); a.put("appliedAt",LocalDate.now().toString()); store.save("application",appId,a); ra.addFlashAttribute("success","Application submitted. Reference: "+appId); return "redirect:/opportunity/"+id;
  }
  @GetMapping("/applications") public String applications(Model m) { m.addAttribute("records",store.findAll("application")); return "applications"; }
  @PostMapping("/applications/{id}/status") public String applicationStatus(@PathVariable String id,@RequestParam String status,RedirectAttributes ra) { Map<String,Object> a=store.find("application",id).orElseThrow(); a.put("status",status); store.save("application",id,a); ra.addFlashAttribute("success","Application status updated."); return "redirect:/applications"; }
  @GetMapping("/interviews") public String interviews(Model m) {
    m.addAttribute("records",store.findAll("interview"));
    m.addAttribute("applications",store.findAll("application"));
    m.addAttribute("candidates",store.findAll("candidate"));
    m.addAttribute("jobs",store.findAll("job").stream().filter(j -> !"CLOSED".equalsIgnoreCase(String.valueOf(j.get("status")))).toList());
    return "interviews";
  }
  @PostMapping("/interviews/save") public String saveInterview(@RequestParam Map<String,String> form,RedirectAttributes ra) {
    Optional<Map<String,Object>> candidate = store.find("candidate", form.getOrDefault("candidateId", ""));
    Optional<Map<String,Object>> job = store.find("job", form.getOrDefault("jobId", ""));
    if (candidate.isEmpty() || job.isEmpty()) { ra.addFlashAttribute("error", "Select a valid candidate and job from the dropdowns."); return "redirect:/interviews"; }
    String id=newId(); Map<String,Object> r=new LinkedHashMap<>(form);
    r.put("candidateName", candidate.get().get("fullName")); r.put("email", candidate.get().get("email"));
    r.put("jobTitle", job.get().get("title")); r.put("company", job.get().get("company"));
    r.remove("candidateId"); r.remove("jobId"); r.put("id",id); r.put("status","SCHEDULED");
    store.save("interview",id,r); ra.addFlashAttribute("success","Interview scheduled for the selected candidate and job."); return "redirect:/interviews";
  }
  @GetMapping("/placements") public String placements(Model m) {
    m.addAttribute("records",store.findAll("placement"));
    m.addAttribute("applications",store.findAll("application"));
    m.addAttribute("candidates",store.findAll("candidate"));
    m.addAttribute("employers",store.findAll("employer"));
    m.addAttribute("jobs",store.findAll("job"));
    return "placements";
  }
  @PostMapping("/placements/save") public String savePlacement(@RequestParam Map<String,String> form,RedirectAttributes ra) {
    Optional<Map<String,Object>> candidate = store.find("candidate", form.getOrDefault("candidateId", ""));
    Optional<Map<String,Object>> employer = store.find("employer", form.getOrDefault("employerId", ""));
    Optional<Map<String,Object>> job = store.find("job", form.getOrDefault("jobId", ""));
    if (candidate.isEmpty() || employer.isEmpty() || job.isEmpty()) { ra.addFlashAttribute("error", "Select a valid candidate, employer and job from the dropdowns."); return "redirect:/placements"; }
    String id=newId(); Map<String,Object> r=new LinkedHashMap<>(form);
    r.put("candidateName", candidate.get().get("fullName")); r.put("email", candidate.get().get("email"));
    r.put("company", firstNonBlank(employer.get().get("companyName"), employer.get().get("company"), employer.get().get("name")));
    r.put("jobTitle", job.get().get("title")); r.remove("candidateId"); r.remove("employerId"); r.remove("jobId");
    r.put("id",id); r.put("status","PLACED"); r.put("placedAt",LocalDate.now().toString()); store.save("placement",id,r); ra.addFlashAttribute("success","Placement recorded for the selected candidate, employer and job."); return "redirect:/placements";
  }
  @GetMapping("/reports") public String reports(Model m) { m.addAttribute("candidates",store.count("candidate")); m.addAttribute("employers",store.count("employer")); m.addAttribute("jobs",store.count("job")); m.addAttribute("applications",store.count("application")); m.addAttribute("interviews",store.count("interview")); m.addAttribute("placements",store.count("placement")); return "reports"; }
  @GetMapping("/portal") public String portal(Authentication auth,Model m) { m.addAttribute("username",auth.getName()); m.addAttribute("applications",store.findAll("application").stream().filter(a -> auth.getName().equals(a.get("email"))).toList()); return "portal"; }
  private Object firstNonBlank(Object... values) {
    for (Object value : values) if (value != null && !String.valueOf(value).isBlank() && !"null".equals(String.valueOf(value))) return value;
    return "";
  }
  private String newId() { return UUID.randomUUID().toString().substring(0,8).toUpperCase(); }
  private String normalizedEmail(String email) { return email == null ? "" : email.trim().toLowerCase(Locale.ROOT); }
}

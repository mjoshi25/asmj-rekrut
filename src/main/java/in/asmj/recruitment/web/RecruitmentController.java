package in.asmj.recruitment.web;

import in.asmj.recruitment.store.RecordStore;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.beans.factory.ObjectProvider;
import in.asmj.recruitment.service.MeetingNotificationService;
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
  private final ObjectProvider<MeetingNotificationService> meetingNotifications;

  public RecruitmentController(RecordStore store, UserDetailsManager users, PasswordEncoder passwordEncoder, ObjectProvider<MeetingNotificationService> meetingNotifications) {
    this.store = store; this.users = users; this.passwordEncoder = passwordEncoder; this.meetingNotifications = meetingNotifications;
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
    m.addAttribute("jobs", store.findAll("job").stream().filter(j -> auth.getName().equalsIgnoreCase(String.valueOf(j.get("employerEmail")))).toList());
    m.addAttribute("jobCount", store.findAll("job").stream().filter(j -> auth.getName().equalsIgnoreCase(String.valueOf(j.get("employerEmail")))).count());
    m.addAttribute("applicationCount", store.findAll("application").stream().filter(a -> store.find("job", String.valueOf(a.get("jobId"))).map(j -> auth.getName().equalsIgnoreCase(String.valueOf(j.get("employerEmail")))).orElse(false)).count());
    return "employer-dashboard";
  }

  @GetMapping("/employer/authorizations") public String employerAuthorizations(Model m, Authentication auth) {
    Map<String,Object> employer = findEmployerForEmail(auth.getName());
    m.addAttribute("employer", employer);
    m.addAttribute("recruiters", store.findAll("recruiterRegistration").stream().filter(r -> "APPROVED".equalsIgnoreCase(String.valueOf(r.get("status")))).toList());
    String employerId = employer == null ? "" : String.valueOf(employer.get("id"));
    m.addAttribute("authorizations", store.findAll("recruiterAuthorization").stream().filter(a -> employerId.equals(String.valueOf(a.get("employerId")))).toList());
    return "employer-authorizations";
  }

  @PostMapping("/employer/authorizations/grant") public String grantRecruiterAuthorization(@RequestParam String recruiterId, Authentication auth, RedirectAttributes ra) {
    Map<String,Object> employer = findEmployerForEmail(auth.getName());
    Optional<Map<String,Object>> recruiterOpt = store.find("recruiterRegistration", recruiterId);
    if (employer == null || recruiterOpt.isEmpty() || !"APPROVED".equalsIgnoreCase(String.valueOf(recruiterOpt.get().get("status")))) {
      ra.addFlashAttribute("error", "Choose a valid approved recruiter and ensure your employer account is approved.");
      return "redirect:/employer/authorizations";
    }
    Map<String,Object> recruiter = recruiterOpt.get();
    String employerId = String.valueOf(employer.get("id"));
    String recruiterEmail = normalizedEmail(String.valueOf(recruiter.get("email")));
    Map<String,Object> existing = store.findAll("recruiterAuthorization").stream()
      .filter(a -> employerId.equals(String.valueOf(a.get("employerId"))) && recruiterEmail.equalsIgnoreCase(String.valueOf(a.get("recruiterEmail"))))
      .findFirst().orElse(null);
    Map<String,Object> authorization = existing == null ? new LinkedHashMap<>() : new LinkedHashMap<>(existing);
    String id = existing == null ? newId() : String.valueOf(existing.get("id"));
    authorization.put("id", id); authorization.put("employerId", employerId);
    authorization.put("employerEmail", auth.getName());
    authorization.put("companyName", firstNonBlank(employer.get("companyName"), employer.get("company"), employer.get("name")));
    authorization.put("recruiterId", recruiterId); authorization.put("recruiterEmail", recruiterEmail);
    authorization.put("recruiterName", firstNonBlank(recruiter.get("fullName"), recruiter.get("name"), recruiter.get("agencyName")));
    authorization.put("status", "ACTIVE"); authorization.put("grantedAt", LocalDate.now().toString());
    authorization.put("canPostJobs", true); authorization.put("canManageCandidates", true);
    store.save("recruiterAuthorization", id, authorization);
    ra.addFlashAttribute("success", "Recruiter authorized. They can now select your company when posting jobs.");
    return "redirect:/employer/authorizations";
  }

  @PostMapping("/employer/authorizations/{id}/revoke") public String revokeRecruiterAuthorization(@PathVariable String id, Authentication auth, RedirectAttributes ra) {
    Optional<Map<String,Object>> found = store.find("recruiterAuthorization", id);
    if (found.isPresent() && auth.getName().equalsIgnoreCase(String.valueOf(found.get().get("employerEmail")))) {
      Map<String,Object> record = found.get(); record.put("status", "REVOKED"); record.put("revokedAt", LocalDate.now().toString()); store.save("recruiterAuthorization", id, record);
      ra.addFlashAttribute("success", "Recruiter authorization revoked. They can no longer post new jobs for your company.");
    } else ra.addFlashAttribute("error", "Authorization not found for your company.");
    return "redirect:/employer/authorizations";
  }

  private Map<String,Object> findEmployerForEmail(String email) {
    return store.findAll("employer").stream().filter(e -> email.equalsIgnoreCase(String.valueOf(e.get("email"))) && "APPROVED".equalsIgnoreCase(String.valueOf(e.get("status"))))
      .findFirst().orElseGet(() -> store.findAll("employerRegistration").stream().filter(e -> email.equalsIgnoreCase(String.valueOf(e.get("email"))) && "APPROVED".equalsIgnoreCase(String.valueOf(e.get("status")))).findFirst().orElse(null));
  }
  private void populateDashboard(Model m, Authentication auth) {
    m.addAttribute("candidateCount",store.count("candidate")); m.addAttribute("employerCount",store.count("employer"));
    m.addAttribute("jobCount",store.count("job")); m.addAttribute("applicationCount",store.count("application"));
    m.addAttribute("recentJobs",store.findAll("job").stream().limit(5).toList()); m.addAttribute("name",auth.getName()); m.addAttribute("adminChecklist", store.find("settings", "adminGettingStarted").orElse(Map.of("items", List.of())));
  }
  private boolean hasRole(Authentication auth, String role) {
    return auth != null && auth.getAuthorities().stream().anyMatch(a -> role.equals(a.getAuthority()));
  }
  @GetMapping("/jobs") public String jobs(@RequestParam(required=false) String q, Model m) {
    List<Map<String,Object>> all = store.findAll("job").stream().filter(j -> !"CLOSED".equals(j.get("status"))).toList();
    if (q != null && !q.isBlank()) all = all.stream().filter(j -> String.valueOf(j.get("title")).toLowerCase().contains(q.toLowerCase()) || String.valueOf(j.get("location")).toLowerCase().contains(q.toLowerCase())).toList();
    m.addAttribute("jobs",all); m.addAttribute("q",q==null?"":q); return "jobs";
  }
  @GetMapping("/opportunity/{id}") public String job(@PathVariable String id, Model m, Authentication auth) {
    Map<String,Object> job=store.find("job",id).orElseThrow(); m.addAttribute("job",job);
    Map<String,Object> candidateProfile=null;
    if(auth!=null && hasRole(auth,"ROLE_CANDIDATE")) candidateProfile=store.findAll("candidate").stream().filter(c->auth.getName().equalsIgnoreCase(String.valueOf(c.get("email")))).findFirst().orElse(null);
    m.addAttribute("candidateProfile",candidateProfile); return "job-detail";
  }

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
      .filter(e -> "APPROVED".equalsIgnoreCase(String.valueOf(e.get("status"))))
      .toList();
    if (hasRole(auth, "ROLE_EMPLOYER")) {
      employers = employers.stream().filter(e -> auth.getName().equalsIgnoreCase(String.valueOf(e.get("email")))).toList();
    } else if (hasRole(auth, "ROLE_RECRUITER")) {
      Set<String> authorizedIds = new HashSet<>();
      store.findAll("recruiterAuthorization").stream()
        .filter(a -> auth.getName().equalsIgnoreCase(String.valueOf(a.get("recruiterEmail"))) && "ACTIVE".equalsIgnoreCase(String.valueOf(a.get("status"))) && Boolean.TRUE.equals(a.get("canPostJobs")))
        .forEach(a -> authorizedIds.add(String.valueOf(a.get("employerId"))));
      employers = employers.stream().filter(e -> authorizedIds.contains(String.valueOf(e.get("id")))).toList();
    }
    m.addAttribute("employers", employers);
    m.addAttribute("canSelectEmployer", hasRole(auth, "ROLE_ADMIN") || hasRole(auth, "ROLE_RECRUITER"));
    m.addAttribute("isRecruiter", hasRole(auth, "ROLE_RECRUITER"));
    return "job-form";
  }
  @PostMapping("/jobs/save") public String saveJob(@RequestParam Map<String,String> form, Authentication auth, RedirectAttributes ra) {
    String employerId = form.getOrDefault("employerId", "").trim();
    Map<String,Object> employer = employerId.isBlank() ? null : store.find("employer", employerId).orElse(null);
    if (employer == null || !"APPROVED".equalsIgnoreCase(String.valueOf(employer.get("status")))) {
      ra.addFlashAttribute("error", "Select an approved employer from the dropdown before publishing this job."); return "redirect:/jobs/new";
    }
    if (hasRole(auth, "ROLE_EMPLOYER") && !auth.getName().equalsIgnoreCase(String.valueOf(employer.get("email")))) {
      ra.addFlashAttribute("error", "Employer accounts can publish jobs only for their own company."); return "redirect:/jobs/new";
    }
    if (hasRole(auth, "ROLE_RECRUITER")) {
      boolean authorized = store.findAll("recruiterAuthorization").stream().anyMatch(a -> auth.getName().equalsIgnoreCase(String.valueOf(a.get("recruiterEmail"))) && employerId.equals(String.valueOf(a.get("employerId"))) && "ACTIVE".equalsIgnoreCase(String.valueOf(a.get("status"))) && Boolean.TRUE.equals(a.get("canPostJobs")));
      if (!authorized) { ra.addFlashAttribute("error", "This employer has not authorized you to post jobs. Ask the employer to grant access first."); return "redirect:/jobs/new"; }
    }
    Map<String,Object> r=new LinkedHashMap<>(form);
    r.put("employerId", employerId);
    r.put("company", firstNonBlank(employer.get("companyName"), employer.get("company"), employer.get("name")));
    r.put("employerEmail", employer.get("email"));
    r.put("id",newId()); r.putIfAbsent("status","OPEN"); r.put("createdAt",LocalDate.now().toString()); r.put("postedBy",auth.getName()); r.put("postedByRole", hasRole(auth,"ROLE_RECRUITER") ? "RECRUITER" : hasRole(auth,"ROLE_EMPLOYER") ? "EMPLOYER" : "ADMIN"); if(hasRole(auth,"ROLE_RECRUITER")) r.put("recruiterEmail",auth.getName());
    store.save("job",String.valueOf(r.get("id")),r); ra.addFlashAttribute("success","Job opening published and linked to the selected employer."); return "redirect:/jobs";
  }
  @PostMapping("/jobs/{id}/apply") public String apply(@PathVariable String id, @RequestParam String fullName, @RequestParam String email, @RequestParam(required=false) String phone, @RequestParam(required=false) String alternateMobile, Authentication auth, RedirectAttributes ra) {
    // Defense in depth: applying is candidate-only even if this controller is invoked
    // outside the expected Spring Security request flow.
    if (auth == null || !auth.isAuthenticated() || !hasRole(auth, "ROLE_CANDIDATE")) {
      ra.addFlashAttribute("error", "Please sign in with your candidate account to apply for this job.");
      return "redirect:/login";
    }
    Map<String,Object> job=store.find("job",id).orElseThrow(); String normalized=normalizedEmail(auth.getName());
    String lookupEmail=normalized; Map<String,Object> candidate=store.findAll("candidate").stream().filter(c->lookupEmail.equalsIgnoreCase(String.valueOf(c.get("email")))).findFirst().orElse(null);
    if(candidate!=null){fullName=String.valueOf(candidate.getOrDefault("fullName",fullName));phone=String.valueOf(candidate.getOrDefault("mobile",candidate.getOrDefault("phone",phone==null?"":phone)));alternateMobile=String.valueOf(candidate.getOrDefault("alternateMobile",alternateMobile==null?"":alternateMobile));}
    String appId=newId(); Map<String,Object> a=new LinkedHashMap<>(); a.put("id",appId); a.put("jobId",id); a.put("jobTitle",job.get("title")); a.put("company",job.get("company")); a.put("employerId",job.get("employerId")); a.put("employerEmail",job.get("employerEmail")); a.put("recruiterEmail",job.getOrDefault("recruiterEmail","")); a.put("candidateId",candidate==null?"":candidate.get("id")); a.put("fullName",fullName); a.put("email",normalized); a.put("phone",phone); a.put("alternateMobile",alternateMobile); a.put("status","APPLIED"); a.put("appliedAt",LocalDate.now().toString()); store.save("application",appId,a); ra.addFlashAttribute("success","Application submitted. Reference: "+appId); return "redirect:/opportunity/"+id;
  }
  @GetMapping("/applications") public String applications(Model m, Authentication auth) {
    List<Map<String,Object>> records = store.findAll("application");
    if (!hasRole(auth, "ROLE_ADMIN")) {
      if (hasRole(auth, "ROLE_EMPLOYER")) records = records.stream().filter(a -> auth.getName().equalsIgnoreCase(String.valueOf(a.get("employerEmail")))).toList();
      else if (hasRole(auth, "ROLE_RECRUITER")) records = records.stream().filter(a -> auth.getName().equalsIgnoreCase(String.valueOf(a.get("recruiterEmail"))) && store.findAll("recruiterAuthorization").stream().anyMatch(x -> auth.getName().equalsIgnoreCase(String.valueOf(x.get("recruiterEmail"))) && String.valueOf(a.get("employerId")).equals(String.valueOf(x.get("employerId"))) && "ACTIVE".equalsIgnoreCase(String.valueOf(x.get("status"))) && Boolean.TRUE.equals(x.get("canManageCandidates")))).toList();
      else records = List.of();
    }
    // Older application records predate the hiring workflow and do not contain offerStatus.
    // Populate display defaults before Thymeleaf evaluates applications.html.
    for (Map<String,Object> record : records) {
      record.putIfAbsent("offerStatus", "");
      record.putIfAbsent("documentRequestStatus", "");
      record.putIfAbsent("status", "APPLIED");
      record.putIfAbsent("appliedAt", "");
      record.putIfAbsent("fullName", "Candidate");
      record.putIfAbsent("email", "");
      record.putIfAbsent("jobTitle", "");
    }
    m.addAttribute("records", records); return "applications";
  }
  @PostMapping("/applications/{id}/status") public String applicationStatus(@PathVariable String id,@RequestParam String status,RedirectAttributes ra) {
    if(!Set.of("APPLIED","SHORTLISTED","INTERVIEW","OFFERED","SELECTED","HIRED","DOCUMENTS_RECEIVED","OFFER_SENT","OFFER_ACCEPTED","JOINED","REJECTED").contains(status)){ra.addFlashAttribute("error","Invalid application status.");return "redirect:/applications";}
    Map<String,Object> a=store.find("application",id).orElseThrow();
    if (!canManageApplication(a, org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication())) { ra.addFlashAttribute("error", "You are not authorized to update this application."); return "redirect:/applications"; }
    String previous = String.valueOf(a.getOrDefault("status", "APPLIED"));
    if (!status.equals(previous)) {
      String actor = Optional.ofNullable(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()).map(Authentication::getName).orElse("system");
      Map<String,Object> audit = new LinkedHashMap<>(); audit.put("id", newId()); audit.put("applicationId", id); audit.put("candidateEmail", a.get("email")); audit.put("fromStatus", previous); audit.put("toStatus", status); audit.put("changedBy", actor); audit.put("changedAt", java.time.Instant.now().toString()); store.save("applicationStatusAudit", String.valueOf(audit.get("id")), audit);
      if (a.get("statusHistory") instanceof List<?> oldHistory) { List<Object> history = new ArrayList<>(oldHistory); history.add(audit); a.put("statusHistory", history); } else a.put("statusHistory", new ArrayList<>(List.of(audit)));
      String notificationId = newId(); Map<String,Object> n = new LinkedHashMap<>(); n.put("id", notificationId); n.put("candidateEmail", a.get("email")); n.put("recipientEmail", a.get("email")); n.put("applicationId", id); n.put("type", "APPLICATION_STATUS_CHANGED"); n.put("message", "Your application for " + a.getOrDefault("jobTitle", "the role") + " changed from " + previous.replace('_',' ') + " to " + status.replace('_',' ') + "."); n.put("createdAt", java.time.Instant.now().toString()); n.put("read", false); store.save("notification", notificationId, n);
    }
    a.put("status",status); a.put("statusUpdatedAt",LocalDate.now().toString());
    if ("HIRED".equals(status) && !"HIRED".equals(previous)) {
      a.put("documentRequestStatus", "REQUESTED"); a.put("documentsRequestedAt", LocalDate.now().toString());
      String notificationId = newId(); Map<String,Object> n = new LinkedHashMap<>(); n.put("id", notificationId); n.put("candidateEmail", a.get("email")); n.put("applicationId", id); n.put("type", "JOINING_DOCUMENTS_REQUIRED"); n.put("message", "Congratulations! You have been marked Hired for " + a.getOrDefault("jobTitle", "the role") + ". Please upload your joining documents."); n.put("createdAt", LocalDate.now().toString()); n.put("read", false); store.save("notification", notificationId, n);
    }
    store.save("application",id,a);
    if(Set.of("SELECTED","HIRED","OFFER_SENT","OFFER_ACCEPTED","JOINED").contains(status)) createCommissionIfMissing(a);
    ra.addFlashAttribute("success", "Application stage updated to " + status.replace('_',' ') + ". Status history and candidate notification recorded." + (Set.of("SELECTED","HIRED","OFFER_SENT","OFFER_ACCEPTED","JOINED").contains(status) ? " Commission record created or confirmed." : "")); return "redirect:/applications";
  }
  private boolean canManageApplication(Map<String,Object> app, Authentication auth) {
    if (auth == null) return false;
    if (hasRole(auth, "ROLE_ADMIN")) return true;
    String employerEmail = String.valueOf(app.getOrDefault("employerEmail", ""));
    if (hasRole(auth, "ROLE_EMPLOYER")) return auth.getName().equalsIgnoreCase(employerEmail);
    if (hasRole(auth, "ROLE_RECRUITER")) {
      String employerId = String.valueOf(app.getOrDefault("employerId", ""));
      return auth.getName().equalsIgnoreCase(String.valueOf(app.getOrDefault("recruiterEmail", ""))) && store.findAll("recruiterAuthorization").stream().anyMatch(x -> auth.getName().equalsIgnoreCase(String.valueOf(x.get("recruiterEmail"))) && employerId.equals(String.valueOf(x.get("employerId"))) && "ACTIVE".equalsIgnoreCase(String.valueOf(x.get("status"))) && Boolean.TRUE.equals(x.get("canManageCandidates")));
    }
    return false;
  }
  private void createCommissionIfMissing(Map<String,Object> application) {
    boolean exists=store.findAll("commission").stream().anyMatch(c->String.valueOf(application.get("id")).equals(String.valueOf(c.get("applicationId")))); if(exists)return;
    Map<String,Object> job=store.find("job",String.valueOf(application.get("jobId"))).orElse(new LinkedHashMap<>());
    Map<String,Object> cfg=store.find("settings","commission").orElse(Map.of("type","PERCENT","value",8));
    String type=String.valueOf(cfg.getOrDefault("type","PERCENT")); double value=parseNumber(cfg.get("value")); double salary=parseSalary(job.get("salary"));
    boolean salaryRequired="PERCENT".equals(type) && salary<=0;
    double amount=salaryRequired?0.0:("FIXED".equals(type)?value:Math.round((salary*value/100.0)*100.0)/100.0);
    String id=newId(); Map<String,Object> c=new LinkedHashMap<>(); c.put("id",id); c.put("applicationId",application.get("id")); c.put("candidateId",application.getOrDefault("candidateId","")); c.put("candidateName",application.get("fullName")); c.put("candidateEmail",application.get("email")); c.put("jobId",application.get("jobId")); c.put("jobTitle",application.get("jobTitle")); c.put("employerId",application.getOrDefault("employerId",job.get("employerId"))); c.put("company",application.getOrDefault("company",job.get("company"))); c.put("employerEmail",application.getOrDefault("employerEmail",job.get("employerEmail"))); c.put("recruiterEmail",application.getOrDefault("recruiterEmail",job.getOrDefault("recruiterEmail",""))); c.put("modelType",type); c.put("modelValue",value); c.put("salaryBase",salary); c.put("amount",amount); c.put("currency","INR"); c.put("paymentStatus","PENDING"); c.put("calculationStatus",salaryRequired?"NEEDS_SALARY":"CALCULATED"); c.put("createdAt",LocalDate.now().toString()); store.save("commission",id,c);
  }
  private double parseNumber(Object v){if(v==null)return 0;String s=String.valueOf(v).replaceAll("[^0-9.]","");try{return s.isBlank()?0:Double.parseDouble(s);}catch(Exception e){return 0;}}
  /** Accept only one full annual INR amount; never infer LPA, monthly pay, or a range. */
  private double parseSalary(Object v){
    if(v==null)return 0;
    String raw=String.valueOf(v).trim().toLowerCase(Locale.ROOT);
    if(raw.isBlank() || raw.matches(".*(lpa|lakh|lac|crore|\\bcr\\b|per month|monthly|/month|salary range).*"))return 0;
    if(raw.matches(".*(–|—|\\bto\\b).*"))return 0;
    if(raw.matches(".*[0-9]\\s*-\\s*[₹$]?[0-9].*"))return 0;
    String normalized=raw.replace(",", "");
    java.util.regex.Matcher m=java.util.regex.Pattern.compile("[0-9]+(?:\\.[0-9]+)?").matcher(normalized);
    if(!m.find())return 0;
    String number=m.group();
    if(m.find())return 0;
    try { double amount=Double.parseDouble(number); return Double.isFinite(amount)&&amount>0&&amount<=100000000000d?amount:0; }
    catch(Exception e){return 0;}
  }
  @GetMapping("/interviews") public String interviews(Model m, Authentication auth) {
    List<Map<String,Object>> visibleJobs = store.findAll("job").stream()
        .filter(j -> !"CLOSED".equalsIgnoreCase(String.valueOf(j.get("status"))))
        .filter(j -> canManageJob(auth, j)).toList();
    Set<String> visibleJobIds = visibleJobs.stream().map(j -> String.valueOf(j.get("id"))).collect(java.util.stream.Collectors.toSet());
    List<Map<String,Object>> visibleApplications = store.findAll("application").stream()
        .filter(a -> visibleJobIds.contains(String.valueOf(a.get("jobId")))).toList();
    Set<String> visibleCandidateEmails = visibleApplications.stream().map(a -> String.valueOf(a.get("email")))
        .collect(java.util.stream.Collectors.toSet());
    m.addAttribute("records", store.findAll("interview").stream()
        .filter(i -> visibleJobIds.contains(String.valueOf(i.get("jobId")))
            && visibleCandidateEmails.contains(String.valueOf(i.get("email")))).toList());
    m.addAttribute("applications", visibleApplications);
    m.addAttribute("candidates", store.findAll("candidate").stream()
        .filter(c -> visibleCandidateEmails.contains(String.valueOf(c.get("email")))).toList());
    m.addAttribute("jobs", visibleJobs);
    return "interviews";
  }

  private boolean canManageJob(Authentication auth, Map<String,Object> job) {
    if (auth == null || job == null) return false;
    if (hasRole(auth, "ROLE_ADMIN")) return true;
    if (hasRole(auth, "ROLE_EMPLOYER")) return auth.getName().equalsIgnoreCase(String.valueOf(job.getOrDefault("employerEmail", "")));
    if (hasRole(auth, "ROLE_RECRUITER")) {
      String employerId = String.valueOf(job.getOrDefault("employerId", ""));
      return auth.getName().equalsIgnoreCase(String.valueOf(job.getOrDefault("recruiterEmail", "")))
          && store.findAll("recruiterAuthorization").stream().anyMatch(a ->
              auth.getName().equalsIgnoreCase(String.valueOf(a.get("recruiterEmail")))
              && employerId.equals(String.valueOf(a.get("employerId")))
              && "ACTIVE".equalsIgnoreCase(String.valueOf(a.get("status")))
              && Boolean.TRUE.equals(a.get("canManageCandidates")));
    }
    return false;
  }
  @PostMapping("/interviews/save")
  public String saveInterview(
      @RequestParam(value="candidateIds", required=false) List<String> candidateIds,
      @RequestParam(value="candidateId", required=false) String legacyCandidateId,
      @RequestParam String jobId,
      @RequestParam String interviewer,
      @RequestParam String scheduledAt,
      @RequestParam String interviewType,
      @RequestParam(value="meetingUrl", required=false) String suppliedMeetingUrl,
      @RequestParam(value="notes", required=false) String notes,
      Authentication auth, RedirectAttributes ra) {
    List<String> selectedIds = new ArrayList<>();
    if (candidateIds != null) selectedIds.addAll(candidateIds);
    if (selectedIds.isEmpty() && legacyCandidateId != null && !legacyCandidateId.isBlank()) selectedIds.add(legacyCandidateId);
    selectedIds = selectedIds.stream().filter(Objects::nonNull).map(String::trim).filter(x -> !x.isBlank()).distinct().toList();
    Optional<Map<String,Object>> jobOpt = store.find("job", jobId);
    if (selectedIds.isEmpty() || jobOpt.isEmpty()) {
      ra.addFlashAttribute("error", "Select at least one candidate and a valid job opening.");
      return "redirect:/interviews";
    }
    String provider = interviewType == null ? "Google Meet" : interviewType.trim();
    boolean online = "Google Meet".equals(provider) || "Microsoft Teams".equals(provider);
    String meetingUrl = suppliedMeetingUrl == null ? "" : suppliedMeetingUrl.trim();
    if (online && !validMeetingUrl(provider, meetingUrl)) {
      ra.addFlashAttribute("error", "Enter a valid HTTPS " + provider + " meeting link before notifying participants.");
      return "redirect:/interviews";
    }
    if (!online) meetingUrl = "";
    Map<String,Object> job = jobOpt.get();
    if (!canManageJob(auth, job)) {
      ra.addFlashAttribute("error", "You are not authorized to schedule interviews for this job.");
      return "redirect:/interviews";
    }
    // A user must not be able to attach an unrelated candidate to another employer's job.
    Set<String> eligibleEmails = store.findAll("application").stream()
        .filter(a -> jobId.equals(String.valueOf(a.get("jobId"))))
        .map(a -> String.valueOf(a.get("email"))).collect(java.util.stream.Collectors.toSet());
    String groupId = UUID.randomUUID().toString().replace("-", "");
    int notified = 0;
    int emailSent = 0;
    List<String> invalidCandidates = new ArrayList<>();
    for (String selectedId : selectedIds) {
      Optional<Map<String,Object>> candidateOpt = store.find("candidate", selectedId);
      if (candidateOpt.isEmpty()) { invalidCandidates.add(selectedId); continue; }
      Map<String,Object> candidate = candidateOpt.get();
      String email = String.valueOf(candidate.getOrDefault("email", "")).trim();
      if (email.isBlank() || !email.contains("@") || !eligibleEmails.contains(email)) {
        invalidCandidates.add(String.valueOf(candidate.getOrDefault("fullName", selectedId)));
        continue;
      }
      String id = UUID.randomUUID().toString().replace("-", "");
      Map<String,Object> r = new LinkedHashMap<>();
      r.put("id", id); r.put("groupMeetingId", groupId);
      r.put("candidateId", candidate.getOrDefault("id", selectedId));
      r.put("jobId", job.getOrDefault("id", jobId)); r.put("candidateName", candidate.getOrDefault("fullName", "Candidate"));
      r.put("email", email); r.put("jobTitle", job.getOrDefault("title", "Interview")); r.put("company", job.getOrDefault("company", ""));
      r.put("interviewer", interviewer.trim()); r.put("scheduledAt", scheduledAt);
      r.put("interviewType", provider); r.put("meetingProvider", online ? provider : "");
      r.put("notes", notes == null ? "" : notes.trim()); r.put("meetingUrl", meetingUrl); r.put("status", "SCHEDULED");
      r.put("createdBy", auth == null ? "" : auth.getName()); r.put("createdAt", java.time.Instant.now().toString());
      store.save("interview", id, r);
      String notificationId = newId(); Map<String,Object> n = new LinkedHashMap<>();
      n.put("id", notificationId); n.put("candidateEmail", email); n.put("interviewId", id); n.put("groupMeetingId", groupId); n.put("type", "INTERVIEW_SCHEDULED");
      n.put("message", "An interview has been scheduled for " + r.get("jobTitle") + " on " + scheduledAt + (meetingUrl.isBlank() ? "." : ". Join via " + provider + ": " + meetingUrl));
      n.put("meetingUrl", meetingUrl); n.put("meetingProvider", provider); n.put("scheduledAt", scheduledAt); n.put("createdAt", java.time.Instant.now().toString()); n.put("read", false);
      store.save("notification", notificationId, n);
      notified++;
      if (meetingNotifications.getIfAvailable() != null && meetingNotifications.getIfAvailable().sendInterviewInvite(r)) emailSent++;
    }
    if (notified == 0) {
      ra.addFlashAttribute("error", "No invitations were created. Check that the selected candidates exist and have valid email addresses.");
    } else {
      String msg = "Interview invitation saved for " + notified + " participant(s).";
      if (emailSent > 0) msg += " Email sent to " + emailSent + " participant(s).";
      else msg += " Portal notifications are available; email delivery is not confirmed. Configure SMTP if email notifications are required.";
      if (!invalidCandidates.isEmpty()) msg += " Skipped " + invalidCandidates.size() + " invalid candidate selection(s).";
      ra.addFlashAttribute("success", msg);
    }
    return "redirect:/interviews";
  }

  private boolean validMeetingUrl(String provider, String value) {
    try {
      java.net.URI uri = java.net.URI.create(value);
      if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) return false;
      String host = uri.getHost().toLowerCase(Locale.ROOT);
      if ("Google Meet".equals(provider)) return host.equals("meet.google.com");
      if ("Microsoft Teams".equals(provider)) return host.equals("teams.microsoft.com") || host.equals("teams.live.com");
      return false;
    } catch (Exception ex) { return false; }
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
    r.put("jobTitle", job.get().get("title")); r.put("candidateId", candidate.get().get("id")); r.put("employerId", employer.get().get("id")); r.put("jobId", job.get().get("id")); r.put("employerEmail", employer.get().get("email")); r.put("recruiterEmail", job.get().getOrDefault("recruiterEmail", ""));
    r.put("id",id); r.put("status","PLACED"); r.put("placedAt",LocalDate.now().toString()); store.save("placement",id,r); ra.addFlashAttribute("success","Placement recorded for the selected candidate, employer and job."); return "redirect:/placements";
  }
  @GetMapping("/reports") public String reports(Model m) { m.addAttribute("candidates",store.count("candidate")); m.addAttribute("employers",store.count("employer")); m.addAttribute("jobs",store.count("job")); m.addAttribute("applications",store.count("application")); m.addAttribute("interviews",store.count("interview")); m.addAttribute("placements",store.count("placement")); return "reports"; }
  @GetMapping("/portal") public String portal(Authentication auth,Model m) {
    String email = auth.getName();
    m.addAttribute("username", email);
    m.addAttribute("applications",store.findAll("application").stream().filter(a -> email.equalsIgnoreCase(String.valueOf(a.get("email")))).toList());
    m.addAttribute("interviews", store.findAll("interview").stream().filter(i -> email.equalsIgnoreCase(String.valueOf(i.get("email"))) && !"CANCELLED".equalsIgnoreCase(String.valueOf(i.get("status")))).toList());
    m.addAttribute("notifications", store.findAll("notification").stream().filter(n -> email.equalsIgnoreCase(String.valueOf(n.get("candidateEmail"))) && "INTERVIEW_SCHEDULED".equals(String.valueOf(n.get("type")))).toList());
    return "portal";
  }
  private Object firstNonBlank(Object... values) {
    for (Object value : values) if (value != null && !String.valueOf(value).isBlank() && !"null".equals(String.valueOf(value))) return value;
    return "";
  }
  private String newId() { return UUID.randomUUID().toString().substring(0,8).toUpperCase(); }
  private String normalizedEmail(String email) { return email == null ? "" : email.trim().toLowerCase(Locale.ROOT); }
}

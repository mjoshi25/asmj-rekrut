package in.asmj.recruitment.web;

import in.asmj.recruitment.store.RecordStore;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

@Controller
public class HiringWorkflowController {
  private final RecordStore store;
  private final Path uploadRoot;
  public HiringWorkflowController(RecordStore store, @Value("${asmj.upload-dir:./data/private-uploads}") String uploadDir) {
    this.store = store; this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
  }

  @GetMapping("/candidate/hiring")
  public String candidateHiring(Authentication auth, Model model) {
    List<Map<String,Object>> apps = candidateApps(auth.getName());
    model.addAttribute("applications", apps);
    model.addAttribute("hiredApplications", apps.stream().filter(a -> "HIRED".equalsIgnoreCase(String.valueOf(a.get("status")))).toList());
    model.addAttribute("documents", store.findAll("candidateDocument").stream().filter(d -> auth.getName().equalsIgnoreCase(String.valueOf(d.get("candidateEmail")))).toList());
    model.addAttribute("offers", store.findAll("offerLetter").stream().filter(o -> auth.getName().equalsIgnoreCase(String.valueOf(o.get("candidateEmail")))).toList());
    model.addAttribute("notifications", store.findAll("notification").stream().filter(n -> auth.getName().equalsIgnoreCase(String.valueOf(n.get("candidateEmail")))).toList());
    return "candidate-hiring";
  }

  @PostMapping("/candidate/hiring/{applicationId}/documents")
  public String uploadDocument(@PathVariable String applicationId, @RequestParam String documentType, @RequestParam("file") MultipartFile file, Authentication auth, RedirectAttributes ra) {
    Map<String,Object> app = application(applicationId);
    if (!isCandidateOwner(app, auth)) return deny(ra, "/candidate/hiring");
    if (!"HIRED".equalsIgnoreCase(String.valueOf(app.get("status")))) { ra.addFlashAttribute("error", "Joining documents can be uploaded after the application is marked Hired."); return "redirect:/candidate/hiring"; }
    if (file == null || file.isEmpty() || file.getSize() > 8L * 1024 * 1024) { ra.addFlashAttribute("error", "Choose a file up to 8 MB."); return "redirect:/candidate/hiring"; }
    String safeType = documentType == null ? "Other" : documentType.trim();
    String uploadExt = extension(Objects.requireNonNullElse(file.getOriginalFilename(), "")).toLowerCase(Locale.ROOT);
    if (!Set.of(".pdf", ".png", ".jpg", ".jpeg", ".doc", ".docx").contains(uploadExt)) { ra.addFlashAttribute("error", "Allowed document formats: PDF, PNG, JPG, DOC or DOCX."); return "redirect:/candidate/hiring"; }
    if (!Set.of("Resume / CV", "Government ID", "Address Proof", "Education Certificate", "Experience Letter", "Relieving Letter", "Photograph", "Bank Details", "Other").contains(safeType)) { ra.addFlashAttribute("error", "Choose a valid document type."); return "redirect:/candidate/hiring"; }
    try {
      String id = id(); String original = Paths.get(Objects.requireNonNullElse(file.getOriginalFilename(), "document")).getFileName().toString();
      String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')).replaceAll("[^.A-Za-z0-9]", "") : "";
      Path dir = uploadRoot.resolve("candidates").resolve(safeSegment(String.valueOf(app.get("candidateId")))).resolve(safeSegment(applicationId)).resolve("documents").normalize();
      Files.createDirectories(dir); Path destination = dir.resolve(id + ext).normalize();
      if (!destination.startsWith(uploadRoot)) throw new IOException("Invalid upload path");
      file.transferTo(destination);
      Map<String,Object> d = new LinkedHashMap<>(); d.put("id", id); d.put("applicationId", applicationId); d.put("candidateId", app.getOrDefault("candidateId", "")); d.put("candidateEmail", auth.getName()); d.put("candidateName", app.get("fullName")); d.put("documentType", safeType); d.put("originalFilename", original); d.put("storedPath", destination.toString()); d.put("contentType", Objects.requireNonNullElse(file.getContentType(), "application/octet-stream")); d.put("size", file.getSize()); d.put("uploadedAt", LocalDate.now().toString()); d.put("reviewStatus", "PENDING_REVIEW"); store.save("candidateDocument", id, d);
      app.put("documentRequestStatus", "UPLOADED"); store.save("application", applicationId, app);
      ra.addFlashAttribute("success", "Document uploaded securely. The employer can review it from the application workflow.");
    } catch (Exception e) { ra.addFlashAttribute("error", "Upload failed. Please retry with a file up to 5 MB."); }
    return "redirect:/candidate/hiring";
  }

  @GetMapping("/hiring/applications/{applicationId}/documents")
  public String employerDocuments(@PathVariable String applicationId, Authentication auth, Model model, RedirectAttributes ra) {
    Map<String,Object> app = application(applicationId);
    if (!canEmployerAccess(app, auth)) { ra.addFlashAttribute("error", "You are not authorized to view this candidate's documents."); return "redirect:/applications"; }
    model.addAttribute("application", app);
    model.addAttribute("documents", store.findAll("candidateDocument").stream().filter(d -> applicationId.equals(String.valueOf(d.get("applicationId")))).toList());
    model.addAttribute("offers", store.findAll("offerLetter").stream().filter(o -> applicationId.equals(String.valueOf(o.get("applicationId")))).toList());
    return "hiring-documents";
  }

  @PostMapping("/hiring/applications/{applicationId}/offer")
  public String uploadOffer(@PathVariable String applicationId, @RequestParam(value="file", required=false) MultipartFile file, @RequestParam(required=false) String salary, @RequestParam(required=false) String joiningDate, @RequestParam(required=false) String notes, Authentication auth, RedirectAttributes ra) {
    Map<String,Object> app = application(applicationId);
    if (!canEmployerAccess(app, auth)) return deny(ra, "/applications");
    if (!"HIRED".equalsIgnoreCase(String.valueOf(app.get("status")))) { ra.addFlashAttribute("error", "Mark the candidate Hired before issuing an offer letter."); return "redirect:/hiring/applications/" + applicationId + "/documents"; }
    if (file == null || file.isEmpty() || file.getSize() > 8L * 1024 * 1024) { ra.addFlashAttribute("error", "Choose an offer letter file up to 8 MB."); return "redirect:/hiring/applications/" + applicationId + "/documents"; }
    String offerExt = extension(Objects.requireNonNullElse(file.getOriginalFilename(), "")).toLowerCase(Locale.ROOT);
    if (!Set.of(".pdf", ".doc", ".docx").contains(offerExt)) { ra.addFlashAttribute("error", "Offer letters must be PDF, DOC or DOCX files."); return "redirect:/hiring/applications/" + applicationId + "/documents"; }
    try {
      int version = (int) store.findAll("offerLetter").stream().filter(o -> applicationId.equals(String.valueOf(o.get("applicationId")))).count() + 1;
      String id = id(); String original = Paths.get(Objects.requireNonNullElse(file.getOriginalFilename(), "offer.pdf")).getFileName().toString();
      Path dir = uploadRoot.resolve("candidates").resolve(safeSegment(String.valueOf(app.get("candidateId")))).resolve(safeSegment(applicationId)).resolve("offers").normalize(); Files.createDirectories(dir);
      Path destination = dir.resolve(id + extension(original)).normalize(); if (!destination.startsWith(uploadRoot)) throw new IOException("Invalid path"); file.transferTo(destination);
      Map<String,Object> offer = new LinkedHashMap<>(); offer.put("id", id); offer.put("applicationId", applicationId); offer.put("candidateId", app.getOrDefault("candidateId", "")); offer.put("candidateEmail", app.get("email")); offer.put("employerEmail", auth.getName()); offer.put("version", version); offer.put("originalFilename", original); offer.put("storedPath", destination.toString()); offer.put("contentType", Objects.requireNonNullElse(file.getContentType(), "application/octet-stream")); offer.put("salary", Objects.requireNonNullElse(salary, "")); offer.put("joiningDate", Objects.requireNonNullElse(joiningDate, "")); offer.put("notes", Objects.requireNonNullElse(notes, "")); offer.put("status", "PENDING_CANDIDATE_RESPONSE"); offer.put("uploadedAt", LocalDate.now().toString()); store.save("offerLetter", id, offer);
      for (Map<String,Object> old : store.findAll("offerLetter")) if (applicationId.equals(String.valueOf(old.get("applicationId"))) && !id.equals(String.valueOf(old.get("id"))) && "PENDING_CANDIDATE_RESPONSE".equals(String.valueOf(old.get("status")))) { old.put("status", "SUPERSEDED"); store.save("offerLetter", String.valueOf(old.get("id")), old); }
      app.put("offerStatus", "PENDING_CANDIDATE_RESPONSE"); app.put("latestOfferId", id); store.save("application", applicationId, app);
      ra.addFlashAttribute("success", "Offer letter v" + version + " sent to the candidate.");
    } catch (Exception e) { ra.addFlashAttribute("error", "Could not store the offer letter. Check the file type and size (maximum 8 MB), then retry."); }
    return "redirect:/hiring/applications/" + applicationId + "/documents";
  }

  @PostMapping("/candidate/hiring/offers/{offerId}/respond")
  public String respondOffer(@PathVariable String offerId, @RequestParam String decision, @RequestParam(required=false) String comments, Authentication auth, RedirectAttributes ra) {
    Map<String,Object> offer = store.find("offerLetter", offerId).orElse(null);
    if (offer == null || !auth.getName().equalsIgnoreCase(String.valueOf(offer.get("candidateEmail")))) return deny(ra, "/candidate/hiring");
    if (!"PENDING_CANDIDATE_RESPONSE".equals(String.valueOf(offer.get("status")))) { ra.addFlashAttribute("error", "This offer version is no longer awaiting a response."); return "redirect:/candidate/hiring"; }
    if ("ACCEPT".equals(decision)) { offer.put("status", "ACCEPTED"); offer.put("respondedAt", LocalDate.now().toString()); offer.put("candidateComments", ""); }
    else if ("REVISE".equals(decision)) { if (comments == null || comments.isBlank()) { ra.addFlashAttribute("error", "Please explain the revision you are requesting."); return "redirect:/candidate/hiring"; } offer.put("status", "REVISION_REQUESTED"); offer.put("respondedAt", LocalDate.now().toString()); offer.put("candidateComments", comments.trim()); }
    else { ra.addFlashAttribute("error", "Choose Accept Offer or Request Revision."); return "redirect:/candidate/hiring"; }
    store.save("offerLetter", offerId, offer); Map<String,Object> app = application(String.valueOf(offer.get("applicationId"))); app.put("offerStatus", offer.get("status")); store.save("application", String.valueOf(app.get("id")), app);
    ra.addFlashAttribute("success", "Your response has been recorded and shared with the employer."); return "redirect:/candidate/hiring";
  }

  @PostMapping("/hiring/documents/{documentId}/review")
  public String reviewDocument(@PathVariable String documentId, @RequestParam String status, Authentication auth, RedirectAttributes ra) {
    Map<String,Object> d = store.find("candidateDocument", documentId).orElse(null);
    if (d == null) return deny(ra, "/applications");
    Map<String,Object> app = application(String.valueOf(d.get("applicationId")));
    if (!canEmployerAccess(app, auth)) return deny(ra, "/applications");
    if (!Set.of("VERIFIED", "REUPLOAD_REQUIRED").contains(status)) { ra.addFlashAttribute("error", "Invalid review status."); return "redirect:/hiring/applications/" + app.get("id") + "/documents"; }
    d.put("reviewStatus", status); d.put("reviewedBy", auth.getName()); d.put("reviewedAt", LocalDate.now().toString()); store.save("candidateDocument", documentId, d);
    ra.addFlashAttribute("success", "Document review saved."); return "redirect:/hiring/applications/" + app.get("id") + "/documents";
  }

  @GetMapping("/hiring/files/{kind}/{id}")
  public void download(@PathVariable String kind, @PathVariable String id, Authentication auth, HttpServletResponse response) throws IOException {
    String type = "document".equals(kind) ? "candidateDocument" : "offer".equals(kind) ? "offerLetter" : null;
    if (type == null) { response.sendError(404); return; }
    Map<String,Object> file = store.find(type, id).orElse(null); if (file == null) { response.sendError(404); return; }
    boolean allowed = auth != null && (auth.getName().equalsIgnoreCase(String.valueOf(file.get("candidateEmail"))) || ("offerLetter".equals(type) && auth.getName().equalsIgnoreCase(String.valueOf(file.get("employerEmail")))));
    if (!allowed && auth != null) { Map<String,Object> app = application(String.valueOf(file.get("applicationId"))); allowed = canEmployerAccess(app, auth); }
    if (!allowed) { response.sendError(403); return; }
    Path path = Paths.get(String.valueOf(file.get("storedPath"))).toAbsolutePath().normalize();
    if (!path.startsWith(uploadRoot) || !Files.isRegularFile(path)) { response.sendError(404); return; }
    response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE); response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + String.valueOf(file.get("originalFilename")).replaceAll("[\"\\r\\n]", "") + "\""); Files.copy(path, response.getOutputStream());
  }

  @GetMapping("/founder") public String founder(Model model) {
    Map<String,Object> content = store.find("siteContent", "publicPages").orElse(Map.of());
    model.addAttribute("founderHeadline", content.getOrDefault("founderHeadline", "Building better connections between talent and opportunity."));
    model.addAttribute("founderIntro", content.getOrDefault("founderIntro", "ASMJ Recruitment is designed to make the journey between candidates, employers and recruiters more transparent, organized and human."));
    model.addAttribute("founderName", content.getOrDefault("founderName", ""));
    model.addAttribute("founderStory", content.getOrDefault("founderStory", "Our aim is to help people discover meaningful career opportunities while giving employers and authorized recruiters a clear process to manage applications, hiring decisions, joining documents and offer letters."));
    return "founder";
  }
  @GetMapping("/compliance") public String compliance(Model model) {
    Map<String,Object> content = store.find("siteContent", "publicPages").orElse(Map.of());
    model.addAttribute("privacyPolicy", content.getOrDefault("privacyPolicy", "ASMJ Recruitment uses account and application information to provide recruitment services, support hiring decisions and communicate status updates. Access to candidate information should be limited to authorized users who need it to perform their duties."));
    model.addAttribute("termsPolicy", content.getOrDefault("termsPolicy", "Users are responsible for the accuracy and lawful submission of information they provide. Access may be restricted for misuse, unauthorized disclosure, fraudulent activity or violations of platform policies."));
    model.addAttribute("complianceContact", content.getOrDefault("complianceContact", "Contact the ASMJ platform administrator to report a concern."));
    return "compliance";
  }
  @GetMapping("/admin/site-content") public String siteContent(Model model) {
    Map<String,Object> content = new LinkedHashMap<>(store.find("siteContent", "publicPages").orElse(Map.of()));
    content.putIfAbsent("founderHeadline", "Building better connections between talent and opportunity.");
    content.putIfAbsent("founderIntro", "ASMJ Recruitment is designed to make the journey between candidates, employers and recruiters more transparent, organized and human.");
    content.putIfAbsent("founderName", "");
    content.putIfAbsent("founderStory", "Our aim is to help people discover meaningful career opportunities while giving employers and authorized recruiters a clear process to manage applications, hiring decisions, joining documents and offer letters.");
    content.putIfAbsent("privacyPolicy", "ASMJ Recruitment uses account and application information to provide recruitment services, support hiring decisions and communicate status updates. Access to candidate information should be limited to authorized users who need it to perform their duties.");
    content.putIfAbsent("termsPolicy", "Users are responsible for the accuracy and lawful submission of information they provide. Access may be restricted for misuse, unauthorized disclosure, fraudulent activity or violations of platform policies.");
    content.putIfAbsent("complianceContact", "Contact the ASMJ platform administrator to report a concern.");
    model.addAttribute("content", content); return "admin-site-content";
  }
  @PostMapping("/admin/site-content") public String saveSiteContent(@RequestParam Map<String,String> form, Authentication auth, RedirectAttributes ra) {
    Map<String,Object> content = new LinkedHashMap<>();
    for (String key : List.of("founderHeadline", "founderIntro", "founderName", "founderStory", "privacyPolicy", "termsPolicy", "complianceContact")) content.put(key, Objects.requireNonNullElse(form.get(key), "").trim());
    if (String.valueOf(content.get("founderHeadline")).isBlank() || String.valueOf(content.get("founderIntro")).isBlank()
        || String.valueOf(content.get("founderStory")).isBlank() || String.valueOf(content.get("privacyPolicy")).isBlank()
        || String.valueOf(content.get("termsPolicy")).isBlank() || String.valueOf(content.get("complianceContact")).isBlank()) {
      ra.addFlashAttribute("error", "Please complete all required public page fields before saving.");
      return "redirect:/admin/site-content";
    }
    content.put("updatedAt", LocalDate.now().toString()); content.put("updatedBy", auth == null ? "unknown" : auth.getName()); store.save("siteContent", "publicPages", content);
    ra.addFlashAttribute("success", "Founder and compliance page content saved."); return "redirect:/admin/site-content";
  }

  private List<Map<String,Object>> candidateApps(String email) { return store.findAll("application").stream().filter(a -> email.equalsIgnoreCase(String.valueOf(a.get("email")))).toList(); }
  private Map<String,Object> application(String id) { return store.find("application", id).orElseThrow(() -> new NoSuchElementException("Application not found")); }
  private boolean isCandidateOwner(Map<String,Object> app, Authentication auth) { return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CANDIDATE")) && auth.getName().equalsIgnoreCase(String.valueOf(app.get("email"))); }
  private boolean canEmployerAccess(Map<String,Object> app, Authentication auth) {
    if (auth == null) return false;
    if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) return true;
    if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_EMPLOYER"))) return auth.getName().equalsIgnoreCase(String.valueOf(app.get("employerEmail")));
    if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_RECRUITER"))) return auth.getName().equalsIgnoreCase(String.valueOf(app.get("recruiterEmail"))) && store.findAll("recruiterAuthorization").stream().anyMatch(x -> auth.getName().equalsIgnoreCase(String.valueOf(x.get("recruiterEmail"))) && String.valueOf(app.get("employerId")).equals(String.valueOf(x.get("employerId"))) && "ACTIVE".equalsIgnoreCase(String.valueOf(x.get("status"))) && Boolean.TRUE.equals(x.get("canManageCandidates")));
    return false;
  }
  private String deny(RedirectAttributes ra, String target) { ra.addFlashAttribute("error", "You are not authorized to perform this action."); return "redirect:" + target; }
  private String id() { return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT); }
  private String safeSegment(String value) { return value == null ? "unknown" : value.replaceAll("[^A-Za-z0-9_-]", "_"); }
  private String extension(String name) { int i = name.lastIndexOf('.'); return i >= 0 ? name.substring(i).replaceAll("[^.A-Za-z0-9]", "") : ""; }
}

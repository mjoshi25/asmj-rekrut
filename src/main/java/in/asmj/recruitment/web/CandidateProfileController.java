package in.asmj.recruitment.web;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import in.asmj.recruitment.store.RecordStore;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class CandidateProfileController {
  private final RecordStore store;
  private final String cloudName, cloudKey, cloudSecret;
  public CandidateProfileController(RecordStore store,
      @Value("${cloudinary.cloud-name:}") String cloudName,
      @Value("${cloudinary.api-key:}") String cloudKey,
      @Value("${cloudinary.api-secret:}") String cloudSecret) {
    this.store=store; this.cloudName=cloudName; this.cloudKey=cloudKey; this.cloudSecret=cloudSecret;
  }

  @GetMapping("/candidate/profile")
  public String profile(Authentication auth, Model model) {
    Map<String,Object> profile = profileForEmail(auth.getName());
    if (profile == null) { profile = new LinkedHashMap<>(); profile.put("email", auth.getName()); }
    model.addAttribute("profile", profile);
    return "candidate-profile";
  }

  @PostMapping(value="/candidate/profile/save", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  public String saveProfile(@RequestParam Map<String,String> form, @RequestParam(name="photo", required=false) MultipartFile photo,
                            Authentication auth, RedirectAttributes ra) {
    Map<String,Object> existing = profileForEmail(auth.getName());
    Map<String,Object> profile = existing == null ? new LinkedHashMap<>() : new LinkedHashMap<>(existing);
    String id = existing == null ? UUID.randomUUID().toString().substring(0,8).toUpperCase() : String.valueOf(existing.get("id"));
    form.forEach((k,v) -> { if (!Set.of("password","confirmPassword","_csrf","educationDetailsJson","projectDetailsJson","technologyExperienceJson","languagesKnownJson").contains(k)) profile.put(k, v == null ? "" : v.trim()); });
    try {
      com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
      com.fasterxml.jackson.core.type.TypeReference<List<Map<String,Object>>> listType = new com.fasterxml.jackson.core.type.TypeReference<>() {};
      profile.put("educationDetails", parseRows(mapper, listType, form.get("educationDetailsJson")));
      profile.put("projectDetails", parseRows(mapper, listType, form.get("projectDetailsJson")));
      profile.put("technologyExperience", parseRows(mapper, listType, form.get("technologyExperienceJson")));
      profile.put("languagesKnown", parseRows(mapper, listType, form.get("languagesKnownJson")));
    } catch (Exception ex) {
      ra.addFlashAttribute("error", "Some repeated profile details could not be read. Please review the entries and save again.");
      return "redirect:/candidate/profile";
    }
    profile.put("id",id); profile.put("email",auth.getName().trim().toLowerCase(Locale.ROOT));
    profile.put("status", profile.getOrDefault("status","ACTIVE")); profile.put("profileUpdatedAt", java.time.LocalDate.now().toString());
    if (photo != null && !photo.isEmpty()) {
      if (photo.getContentType()==null || !photo.getContentType().startsWith("image/")) { ra.addFlashAttribute("error","Please upload an image file for the candidate photo."); return "redirect:/candidate/profile"; }
      if (photo.getSize() > 5L*1024*1024) { ra.addFlashAttribute("error","Photo must be 5 MB or smaller."); return "redirect:/candidate/profile"; }
      try { profile.put("photoUrl", uploadPhoto(photo)); }
      catch (Exception e) { ra.addFlashAttribute("error","Photo upload failed. Configure CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY and CLOUDINARY_API_SECRET, then try again."); return "redirect:/candidate/profile"; }
    }
    store.save("candidate",id,profile);
    ra.addFlashAttribute("success","Candidate profile saved. Your generated resume now reflects the latest details.");
    return "redirect:/candidate/profile";
  }

  @PostMapping(value="/candidate/profile/import", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  public String importExcel(@RequestParam("excelFile") MultipartFile file, Authentication auth, RedirectAttributes ra) {
    if (file == null || file.isEmpty() || !(file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx") || file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xls"))) {
      ra.addFlashAttribute("error","Upload an Excel .xlsx or .xls file."); return "redirect:/candidate/profile";
    }
    try (InputStream in=file.getInputStream(); Workbook wb=WorkbookFactory.create(in)) {
      Sheet sheet=wb.getSheetAt(0); if (sheet.getPhysicalNumberOfRows()<2) throw new IllegalArgumentException("Add a header row and one candidate data row.");
      DataFormatter fmt=new DataFormatter(); Row header=sheet.getRow(0), row=sheet.getRow(1);
      if (header==null || row==null) throw new IllegalArgumentException("The spreadsheet is missing its header or profile row.");
      Map<String,String> values=new LinkedHashMap<>();
      for (int c=0;c<header.getLastCellNum();c++) {
        String key=normalizeHeader(fmt.formatCellValue(header.getCell(c)));
        if (!key.isBlank() && c<row.getLastCellNum()) values.put(key,fmt.formatCellValue(row.getCell(c)).trim());
      }
      Map<String,Object> existing=profileForEmail(auth.getName());
      Map<String,Object> profile=existing==null?new LinkedHashMap<>():new LinkedHashMap<>(existing);
      String id=existing==null?UUID.randomUUID().toString().substring(0,8).toUpperCase():String.valueOf(existing.get("id"));
      Map<String,String> mapping=Map.ofEntries(
        Map.entry("fullname","fullName"),Map.entry("name","fullName"),Map.entry("email","email"),Map.entry("mobile","mobile"),Map.entry("mobilenumber","mobile"),Map.entry("alternatemobile","alternateMobile"),Map.entry("alternatephone","alternateMobile"),Map.entry("location","location"),Map.entry("currentaddress","currentAddress"),Map.entry("hobbies","hobbies"),Map.entry("city","city"),Map.entry("state","state"),Map.entry("pincode","pinCode"),Map.entry("highestqualification","qualification"),Map.entry("qualification","qualification"),Map.entry("experienceyears","experienceYears"),Map.entry("experience","experienceYears"),Map.entry("currentrole","currentRole"),Map.entry("desiredrole","desiredRole"),Map.entry("skills","skills"),Map.entry("summary","summary"),Map.entry("linkedinurl","linkedinUrl"),Map.entry("portfoliourl","portfolioUrl"),Map.entry("education","education"),Map.entry("workexperience","workExperience"),Map.entry("certifications","certifications"),Map.entry("languages","languages"));
      for (var e:values.entrySet()) { String target=mapping.get(e.getKey()); if(target!=null && !target.equals("email") && !e.getValue().isBlank()) profile.put(target,e.getValue()); }
      profile.put("id",id); profile.put("email",auth.getName().trim().toLowerCase(Locale.ROOT)); profile.put("status",profile.getOrDefault("status","ACTIVE")); profile.put("profileUpdatedAt",java.time.LocalDate.now().toString());
      store.save("candidate",id,profile);
      ra.addFlashAttribute("success","Excel details imported into your profile. Review the fields and save any changes to regenerate your resume.");
    } catch (Exception e) { ra.addFlashAttribute("error","Could not import Excel: "+(e.getMessage()==null?"check the template and try again":e.getMessage())); }
    return "redirect:/candidate/profile";
  }

  @GetMapping("/candidate/profile/template.xlsx")
  public ResponseEntity<byte[]> template() throws Exception {
    try (Workbook wb=new XSSFWorkbook(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
      Sheet sh=wb.createSheet("Candidate Profile");
      String[] headers={"Full Name","Email","Mobile","Alternate Mobile","Location","City","State","PIN Code","Highest Qualification","Experience Years","Current Role","Desired Role","Skills","Summary","LinkedIn URL","Portfolio URL","Education","Work Experience","Certifications","Languages"};
      Row h=sh.createRow(0); for(int i=0;i<headers.length;i++){h.createCell(i).setCellValue(headers[i]);sh.setColumnWidth(i, Math.min(12000,Math.max(4000,headers[i].length()*420)));}
      Row sample=sh.createRow(1); for(int i=0;i<headers.length;i++) sample.createCell(i).setCellValue(i==1?"your.email@example.com":i==0?"Your Full Name":"");
      wb.write(out);
      return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=ASMJ_Candidate_Profile_Template.xlsx").contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(out.toByteArray());
    }
  }

  @GetMapping("/candidates/{id}/resume")
  public String resume(@PathVariable String id, Authentication auth, Model model) {
    Map<String,Object> profile=store.find("candidate",id).orElseThrow();
    if (!canViewCandidate(auth,profile)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
    model.addAttribute("candidate",profile); return "candidate-resume";
  }

  @GetMapping("/candidate-search")
  public String search(@RequestParam(required=false) String q, @RequestParam(required=false) String skills,
      @RequestParam(required=false) String qualification, @RequestParam(required=false) String experience,
      @RequestParam(required=false) String location, Authentication auth, Model model) {
    List<Map<String,Object>> candidates=store.findAll("candidate");
    if(q!=null&&!q.isBlank()) candidates=candidates.stream().filter(c->contains(c,"fullName",q)||contains(c,"desiredRole",q)||contains(c,"currentRole",q)||contains(c,"email",q)).toList();
    if(skills!=null&&!skills.isBlank()) candidates=candidates.stream().filter(c->contains(c,"skills",skills)).toList();
    if(qualification!=null&&!qualification.isBlank()) candidates=candidates.stream().filter(c->contains(c,"qualification",qualification)).toList();
    if(experience!=null&&!experience.isBlank()) candidates=candidates.stream().filter(c->contains(c,"experienceYears",experience)).toList();
    if(location!=null&&!location.isBlank()) candidates=candidates.stream().filter(c->contains(c,"location",location)||contains(c,"city",location)||contains(c,"state",location)).toList();
    model.addAttribute("records",candidates); model.addAttribute("q",q==null?"":q); model.addAttribute("skills",skills==null?"":skills); model.addAttribute("qualification",qualification==null?"":qualification); model.addAttribute("experience",experience==null?"":experience); model.addAttribute("location",location==null?"":location);
    return "candidate-search";
  }

  private boolean canViewCandidate(Authentication auth,Map<String,Object> c) {
    if(auth==null)return false;
    boolean allowed=auth.getAuthorities().stream().anyMatch(a->Set.of("ROLE_ADMIN","ROLE_EMPLOYER","ROLE_RECRUITER").contains(a.getAuthority()));
    return allowed || auth.getName().equalsIgnoreCase(String.valueOf(c.get("email")));
  }
  private List<Map<String,Object>> parseRows(com.fasterxml.jackson.databind.ObjectMapper mapper,
      com.fasterxml.jackson.core.type.TypeReference<List<Map<String,Object>>> type, String json) throws Exception {
    if (json == null || json.isBlank()) return new ArrayList<>();
    List<Map<String,Object>> rows = mapper.readValue(json, type);
    return rows == null ? new ArrayList<>() : rows.stream().filter(r -> r.values().stream().anyMatch(v -> v != null && !String.valueOf(v).isBlank() && !"false".equals(String.valueOf(v)))).toList();
  }
  private Map<String,Object> profileForEmail(String email) { return store.findAll("candidate").stream().filter(c->email.equalsIgnoreCase(String.valueOf(c.get("email")))).findFirst().orElse(null); }
  private boolean contains(Map<String,Object> m,String k,String q){return String.valueOf(m.getOrDefault(k,"")).toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT));}
  private String normalizeHeader(String h){return h==null?"":h.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");}
  private String uploadPhoto(MultipartFile file) throws Exception {
    if(cloudName.isBlank()||cloudKey.isBlank()||cloudSecret.isBlank()) throw new IllegalStateException("Cloudinary is not configured");
    Cloudinary cloudinary=new Cloudinary(ObjectUtils.asMap("cloud_name",cloudName,"api_key",cloudKey,"api_secret",cloudSecret,"secure",true));
    Map result=cloudinary.uploader().upload(file.getBytes(),ObjectUtils.asMap("folder","asmj-recruitment/candidates","resource_type","image"));
    return String.valueOf(result.get("secure_url"));
  }
}

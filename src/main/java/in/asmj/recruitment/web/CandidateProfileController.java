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
    if (file == null || file.isEmpty() || file.getOriginalFilename() == null ||
        !(file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx") || file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xls"))) {
      ra.addFlashAttribute("error","Upload an Excel .xlsx or .xls file."); return "redirect:/candidate/profile";
    }
    try (InputStream in=file.getInputStream(); Workbook wb=WorkbookFactory.create(in)) {
      DataFormatter fmt=new DataFormatter();
      Map<String,Object> existing=profileForEmail(auth.getName());
      Map<String,Object> profile=existing==null?new LinkedHashMap<>():new LinkedHashMap<>(existing);
      String id=existing==null?UUID.randomUUID().toString().substring(0,8).toUpperCase():String.valueOf(existing.get("id"));
      Map<String,String> mapping=Map.ofEntries(
        Map.entry("fullname","fullName"),Map.entry("name","fullName"),Map.entry("email","email"),Map.entry("mobile","mobile"),Map.entry("mobilenumber","mobile"),Map.entry("alternatemobile","alternateMobile"),Map.entry("alternatephone","alternateMobile"),Map.entry("currentaddress","currentAddress"),Map.entry("address","currentAddress"),Map.entry("location","location"),Map.entry("currentlocation","location"),Map.entry("city","city"),Map.entry("state","state"),Map.entry("pincode","pinCode"),Map.entry("highestqualification","qualification"),Map.entry("qualification","qualification"),Map.entry("experienceyears","experienceYears"),Map.entry("overallexperience","experienceYears"),Map.entry("overall experience","experienceYears"),Map.entry("experience","experienceYears"),Map.entry("currentrole","currentRole"),Map.entry("currentjobtitle","currentRole"),Map.entry("desiredrole","desiredRole"),Map.entry("targetrole","desiredRole"),Map.entry("skills","skills"),Map.entry("keyskills","skills"),Map.entry("summary","summary"),Map.entry("professionalsummary","summary"),Map.entry("linkedinurl","linkedinUrl"),Map.entry("linkedin","linkedinUrl"),Map.entry("portfoliourl","portfolioUrl"),Map.entry("githuburl","portfolioUrl"),Map.entry("hobbies","hobbies"),Map.entry("education","education"),Map.entry("workexperience","workExperience"),Map.entry("certifications","certifications"),Map.entry("additionalinformation","additionalInformation"),Map.entry("awards","additionalInformation"));
      int importedRows=0;
      for (int si=0; si<wb.getNumberOfSheets(); si++) {
        Sheet sheet=wb.getSheetAt(si); if(sheet.getPhysicalNumberOfRows()<2) continue;
        String sheetName=normalizeHeader(sheet.getSheetName());
        if (sheetName.equals("instructions") || sheetName.equals("readme")) continue;
        Row header=sheet.getRow(0); if(header==null) continue;
        List<Map<String,String>> rows=new ArrayList<>();
        for(int ri=1;ri<=sheet.getLastRowNum();ri++) {
          Row row=sheet.getRow(ri); if(row==null) continue;
          Map<String,String> values=new LinkedHashMap<>();
          for(int c=0;c<header.getLastCellNum();c++) {
            String key=normalizeHeader(fmt.formatCellValue(header.getCell(c)));
            String value=readExcelCell(row.getCell(c),fmt,key);
            if(!key.isBlank()) values.put(key,value);
          }
          String importMarker=values.getOrDefault("importrowclearSampletoimport".toLowerCase(Locale.ROOT), values.getOrDefault("examplerowclearsampletoimport", ""));
          if ("sample".equalsIgnoreCase(importMarker.trim())) continue;
          if(values.values().stream().anyMatch(v->v!=null&&!v.isBlank())) { rows.add(values); importedRows++; }
        }
        if(rows.isEmpty()) continue;
        if(si==0 || sheetName.contains("personalinformation") || sheetName.equals("candidateprofile")) {
          for(var entry:rows.get(0).entrySet()) {
            String target=mapping.get(entry.getKey()); String value=entry.getValue();
            if(target!=null && !target.equals("email") && value!=null&&!value.isBlank()) profile.put(target,value);
          }
        }
        if(sheetName.contains("education")) {
          List<Map<String,Object>> list=new ArrayList<>();
          for(Map<String,String> r:rows) { Map<String,Object> x=pick(r, Map.ofEntries(Map.entry("qualification","qualification"),Map.entry("degree","qualification"),Map.entry("institution","institution"),Map.entry("university","institution"),Map.entry("specialization","specialization"),Map.entry("stream","specialization"),Map.entry("grade","grade"),Map.entry("percentage","grade"),Map.entry("cgpa","grade"),Map.entry("startmonth","startMonth"),Map.entry("startdate","startMonth"),Map.entry("endmonth","endMonth"),Map.entry("enddate","endMonth"),Map.entry("details","details"),Map.entry("achievements","details"))); if(!x.isEmpty()) list.add(x); }
          profile.put("educationDetails",list);
        } else if(sheetName.contains("project")) {
          List<Map<String,Object>> list=new ArrayList<>();
          for(Map<String,String> r:rows) { Map<String,Object> x=pick(r, Map.ofEntries(Map.entry("title","title"),Map.entry("projecttitle","title"),Map.entry("role","role"),Map.entry("yourrole","role"),Map.entry("startmonth","startMonth"),Map.entry("endmonth","endMonth"),Map.entry("current","current"),Map.entry("currentlyworking","current"),Map.entry("technologies","technologies"),Map.entry("technology","technologies"),Map.entry("description","description"),Map.entry("achievements","description"),Map.entry("url","url"),Map.entry("projecturl","url"))); if(!x.isEmpty()) { String current=String.valueOf(x.getOrDefault("current","")); x.put("current",List.of("true","yes","y","1","present").contains(current.toLowerCase(Locale.ROOT))); if(Boolean.TRUE.equals(x.get("current"))) x.put("endMonth",""); list.add(x); } }
          profile.put("projectDetails",list);
        } else if(sheetName.contains("technology") || sheetName.contains("skillsexperience")) {
          List<Map<String,Object>> list=new ArrayList<>();
          for(Map<String,String> r:rows) { Map<String,Object> x=pick(r, Map.ofEntries(Map.entry("technology","technology"),Map.entry("tool","technology"),Map.entry("level","level"),Map.entry("proficiencylevel","level"),Map.entry("years","years"),Map.entry("experienceyears","years"),Map.entry("months","months"),Map.entry("experiencemonths","months"),Map.entry("notes","notes"),Map.entry("lastused","notes"),Map.entry("lastusednotes","notes"))); if(!x.isEmpty()) list.add(x); }
          profile.put("technologyExperience",list);
        } else if(sheetName.contains("language")) {
          List<Map<String,Object>> list=new ArrayList<>();
          for(Map<String,String> r:rows) { Map<String,Object> x=pick(r, Map.ofEntries(Map.entry("language","language"),Map.entry("name","language"),Map.entry("proficiency","proficiency"),Map.entry("level","proficiency"))); if(!x.isEmpty()) list.add(x); }
          profile.put("languagesKnown",list);
          profile.put("languages",rows.stream().map(r->r.getOrDefault("language",r.getOrDefault("name",""))).filter(v->!v.isBlank()).collect(Collectors.joining(", ")));
        } else if(sheetName.contains("employment") || sheetName.contains("workhistory") || sheetName.contains("careerhistory")) {
          List<String> lines=new ArrayList<>();
          for(Map<String,String> r:rows) { String line=String.join(" | ",List.of(r.getOrDefault("company",""),r.getOrDefault("jobtitle",r.getOrDefault("role","")),r.getOrDefault("startmonth",""),r.getOrDefault("endmonth",r.getOrDefault("enddate","Present")),r.getOrDefault("responsibilities",""),r.getOrDefault("achievements",""))).replaceAll("( \\| )+"," | ").replaceAll("^ \\| | \\| $","").trim(); if(!line.isBlank()) lines.add(line); }
          if(!lines.isEmpty()) profile.put("workExperience",String.join("\n",lines));
        } else if(sheetName.contains("additional") || sheetName.contains("certification")) {
          for(Map<String,String> r:rows) { String cert=r.getOrDefault("certification",r.getOrDefault("certificationname","")); if(!cert.isBlank()) profile.put("certifications",String.valueOf(profile.getOrDefault("certifications","")).concat(String.valueOf(profile.getOrDefault("certifications","")).isBlank()?"":"\n").concat(cert+(r.getOrDefault("issuingbody","").isBlank()?"":" - "+r.get("issuingbody"))+(r.getOrDefault("year","").isBlank()?"":" ("+r.get("year")+")"))); String add=r.get("additionalinformation"); if(add==null||add.isBlank()) add=r.getOrDefault("awards",""); if(!add.isBlank()) profile.put("additionalInformation",String.valueOf(profile.getOrDefault("additionalInformation","")).concat(String.valueOf(profile.getOrDefault("additionalInformation","")).isBlank()?"":"\n").concat(add)); }
        }
      }
      if(importedRows==0) throw new IllegalArgumentException("No profile data rows found. Fill in the downloaded template and try again.");
      profile.put("id",id); profile.put("email",auth.getName().trim().toLowerCase(Locale.ROOT)); profile.put("status",profile.getOrDefault("status","ACTIVE")); profile.put("profileUpdatedAt",java.time.LocalDate.now().toString());
      store.save("candidate",id,profile);
      ra.addFlashAttribute("success","Excel details imported into your profile, including repeated education, project, technology and language entries. Review your profile and save to regenerate the resume.");
    } catch (Exception e) { ra.addFlashAttribute("error","Could not import Excel: "+(e.getMessage()==null?"check the downloaded template and try again":e.getMessage())); }
    return "redirect:/candidate/profile";
  }

  @GetMapping("/candidate/profile/template.xlsx")
  public ResponseEntity<byte[]> template() throws Exception {
    try (Workbook wb=new XSSFWorkbook(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
      CellStyle head=wb.createCellStyle(); Font font=wb.createFont(); font.setBold(true); head.setFont(font); head.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex()); head.setFillPattern(FillPatternType.SOLID_FOREGROUND);
      CellStyle sampleStyle=wb.createCellStyle(); sampleStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex()); sampleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
      addSheet(wb,"Personal Information",new String[]{"Full Name","Mobile","Alternate Mobile","Current Address","City","State","PIN Code","Current Location","LinkedIn URL","Portfolio URL","Hobbies","Current Role","Desired Role","Highest Qualification","Overall Experience","Professional Summary","Key Skills"},new String[]{"SAMPLE - Rahul Sharma","9876543210","9123456780","12 Example Road, Unit 4","Bhubaneswar","Odisha","751001","Bhubaneswar, Odisha","https://linkedin.com/in/rahul-sharma","https://github.com/rahulsharma","Reading, badminton, travel","Software Developer","Java Developer","B.Tech Computer Science","3 years 6 months","Software developer experienced in Java, Spring Boot and REST APIs.","Java, Spring Boot, REST API, MongoDB"},head,sampleStyle);
      addSheet(wb,"Education",new String[]{"Qualification","Institution","Specialization","Grade","Start Month","End Month","Details"},new String[]{"SAMPLE - B.Tech","Example University","Computer Science","8.2 CGPA","2019-07","2023-06","Final-year project on inventory management"},head,sampleStyle);
      addSheet(wb,"Projects",new String[]{"Project Title","Role","Start Month","End Month","Currently Working","Technologies","Description","Project URL"},new String[]{"SAMPLE - Inventory Portal","Backend Developer","2024-01","2024-10","No","Java, Spring Boot, MongoDB","Built REST APIs, authentication and reporting features.","https://example.com/inventory"},head,sampleStyle);
      addSheet(wb,"Technology Experience",new String[]{"Technology","Level","Years","Months","Last Used / Notes"},new String[]{"SAMPLE - Java","Advanced","3","6","Used in current role; last used in 2026"},head,sampleStyle);
      addSheet(wb,"Languages",new String[]{"Language","Proficiency"},new String[]{"SAMPLE - English","Professional"},head,sampleStyle);
      addSheet(wb,"Employment",new String[]{"Company","Job Title","Start Month","End Month","Responsibilities","Achievements"},new String[]{"SAMPLE - Example Technologies Pvt Ltd","Software Developer","2023-07","Present","Develop and maintain Spring Boot services and APIs.","Improved API response time and added automated tests."},head,sampleStyle);
      addSheet(wb,"Certifications & Other",new String[]{"Certification Name","Issuing Body","Year","Additional Information","Awards"},new String[]{"SAMPLE - Java Programming Certificate","Example Training Institute","2024","Volunteer mentor; contributed to internal technical documentation.","Quarterly Star Performer"},head,sampleStyle);
      addInstructionsSheet(wb,head);
      wb.write(out);
      return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=ASMJ_Detailed_Candidate_Profile_Template.xlsx").contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(out.toByteArray());
    }
  }

  private void addSheet(Workbook wb,String name,String[] headers,String[] sample,CellStyle head,CellStyle sampleStyle) {
    Sheet sheet=wb.createSheet(name); Row h=sheet.createRow(0);
    for(int i=0;i<headers.length;i++){Cell cell=h.createCell(i);cell.setCellValue(headers[i]);cell.setCellStyle(head);sheet.setColumnWidth(i,Math.min(16000,Math.max(4200,headers[i].length()*420)));}
    Cell markerHeader=h.createCell(headers.length); markerHeader.setCellValue("Import Row - clear SAMPLE to import"); markerHeader.setCellStyle(head); sheet.setColumnWidth(headers.length,7800);
    Row example=sheet.createRow(1);
    for(int i=0;i<headers.length;i++){Cell cell=example.createCell(i);cell.setCellValue(i<sample.length?sample[i]:"");cell.setCellStyle(sampleStyle);}
    Cell marker=example.createCell(headers.length); marker.setCellValue("SAMPLE"); marker.setCellStyle(sampleStyle);
    sheet.createFreezePane(0,1); sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0,1,0,headers.length));
  }

  private void addInstructionsSheet(Workbook wb,CellStyle head) {
    Sheet sheet=wb.createSheet("Instructions");
    String[][] rows={
      {"ASMJ Recruitment - Excel profile import","Instructions"},
      {"1. Sample rows","Yellow rows are examples only. Replace the example values and clear the SAMPLE marker in the last column to import that row, or delete the sample row."},
      {"2. Add multiple records","Education, Projects, Technology Experience, Languages and Employment support multiple rows. Copy the header format and add one record per row. Leave the marker column blank for real rows."},
      {"3. Month/year format","Enter start and end months as YYYY-MM, for example 2023-07. Excel date cells are also accepted and normalized to YYYY-MM."},
      {"4. Current project/job","For a current project, set Currently Working to Yes and leave End Month blank. For current employment, enter Present in End Month."},
      {"5. Personal Information","Use one row only. Replace sample name, phone, address, experience, summary and skills with your own details."},
      {"6. Import process","Upload the completed .xlsx workbook from Candidate Profile. Review imported data on the profile page and click Save Profile."},
      {"7. Privacy","Do not include passwords, identity document numbers or other information not required for your resume."}
    };
    for(int r=0;r<rows.length;r++){Row row=sheet.createRow(r);for(int c=0;c<2;c++){Cell cell=row.createCell(c);cell.setCellValue(rows[r][c]);if(r==0)cell.setCellStyle(head);}}
    sheet.setColumnWidth(0,8000);sheet.setColumnWidth(1,22000);sheet.createFreezePane(0,1);
  }

  private String readExcelCell(Cell cell,DataFormatter formatter,String headerKey) {
    if(cell==null) return "";
    // Excel date cells are stored as numeric serials. Some users type dates as text,
    // even when the cell has a date number format, so never call getNumericCellValue()
    // unless the underlying cell is actually numeric.
    if(cell.getCellType() == CellType.NUMERIC
        && DateUtil.isCellDateFormatted(cell)
        && (headerKey.contains("month") || headerKey.contains("date"))) {
      try {
        return DateUtil.getLocalDateTime(cell.getNumericCellValue()).toLocalDate()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
      } catch(Exception ignored) { /* Fall through and parse the displayed cell text. */ }
    }
    if(cell.getCellType() == CellType.FORMULA
        && (headerKey.contains("month") || headerKey.contains("date"))) {
      try {
        CellValue cached = cell.getSheet().getWorkbook().getCreationHelper()
            .createFormulaEvaluator().evaluate(cell);
        if(cached != null && cached.getCellType() == CellType.NUMERIC
            && DateUtil.isCellDateFormatted(cell)) {
          return DateUtil.getLocalDateTime(cached.getNumberValue()).toLocalDate()
              .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
        }
      } catch(Exception ignored) { /* Fall through and parse the displayed cell text. */ }
    }
    String value=formatter.formatCellValue(cell).trim();
    if(value.isBlank() || !(headerKey.contains("month") || headerKey.contains("date"))) return value;
    return normalizeMonth(value);
  }

  private String normalizeMonth(String value) {
    String v=value.trim();
    if(v.isBlank() || v.equalsIgnoreCase("present") || v.equalsIgnoreCase("current") || v.equalsIgnoreCase("ongoing")) return v.equalsIgnoreCase("current") || v.equalsIgnoreCase("ongoing") ? "Present" : v;
    java.util.regex.Matcher ym=java.util.regex.Pattern.compile("^(\\d{4})[-/](\\d{1,2})(?:[-/]\\d{1,2})?$").matcher(v);
    if(ym.matches()) return String.format(Locale.ROOT,"%s-%02d",ym.group(1),Integer.parseInt(ym.group(2)));
    java.util.regex.Matcher my=java.util.regex.Pattern.compile("^(\\d{1,2})[-/](\\d{4})$").matcher(v);
    if(my.matches()) return String.format(Locale.ROOT,"%s-%02d",my.group(2),Integer.parseInt(my.group(1)));
    for(String pattern:List.of("MMM-yyyy","MMMM yyyy","MMM-yy","MM/yyyy","M/yyyy")) {
      try { java.time.YearMonth month=java.time.YearMonth.parse(v,java.time.format.DateTimeFormatter.ofPattern(pattern,Locale.ENGLISH)); return month.toString(); }
      catch(Exception ignored) { }
    }
    try { return java.time.LocalDate.parse(v).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")); } catch(Exception ignored) { }
    return v;
  }

  private Map<String,Object> pick(Map<String,String> row,Map<String,String> mapping) {
    Map<String,Object> result=new LinkedHashMap<>();
    row.forEach((key,value)->{String target=mapping.get(key);if(target!=null&&value!=null&&!value.isBlank())result.put(target,value);});
    return result;
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
    if(experience!=null&&!experience.isBlank()) candidates=candidates.stream().filter(c->matchesExperience(c,experience)).toList();
    if(location!=null&&!location.isBlank()) candidates=candidates.stream().filter(c->contains(c,"location",location)||contains(c,"city",location)||contains(c,"state",location)).toList();
    model.addAttribute("records",candidates); model.addAttribute("q",q==null?"":q); model.addAttribute("skills",skills==null?"":skills); model.addAttribute("qualification",qualification==null?"":qualification); model.addAttribute("experience",experience==null?"":experience); model.addAttribute("location",location==null?"":location);
    return "candidate-search";
  }

  private boolean matchesExperience(Map<String,Object> candidate,String filter) {
    String f=filter.trim().toLowerCase(Locale.ROOT);
    int total=experienceMonths(candidate.get("experienceYears"));
    if(total<0) total=experienceMonths(candidate.get("experience"));
    if(total<0) return false;
    try {
      java.util.regex.Matcher range=java.util.regex.Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*(?:-|–|to)\\s*(\\d+(?:\\.\\d+)?)(?:\\s*(?:years?|yrs?))?$").matcher(f);
      if(range.matches()) { double min=Double.parseDouble(range.group(1)), max=Double.parseDouble(range.group(2)); return total>=Math.round(min*12) && total<=Math.round(max*12); }
      java.util.regex.Matcher plus=java.util.regex.Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*\\+$").matcher(f);
      if(plus.matches()) return total>=Math.round(Double.parseDouble(plus.group(1))*12);
      if(f.matches("\\d+(?:\\.\\d+)?(?:\\s*(?:years?|yrs?))?")) { double years=Double.parseDouble(f.replaceAll("\\s*(?:years?|yrs?)$", "")); return total>=Math.round(years*12) && total<Math.round(years*12)+12; }
      if(f.matches(".*(?:years?|yrs?|months?|mos?).*")) { int requested=experienceMonths(f); return requested>=0 && total==requested; }
    } catch(NumberFormatException ignored) { return false; }
    return String.valueOf(candidate.getOrDefault("experienceYears",candidate.getOrDefault("experience",""))).toLowerCase(Locale.ROOT).contains(f);
  }

  private int experienceMonths(Object value) {
    if(value==null) return -1;
    String s=String.valueOf(value).trim().toLowerCase(Locale.ROOT);
    if(s.isBlank()) return -1;
    try { if(s.matches("\\d+(?:\\.\\d+)?")) return (int)Math.round(Double.parseDouble(s)*12); } catch(NumberFormatException ignored) { return -1; }
    java.util.regex.Matcher ym=java.util.regex.Pattern.compile("(?:(\\d+(?:\\.\\d+)?)\\s*(?:years?|yrs?|y)\\b)?\\s*(?:(\\d+)\\s*(?:months?|mos?|m)\\b)?").matcher(s);
    if(ym.matches() && (ym.group(1)!=null||ym.group(2)!=null)) { double years=ym.group(1)==null?0:Double.parseDouble(ym.group(1)); int months=ym.group(2)==null?0:Integer.parseInt(ym.group(2)); return (int)Math.round(years*12)+months; }
    java.util.regex.Matcher number=java.util.regex.Pattern.compile("^(\\d+(?:\\.\\d+)?)").matcher(s);
    if(number.find()) try { return (int)Math.round(Double.parseDouble(number.group(1))*12); } catch(NumberFormatException ignored) { return -1; }
    return -1;
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

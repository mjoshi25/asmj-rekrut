package in.asmj.recruitment.config;

import in.asmj.recruitment.store.RecordStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

/** Idempotent demo data for a first-run recruitment portal. Existing records are never overwritten. */
@Component
public class DemoDataSeeder implements ApplicationRunner {
  private static final String EMPLOYER_EMAIL = "sarojkumar@asmj.co.in";
  private final RecordStore store;
  private final UserDetailsManager users;
  private final PasswordEncoder encoder;

  public DemoDataSeeder(RecordStore store, UserDetailsManager users, PasswordEncoder encoder) {
    this.store = store; this.users = users; this.encoder = encoder;
  }

  @Override public void run(ApplicationArguments args) {
    if (!users.userExists(EMPLOYER_EMAIL)) {
      users.createUser(User.withUsername(EMPLOYER_EMAIL).password(encoder.encode("Saroj@123")).roles("EMPLOYER").build());
    }

    Map<String,Object> employer = store.findAll("employer").stream()
      .filter(e -> EMPLOYER_EMAIL.equalsIgnoreCase(String.valueOf(e.get("email"))))
      .findFirst().orElse(null);
    String employerId;
    if (employer == null) {
      employerId = "demo-employer-sarojkumar";
      employer = new LinkedHashMap<>();
      employer.put("id", employerId);
      employer.put("name", "Saroj Kumar");
      employer.put("fullName", "Saroj Kumar");
      employer.put("companyName", "ASMJ Talent Partners");
      employer.put("company", "ASMJ Talent Partners");
      employer.put("email", EMPLOYER_EMAIL);
      employer.put("industry", "Recruitment & Business Services");
      employer.put("location", "Bhubaneswar, Odisha, India");
      employer.put("status", "APPROVED");
      employer.put("verified", true);
      employer.put("isDemoSeed", true);
      employer.put("createdAt", LocalDate.now().toString());
      store.save("employer", employerId, employer);
    } else {
      employerId = String.valueOf(employer.get("id"));
    }

    seedJob(employerId, "demo-job-java-developer", "Java / Spring Boot Developer", "Bhubaneswar, Odisha / Hybrid", "Full-time", "₹5–9 LPA", "1–3 years", "Java, Spring Boot, REST APIs, SQL, Git",
      "Build and maintain Spring Boot services and REST APIs. Work with the team on code reviews, testing, bug fixes and production support.\n\nRequirements: Java fundamentals, Spring Boot, REST API development, SQL and Git. Familiarity with MongoDB and cloud deployment is a plus.\n\nDemo posting: confirm the role details with the employer before applying.");
    seedJob(employerId, "demo-job-hr-recruiter", "HR Recruiter / Talent Acquisition Associate", "Bhubaneswar, Odisha", "Full-time", "₹2.8–4.5 LPA", "0–3 years", "Talent sourcing, screening, communication, interview coordination",
      "Support end-to-end recruitment including sourcing, screening calls, interview scheduling and candidate follow-up. Maintain accurate applicant records and provide a professional candidate experience.\n\nRequirements: strong communication, organization and follow-up skills. Freshers with relevant training may apply.");
    seedJob(employerId, "demo-job-digital-marketing", "Digital Marketing Executive", "Remote / Odisha", "Full-time", "₹3–5 LPA", "1–3 years", "SEO, social media, content, analytics, campaign reporting",
      "Plan and coordinate digital campaigns, prepare social content, track campaign performance and support search-engine optimization.\n\nRequirements: practical understanding of SEO and social platforms, writing skills and basic analytics. A portfolio is welcome.");
    seedJob(employerId, "demo-job-customer-support", "Customer Support Associate", "Cuttack / Hybrid", "Full-time", "₹2.4–3.6 LPA", "Fresher", "Customer service, email support, problem solving, Odia, Hindi, English",
      "Respond to customer questions through phone and email, record issues, coordinate resolutions and maintain clear service notes.\n\nRequirements: courteous communication in Odia and Hindi; English and basic computer skills are desirable. Training will be provided.");
    seedJob(employerId, "demo-job-sales-executive", "Business Development / Sales Executive", "Odisha (field + office)", "Full-time", "₹3–6 LPA + incentives", "1–4 years", "Lead generation, client relationships, presentations, CRM",
      "Identify prospective business customers, qualify leads, arrange meetings and maintain follow-up in the CRM. Help prepare proposals and share market feedback with the team.\n\nRequirements: clear communication, relationship-building skills and willingness to travel locally. Incentives depend on the final employment terms.");
    seedJob(employerId, "demo-job-data-analyst-intern", "Data Analyst Intern", "Remote / Bhubaneswar", "Internship", "₹10,000–₹18,000 per month", "Fresher", "Excel, SQL basics, data cleaning, dashboards, attention to detail",
      "Assist with cleaning spreadsheets, preparing recurring reports, checking data quality and building simple dashboards.\n\nRequirements: spreadsheet skills and basic SQL or analytics coursework. Internship duration and conversion prospects must be confirmed with the employer.");

    store.find("settings", "adminGettingStarted").orElseGet(() -> {
      Map<String,Object> guide = new LinkedHashMap<>();
      guide.put("id", "adminGettingStarted");
      guide.put("title", "Administrator setup checklist");
      guide.put("items", List.of("Review employer and recruiter registrations", "Verify company details before approving access", "Review public job descriptions and close expired roles", "Check candidate document access and upload persistence", "Publish reviewed privacy, terms and grievance contact details"));
      guide.put("updatedAt", LocalDate.now().toString());
      store.save("settings", "adminGettingStarted", guide);
      return guide;
    });
  }

  private void seedJob(String employerId, String id, String title, String location, String type,
                       String salary, String experience, String skills, String description) {
    if (store.find("job", id).isPresent()) return;
    Map<String,Object> job = new LinkedHashMap<>();
    job.put("id", id); job.put("title", title); job.put("company", "ASMJ Talent Partners");
    job.put("employerId", employerId); job.put("employerEmail", EMPLOYER_EMAIL);
    job.put("location", location); job.put("jobType", type); job.put("salary", salary);
    job.put("experience", experience); job.put("skills", skills); job.put("description", description);
    job.put("status", "OPEN"); job.put("postedBy", EMPLOYER_EMAIL); job.put("postedByRole", "EMPLOYER");
    job.put("isDemoSeed", true); job.put("createdAt", LocalDate.now().toString());
    store.save("job", id, job);
  }
}

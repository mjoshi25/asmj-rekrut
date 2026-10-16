package in.asmj.recruitment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.UserDetailsManager;
import in.asmj.recruitment.store.RecordStore;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
  @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

  @Bean UserDetailsManager users(PasswordEncoder encoder, RecordStore store) {
    PersistentUserDetailsManager manager = new PersistentUserDetailsManager(store, encoder);
    seed(manager, encoder, "admin@asmj.co.in", "Admin@123", "ADMIN");
    seed(manager, encoder, "recruiter@asmj.co.in", "Recruit@123", "RECRUITER");
    seed(manager, encoder, "employer@asmj.co.in", "Employer@123", "EMPLOYER");
    seed(manager, encoder, "candidate@asmj.co.in", "Candidate@123", "CANDIDATE");
    return manager;
  }

  private void seed(UserDetailsManager manager, PasswordEncoder encoder, String email, String password, String role) {
    if (!manager.userExists(email)) manager.createUser(User.withUsername(email).password(encoder.encode(password)).roles(role).build());
  }

  @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
    return http.authorizeHttpRequests(a -> a
        .requestMatchers("/", "/jobs", "/opportunity/*", "/jobs/*/apply", "/register/**", "/login", "/css/**", "/js/**", "/images/**", "/h2-console/**", "/error").permitAll()
        .requestMatchers("/admin/**", "/reports/**").hasRole("ADMIN")
        .requestMatchers("/candidate/profile/**", "/candidate/profile").hasRole("CANDIDATE")
        .requestMatchers("/settings/**").authenticated()
        .requestMatchers("/candidate-search").hasAnyRole("ADMIN", "EMPLOYER", "RECRUITER")
        .requestMatchers("/candidates/*/resume").hasAnyRole("ADMIN", "EMPLOYER", "RECRUITER", "CANDIDATE")
        .requestMatchers("/recruiter/**").hasRole("RECRUITER")
        .requestMatchers("/employer/dashboard", "/employer/authorizations/**").hasRole("EMPLOYER")
        .requestMatchers("/candidates/**").hasAnyRole("ADMIN", "RECRUITER", "EMPLOYER")
        .requestMatchers("/employers/**", "/jobs/new", "/jobs/save", "/applications/**", "/interviews/**", "/placements/**").hasAnyRole("ADMIN", "RECRUITER", "EMPLOYER")
        .requestMatchers("/portal/**").hasAnyRole("ADMIN", "RECRUITER", "EMPLOYER", "CANDIDATE")
        .anyRequest().authenticated())
      .formLogin(f -> f.loginPage("/login").successHandler((request, response, authentication) -> {
          boolean admin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
          boolean recruiter = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_RECRUITER"));
          boolean employer = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_EMPLOYER"));
          boolean candidate = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CANDIDATE"));
          boolean pending = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_PENDING_EMPLOYER") || a.getAuthority().equals("ROLE_PENDING_RECRUITER"));
          String target = pending ? "/pending-approval" : admin ? "/admin/dashboard" : recruiter ? "/recruiter/dashboard" : employer ? "/employer/dashboard" : candidate ? "/portal" : "/dashboard";
          response.sendRedirect(request.getContextPath() + target);
        }).permitAll())
      .logout(l -> l.logoutSuccessUrl("/").permitAll())
      .csrf(c -> c.ignoringRequestMatchers("/h2-console/**"))
      .headers(h -> h.frameOptions(f -> f.sameOrigin())).build();
  }
}

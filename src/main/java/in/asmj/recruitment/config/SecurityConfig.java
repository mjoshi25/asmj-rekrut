package in.asmj.recruitment.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
public class SecurityConfig {
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
 @Bean UserDetailsService users(PasswordEncoder encoder){
   return new InMemoryUserDetailsManager(
     User.withUsername("admin@asmj.co.in").password(encoder.encode("Admin@123")).roles("ADMIN").build(),
     User.withUsername("recruiter@asmj.co.in").password(encoder.encode("Recruit@123")).roles("RECRUITER").build(),
     User.withUsername("employer@asmj.co.in").password(encoder.encode("Employer@123")).roles("EMPLOYER").build(),
     User.withUsername("candidate@asmj.co.in").password(encoder.encode("Candidate@123")).roles("CANDIDATE").build());
 }
 @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
   return http.authorizeHttpRequests(a->a
      .requestMatchers("/","/jobs","/opportunity/*","/jobs/*/apply","/register/**","/css/**","/js/**","/h2-console/**","/error").permitAll()
      .requestMatchers("/admin/**","/reports/**").hasRole("ADMIN")
      .requestMatchers("/candidates/**","/employers/**","/jobs/new","/jobs/save","/applications/**","/interviews/**","/placements/**").hasAnyRole("ADMIN","RECRUITER","EMPLOYER")
      .requestMatchers("/portal/**").hasAnyRole("ADMIN","RECRUITER","EMPLOYER","CANDIDATE")
      .anyRequest().authenticated())
    .formLogin(f->f.loginPage("/login").defaultSuccessUrl("/dashboard",true).permitAll())
    .logout(l->l.logoutSuccessUrl("/").permitAll())
    .csrf(c->c.ignoringRequestMatchers("/h2-console/**"))
    .headers(h->h.frameOptions(f->f.sameOrigin())).build();
 }
}

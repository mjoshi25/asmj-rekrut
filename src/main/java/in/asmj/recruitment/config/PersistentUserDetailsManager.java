package in.asmj.recruitment.config;

import in.asmj.recruitment.store.RecordStore;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.UserDetailsManager;

import java.util.*;

/** Stores login accounts through the same persistent adapter as recruitment records. */
public class PersistentUserDetailsManager implements UserDetailsManager, UserDetailsService {
  private final RecordStore store;
  private final PasswordEncoder passwordEncoder;
  public PersistentUserDetailsManager(RecordStore store, PasswordEncoder passwordEncoder) { this.store = store; this.passwordEncoder = passwordEncoder; }

  @Override public void createUser(UserDetails user) {
    String username = normalize(user.getUsername());
    if (userExists(username)) throw new IllegalArgumentException("User already exists: " + username);
    store.save("authUser", username, toRecord(user, username));
  }
  @Override public void updateUser(UserDetails user) {
    String username = normalize(user.getUsername());
    if (!userExists(username)) throw new UsernameNotFoundException("User not found: " + username);
    store.save("authUser", username, toRecord(user, username));
  }
  @Override public void deleteUser(String username) { store.delete("authUser", normalize(username)); }
  @Override public void changePassword(String oldPassword, String newPassword) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || auth.getName() == null) throw new IllegalStateException("No authenticated user is available.");
    String username = normalize(auth.getName());
    Map<String,Object> record = store.find("authUser", username).orElseThrow(() -> new UsernameNotFoundException(username));
    if (!passwordEncoder.matches(oldPassword, String.valueOf(record.get("password")))) throw new IllegalArgumentException("Current password is incorrect.");
    record.put("password", passwordEncoder.encode(newPassword));
    store.save("authUser", username, record);
  }
  @Override public boolean userExists(String username) { return store.find("authUser", normalize(username)).isPresent(); }
  @Override public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    Map<String,Object> record = store.find("authUser", normalize(username)).orElseThrow(() -> new UsernameNotFoundException("User not found"));
    Object raw = record.get("authorities");
    List<String> authorities = new ArrayList<>();
    if (raw instanceof Collection<?> collection) collection.forEach(a -> authorities.add(String.valueOf(a)));
    else if (raw != null) authorities.addAll(Arrays.asList(String.valueOf(raw).split(",")));
    if (authorities.isEmpty()) authorities.add("ROLE_CANDIDATE");
    return User.withUsername(String.valueOf(record.get("username")))
        .password(String.valueOf(record.get("password")))
        .authorities(authorities.stream().map(String::trim).filter(a -> !a.isBlank()).map(SimpleGrantedAuthority::new).toList())
        .build();
  }
  private Map<String,Object> toRecord(UserDetails user, String username) {
    Map<String,Object> record = new LinkedHashMap<>();
    record.put("id", username); record.put("username", username); record.put("password", user.getPassword());
    record.put("authorities", user.getAuthorities().stream().map(a -> a.getAuthority()).toList());
    record.put("updatedAt", java.time.LocalDate.now().toString());
    return record;
  }
  private String normalize(String username) { return username == null ? "" : username.trim().toLowerCase(Locale.ROOT); }
}

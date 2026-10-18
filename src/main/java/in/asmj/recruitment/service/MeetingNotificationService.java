package in.asmj.recruitment.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "asmj.mail.enabled", havingValue = "true")
public class MeetingNotificationService {
  private final JavaMailSender mailSender;
  public MeetingNotificationService(JavaMailSender mailSender) { this.mailSender = mailSender; }

  public boolean sendInterviewInvite(Map<String,Object> interview) {
    try {
      String to = String.valueOf(interview.getOrDefault("email", "")).trim();
      if (to.isBlank() || !to.contains("@")) return false;
      String mode = String.valueOf(interview.getOrDefault("meetingProvider", interview.getOrDefault("interviewType", "Interview")));
      String subject = "ASMJ Recruitment interview: " + interview.getOrDefault("jobTitle", "Interview");
      String body = "Hello " + interview.getOrDefault("candidateName", "Candidate") + ",\n\n"
        + "Your recruitment interview has been scheduled.\n\n"
        + "Role: " + interview.getOrDefault("jobTitle", "") + "\n"
        + "Company: " + interview.getOrDefault("company", "") + "\n"
        + "Interviewer: " + interview.getOrDefault("interviewer", "") + "\n"
        + "Date and time: " + interview.getOrDefault("scheduledAt", "") + "\n"
        + "Meeting type: " + mode + "\n"
        + (String.valueOf(interview.getOrDefault("meetingUrl", "")).isBlank() ? "" : "Join meeting: " + interview.getOrDefault("meetingUrl", "") + "\n")
        + "Notes: " + interview.getOrDefault("notes", "") + "\n\n"
        + "Please open the meeting link at the scheduled time. If you cannot join, contact the interviewer using the details provided separately.\n\n"
        + "ASMJ Recruitment";
      SimpleMailMessage message = new SimpleMailMessage();
      message.setTo(to); message.setSubject(subject); message.setText(body);
      String from = System.getenv("SMTP_FROM");
      if (from != null && !from.isBlank()) message.setFrom(from);
      mailSender.send(message);
      return true;
    } catch (Exception ex) {
      return false;
    }
  }
}

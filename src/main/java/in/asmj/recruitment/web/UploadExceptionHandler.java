package in.asmj.recruitment.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Converts oversized multipart requests into a useful workflow message instead of a Whitelabel page. */
@ControllerAdvice
public class UploadExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(UploadExceptionHandler.class);
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public String handleMaxUpload(MaxUploadSizeExceededException ex, HttpServletRequest request, RedirectAttributes ra) {
    String uri = request.getRequestURI();
    log.warn("Multipart upload rejected uri={} contentType={} contentLength={} reason={}", uri, request.getContentType(), request.getContentLengthLong(), ex.getMessage(), ex);
    ra.addFlashAttribute("error", "The selected upload is too large. Offer letters and joining documents must be 8 MB or smaller.");
    if (uri != null && uri.matches("/hiring/applications/[^/]+/offer")) {
      String applicationId = uri.substring("/hiring/applications/".length(), uri.length() - "/offer".length());
      return "redirect:/hiring/applications/" + applicationId + "/documents";
    }
    return "redirect:/candidate/hiring";
  }
}

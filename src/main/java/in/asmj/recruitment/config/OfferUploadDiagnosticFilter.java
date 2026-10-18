package in.asmj.recruitment.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Logs offer-letter POST requests before Spring MVC parses multipart content.
 * This is intentionally small and never reads or logs the uploaded file contents.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class OfferUploadDiagnosticFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(OfferUploadDiagnosticFilter.class);
    private static final String OFFER_PATH = "/hiring/applications/";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !"POST".equalsIgnoreCase(request.getMethod())
                || uri == null
                || !uri.startsWith(OFFER_PATH)
                || !uri.endsWith("/offer");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        log.info("OFFER_UPLOAD_REQUEST_RECEIVED method={} uri={} contentType={} contentLength={} transferEncoding={}",
                request.getMethod(), uri, request.getContentType(), request.getContentLengthLong(),
                request.getHeader("Transfer-Encoding"));
        try {
            filterChain.doFilter(request, response);
            log.info("OFFER_UPLOAD_REQUEST_FINISHED uri={} status={}", uri, response.getStatus());
        } catch (IOException | ServletException | RuntimeException ex) {
            log.error("OFFER_UPLOAD_REQUEST_EXCEPTION uri={} contentType={} contentLength={} exceptionType={} message={}",
                    uri, request.getContentType(), request.getContentLengthLong(), ex.getClass().getName(), ex.getMessage(), ex);
            throw ex;
        }
    }
}

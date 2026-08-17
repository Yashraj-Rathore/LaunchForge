package dev.launchforge.controlapi.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

final class SecurityProblemWriter {
  private SecurityProblemWriter() {}

  static void write(
      HttpServletRequest request,
      HttpServletResponse response,
      HttpStatus status,
      String code,
      String detail)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response
        .getWriter()
        .write(
            "{\"type\":\"https://launchforge.dev/problems/"
                + code.toLowerCase(Locale.ROOT)
                + "\",\"title\":\""
                + status.getReasonPhrase()
                + "\",\"status\":"
                + status.value()
                + ",\"detail\":\""
                + detail
                + "\",\"code\":\""
                + code
                + "\",\"correlationId\":\""
                + CorrelationIdFilter.get(request)
                + "\"}");
  }
}

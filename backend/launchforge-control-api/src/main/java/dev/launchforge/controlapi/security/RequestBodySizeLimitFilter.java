package dev.launchforge.controlapi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public final class RequestBodySizeLimitFilter extends OncePerRequestFilter {
  private final int maximumBytes;

  public RequestBodySizeLimitFilter(ControlPlaneAbuseProperties properties) {
    maximumBytes = properties.maximumRequestBytes();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!request.getRequestURI().startsWith("/api/") || !mayHaveBody(request.getMethod())) {
      chain.doFilter(request, response);
      return;
    }
    if (request.getContentLengthLong() > maximumBytes) {
      tooLarge(request, response);
      return;
    }
    byte[] body = request.getInputStream().readNBytes(maximumBytes + 1);
    if (body.length > maximumBytes) {
      tooLarge(request, response);
      return;
    }
    chain.doFilter(new CachedBodyRequest(request, body), response);
  }

  private static boolean mayHaveBody(String method) {
    return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
  }

  private static void tooLarge(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    SecurityProblemWriter.write(
        request,
        response,
        HttpStatus.CONTENT_TOO_LARGE,
        "MANAGEMENT_BODY_TOO_LARGE",
        "The management request body exceeds the version-one limit");
  }

  private static final class CachedBodyRequest extends HttpServletRequestWrapper {
    private final byte[] body;

    private CachedBodyRequest(HttpServletRequest request, byte[] body) {
      super(request);
      this.body = body.clone();
    }

    @Override
    public ServletInputStream getInputStream() {
      ByteArrayInputStream input = new ByteArrayInputStream(body);
      return new ServletInputStream() {
        @Override
        public boolean isFinished() {
          return input.available() == 0;
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
          throw new UnsupportedOperationException("Non-blocking reads are not supported");
        }

        @Override
        public int read() {
          return input.read();
        }
      };
    }

    @Override
    public BufferedReader getReader() {
      String encoding = getCharacterEncoding();
      Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
      return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }

    @Override
    public int getContentLength() {
      return body.length;
    }

    @Override
    public long getContentLengthLong() {
      return body.length;
    }
  }
}

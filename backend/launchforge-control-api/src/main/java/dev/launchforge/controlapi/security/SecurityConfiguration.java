package dev.launchforge.controlapi.security;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableConfigurationProperties({SessionSecurityProperties.class, ControlPlaneAbuseProperties.class})
public class SecurityConfiguration {
  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      SessionSecurityProperties sessionProperties,
      ManagementRateLimiter rateLimiter,
      Clock clock)
      throws Exception {
    HttpSessionCsrfTokenRepository csrfRepository = new HttpSessionCsrfTokenRepository();
    csrfRepository.setHeaderName("X-CSRF-TOKEN");

    http.authorizeHttpRequests(
            authorization ->
                authorization
                    .requestMatchers(
                        "/",
                        "/index.html",
                        "/assets/**",
                        "/error",
                        "/actuator/health",
                        "/actuator/info",
                        "/oauth2/**",
                        "/login/**")
                    .permitAll()
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .exceptionHandling(
            exceptions ->
                exceptions.defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    request -> request.getRequestURI().startsWith("/api/")))
        .oauth2Login(oauth2 -> {})
        .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository))
        .headers(
            headers ->
                headers
                    .contentSecurityPolicy(
                        policy ->
                            policy.policyDirectives(
                                "default-src 'self'; object-src 'none'; base-uri 'self'; "
                                    + "frame-ancestors 'none'; form-action 'self'; "
                                    + "script-src 'self'; style-src 'self'; img-src 'self' data:; "
                                    + "font-src 'self'; connect-src 'self'"))
                    .httpStrictTransportSecurity(
                        hsts ->
                            hsts.includeSubDomains(true).preload(true).maxAgeInSeconds(31_536_000))
                    .referrerPolicy(
                        policy ->
                            policy.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                    .frameOptions(frame -> frame.deny()))
        .sessionManagement(
            sessions -> sessions.sessionFixation(fixation -> fixation.changeSessionId()))
        .logout(
            logout ->
                logout
                    .logoutUrl("/api/v1/auth/logout")
                    .invalidateHttpSession(true)
                    .clearAuthentication(true)
                    .deleteCookies("launchforge_session", "__Host-launchforge_session")
                    .logoutSuccessHandler(
                        (request, response, authentication) ->
                            response.setStatus(HttpStatus.NO_CONTENT.value())))
        .addFilterAfter(
            new AbsoluteSessionLifetimeFilter(sessionProperties, clock), AuthorizationFilter.class)
        .addFilterBefore(new ManagementRateLimitFilter(rateLimiter), AuthorizationFilter.class);
    return http.build();
  }
}

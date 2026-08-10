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

@Configuration
@EnableConfigurationProperties(SessionSecurityProperties.class)
public class SecurityConfiguration {
  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, SessionSecurityProperties sessionProperties, Clock clock)
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
            new AbsoluteSessionLifetimeFilter(sessionProperties, clock), AuthorizationFilter.class);
    return http.build();
  }
}

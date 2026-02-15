package com.pet.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pet.api.error.ApiError;
import com.pet.api.response.ApiResponse;
import com.pet.security.JwtAuthenticationFilter;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter,
      ObjectMapper objectMapper)
      throws Exception {
    return http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint((request, response, authException) -> {
              response.setStatus(401);
              response.setCharacterEncoding(StandardCharsets.UTF_8.name());
              response.setContentType("application/json;charset=UTF-8");
              String body = objectMapper.writeValueAsString(
                  new ApiResponse<>(ApiError.UNAUTHORIZED.code(), ApiError.UNAUTHORIZED.message(), null));
              response.getWriter().write(body);
            })
            .accessDeniedHandler((request, response, accessDeniedException) -> {
              response.setStatus(403);
              response.setCharacterEncoding(StandardCharsets.UTF_8.name());
              response.setContentType("application/json;charset=UTF-8");
              String body = objectMapper.writeValueAsString(
                  new ApiResponse<>(ApiError.FORBIDDEN.code(), ApiError.FORBIDDEN.message(), null));
              response.getWriter().write(body);
            }))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/auth/**", "/actuator/health", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
            .requestMatchers("/api/**").authenticated()
            .anyRequest().permitAll())
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .httpBasic(Customizer.withDefaults())
        .build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}

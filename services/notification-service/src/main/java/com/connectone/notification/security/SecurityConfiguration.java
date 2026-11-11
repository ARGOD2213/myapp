package com.connectone.notification.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain chain(HttpSecurity http, JwtVerifier verifier) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> error(res, 401, "Unauthorized", "AUTHENTICATION", "Authentication is required or the token is invalid"))
                        .accessDeniedHandler((req, res, ex) -> error(res, 403, "Forbidden", "AUTHORIZATION", "You do not have permission to access this resource")))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/prometheus").permitAll()   // internal network only (the gateway never routes /actuator)
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(new OncePerRequestFilter() {
                    @Override
                    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
                        String h = req.getHeader(HttpHeaders.AUTHORIZATION);
                        if (h != null && h.startsWith("Bearer ")) {
                            JwtVerifier.Principal p = verifier.verify(h.substring(7));
                            if (p != null) {
                                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                                p.roles().forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
                                p.permissions().forEach(x -> authorities.add(new SimpleGrantedAuthority(x)));
                                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, authorities));
                                MDC.put("userId", Long.toString(p.userId()));
                            }
                        }
                        chain.doFilter(req, res);
                    }
                }, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private static void error(HttpServletResponse res, int status, String name, String category, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.getWriter().write("{\"status\":" + status + ",\"error\":\"" + name + "\",\"category\":\"" + category + "\",\"message\":\"" + message
                + "\",\"correlationId\":\"" + MDC.get("correlationId") + "\"}");
    }
}

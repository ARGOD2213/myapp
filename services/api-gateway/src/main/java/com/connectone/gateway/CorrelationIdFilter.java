package com.connectone.gateway;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Edge concern: every request gets a correlation id HERE (or keeps a well-formed one sent by the client). It is forwarded to the
 * downstream services in X-Correlation-Id, returned to the caller, and logged, so one user action is traceable across
 * gateway -> core-service -> Kafka -> notification-service by grepping a single id.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Correlation-Id";
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]{8,64}");
    private static final Logger ACCESS = LoggerFactory.getLogger("connectone.gateway.access");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String inbound = request.getHeader(HEADER);
        String id = inbound != null && SAFE.matcher(inbound).matches() ? inbound : UUID.randomUUID().toString();
        MDC.put("correlationId", id);
        response.setHeader(HEADER, id);
        HttpServletRequest forwarded = new HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                return HEADER.equalsIgnoreCase(name) ? id : super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                return HEADER.equalsIgnoreCase(name) ? Collections.enumeration(java.util.List.of(id)) : super.getHeaders(name);
            }

            @Override
            public Enumeration<String> getHeaderNames() {
                java.util.Set<String> names = new java.util.LinkedHashSet<>(Collections.list(super.getHeaderNames()));
                names.add(HEADER);
                return Collections.enumeration(names);
            }
        };
        long start = System.nanoTime();
        try {
            chain.doFilter(forwarded, response);
        } finally {
            if (!request.getRequestURI().startsWith("/actuator/health")) {
                ACCESS.info("method={} path={} status={} durationMs={}", request.getMethod(), request.getRequestURI(), response.getStatus(), (System.nanoTime() - start) / 1_000_000);
            }
            MDC.clear();
        }
    }
}

package com.connectone.notification.web;

import java.io.IOException;
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
import jakarta.servlet.http.HttpServletResponse;

/** Continues the correlation id created at the gateway so one user action can be followed across services. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]{8,64}");
    private static final Logger ACCESS = LoggerFactory.getLogger("connectone.access");

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String in = req.getHeader(HEADER);
        String id = in != null && SAFE.matcher(in).matches() ? in : UUID.randomUUID().toString();
        MDC.put("correlationId", id);
        res.setHeader(HEADER, id);
        long t = System.nanoTime();
        try {
            chain.doFilter(req, res);
        } finally {
            if (!req.getRequestURI().startsWith("/actuator/health")) {
                ACCESS.info("method={} path={} status={} durationMs={}", req.getMethod(), req.getRequestURI(), res.getStatus(), (System.nanoTime() - t) / 1_000_000);
            }
            MDC.clear();
        }
    }
}

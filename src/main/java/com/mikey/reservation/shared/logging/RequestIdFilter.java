package com.mikey.reservation.shared.logging;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component @Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = request.getHeader("X-Request-ID");
        if (id == null || !id.matches("[a-zA-Z0-9_-]{1,64}")) id = UUID.randomUUID().toString();
        MDC.put("requestId", id);
        response.setHeader("X-Request-ID", id);
        long start = System.nanoTime();
        try { chain.doFilter(request, response); }
        finally {
            log.info("http_request method={} path={} status={} durationMs={}", request.getMethod(),
                    request.getRequestURI().replaceAll("[\\r\\n]", "_"), response.getStatus(), (System.nanoTime() - start) / 1_000_000);
            MDC.remove("requestId");
        }
    }
}


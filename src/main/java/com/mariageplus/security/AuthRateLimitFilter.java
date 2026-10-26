package com.mariageplus.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Applies an IP-based limit to anonymous authentication endpoints. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final AuthRateLimiter rateLimiter;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || endpoint(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String endpoint = endpoint(request);
        if (endpoint == null || rateLimiter.tryAcquire(request.getRemoteAddr(), endpoint)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(429);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", Long.toString(rateLimiter.getWindowSeconds()));
        response.getWriter().write("{\"error\":\"Trop de tentatives. Réessayez dans un instant.\"}");
    }

    private String endpoint(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String path = request.getRequestURI().substring(contextPath.length());
        return switch (path) {
            case "/auth/login" -> "login";
            case "/auth/register" -> "register";
            case "/auth/refresh" -> "refresh";
            default -> null;
        };
    }
}

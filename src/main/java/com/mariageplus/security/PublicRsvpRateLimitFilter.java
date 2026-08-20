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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Applies rate limiting only to the anonymous public invitation endpoints. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
public class PublicRsvpRateLimitFilter extends OncePerRequestFilter {

    private static final Pattern PUBLIC_INVITATION_PATH =
            Pattern.compile("^/api/public/invitations/([^/]+)(/rsvp)?$");

    private final PublicRsvpRateLimiter rateLimiter;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method) && !"POST".equalsIgnoreCase(method)) {
            return true;
        }
        return !PUBLIC_INVITATION_PATH.matcher(requestPath(request)).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Matcher path = PUBLIC_INVITATION_PATH.matcher(requestPath(request));
        if (!path.matches()) {
            filterChain.doFilter(request, response);
            return;
        }

        String publicToken = path.group(1);
        boolean submission = "POST".equalsIgnoreCase(request.getMethod());
        if (!rateLimiter.tryAcquire(request.getRemoteAddr(), publicToken, submission)) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Retry-After", Long.toString(rateLimiter.getWindowSeconds()));
            response.getWriter().write("{\"error\":\"Trop de requetes. Reessayez dans un instant.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String requestPath(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String requestUri = request.getRequestURI();
        return requestUri.substring(contextPath.length());
    }
}

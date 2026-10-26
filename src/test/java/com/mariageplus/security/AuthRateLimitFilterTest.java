package com.mariageplus.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthRateLimitFilterTest {

    @Test
    void filter_ShouldReturn429_WhenLoginIpLimitIsExceeded() throws Exception {
        AuthRateLimiter limiter = new AuthRateLimiter(2, 5, 10, 60);
        AuthRateLimitFilter filter = new AuthRateLimitFilter(limiter);

        for (int request = 0; request < 2; request++) {
            MockHttpServletResponse response = execute(filter, "/auth/login");
            assertEquals(200, response.getStatus());
        }

        MockHttpServletResponse rejected = execute(filter, "/auth/login");
        assertEquals(429, rejected.getStatus());
        assertEquals("60", rejected.getHeader("Retry-After"));
    }

    @Test
    void filter_ShouldNotLimitUnrelatedRoutes() throws Exception {
        AuthRateLimiter limiter = new AuthRateLimiter(1, 1, 1, 60);
        AuthRateLimitFilter filter = new AuthRateLimitFilter(limiter);

        MockHttpServletResponse response = execute(filter, "/api/events");

        assertEquals(200, response.getStatus());
    }

    private MockHttpServletResponse execute(AuthRateLimitFilter filter, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr("192.0.2.10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}

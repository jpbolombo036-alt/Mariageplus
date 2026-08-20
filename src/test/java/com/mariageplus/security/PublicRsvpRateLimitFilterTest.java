package com.mariageplus.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PublicRsvpRateLimitFilterTest {

    @Test
    void filter_ShouldReturn429AndRetryAfter_WhenSubmissionLimitIsExceeded() throws Exception {
        PublicRsvpRateLimiter limiter = new PublicRsvpRateLimiter(30, 6, 120, 60);
        PublicRsvpRateLimitFilter filter = new PublicRsvpRateLimitFilter(limiter);

        for (int request = 0; request < 6; request++) {
            MockHttpServletResponse response = execute(filter);
            assertEquals(200, response.getStatus());
        }

        MockHttpServletResponse rejected = execute(filter);
        assertEquals(429, rejected.getStatus());
        assertEquals("60", rejected.getHeader("Retry-After"));
    }

    private MockHttpServletResponse execute(PublicRsvpRateLimitFilter filter) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/public/invitations/token/rsvp");
        request.setRemoteAddr("192.0.2.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}

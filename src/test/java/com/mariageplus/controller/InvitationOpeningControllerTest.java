package com.mariageplus.controller;

import com.mariageplus.service.InvitationCardService;
import com.mariageplus.service.InvitationService;
import com.mariageplus.service.RsvpService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InvitationOpeningControllerTest {
    private final InvitationService invitations = mock(InvitationService.class);
    private final RsvpService rsvp = mock(RsvpService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new PublicInvitationController(
            rsvp, mock(InvitationCardService.class), invitations)).build();

    @Test
    void pageOpeningReturnsNoContentAndRecordsToken() throws Exception {
        mvc.perform(post("/api/public/invitations/test-token/opened"))
                .andExpect(status().isNoContent());
        verify(invitations).markOpened("test-token");
    }

    @Test
    void readingInvitationDoesNotRecordAnOpening() throws Exception {
        mvc.perform(get("/api/public/invitations/test-token"))
                .andExpect(status().isOk());
        verify(rsvp).getPublicInvitation("test-token");
        verifyNoInteractions(invitations);
    }
}
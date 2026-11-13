package com.mariageplus.dto;

import com.mariageplus.dto.event.UpdateEventRequest;
import com.mariageplus.dto.event.CreateEventRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InvitationCardTemplateTest {
    @Test
    void validatesTemplateWithoutBreakingPartialUpdates() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var request = new UpdateEventRequest();
            assertTrue(validator.validateProperty(request, "invitationCardTemplate").isEmpty());
            for (String template : new String[]{"STANDARD", "ROMANCE", "PRESTIGE"}) {
                request.setInvitationCardTemplate(template);
                assertTrue(validator.validateProperty(request, "invitationCardTemplate").isEmpty());
            }
            request.setInvitationCardTemplate("UNKNOWN");
            assertFalse(validator.validateProperty(request, "invitationCardTemplate").isEmpty());
            var creation = new CreateEventRequest();
            creation.setInvitationCardTemplate("UNKNOWN");
            assertFalse(validator.validateProperty(creation, "invitationCardTemplate").isEmpty());
        }
    }
}

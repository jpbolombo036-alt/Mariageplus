package com.mariageplus.service;

import com.mariageplus.entity.Guest;
import com.mariageplus.entity.Wedding;
import com.mariageplus.exception.MailDeliveryException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvitationMailServiceTest {

    @Mock private ObjectProvider<JavaMailSender> mailSenderProvider;
    @Mock private SpringTemplateEngine templateEngine;
    @Mock private IcsCalendarService icsCalendarService;
    @Mock private JavaMailSender mailSender;

    private InvitationMailService service;

    @BeforeEach
    void setUp() {
        service = new InvitationMailService(mailSenderProvider, templateEngine, icsCalendarService);
        ReflectionTestUtils.setField(service, "frontendUrl", "http://localhost:3000/");
        ReflectionTestUtils.setField(service, "mailFrom", "noreply@test.local");
        ReflectionTestUtils.setField(service, "smtpUsername", "");
        ReflectionTestUtils.setField(service, "smtpPassword", "");
    }

    @Test
    void publicInviteUrl_stripsTrailingSlash() {
        assertEquals("http://localhost:3000/invitations/abc", service.publicInviteUrl("abc"));
    }

    @Test
    void sendInvitation_notConfigured_returnsFalse() {
        Guest guest = Guest.builder().firstName("Jean").email("jean@ex.com").build();
        Wedding wedding = Wedding.builder()
                .groomFirstName("Jean").groomLastName("K")
                .brideFirstName("Marie").brideLastName("M").build();

        assertFalse(service.sendInvitation(guest, wedding, null, "http://x"));
        verifyNoInteractions(mailSenderProvider);
    }

    @Test
    void sendInvitation_configured_sendsMime() {
        ReflectionTestUtils.setField(service, "smtpUsername", "user");
        ReflectionTestUtils.setField(service, "smtpPassword", "pass");
        MimeMessage mimeMessage = new JavaMailSenderImpl().createMimeMessage();
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("mail/invitation"), any(Context.class))).thenReturn("<html/>");

        Guest guest = Guest.builder().firstName("Jean").email("jean@ex.com").build();
        Wedding wedding = Wedding.builder()
                .groomFirstName("Jean").groomLastName("Kabongo")
                .brideFirstName("Marie").brideLastName("Mukendi").build();

        assertTrue(service.sendInvitation(guest, wedding, null, "http://localhost:3000/invitations/tok"));
        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendInvitation_configured_failure_throwsMailDelivery() {
        ReflectionTestUtils.setField(service, "smtpUsername", "user");
        ReflectionTestUtils.setField(service, "smtpPassword", "pass");
        MimeMessage mimeMessage = new JavaMailSenderImpl().createMimeMessage();
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("mail/invitation"), any(Context.class))).thenReturn("<html/>");
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        Guest guest = Guest.builder().firstName("Jean").email("jean@ex.com").build();
        Wedding wedding = Wedding.builder()
                .groomFirstName("Jean").groomLastName("K")
                .brideFirstName("Marie").brideLastName("M").build();

        assertThrows(MailDeliveryException.class,
                () -> service.sendInvitation(guest, wedding, null, "http://x"));
    }
}

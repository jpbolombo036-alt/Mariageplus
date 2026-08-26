package com.mariageplus.controller;

import com.mariageplus.dto.invitation.PublicInvitationPage;
import com.mariageplus.dto.rsvp.SubmitRsvpRequest;
import com.mariageplus.service.RsvpService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Page web publique d'invitation ({@code /invitations/{token}}), servie par le backend
 * en Thymeleaf. C'est le lien mis dans l'email / le QR. Ne contient aucune donnée
 * administrative ; l'accès est soumis aux mêmes règles que l'API publique (résolution
 * par publicToken, 404 si inconnu / annulé / expiré / mariage passé).
 */
@Controller
@RequiredArgsConstructor
public class PublicInvitationPageController {

    private final RsvpService rsvpService;

    @GetMapping("/invitations/{publicToken}")
    public String invitation(@PathVariable String publicToken, Model model) {
        PublicInvitationPage page = rsvpService.getPublicPage(publicToken);
        model.addAttribute("page", page);
        return "public-invitation";
    }

    @PostMapping("/invitations/{publicToken}/rsvp")
    public String submit(@PathVariable String publicToken,
                         @RequestParam String status,
                         @RequestParam(required = false) Integer numberOfAttendees,
                         RedirectAttributes redirect) {
        SubmitRsvpRequest request = new SubmitRsvpRequest();
        request.setStatus(status);
        request.setNumberOfAttendees(numberOfAttendees);
        rsvpService.submitRsvp(publicToken, request);
        return "redirect:/invitations/" + publicToken;
    }
}
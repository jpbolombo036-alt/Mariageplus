package com.mariageplus.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.mariageplus.entity.*;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.repository.*;
import com.mariageplus.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Documents PDF du module « Salles » : devis et reçu de paiement. */
@Service
@RequiredArgsConstructor
public class VenueDocumentService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final VenueBookingService bookingService;
    private final VenueRepository venueRepository;
    private final VenueRateRepository rateRepository;
    private final VenueClientRepository clientRepository;
    private final VenueBookingExtraRepository extraRepository;
    private final VenueBookingPaymentRepository paymentRepository;
    private final OrganizationRepository organizationRepository;
    private final VenueDocumentSettingsService documentSettingsService;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public byte[] quotePdf(Long bookingId) {
        securityUtils.assertPermission("BOOKING_VIEW");
        VenueBooking booking = bookingService.loadInScope(bookingId);
        Venue venue = venueRepository.findById(booking.getVenueId()).orElse(null);
        VenueClient client = clientRepository.findById(booking.getClientId()).orElse(null);
        VenueCurrency cur = booking.getCurrency();

        return render(booking.getOrganizationId(), doc -> {
            header(doc, booking.getOrganizationId(), "DEVIS N° D-" + VenueBookingService.reference(booking).substring(4),
                    "Date : " + DATE.format(LocalDate.now()));
            partyBlock(doc, booking, venue, client);

            PdfPTable lines = new PdfPTable(new float[]{5, 1, 2, 2});
            lines.setWidthPercentage(100);
            lines.setSpacingBefore(12);
            for (String h : List.of("Désignation", "Qté", "Prix unitaire", "Montant")) {
                lines.addCell(headerCell(h));
            }
            String baseLabel = "Location de la salle";
            if (booking.getRateId() != null) {
                VenueRate rate = rateRepository.findById(booking.getRateId()).orElse(null);
                if (rate != null) {
                    baseLabel += " (" + rate.getLabel() + ")";
                }
            }
            line(lines, baseLabel, 1, booking.getBasePrice(), cur);
            for (VenueBookingExtra e : extraRepository.findByBookingIdOrderByIdAsc(booking.getId())) {
                line(lines, e.getLabel(), e.getQuantity(), e.getUnitPrice(), cur);
            }
            doc.add(lines);

            BigDecimal paid = bookingService.paidAmount(booking.getId());
            PdfPTable totals = new PdfPTable(new float[]{8, 2});
            totals.setWidthPercentage(100);
            if (booking.getDiscount().signum() > 0) {
                totalRow(totals, "Remise", "- " + VenueMoney.format(booking.getDiscount(), cur), false);
            }
            totalRow(totals, "TOTAL", VenueMoney.format(booking.getTotalAmount(), cur), true);
            if (paid.signum() > 0) {
                totalRow(totals, "Déjà versé", VenueMoney.format(paid, cur), false);
                totalRow(totals, "Reste à payer", VenueMoney.format(booking.getTotalAmount().subtract(paid), cur), true);
            }
            doc.add(totals);
            if (booking.getStatus() == VenueBookingStatus.OPTION && booking.getOptionExpiresAt() != null) {
                doc.add(new Paragraph(" "));
                doc.add(new Paragraph("Date réservée en option jusqu'au " + DATE_TIME.format(booking.getOptionExpiresAt())
                        + ". Sans acompte à cette date, la salle sera libérée.", font(10, false)));
            }
        });
    }

    @Transactional(readOnly = true)
    public byte[] receiptPdf(Long bookingId, Long paymentId) {
        securityUtils.assertPermission("BOOKING_VIEW");
        VenueBooking booking = bookingService.loadInScope(bookingId);
        VenueBookingPayment payment = paymentRepository.findByIdAndBookingId(paymentId, booking.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Paiement introuvable : " + paymentId));
        Venue venue = venueRepository.findById(booking.getVenueId()).orElse(null);
        VenueClient client = clientRepository.findById(booking.getClientId()).orElse(null);
        VenueCurrency cur = booking.getCurrency();

        // Reste à payer APRÈS ce paiement (paiements dans l'ordre chronologique).
        BigDecimal paidUpTo = BigDecimal.ZERO;
        for (VenueBookingPayment p : paymentRepository.findByBookingIdOrderByPaidAtAscIdAsc(booking.getId())) {
            paidUpTo = p.getKind() == VenuePaymentKind.REFUND
                    ? paidUpTo.subtract(p.getAmountInBookingCurrency())
                    : paidUpTo.add(p.getAmountInBookingCurrency());
            if (p.getId().equals(payment.getId())) {
                break;
            }
        }
        final BigDecimal paidToDate = paidUpTo;
        final boolean refund = payment.getKind() == VenuePaymentKind.REFUND;
        BigDecimal remaining = booking.getTotalAmount().subtract(paidToDate);

        return render(booking.getOrganizationId(), doc -> {
            header(doc, booking.getOrganizationId(),
                    (refund ? "REÇU DE REMBOURSEMENT N° " : "REÇU N° ") + VenueBookingService.receiptNumber(payment),
                    (refund ? "Date du remboursement : " : "Date du paiement : ") + DATE_TIME.format(payment.getPaidAt()));
            partyBlock(doc, booking, venue, client);

            PdfPTable t = new PdfPTable(new float[]{4, 6});
            t.setWidthPercentage(100);
            t.setSpacingBefore(12);
            row(t, refund ? "Montant remboursé" : "Montant reçu", VenueMoney.format(payment.getAmount(), payment.getCurrency()), true);
            if (payment.getCurrency() != cur) {
                row(t, "Taux appliqué", "1 USD = " + payment.getExchangeRate().stripTrailingZeros().toPlainString() + " CDF", false);
                row(t, "Équivalent", VenueMoney.format(payment.getAmountInBookingCurrency(), cur), false);
            }
            row(t, refund ? "Mode de remboursement" : "Mode de paiement", label(payment.getMethod()), false);
            if (payment.getReference() != null) {
                row(t, "Référence", payment.getReference(), false);
            }
            row(t, "Total de la réservation", VenueMoney.format(booking.getTotalAmount(), cur), false);
            row(t, "Total versé à ce jour (net)", VenueMoney.format(paidToDate, cur), false);
            if (booking.getStatus() == VenueBookingStatus.ANNULEE) {
                row(t, "Réservation annulée — montant retenu", VenueMoney.format(paidToDate, cur), true);
            } else {
                row(t, "Reste à payer", VenueMoney.format(remaining, cur), true);
            }
            doc.add(t);
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Signature et cachet :", font(10, false)));
        });
    }

    // ---- Mise en page ---------------------------------------------------------

    private interface Body {
        void write(Document doc) throws DocumentException;
    }

    private byte[] render(Long organizationId, Body body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // Marge basse élargie : le pied de page (mentions légales) y est écrit sur chaque page.
        Document doc = new Document(PageSize.A4, 50, 50, 50, 70);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            List<String> footer = footerLines(organizationId);
            if (!footer.isEmpty()) {
                writer.setPageEvent(new PdfPageEventHelper() {
                    @Override
                    public void onEndPage(PdfWriter w, Document d) {
                        float y = 28 + 11 * (footer.size() - 1);
                        for (String line : footer) {
                            ColumnText.showTextAligned(w.getDirectContent(), Element.ALIGN_CENTER,
                                    new Phrase(line, FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(110, 110, 110))),
                                    (d.left() + d.right()) / 2, y, 0);
                            y -= 11;
                        }
                    }
                });
            }
            doc.open();
            body.write(doc);
        } catch (DocumentException e) {
            throw new IllegalStateException("Impossible de générer le PDF", e);
        } finally {
            if (doc.isOpen()) {
                doc.close();
            }
        }
        return out.toByteArray();
    }

    /**
     * En-tête : logo (si fourni) à gauche, raison sociale, coordonnées et mentions
     * légales à droite. Chaque champ vide de l'en-tête retombe sur la fiche de
     * l'organisation (nom, adresse, téléphone, email).
     */
    private void header(Document doc, Long organizationId, String title, String subtitle) throws DocumentException {
        Organization org = organizationRepository.findById(organizationId).orElse(null);
        VenueDocumentSettings s = documentSettingsService.settingsFor(organizationId).orElse(null);
        String name = firstNonBlank(s == null ? null : s.getLegalName(), org == null ? null : org.getName());
        String address = firstNonBlank(s == null ? null : s.getAddress(), org == null ? null : org.getAddress());
        String phone = firstNonBlank(s == null ? null : s.getPhone(), org == null ? null : org.getPhone());
        String email = firstNonBlank(s == null ? null : s.getEmail(), org == null ? null : org.getEmail());

        PdfPTable top = new PdfPTable(new float[]{2, 7});
        top.setWidthPercentage(100);
        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        byte[] logo = documentSettingsService.logoBytes(organizationId);
        if (logo != null) {
            try {
                Image image = Image.getInstance(logo);
                image.scaleToFit(110, 70);
                logoCell.addElement(image);
            } catch (Exception ignored) {
                // Logo illisible : le document reste valable sans lui.
            }
        }
        PdfPCell identity = new PdfPCell();
        identity.setBorder(Rectangle.NO_BORDER);
        identity.setHorizontalAlignment(Element.ALIGN_RIGHT);
        identity.addElement(aligned(name == null ? "" : name, font(15, true)));
        List<String> contact = new ArrayList<>();
        if (address != null) contact.add(address);
        if (phone != null) contact.add("Tél. " + phone);
        if (email != null) contact.add(email);
        if (!contact.isEmpty()) {
            identity.addElement(aligned(String.join("  ·  ", contact), font(9, false)));
        }
        String legal = legalMentions(s);
        if (legal != null) {
            identity.addElement(aligned(legal, font(8, false)));
        }
        if (logo != null) {
            top.addCell(logoCell);
            top.addCell(identity);
        } else {
            identity.setColspan(2);
            top.addCell(identity);
        }
        doc.add(top);
        doc.add(new Paragraph(" "));
        doc.add(new Paragraph(title, font(14, true)));
        doc.add(new Paragraph(subtitle, font(10, false)));
    }

    private Paragraph aligned(String text, Font font) {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_RIGHT);
        return p;
    }

    /** « RCCM : … · Id. Nat. : … · NIF : … » (seulement les mentions renseignées). */
    private static String legalMentions(VenueDocumentSettings s) {
        if (s == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        if (s.getRccm() != null) parts.add("RCCM : " + s.getRccm());
        if (s.getIdNat() != null) parts.add("Id. Nat. : " + s.getIdNat());
        if (s.getNif() != null) parts.add("NIF : " + s.getNif());
        return parts.isEmpty() ? null : String.join("  ·  ", parts);
    }

    /** Pied de page : texte libre du gérant, puis raison sociale et mentions légales. */
    private List<String> footerLines(Long organizationId) {
        VenueDocumentSettings s = documentSettingsService.settingsFor(organizationId).orElse(null);
        List<String> lines = new ArrayList<>();
        if (s == null) {
            return lines;
        }
        if (s.getFooterNote() != null) {
            lines.add(s.getFooterNote());
        }
        String legal = legalMentions(s);
        if (legal != null) {
            lines.add(s.getLegalName() == null ? legal : s.getLegalName() + "  ·  " + legal);
        }
        return lines;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a.trim();
        return b == null || b.isBlank() ? null : b.trim();
    }

    private void partyBlock(Document doc, VenueBooking b, Venue venue, VenueClient client) throws DocumentException {
        PdfPTable t = new PdfPTable(new float[]{4, 6});
        t.setWidthPercentage(100);
        t.setSpacingBefore(10);
        row(t, "Client", client == null ? "" : client.getFullName()
                + (client.getPhone() == null ? "" : " — " + client.getPhone()), false);
        row(t, "Réservation", VenueBookingService.reference(b) + (b.getTitle() == null ? "" : " — " + b.getTitle()), false);
        row(t, "Salle", venue == null ? "" : venue.getName() + (venue.getAddress() == null ? "" : ", " + venue.getAddress()), false);
        row(t, "Période", "du " + DATE_TIME.format(b.getStartAt()) + " au " + DATE_TIME.format(b.getEndAt()), false);
        if (b.getGuestCount() != null) {
            row(t, "Nombre de personnes", String.valueOf(b.getGuestCount()), false);
        }
        doc.add(t);
    }

    private void line(PdfPTable t, String label, int qty, BigDecimal unit, VenueCurrency cur) {
        t.addCell(cell(label, false));
        t.addCell(cell(String.valueOf(qty), false));
        t.addCell(cell(VenueMoney.format(unit, cur), false));
        t.addCell(cell(VenueMoney.format(unit.multiply(BigDecimal.valueOf(qty)), cur), false));
    }

    private void totalRow(PdfPTable t, String label, String value, boolean bold) {
        PdfPCell l = cell(label, bold);
        l.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(l);
        t.addCell(cell(value, bold));
    }

    private void row(PdfPTable t, String label, String value, boolean bold) {
        PdfPCell l = cell(label, true);
        l.setBackgroundColor(new Color(240, 240, 240));
        t.addCell(l);
        t.addCell(cell(value, bold));
    }

    private PdfPCell headerCell(String text) {
        PdfPCell c = cell(text, true);
        c.setBackgroundColor(new Color(225, 225, 225));
        return c;
    }

    private PdfPCell cell(String text, boolean bold) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, font(10, bold)));
        c.setPadding(5);
        return c;
    }

    private Font font(int size, boolean bold) {
        return FontFactory.getFont(bold ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA, size);
    }

    private String label(VenuePaymentMethod method) {
        return switch (method) {
            case ESPECES -> "Espèces";
            case MPESA -> "M-Pesa";
            case ORANGE_MONEY -> "Orange Money";
            case AIRTEL_MONEY -> "Airtel Money";
            case VIREMENT -> "Virement bancaire";
        };
    }
}

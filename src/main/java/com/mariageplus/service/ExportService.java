package com.mariageplus.service;

import com.mariageplus.dto.dashboard.WeddingDashboardResponse;
import com.mariageplus.entity.Guest;
import com.mariageplus.entity.GuestCategory;
import com.mariageplus.entity.Invitation;
import com.mariageplus.entity.Rsvp;
import com.mariageplus.entity.RsvpStatus;
import com.mariageplus.entity.Event;
import com.mariageplus.entity.WeddingTable;
import com.mariageplus.repository.CheckInRepository;
import com.mariageplus.repository.GuestCategoryRepository;
import com.mariageplus.repository.GuestRepository;
import com.mariageplus.repository.InvitationRepository;
import com.mariageplus.repository.RsvpRepository;
import com.mariageplus.repository.TableAssignmentRepository;
import com.mariageplus.repository.WeddingTableRepository;
import com.mariageplus.security.SecurityUtils;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExportService {

    private final EventService eventService;
    private final GuestRepository guestRepository;
    private final GuestCategoryRepository guestCategoryRepository;
    private final InvitationRepository invitationRepository;
    private final RsvpRepository rsvpRepository;
    private final CheckInRepository checkInRepository;
    private final WeddingTableRepository weddingTableRepository;
    private final TableAssignmentRepository tableAssignmentRepository;
    private final WeddingDashboardService weddingDashboardService;
    private final SecurityUtils securityUtils;

    public byte[] exportGuestsCsv(Long weddingId) {
        securityUtils.assertPermission("GUEST_EXPORT");
        Event event = eventService.loadInOrgScope(weddingId);
        List<Guest> guests = guestRepository.findByWeddingId(weddingId);
        Map<Long, String> categories = guestCategoryRepository.findByWeddingId(weddingId).stream()
                .collect(Collectors.toMap(GuestCategory::getId, GuestCategory::getName, (a, b) -> a));

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("id,firstName,lastName,email,phone,address,allowedCompanions,category,active");
        guests.stream()
                .sorted(Comparator.comparing(Guest::getLastName).thenComparing(Guest::getFirstName))
                .forEach(g -> {
                    String category = g.getCategoryId() == null ? "" : categories.getOrDefault(g.getCategoryId(), "");
                    pw.printf("%d,%s,%s,%s,%s,%s,%d,%s,%s%n",
                            g.getId(),
                            csv(g.getFirstName()),
                            csv(g.getLastName()),
                            csv(g.getEmail()),
                            csv(g.getPhone()),
                            csv(g.getAddress()),
                            g.getAllowedCompanions() == null ? 0 : g.getAllowedCompanions(),
                            csv(category),
                            g.isActive());
                });
        return sw.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] exportInvitationsCsv(Long weddingId) {
        securityUtils.assertPermission("INVITATION_VIEW");
        Event event = eventService.loadInOrgScope(weddingId);
        List<Invitation> invitations = invitationRepository.findByWeddingId(weddingId);
        Map<Long, Rsvp> rsvps = rsvpRepository.findByInvitationIdIn(
                invitations.stream().map(Invitation::getId).toList()).stream()
                .collect(Collectors.toMap(Rsvp::getInvitationId, r -> r, (a, b) -> a));

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("id,guestId,invitationCode,status,sentAt,lastSentAt,reminderCount,openedAt,rsvpStatus,rsvpAttendees");
        invitations.stream()
                .sorted(Comparator.comparing(Invitation::getId))
                .forEach(i -> {
                    Rsvp rsvp = rsvps.get(i.getId());
                    pw.printf("%d,%d,%s,%s,%s,%s,%d,%s,%s,%s%n",
                            i.getId(),
                            i.getGuestId(),
                            csv(i.getInvitationCode()),
                            csv(i.getStatus() == null ? "" : i.getStatus().name()),
                            csv(i.getSentAt() == null ? "" : i.getSentAt().toString()),
                            csv(i.getLastSentAt() == null ? "" : i.getLastSentAt().toString()),
                            i.getReminderCount(),
                            csv(i.getOpenedAt() == null ? "" : i.getOpenedAt().toString()),
                            rsvp == null ? "" : csv(rsvp.getStatus() == null ? "" : rsvp.getStatus().name()),
                            rsvp == null || rsvp.getNumberOfAttendees() == null ? "" : rsvp.getNumberOfAttendees());
                });
        return sw.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] exportRsvpsCsv(Long weddingId) {
        securityUtils.assertPermission("RSVP_VIEW");
        Event event = eventService.loadInOrgScope(weddingId);
        List<Rsvp> rsvps = rsvpRepository.findActiveByWeddingId(weddingId);
        Map<Long, Invitation> invitations = invitationRepository.findByWeddingId(weddingId).stream()
                .collect(Collectors.toMap(Invitation::getId, i -> i, (a, b) -> a));

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("id,invitationId,guestId,status,numberOfAttendees,respondedAt");
        rsvps.stream()
                .sorted(Comparator.comparing(Rsvp::getId))
                .forEach(r -> {
                    Invitation inv = invitations.get(r.getInvitationId());
                    pw.printf("%d,%d,%s,%s,%s,%s%n",
                            r.getId(),
                            r.getInvitationId(),
                            inv == null ? "" : inv.getGuestId(),
                            csv(r.getStatus() == null ? "" : r.getStatus().name()),
                            r.getNumberOfAttendees() == null ? "" : r.getNumberOfAttendees(),
                            csv(r.getRespondedAt() == null ? "" : r.getRespondedAt().toString()));
                });
        return sw.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] exportTablesCsv(Long weddingId) {
        securityUtils.assertPermission("TABLE_VIEW");
        Event event = eventService.loadInOrgScope(weddingId);
        List<WeddingTable> tables = weddingTableRepository.findByWeddingId(weddingId);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("tableId,tableName,capacity,assignedGuests,remaining");
        tables.stream()
                .sorted(Comparator.comparing(WeddingTable::getName))
                .forEach(t -> {
                    long assigned = tableAssignmentRepository.countByWeddingTableId(t.getId());
                    pw.printf("%d,%s,%d,%d,%d%n",
                            t.getId(),
                            csv(t.getName()),
                            t.getCapacity(),
                            assigned,
                            Math.max(0, t.getCapacity() - assigned));
                });
        return sw.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] exportDashboardPdf(Long weddingId) {
        securityUtils.assertPermission("REPORT_VIEW");
        WeddingDashboardResponse dashboard = weddingDashboardService.getDashboard(weddingId);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 50, 50, 50, 50);
        PdfWriter.getInstance(document, baos);
        document.open();

        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
        Font headingFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);

        document.add(new Paragraph("MariagePlus - Rapport", titleFont));
        document.add(new Paragraph("Mariage : " + dashboard.getWeddingName(), headingFont));
        document.add(new Paragraph(" "));

        document.add(new Paragraph("Invités", headingFont));
        PdfPTable guestTable = new PdfPTable(3);
        guestTable.setWidthPercentage(100);
        guestTable.addCell(headerCell("Total invités"));
        guestTable.addCell(headerCell("Non assignés"));
        guestTable.addCell(headerCell("Taux assignés"));
        guestTable.addCell(valueCell(String.valueOf(dashboard.getGuests().getTotal())));
        guestTable.addCell(valueCell(String.valueOf(dashboard.getGuests().getUnassigned())));
        guestTable.addCell(valueCell(dashboard.getGuests().getTotal() == 0 ? "0%" :
                Math.round((dashboard.getGuests().getTotal() - dashboard.getGuests().getUnassigned()) * 100.0 / dashboard.getGuests().getTotal()) + "%"));
        document.add(guestTable);

        document.add(new Paragraph("Invitations", headingFont));
        PdfPTable invTable = new PdfPTable(3);
        invTable.setWidthPercentage(100);
        invTable.addCell(headerCell("Total"));
        invTable.addCell(headerCell("Acceptées"));
        invTable.addCell(headerCell("Refusées"));
        invTable.addCell(valueCell(String.valueOf(dashboard.getInvitations().getTotal())));
        invTable.addCell(valueCell(String.valueOf(dashboard.getInvitations().getAccepted())));
        invTable.addCell(valueCell(String.valueOf(dashboard.getInvitations().getDeclined())));
        document.add(invTable);

        document.add(new Paragraph("Participation", headingFont));
        PdfPTable partTable = new PdfPTable(3);
        partTable.setWidthPercentage(100);
        partTable.addCell(headerCell("Attendus"));
        partTable.addCell(headerCell("Présents"));
        partTable.addCell(headerCell("Taux présence"));
        partTable.addCell(valueCell(String.valueOf(dashboard.getAttendance().getExpected())));
        partTable.addCell(valueCell(String.valueOf(dashboard.getAttendance().getCheckedIn())));
        partTable.addCell(valueCell(String.valueOf(dashboard.getAttendance().getCheckInRate()) + "%"));
        document.add(partTable);

        document.add(new Paragraph("Tables", headingFont));
        PdfPTable tableTable = new PdfPTable(3);
        tableTable.setWidthPercentage(100);
        tableTable.addCell(headerCell("Tables"));
        tableTable.addCell(headerCell("Capacité"));
        tableTable.addCell(headerCell("Assignés"));
        tableTable.addCell(valueCell(String.valueOf(dashboard.getTables().getTotal())));
        tableTable.addCell(valueCell(String.valueOf(dashboard.getTables().getCapacity())));
        tableTable.addCell(valueCell(String.valueOf(dashboard.getTables().getAssignedGuests())));
        document.add(tableTable);

        if (dashboard.getCategories() != null && !dashboard.getCategories().isEmpty()) {
            document.add(new Paragraph("Par catégorie", headingFont));
            PdfPTable catTable = new PdfPTable(4);
            catTable.setWidthPercentage(100);
            catTable.addCell(headerCell("Catégorie"));
            catTable.addCell(headerCell("Invités"));
            catTable.addCell(headerCell("Acceptés"));
            catTable.addCell(headerCell("Attendus"));
            dashboard.getCategories().forEach(c -> {
                catTable.addCell(valueCell(c.getName()));
                catTable.addCell(valueCell(String.valueOf(c.getTotalGuests())));
                catTable.addCell(valueCell(String.valueOf(c.getAccepted())));
                catTable.addCell(valueCell(String.valueOf(c.getExpectedAttendees())));
            });
            document.add(catTable);
        }

        document.close();
        return baos.toByteArray();
    }

    private PdfPCell headerCell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));
        cell.setBackgroundColor(new java.awt.Color(230, 230, 230));
        return cell;
    }

    private PdfPCell valueCell(String text) {
        return new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA, 11)));
    }

    private String csv(String value) {
        if (value == null) return "";
        boolean mustQuote = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        if (mustQuote) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}

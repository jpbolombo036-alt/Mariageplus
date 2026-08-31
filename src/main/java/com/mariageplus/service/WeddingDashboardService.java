package com.mariageplus.service;

import com.mariageplus.dto.dashboard.AttendanceStatisticsResponse;
import com.mariageplus.dto.dashboard.ActivityItemResponse;
import com.mariageplus.dto.dashboard.CategoryStatisticsResponse;
import com.mariageplus.dto.dashboard.GuestStatisticsResponse;
import com.mariageplus.dto.dashboard.InvitationStatisticsResponse;
import com.mariageplus.dto.dashboard.TableStatisticsResponse;
import com.mariageplus.dto.dashboard.UpcomingSessionResponse;
import com.mariageplus.dto.dashboard.WeddingDashboardResponse;
import com.mariageplus.entity.AuditLog;
import com.mariageplus.entity.EventSession;
import com.mariageplus.entity.GuestCategory;
import com.mariageplus.entity.RsvpStatus;
import com.mariageplus.entity.Event;
import com.mariageplus.repository.AuditLogRepository;
import com.mariageplus.repository.CheckInRepository;
import com.mariageplus.repository.EventSessionRepository;
import com.mariageplus.repository.GuestCategoryRepository;
import com.mariageplus.repository.GuestRepository;
import com.mariageplus.repository.InvitationRepository;
import com.mariageplus.repository.RsvpRepository;
import com.mariageplus.repository.TableAssignmentRepository;
import com.mariageplus.repository.WeddingTableRepository;
import com.mariageplus.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Dashboard de statistiques d'un mariage (Étape 9). Source de vérité : données
 * réelles (Guest → Invitation → RSVP → CheckIn ; WeddingTable → TableAssignment).
 * Agrégats COUNT/SUM côté backend (pas de N+1). totalGuests ≠ expectedAttendees.
 * Lecture seule (readOnly). Invitations liées au mariage (pas à un WeddingEvent)
 * → aucune statistique par événement fabriquée.
 *
 * La réponse est filtrée selon le rôle de l'utilisateur connecté :
 * - AGENT_ACCUEIL : check-in uniquement
 * - GESTIONNAIRE_INVITES : invités, invitations, catégories
 * - ORGANISATEUR / SUPER_ADMIN : vue complète
 */
@Service
@RequiredArgsConstructor
public class WeddingDashboardService {

    private final GuestRepository guestRepository;
    private final InvitationRepository invitationRepository;
    private final RsvpRepository rsvpRepository;
    private final CheckInRepository checkInRepository;
    private final WeddingTableRepository weddingTableRepository;
    private final TableAssignmentRepository tableAssignmentRepository;
    private final GuestCategoryRepository guestCategoryRepository;
    private final EventSessionRepository eventSessionRepository;
    private final AuditLogRepository auditLogRepository;
    private final EventService eventService;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public WeddingDashboardResponse getDashboard(Long weddingId) {
        securityUtils.assertPermission("DASHBOARD_VIEW");
        Event event = eventService.loadInOrgScope(weddingId);

        long totalGuests = guestRepository.countByWeddingId(weddingId);

        long totalInvitations = invitationRepository.countByWeddingId(weddingId);
        long accepted = rsvpRepository.countByStatusForWedding(weddingId, RsvpStatus.ACCEPTED);
        long declined = rsvpRepository.countByStatusForWedding(weddingId, RsvpStatus.DECLINED);
        long pending = Math.max(0, totalInvitations - accepted - declined);
        double responseRate = totalInvitations == 0 ? 0 : round2((accepted + declined) * 100.0 / totalInvitations);

        long expected = rsvpRepository.sumAcceptedAttendeesByWedding(weddingId);
        long checkedIn = checkInRepository.sumCheckedInByWedding(weddingId);
        long remaining = Math.max(0, expected - checkedIn);
        double checkInRate = expected == 0 ? 0 : Math.min(round2(checkedIn * 100.0 / expected), 100);

        long totalTables = weddingTableRepository.countByWeddingId(weddingId);
        long tableCapacity = weddingTableRepository.sumCapacityByWeddingId(weddingId);
        long tableAssigned = tableAssignmentRepository.countByWeddingId(weddingId);
        long tableRemaining = Math.max(0, tableCapacity - tableAssigned);
        long unassigned = Math.max(0, totalGuests - tableAssigned);

        List<CategoryStatisticsResponse> categories = guestCategoryRepository.findByWeddingId(weddingId).stream()
                .map(c -> buildCategory(weddingId, c))
                .collect(Collectors.toList());

        List<String> roles = securityUtils.getCurrentRoles();
        RoleView roleView = resolveRoleView(roles);

        WeddingDashboardResponse.WeddingDashboardResponseBuilder builder = WeddingDashboardResponse.builder()
                .weddingId(weddingId)
                .weddingName(event.getName());

        if (roleView == RoleView.FULL) {
            return builder
                    .guests(GuestStatisticsResponse.builder().total(totalGuests).unassigned(unassigned).build())
                    .invitations(InvitationStatisticsResponse.builder()
                            .total(totalInvitations).accepted(accepted).declined(declined).pending(pending)
                            .responseRate(responseRate).build())
                    .attendance(AttendanceStatisticsResponse.builder()
                            .expected(expected).checkedIn(checkedIn).remaining(remaining).checkInRate(checkInRate).build())
                    .tables(TableStatisticsResponse.builder()
                            .total(totalTables).capacity(tableCapacity).assignedGuests(tableAssigned)
                            .remainingCapacity(tableRemaining).build())
                    .categories(categories)
                    .build();
        }

        if (roleView == RoleView.GUEST_MANAGEMENT) {
            return builder
                    .guests(GuestStatisticsResponse.builder().total(totalGuests).unassigned(unassigned).build())
                    .invitations(InvitationStatisticsResponse.builder()
                            .total(totalInvitations).accepted(accepted).declined(declined).pending(pending)
                            .responseRate(responseRate).build())
                    .categories(categories)
                    .build();
        }

        if (roleView == RoleView.CHECKIN_ONLY) {
            return builder
                    .attendance(AttendanceStatisticsResponse.builder()
                            .expected(expected).checkedIn(checkedIn).remaining(remaining).checkInRate(checkInRate).build())
                    .build();
        }

        return builder.build();
    }

    private enum RoleView {
        FULL,
        GUEST_MANAGEMENT,
        CHECKIN_ONLY
    }

    private RoleView resolveRoleView(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return RoleView.FULL;
        }
        if (roles.contains("AGENT_ACCUEIL")) {
            return RoleView.CHECKIN_ONLY;
        }
        if (roles.contains("GESTIONNAIRE_INVITES")) {
            return RoleView.GUEST_MANAGEMENT;
        }
        return RoleView.FULL;
    }

    /** Répartition par catégorie d'invités (GuestCategory → Guest → Invitation → RSVP). */
    private CategoryStatisticsResponse buildCategory(Long weddingId, GuestCategory category) {
        long totalGuests = guestRepository.countByWeddingIdAndCategoryId(weddingId, category.getId());
        long accepted = rsvpRepository.countByStatusForCategory(weddingId, category.getId(), RsvpStatus.ACCEPTED);
        long declined = rsvpRepository.countByStatusForCategory(weddingId, category.getId(), RsvpStatus.DECLINED);
        long totalInv = invitationRepository.countByWeddingIdAndCategory(weddingId, category.getId());
        long pending = Math.max(0, totalInv - accepted - declined);
        long expected = rsvpRepository.sumAcceptedAttendeesByCategory(weddingId, category.getId());
        return CategoryStatisticsResponse.builder()
                .categoryId(category.getId())
                .name(category.getName())
                .totalGuests(totalGuests)
                .accepted(accepted)
                .declined(declined)
                .pending(pending)
                .expectedAttendees(expected)
                .build();
    }

    /**
     * Prochaine session (sous-étape) à venir de l'événement — carte
     * « Prochain événement » du dashboard. Données réelles : event_sessions
     * (sessionDate >= aujourd'hui) + invités attendus (RSVP acceptés).
     *
     * @return null si aucune session à venir (le contrôleur renverra 204).
     */
    @Transactional(readOnly = true)
    public UpcomingSessionResponse getUpcomingSession(Long weddingId) {
        securityUtils.assertPermission("DASHBOARD_VIEW");
        eventService.loadInOrgScope(weddingId);

        LocalDate today = LocalDate.now();
        EventSession session = eventSessionRepository
                .findFirstByEventIdAndSessionDateGreaterThanEqualOrderBySessionDateAscStartTimeAscIdAsc(weddingId, today)
                .orElse(null);
        if (session == null) {
            return null;
        }

        long expected = rsvpRepository.sumAcceptedAttendeesByWedding(weddingId);
        return UpcomingSessionResponse.builder()
                .id(session.getId())
                .name(session.getName())
                .type(session.getType() == null ? null : session.getType().name())
                .sessionDate(session.getSessionDate())
                .startTime(session.getStartTime())
                .endTime(session.getEndTime())
                .venueName(session.getVenueName())
                .city(session.getCity())
                .expectedAttendees(expected)
                .build();
    }

    /**
     * Activité récente de l'organisation de l'événement — carte
     * « Activité récente » du dashboard. Source de vérité : les traces
     * d'audit réelles (audit_logs), les plus récentes d'abord.
     */
    @Transactional(readOnly = true)
    public List<ActivityItemResponse> getRecentActivity(Long weddingId, int limit) {
        securityUtils.assertPermission("DASHBOARD_VIEW");
        Event event = eventService.loadInOrgScope(weddingId);

        int safeLimit = Math.min(Math.max(limit, 1), 50);
        List<AuditLog> logs = auditLogRepository.findByOrganizationId(
                event.getOrganizationId(),
                PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "performedAt")));

        return logs.stream()
                .map(a -> ActivityItemResponse.builder()
                        .id(a.getId())
                        .action(a.getAction())
                        .entityType(a.getEntityType())
                        .entityId(a.getEntityId())
                        .details(a.getDetails())
                        .performedAt(a.getPerformedAt())
                        .userId(a.getUserId())
                        .build())
                .collect(Collectors.toList());
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
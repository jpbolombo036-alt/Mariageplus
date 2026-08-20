package com.mariageplus.service;

import com.mariageplus.dto.dashboard.AttendanceStatisticsResponse;
import com.mariageplus.dto.dashboard.CategoryStatisticsResponse;
import com.mariageplus.dto.dashboard.GuestStatisticsResponse;
import com.mariageplus.dto.dashboard.InvitationStatisticsResponse;
import com.mariageplus.dto.dashboard.TableStatisticsResponse;
import com.mariageplus.dto.dashboard.WeddingDashboardResponse;
import com.mariageplus.entity.GuestCategory;
import com.mariageplus.entity.RsvpStatus;
import com.mariageplus.entity.Wedding;
import com.mariageplus.repository.CheckInRepository;
import com.mariageplus.repository.GuestCategoryRepository;
import com.mariageplus.repository.GuestRepository;
import com.mariageplus.repository.InvitationRepository;
import com.mariageplus.repository.RsvpRepository;
import com.mariageplus.repository.TableAssignmentRepository;
import com.mariageplus.repository.WeddingTableRepository;
import com.mariageplus.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Dashboard de statistiques d'un mariage (Étape 9). Source de vérité : données
 * réelles (Guest → Invitation → RSVP → CheckIn ; WeddingTable → TableAssignment).
 * Agrégats COUNT/SUM côté backend (pas de N+1). totalGuests ≠ expectedAttendees.
 * Lecture seule (readOnly). Invitations liées au mariage (pas à un WeddingEvent)
 * → aucune statistique par événement fabriquée.
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
    private final WeddingService weddingService;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public WeddingDashboardResponse getDashboard(Long weddingId) {
        securityUtils.assertPermission("DASHBOARD_VIEW");
        Wedding wedding = weddingService.loadInOrgScope(weddingId);

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

        return WeddingDashboardResponse.builder()
                .weddingId(weddingId)
                .weddingName(wedding.getDisplayName())
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

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
package com.mariageplus.service;

import com.mariageplus.dto.venue.VenueExchangeRateRequest;
import com.mariageplus.dto.venue.VenueExchangeRateResponse;
import com.mariageplus.entity.User;
import com.mariageplus.entity.VenueExchangeRate;
import com.mariageplus.repository.UserRepository;
import com.mariageplus.repository.VenueExchangeRateRepository;
import com.mariageplus.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Taux de change USD → CDF de la salle, fixé à la main par le gérant
 * (le marché congolais bouge chaque jour). Les paiements dans l'autre devise
 * reprennent ce taux par défaut ; un taux saisi sur un paiement reste prioritaire.
 */
@Service
@RequiredArgsConstructor
public class VenueExchangeRateService {

    private final VenueExchangeRateRepository repository;
    private final UserRepository userRepository;
    private final VenueModuleGuard guard;
    private final SecurityUtils securityUtils;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public VenueExchangeRateResponse overview() {
        Long orgId = guard.requireEnabledOrganization("BOOKING_VIEW");
        List<VenueExchangeRate> history = repository.findTop30ByOrganizationIdOrderByEffectiveAtDescIdDesc(orgId);
        Map<Long, String> names = userRepository.findAllById(history.stream().map(VenueExchangeRate::getSetBy)
                        .filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, u -> (u.getFirstName() + " " + u.getLastName()).trim()));
        Function<VenueExchangeRate, VenueExchangeRateResponse.Rate> map = r -> VenueExchangeRateResponse.Rate.builder()
                .id(r.getId())
                .cdfPerUsd(r.getCdfPerUsd())
                .effectiveAt(r.getEffectiveAt())
                .setByName(r.getSetBy() == null ? null : names.get(r.getSetBy()))
                .note(r.getNote())
                .build();
        List<VenueExchangeRateResponse.Rate> rates = history.stream().map(map).toList();
        return VenueExchangeRateResponse.builder()
                .current(rates.isEmpty() ? null : rates.get(0))
                .history(rates)
                .build();
    }

    @Transactional
    public VenueExchangeRateResponse setRate(VenueExchangeRateRequest request) {
        Long orgId = guard.requireEnabledOrganization("VENUE_MANAGE");
        BigDecimal rate = request.getCdfPerUsd().setScale(4, RoundingMode.HALF_UP);
        VenueExchangeRate saved = repository.save(VenueExchangeRate.builder()
                .organizationId(orgId)
                .cdfPerUsd(rate)
                .effectiveAt(LocalDateTime.now())
                .setBy(securityUtils.getCurrentUserId())
                .note(request.getNote() == null || request.getNote().isBlank() ? null : request.getNote().trim())
                .build());
        auditService.record("VENUE_EXCHANGE_RATE", saved.getId(), "VenueExchangeRate", securityUtils.getCurrentUserId(),
                orgId, "Taux du jour : 1 USD = " + rate.stripTrailingZeros().toPlainString() + " CDF");
        return overview();
    }

    /**
     * Taux à appliquer à un mouvement d'argent : celui saisi s'il y en a un,
     * sinon le taux du jour de la salle.
     *
     * @throws IllegalArgumentException si aucun taux n'est disponible
     */
    public BigDecimal resolve(Long organizationId, BigDecimal given) {
        if (given != null) {
            return given;
        }
        return repository.findFirstByOrganizationIdOrderByEffectiveAtDescIdDesc(organizationId)
                .map(VenueExchangeRate::getCdfPerUsd)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Aucun taux de change défini : fixez le taux du jour dans Ma salle → Taux de change, ou saisissez-le sur le paiement"));
    }
}

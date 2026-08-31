package com.mariageplus.service;

import com.mariageplus.dto.PageResponse;
import com.mariageplus.dto.drink.CreateDrinkRequest;
import com.mariageplus.dto.drink.DrinkResponse;
import com.mariageplus.dto.drink.UpdateDrinkRequest;
import com.mariageplus.entity.Drink;
import com.mariageplus.entity.Event;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.mapper.DrinkMapper;
import com.mariageplus.repository.DrinkRepository;
import com.mariageplus.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DrinkService {

    private final DrinkRepository drinkRepository;
    private final DrinkMapper drinkMapper;
    private final EventService eventService;
    private final SecurityUtils securityUtils;
    private final AuditService auditService;

    @Transactional
    public DrinkResponse create(Long weddingId, CreateDrinkRequest request) {
        securityUtils.assertPermission("DRINK_CREATE");
        Event event = eventService.loadInOrgScope(weddingId);

        Drink drink = Drink.builder()
                .weddingId(weddingId)
                .name(request.getName())
                .description(request.getDescription())
                .displayOrder(request.getDisplayOrder())
                .active(true)
                .build();
        Drink saved = drinkRepository.save(drink);
        auditService.record("DRINK_CREATE", saved.getId(), "Drink",
                securityUtils.getCurrentUserId(), event.getOrganizationId(),
                "Création de la boisson '" + saved.getName() + "'");
        return drinkMapper.toResponse(saved);
    }

    public PageResponse<DrinkResponse> list(Long weddingId, int page, int size, String sortBy, String sortDir) {
        securityUtils.assertPermission("DRINK_VIEW");
        eventService.loadInOrgScope(weddingId);
        Sort sort = "desc".equalsIgnoreCase(sortDir) ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Drink> drinkPage = drinkRepository.findByWeddingId(weddingId, pageable);
        List<DrinkResponse> content = drinkPage.getContent().stream()
                .map(drinkMapper::toResponse).collect(Collectors.toList());
        return PageResponse.of(content, drinkPage);
    }

    public DrinkResponse getById(Long weddingId, Long drinkId) {
        securityUtils.assertPermission("DRINK_VIEW");
        eventService.loadInOrgScope(weddingId);
        return drinkMapper.toResponse(loadDrink(weddingId, drinkId));
    }

    @Transactional
    public DrinkResponse update(Long weddingId, Long drinkId, UpdateDrinkRequest request) {
        securityUtils.assertPermission("DRINK_UPDATE");
        Event event = eventService.loadInOrgScope(weddingId);
        Drink drink = loadDrink(weddingId, drinkId);
        drinkMapper.updateFromRequest(request, drink);
        Drink saved = drinkRepository.save(drink);
        auditService.record("DRINK_UPDATE", saved.getId(), "Drink",
                securityUtils.getCurrentUserId(), event.getOrganizationId(), "Modification de la boisson");
        return drinkMapper.toResponse(saved);
    }

    @Transactional
    public void delete(Long weddingId, Long drinkId) {
        securityUtils.assertPermission("DRINK_DELETE");
        eventService.loadInOrgScope(weddingId);
        Drink drink = loadDrink(weddingId, drinkId);
        drink.softDelete();
        drinkRepository.save(drink);
    }

    public List<DrinkResponse> listActive(Long weddingId) {
        return drinkRepository.findByWeddingIdAndActiveTrue(weddingId).stream()
                .map(drinkMapper::toResponse)
                .collect(Collectors.toList());
    }

    private Drink loadDrink(Long weddingId, Long drinkId) {
        return drinkRepository.findByIdAndWeddingId(drinkId, weddingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Boisson non trouvée avec l'ID: " + drinkId + " pour le mariage " + weddingId));
    }
}

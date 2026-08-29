package com.mariageplus.service;

import com.mariageplus.dto.PageResponse;
import com.mariageplus.dto.guestcategory.CreateGuestCategoryRequest;
import com.mariageplus.dto.guestcategory.GuestCategoryResponse;
import com.mariageplus.dto.guestcategory.UpdateGuestCategoryRequest;
import com.mariageplus.entity.GuestCategory;
import com.mariageplus.entity.Event;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.mapper.GuestCategoryMapper;
import com.mariageplus.repository.GuestCategoryRepository;
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

/**
 * Module catégories d'invités. L'accès est conditionné par le mariage parent
 * (via {@link WeddingService#loadInOrgScope}) + la permission granulaire.
 */
@Service
@RequiredArgsConstructor
public class GuestCategoryService {

    private final GuestCategoryRepository guestCategoryRepository;
    private final GuestCategoryMapper guestCategoryMapper;
    private final EventService eventService;
    private final SecurityUtils securityUtils;
    private final AuditService auditService;

    @Transactional
    public GuestCategoryResponse create(Long weddingId, CreateGuestCategoryRequest request) {
        securityUtils.assertPermission("CATEGORY_CREATE");
        Event event = eventService.loadInOrgScope(weddingId);

        GuestCategory category = GuestCategory.builder()
                .weddingId(weddingId)
                .name(request.getName())
                .description(request.getDescription())
                .displayOrder(request.getDisplayOrder())
                .active(true)
                .build();
        GuestCategory saved = guestCategoryRepository.save(category);
        auditService.record("GUEST_CATEGORY_CREATE", saved.getId(), "GuestCategory",
                securityUtils.getCurrentUserId(), event.getOrganizationId(),
                "Création de la catégorie '" + saved.getName() + "'");
        return guestCategoryMapper.toResponse(saved);
    }

    public PageResponse<GuestCategoryResponse> list(Long weddingId, int page, int size, String sortBy, String sortDir) {
        securityUtils.assertPermission("CATEGORY_VIEW");
        eventService.loadInOrgScope(weddingId);
        Sort sort = "desc".equalsIgnoreCase(sortDir) ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<GuestCategory> categoryPage = guestCategoryRepository.findByWeddingId(weddingId, pageable);
        List<GuestCategoryResponse> content = categoryPage.getContent().stream()
                .map(guestCategoryMapper::toResponse).collect(Collectors.toList());
        return PageResponse.of(content, categoryPage);
    }

    public GuestCategoryResponse getById(Long weddingId, Long categoryId) {
        securityUtils.assertPermission("CATEGORY_VIEW");
        eventService.loadInOrgScope(weddingId);
        return guestCategoryMapper.toResponse(loadCategory(weddingId, categoryId));
    }

    @Transactional
    public GuestCategoryResponse update(Long weddingId, Long categoryId, UpdateGuestCategoryRequest request) {
        securityUtils.assertPermission("CATEGORY_UPDATE");
        Event event = eventService.loadInOrgScope(weddingId);
        GuestCategory category = loadCategory(weddingId, categoryId);
        if (request.getName() != null) category.setName(request.getName());
        if (request.getDescription() != null) category.setDescription(request.getDescription());
        if (request.getDisplayOrder() != null) category.setDisplayOrder(request.getDisplayOrder());
        if (request.getActive() != null) category.setActive(request.getActive());
        GuestCategory saved = guestCategoryRepository.save(category);
        auditService.record("GUEST_CATEGORY_UPDATE", saved.getId(), "GuestCategory",
                securityUtils.getCurrentUserId(), event.getOrganizationId(), "Modification de la catégorie");
        return guestCategoryMapper.toResponse(saved);
    }

    @Transactional
    public void delete(Long weddingId, Long categoryId) {
        securityUtils.assertPermission("CATEGORY_DELETE");
        eventService.loadInOrgScope(weddingId);
        GuestCategory category = loadCategory(weddingId, categoryId);
        category.softDelete();
        guestCategoryRepository.save(category);
    }

    private GuestCategory loadCategory(Long weddingId, Long categoryId) {
        return guestCategoryRepository.findByIdAndWeddingId(categoryId, weddingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Catégorie non trouvée avec l'ID: " + categoryId + " pour le mariage " + weddingId));
    }
}

package com.mariageplus.service;

import com.mariageplus.dto.table.AssignGuestRequest;
import com.mariageplus.dto.table.CreateWeddingTableRequest;
import com.mariageplus.dto.table.MoveGuestRequest;
import com.mariageplus.dto.table.TableAssignmentResponse;
import com.mariageplus.dto.table.UpdateWeddingTableRequest;
import com.mariageplus.dto.table.WeddingTableResponse;
import com.mariageplus.entity.Guest;
import com.mariageplus.entity.TableAssignment;
import com.mariageplus.entity.Event;
import com.mariageplus.entity.WeddingTable;
import com.mariageplus.exception.ConflictException;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.repository.GuestRepository;
import com.mariageplus.repository.TableAssignmentRepository;
import com.mariageplus.repository.WeddingTableRepository;
import com.mariageplus.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Module tables & affectations (Étape 8). Un guest a au plus une affectation
 * active (UNIQUE guest_id), guest et table du même mariage, capacité jamais
 * dépassée, suppression d'une table occupée refusée (409). Concurrence :
 * affectation/déplacement transactionnels, table cible verrouillée en
 * PESSIMISTIC_WRITE + recalcul du nombre dans la transaction. Isolation :
 * permission + organisation, résolue via WeddingTable → Wedding → Organization.
 */
@Service
@RequiredArgsConstructor
public class WeddingTableService {

    private final WeddingTableRepository weddingTableRepository;
    private final TableAssignmentRepository tableAssignmentRepository;
    private final GuestRepository guestRepository;
    private final EventService eventService;
    private final SecurityUtils securityUtils;
    private final AuditService auditService;

    // ---------------- Tables ----------------

    public List<WeddingTableResponse> list(Long weddingId) {
        securityUtils.assertPermission("TABLE_VIEW");
        eventService.loadInOrgScope(weddingId);
        return weddingTableRepository.findByWeddingId(weddingId).stream()
                .map(this::toTableResponse)
                .collect(Collectors.toList());
    }

    public WeddingTableResponse getById(Long weddingId, Long tableId) {
        securityUtils.assertPermission("TABLE_VIEW");
        return toTableResponse(loadTable(weddingId, tableId));
    }

    @Transactional
    public WeddingTableResponse create(Long weddingId, CreateWeddingTableRequest request) {
        securityUtils.assertPermission("TABLE_CREATE");
        Event event = eventService.loadInOrgScope(weddingId);
        assertNameFree(weddingId, request.getName(), null);

        WeddingTable table = WeddingTable.builder()
                .weddingId(weddingId)
                .name(request.getName().trim())
                .capacity(request.getCapacity())
                .description(request.getDescription())
                .build();
        WeddingTable saved = weddingTableRepository.save(table);
        auditService.record("TABLE_CREATE", saved.getId(), "WeddingTable",
                securityUtils.getCurrentUserId(), event.getOrganizationId(),
                "Création de la table '" + saved.getName() + "'");
        return toTableResponse(saved);
    }

    @Transactional
    public WeddingTableResponse update(Long weddingId, Long tableId, UpdateWeddingTableRequest request) {
        securityUtils.assertPermission("TABLE_UPDATE");
        Event event = eventService.loadInOrgScope(weddingId);
        WeddingTable table = loadTable(weddingId, tableId);
        long assigned = tableAssignmentRepository.countByWeddingTableId(tableId);

        if (request.getName() != null && !request.getName().isBlank()) {
            String newName = request.getName().trim();
            assertNameFree(weddingId, newName, tableId);
            table.setName(newName);
        }
        if (request.getCapacity() != null) {
            if (request.getCapacity() < assigned) {
                throw new ConflictException("Capacité (" + request.getCapacity()
                        + ") inférieure aux invités déjà affectés (" + assigned + ")");
            }
            table.setCapacity(request.getCapacity());
        }
        if (request.getDescription() != null) {
            table.setDescription(request.getDescription());
        }
        WeddingTable saved = weddingTableRepository.save(table);
        auditService.record("TABLE_UPDATE", saved.getId(), "WeddingTable",
                securityUtils.getCurrentUserId(), event.getOrganizationId(),
                "Modification de la table '" + saved.getName() + "'");
        return toTableResponse(saved);
    }

    /**
     * Suppression : autorisée si la table est vide ; refusée (409) sinon (aucune
     * suppression automatique d'affectations).
     */
    @Transactional
    public void delete(Long weddingId, Long tableId) {
        securityUtils.assertPermission("TABLE_DELETE");
        Event event = eventService.loadInOrgScope(weddingId);
        WeddingTable table = loadTable(weddingId, tableId);
        long assigned = tableAssignmentRepository.countByWeddingTableId(tableId);
        if (assigned > 0) {
            throw new ConflictException("Impossible de supprimer : " + assigned
                    + " invité(s) affecté(s) à la table '" + table.getName() + "'");
        }
        weddingTableRepository.delete(table);
        auditService.record("TABLE_DELETE", tableId, "WeddingTable",
                securityUtils.getCurrentUserId(), event.getOrganizationId(), "Suppression de la table");
    }

    // ---------------- Affectation / déplacement ----------------

    /**
     * Affecte un guest à une table (verrou pessimiste sur la table + recalcul du
     * nombre d'affectations dans la transaction → capacité jamais dépassée).
     */
    @Transactional
    public TableAssignmentResponse assign(Long weddingId, Long tableId, AssignGuestRequest request) {
        securityUtils.assertPermission("TABLE_ASSIGN_GUEST");
        Event event = eventService.loadInOrgScope(weddingId);
        WeddingTable table = weddingTableRepository.findByIdForUpdate(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table non trouvée"));
        assertTableBelongsToWedding(table, weddingId);

        Guest guest = guestRepository.findByIdAndWeddingId(request.getGuestId(), weddingId)
                .orElseThrow(() -> new ResourceNotFoundException("Invité non trouvé dans ce mariage"));
        if (tableAssignmentRepository.findByGuestId(guest.getId()).isPresent()) {
            throw new ConflictException("Cet invité est déjà affecté à une table");
        }
        long assigned = tableAssignmentRepository.countByWeddingTableId(tableId);
        if (assigned >= table.getCapacity()) {
            throw new ConflictException("Table '" + table.getName() + "' pleine (capacité " + table.getCapacity() + ")");
        }

        TableAssignment assignment = TableAssignment.builder()
                .weddingTableId(tableId)
                .guestId(guest.getId())
                .assignedBy(securityUtils.getCurrentUserId())
                .build();
        TableAssignment saved = saveAssignment(assignment);
        auditService.record("TABLE_ASSIGN", saved.getId(), "TableAssignment",
                securityUtils.getCurrentUserId(), event.getOrganizationId(),
                "Affectation de '" + guestName(guest) + "' à la table '" + table.getName() + "'");
        return toAssignmentResponse(saved, guest, table);
    }

    /** Déplace une affectation vers une autre table (transactionnel, idempotent). */
    @Transactional
    public TableAssignmentResponse move(Long weddingId, Long assignmentId, MoveGuestRequest request) {
        securityUtils.assertPermission("TABLE_ASSIGN_GUEST");
        Event event = eventService.loadInOrgScope(weddingId);
        TableAssignment assignment = loadAssignment(weddingId, assignmentId);

        WeddingTable target = weddingTableRepository.findByIdForUpdate(request.getTableId())
                .orElseThrow(() -> new ResourceNotFoundException("Table cible non trouvée"));
        assertTableBelongsToWedding(target, weddingId);

        Guest guest = guestRepository.findById(assignment.getGuestId()).orElse(null);
        if (target.getId().equals(assignment.getWeddingTableId())) {
            return toAssignmentResponse(assignment, guest, target);
        }
        long targetAssigned = tableAssignmentRepository.countByWeddingTableId(target.getId());
        if (targetAssigned >= target.getCapacity()) {
            throw new ConflictException("Table '" + target.getName() + "' pleine (capacité " + target.getCapacity() + ")");
        }
        assignment.setWeddingTableId(target.getId());
        TableAssignment saved = tableAssignmentRepository.save(assignment);
        auditService.record("TABLE_MOVE", saved.getId(), "TableAssignment",
                securityUtils.getCurrentUserId(), event.getOrganizationId(),
                "Déplacement de '" + guestName(guest) + "' vers la table '" + target.getName() + "'");
        return toAssignmentResponse(saved, guest, target);
    }

    /** Retrait d'un invité : l'invité n'a plus de table (capacité libérée). */
    @Transactional
    public void remove(Long weddingId, Long assignmentId) {
        securityUtils.assertPermission("TABLE_ASSIGN_GUEST");
        Event event = eventService.loadInOrgScope(weddingId);
        TableAssignment assignment = loadAssignment(weddingId, assignmentId);
        tableAssignmentRepository.delete(assignment);
        auditService.record("TABLE_UNASSIGN", assignmentId, "TableAssignment",
                securityUtils.getCurrentUserId(), event.getOrganizationId(), "Retrait d'un invité d'une table");
    }

    /**
     * Liste toutes les affectations d'un mariage (au plus une par invité), avec le
     * nom de l'invité et de la table résolus par le backend.
     */
    public List<TableAssignmentResponse> listAssignments(Long weddingId) {
        securityUtils.assertPermission("TABLE_VIEW");
        eventService.loadInOrgScope(weddingId);
        Map<Long, WeddingTable> tables = weddingTableRepository.findByWeddingId(weddingId).stream()
                .collect(Collectors.toMap(WeddingTable::getId, t -> t));
        Map<Long, Guest> guests = guestRepository.findByWeddingId(weddingId).stream()
                .collect(Collectors.toMap(Guest::getId, g -> g));
        return tableAssignmentRepository.findAllByWeddingId(weddingId).stream()
                .map(a -> toAssignmentResponse(a,
                        guests.get(a.getGuestId()),
                        tables.get(a.getWeddingTableId())))
                .collect(Collectors.toList());
    }

    /** Sauvegarde en traduisant la contrainte UNIQUE(guest_id) en erreur métier 409. */
    private TableAssignment saveAssignment(TableAssignment assignment) {
        try {
            return tableAssignmentRepository.save(assignment);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new ConflictException("Cet invité est déjà affecté à une table");
        }
    }

    private TableAssignment loadAssignment(Long weddingId, Long assignmentId) {
        TableAssignment assignment = tableAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Affectation non trouvée"));
        WeddingTable table = weddingTableRepository.findById(assignment.getWeddingTableId())
                .orElseThrow(() -> new ResourceNotFoundException("Affectation non trouvée"));
        if (!table.getWeddingId().equals(weddingId)) {
            throw new ResourceNotFoundException("Affectation non trouvée");
        }
        return assignment;
    }

    private WeddingTable loadTable(Long weddingId, Long tableId) {
        return weddingTableRepository.findByIdAndWeddingId(tableId, weddingId)
                .orElseThrow(() -> new ResourceNotFoundException("Table non trouvée"));
    }

    private void assertTableBelongsToWedding(WeddingTable table, Long weddingId) {
        if (table.getWeddingId() == null || !table.getWeddingId().equals(weddingId)) {
            throw new ResourceNotFoundException("Table non trouvée");
        }
    }

    private void assertNameFree(Long weddingId, String name, Long excludeId) {
        String trimmed = name.trim();
        boolean taken = weddingTableRepository.existsByWeddingIdAndName(weddingId, trimmed);
        if (taken && excludeId == null) {
            throw new ConflictException("Une table nommée '" + trimmed + "' existe déjà dans ce mariage");
        }
        if (taken && excludeId != null) {
            weddingTableRepository.findByIdAndWeddingId(excludeId, weddingId).ifPresent(t -> {
                if (!t.getName().equals(trimmed)) {
                    throw new ConflictException("Une table nommée '" + trimmed + "' existe déjà dans ce mariage");
                }
            });
        }
    }

    private WeddingTableResponse toTableResponse(WeddingTable table) {
        long assigned = tableAssignmentRepository.countByWeddingTableId(table.getId());
        return WeddingTableResponse.builder()
                .id(table.getId())
                .name(table.getName())
                .description(table.getDescription())
                .capacity(table.getCapacity())
                .assignedCount(assigned)
                .remainingCapacity(Math.max(0, table.getCapacity() - assigned))
                .build();
    }

    private String guestName(Guest guest) {
        if (guest == null || (guest.getFirstName() == null && guest.getLastName() == null)) {
            return "Inconnu";
        }
        return (guest.getFirstName() == null ? "" : guest.getFirstName())
                + " " + (guest.getLastName() == null ? "" : guest.getLastName());
    }

    private TableAssignmentResponse toAssignmentResponse(TableAssignment assignment, Guest guest, WeddingTable table) {
        return TableAssignmentResponse.builder()
                .assignmentId(assignment.getId())
                .guestId(assignment.getGuestId())
                .guestName(guestName(guest))
                .tableId(table.getId())
                .tableName(table.getName())
                .assignedAt(assignment.getCreatedAt())
                .build();
    }
}
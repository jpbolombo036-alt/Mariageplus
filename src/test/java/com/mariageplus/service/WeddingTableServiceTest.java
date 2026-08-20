package com.mariageplus.service;

import com.mariageplus.dto.table.AssignGuestRequest;
import com.mariageplus.dto.table.MoveGuestRequest;
import com.mariageplus.entity.Guest;
import com.mariageplus.entity.TableAssignment;
import com.mariageplus.entity.Wedding;
import com.mariageplus.entity.WeddingTable;
import com.mariageplus.exception.ConflictException;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.repository.GuestRepository;
import com.mariageplus.repository.TableAssignmentRepository;
import com.mariageplus.repository.WeddingTableRepository;
import com.mariageplus.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires du module tables : permissions, capacité, déplacement et
 * stratégie de concurrence (verrou PESSIMISTIC_WRITE sur la table + recalcul
 * du nombre dans la transaction, comme pour le check-in).
 */
@ExtendWith(MockitoExtension.class)
class WeddingTableServiceTest {

    @Mock private WeddingTableRepository weddingTableRepository;
    @Mock private TableAssignmentRepository tableAssignmentRepository;
    @Mock private GuestRepository guestRepository;
    @Mock private WeddingService weddingService;
    @Mock private SecurityUtils securityUtils;
    @Mock private AuditService auditService;

    @InjectMocks private WeddingTableService weddingTableService;

    private Wedding wedding;
    private WeddingTable table;
    private Guest guest;

    @BeforeEach
    void setUp() {
        wedding = Wedding.builder().build();
        wedding.setId(1L);
        wedding.setOrganizationId(100L);
        table = WeddingTable.builder().weddingId(1L).name("T1").capacity(2).build();
        table.setId(10L);
        guest = Guest.builder().firstName("Jean").lastName("Kabongo").weddingId(1L).build();
        guest.setId(5L);
    }

    private AssignGuestRequest assignRequest(Long guestId) {
        AssignGuestRequest req = new AssignGuestRequest();
        req.setGuestId(guestId);
        return req;
    }

    private MoveGuestRequest moveRequest(Long tableId) {
        MoveGuestRequest req = new MoveGuestRequest();
        req.setTableId(tableId);
        return req;
    }

    private void stubScopeAndTableAndGuest() {
        when(weddingService.loadInOrgScope(1L)).thenReturn(wedding);
        when(weddingTableRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(table));
        when(guestRepository.findByIdAndWeddingId(5L, 1L)).thenReturn(Optional.of(guest));
    }

    @Test
    void assign_requiresPermission() {
        doThrow(new SecurityException("denied")).when(securityUtils).assertPermission("TABLE_ASSIGN_GUEST");
        assertThrows(SecurityException.class,
                () -> weddingTableService.assign(1L, 10L, assignRequest(5L)));
    }

    @Test
    void assign_usesLockedQuery_forConcurrency() {
        stubScopeAndTableAndGuest();
        when(tableAssignmentRepository.findByGuestId(5L)).thenReturn(Optional.empty());
        when(tableAssignmentRepository.countByWeddingTableId(10L)).thenReturn(1L);
        when(tableAssignmentRepository.save(any(TableAssignment.class))).thenAnswer(a -> a.getArgument(0));

        weddingTableService.assign(1L, 10L, assignRequest(5L));

        verify(weddingTableRepository).findByIdForUpdate(10L);
        verify(weddingTableRepository, never()).findById(10L);
    }

    @Test
    void assign_tableFull_throwsConflict() {
        stubScopeAndTableAndGuest();
        when(tableAssignmentRepository.findByGuestId(5L)).thenReturn(Optional.empty());
        when(tableAssignmentRepository.countByWeddingTableId(10L)).thenReturn(2L);

        assertThrows(ConflictException.class,
                () -> weddingTableService.assign(1L, 10L, assignRequest(5L)));
        verify(tableAssignmentRepository, never()).save(any(TableAssignment.class));
    }

    @Test
    void assign_guestAlreadyAssigned_throwsConflict() {
        stubScopeAndTableAndGuest();
        when(tableAssignmentRepository.findByGuestId(5L)).thenReturn(Optional.of(
                TableAssignment.builder().guestId(5L).weddingTableId(10L).build()));

        assertThrows(ConflictException.class,
                () -> weddingTableService.assign(1L, 10L, assignRequest(5L)));
        verify(tableAssignmentRepository, never()).save(any(TableAssignment.class));
    }

    @Test
    void assign_guestOfAnotherWedding_notFound() {
        when(weddingService.loadInOrgScope(1L)).thenReturn(wedding);
        when(weddingTableRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(table));
        when(guestRepository.findByIdAndWeddingId(5L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> weddingTableService.assign(1L, 10L, assignRequest(5L)));
        verify(tableAssignmentRepository, never()).save(any(TableAssignment.class));
    }

    @Test
    void assign_dbDuplicate_guestMapsToConflict() {
        stubScopeAndTableAndGuest();
        when(tableAssignmentRepository.findByGuestId(5L)).thenReturn(Optional.empty());
        when(tableAssignmentRepository.countByWeddingTableId(10L)).thenReturn(0L);
        when(tableAssignmentRepository.save(any(TableAssignment.class)))
                .thenThrow(new DataIntegrityViolationException("uk_table_assignments_guest"));

        assertThrows(ConflictException.class,
                () -> weddingTableService.assign(1L, 10L, assignRequest(5L)));
    }

    @Test
    void move_locksTargetTable() {
        when(weddingService.loadInOrgScope(1L)).thenReturn(wedding);
        TableAssignment existing = TableAssignment.builder().guestId(5L).weddingTableId(10L).build();
        existing.setId(1L);
        when(tableAssignmentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(weddingTableRepository.findById(10L)).thenReturn(Optional.of(table));
        WeddingTable target = WeddingTable.builder().weddingId(1L).name("T2").capacity(2).build();
        target.setId(20L);
        when(weddingTableRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(target));
        when(guestRepository.findById(5L)).thenReturn(Optional.of(guest));
        when(tableAssignmentRepository.countByWeddingTableId(20L)).thenReturn(0L);
        when(tableAssignmentRepository.save(existing)).thenReturn(existing);

        weddingTableService.move(1L, 1L, moveRequest(20L));

        verify(weddingTableRepository).findByIdForUpdate(20L);
    }

    @Test
    void move_targetFull_throwsConflict() {
        when(weddingService.loadInOrgScope(1L)).thenReturn(wedding);
        TableAssignment existing = TableAssignment.builder().guestId(5L).weddingTableId(10L).build();
        existing.setId(1L);
        when(tableAssignmentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(weddingTableRepository.findById(10L)).thenReturn(Optional.of(table));
        WeddingTable target = WeddingTable.builder().weddingId(1L).name("Tfull").capacity(1).build();
        target.setId(20L);
        when(weddingTableRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(target));
        when(tableAssignmentRepository.countByWeddingTableId(20L)).thenReturn(1L);

        assertThrows(ConflictException.class,
                () -> weddingTableService.move(1L, 1L, moveRequest(20L)));
        verify(tableAssignmentRepository, never()).save(any(TableAssignment.class));
    }

    @Test
    void remove_deletesAssignment() {
        when(weddingService.loadInOrgScope(1L)).thenReturn(wedding);
        TableAssignment existing = TableAssignment.builder().guestId(5L).weddingTableId(10L).build();
        existing.setId(1L);
        when(tableAssignmentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(weddingTableRepository.findById(10L)).thenReturn(Optional.of(table));

        weddingTableService.remove(1L, 1L);

        verify(tableAssignmentRepository).delete(existing);
    }

    @Test
    void delete_tableWithAssignments_throwsConflict() {
        when(weddingService.loadInOrgScope(1L)).thenReturn(wedding);
        when(weddingTableRepository.findByIdAndWeddingId(10L, 1L)).thenReturn(Optional.of(table));
        when(tableAssignmentRepository.countByWeddingTableId(10L)).thenReturn(1L);

        assertThrows(ConflictException.class, () -> weddingTableService.delete(1L, 10L));
        verify(weddingTableRepository, never()).delete(any(WeddingTable.class));
    }
}
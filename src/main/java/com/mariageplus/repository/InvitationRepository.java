package com.mariageplus.repository;

import com.mariageplus.entity.Invitation;
import com.mariageplus.entity.InvitationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository des invitations. Les listes sont filtrées par mariage (jamais de
 * filtrage en mémoire). Le soft-delete (deleted_at IS NULL) est appliqué par
 * le filtre de BaseEntity.
 */
@Repository
public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    @Query("select i from Invitation i where i.weddingId = :weddingId and i.deletedAt is null")
    Page<Invitation> findActiveByWeddingId(@Param("weddingId") Long weddingId, Pageable pageable);

    List<Invitation> findByWeddingId(Long weddingId);

    Optional<Invitation> findByIdAndWeddingId(Long id, Long weddingId);

    Optional<Invitation> findByInvitationCode(String invitationCode);

    Optional<Invitation> findByPublicToken(String publicToken);

    /**
     * Résout une invitation par son publicToken avec un verrou pessimiste en écriture
     * (PESSIMISTIC_WRITE). Utilisé pour le check-in : le verrou est posé sur la ligne
     * d'invitation, de sorte que deux check-ins concurrents sur la même invitation
     * soient sérialisés (recalcul de la somme à l'intérieur de la transaction).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invitation i where i.publicToken = :publicToken and i.deletedAt is null")
    Optional<Invitation> findByPublicTokenForUpdate(@Param("publicToken") String publicToken);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invitation i where i.id = :id and i.deletedAt is null")
    Optional<Invitation> findByIdForUpdate(@Param("id") Long id);

    long countByWeddingId(Long weddingId);

    long countByWeddingIdAndStatus(Long weddingId, InvitationStatus status);

    /** Invitations envoyées (SENT) d'un mariage qui n'ont pas encore de réponse RSVP (non-répondants). */
    @Query("select i from Invitation i where i.weddingId = :weddingId and i.status = 'SENT' "
            + "and i.deletedAt is null and not exists "
            + "(select r from Rsvp r where r.invitationId = i.id)")
    List<Invitation> findNonRespondersByWeddingId(@Param("weddingId") Long weddingId);

    @Query("select count(i) from Invitation i where i.weddingId = :weddingId "
            + "and i.status = 'SENT' and i.deletedAt is null and not exists "
            + "(select r from Rsvp r where r.invitationId = i.id)")
    long countNonRespondersByWeddingId(@Param("weddingId") Long weddingId);

    @Query("select count(i) from Invitation i " +
            "where i.weddingId = :weddingId and i.guestId in (select g.id from Guest g where g.categoryId = :categoryId)")
    long countByWeddingIdAndCategory(@Param("weddingId") Long weddingId,
                                     @Param("categoryId") Long categoryId);

    boolean existsByInvitationCode(String invitationCode);

    boolean existsByPublicToken(String publicToken);

    /** Compte les invitations NON supprimées d'un invité (filtre soft-delete). */
    boolean existsByGuestId(Long guestId);

    /** Invitation d'un invité pour un mariage donné (recherche agent d'accueil). */
    Optional<Invitation> findByGuestIdAndWeddingId(Long guestId, Long weddingId);

    /** Recherche par fragment de code d'invitation (recherche agent d'accueil). */
    List<Invitation> findByWeddingIdAndInvitationCodeContainingIgnoreCase(Long weddingId, String code);
}

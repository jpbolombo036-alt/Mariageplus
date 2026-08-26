# Plan de travail — MariagePlus (backend + Flutter)

Synthèse des décisions, implémentations déjà réalisées et reste à faire.
Dernière mise à jour : août 2026.

---

## 1. Règles de scoping décidées

| Rôle | Scope |
|---|---|
| `SUPER_ADMIN` | Plateforme entière (aucune limite) |
| `ORGANISATEUR` | Son organisation (tous ses mariages) |
| `GESTIONNAIRE_INVITES` | **Un ou plusieurs mariages précis** (`wedding_id` sur `organization_members`) |
| `AGENT_ACCUEIL` | **Un ou plusieurs mariages précis** (même mécanique) |

- `wedding_id` est **ignoré** pour `SUPER_ADMIN` / `ORGANISATEUR`.
- Un agent peut gérer **plusieurs** mariages → plusieurs lignes `organization_members` (même user, même org, même rôle, `wedding_id` différent).
- `POST /api/organizations/{id}/members` requiert `wedding_id` pour les 2 rôles agent (400 sinon).
- `addMember` **réutilise un compte existant** si l'email est déjà présent (fin du 409 « email déjà utilisé » pour les agents).
- Contrainte unique : `(user_id, organization_id, role_id, wedding_id)` + **index partiel unique** `(user_id, organization_id, role_id) WHERE wedding_id IS NULL` pour éviter les doublons sur les rôles org-wide.

## 2. Implémentations déjà réalisées (backend)

### 2.1 Swagger public en production
- **Fichier** : `src/main/java/com/mariageplus/config/SecurityConfig.java`
- **Changement** : les chemins `/swagger-ui/**`, `/v3/api-docs/**`, `/webjars/**` sont `permitAll()` dans tous les profils (prod inclus).
- **Vérification** : compilation OK ; `SecurityIntegrationTest` 4/4 passent.

### 2.2 Check-in : `weddingId` obligatoire + garde anti-mariage
- **DTOs** : `ScanCheckInRequest` et `CheckInRequest` ont désormais `@NotNull Long weddingId`.
- **Service** : `CheckInService.assertSameWedding(invitation, request.getWeddingId())` → `ResourceNotFoundException("Invitation introuvable")` (404 identique au token inconnu) si `invitation.getWeddingId() != request.getWeddingId()`.
- **Tests** : `CheckInServiceTest` (+ `scan_wrongWedding_404`, `checkIn_rejects_wrongWedding_404`) et `CheckInIntegrationTest` (+ `scan_wrongWedding_404`, `checkIn_wrongWedding_404`) — 42/42 passent.

### 2.3 Flutter aligné (backend-driven)
- `lib/src/checkin/checkin_api.dart` : `scan(qrToken, weddingId)` et `checkIn(qrToken, weddingId, numberOfAttendees)` envoient `weddingId`.
- Écrans agent : `qr_scanner_screen.dart`, `scan_result_screen.dart` reçoivent et transmettent `weddingId`.
- `checkin_scan_page.dart` et `wedding_detail_page.dart` passent le `weddingId` actif.
- `agent_accueil_home_screen.dart` passe `_weddings.first.id` au scanner.
- `flutter analyze` : **No issues found!**

### 2.4 Rotation du QR (publicToken)
- **Service** : `InvitationService.rotateQrToken(weddingId, invitationId)` — permission `INVITATION_UPDATE`, verrou pessimiste (`findByIdForUpdate`), rejet 404 si deleted / CANCELLED / EXPIRED / mauvais mariage, nouveau `publicToken` (32 car. SecureRandom), audit `INVITATION_QR_ROTATE`, retourne `QrCodeResponse` (nouveau data URI PNG).
- **Contrôleur** : `POST /api/weddings/{wid}/invitations/{id}/qr/rotate` → 200 `QrCodeResponse`.
- **Tests** : `InvitationServiceTest` (5 nouveaux tests) + `InvitationControllerIntegrationTest` (`rotateQr_returnsNewQr_andOldTokenBecomes404`, `rotateQr_organizerA_cannotRotate_forWeddingB`) — 38/38 passent.
- **Docs** : `docs/FRONTEND_FLUTTER.md` (section 5.8, 5.10, 6.8) + `docs/STATUS.md` ligne invitations.

### 2.5 Permissions dans la réponse de login
- **DTO** : `UserResponse` gagne `List<String> permissions`.
- **Service** : `UserService.buildResponse` calcule l'union des permissions via `userRoleRepository.findRoleIdsByUserId` → `rolePermissionRepository.findCodesByRoleIds`.
- **Vérification** : `AuthServiceTest` + `UserServiceTest` — 10/10 passent.
- `LoginResponse.user.permissions`, `/auth/me` et `register` renvoient désormais les permissions.

## 3. Ce qui reste à faire (wedding scoping des agents)

### 3.1 Migration V14
- `ALTER TABLE organization_members ADD COLUMN wedding_id BIGINT`.
- FK vers `weddings(id)`.
- **Drop** de la contrainte existante `uk_org_members (user_id, organization_id, role_id)`.
- **Nouvelle contrainte unique** `(user_id, organization_id, role_id, wedding_id)`.
- **Index partiel unique** : `CREATE UNIQUE INDEX IF NOT EXISTS uk_org_members_org_wide ON organization_members(user_id, organization_id, role_id) WHERE deleted_at IS NULL AND wedding_id IS NULL;`
- Backfill : les agents existants ont `wedding_id = NULL` → legacy org-wide (pas de casse).

### 3.2 Entité + DTOs
- `OrganizationMember.java` : champ `weddingId` (nullable).
- `OrganizationMemberRequest.java` : `weddingId` optionnel ; **requis** si `roleCode` ∈ {`GESTIONNAIRE_INVITES`, `AGENT_ACCUEIL`}.
- `OrganizationMemberResponse.java` : exposer `weddingId`.

### 3.3 Principal + SecurityUtils
- `UserPrincipal` : ajouter `List<Long> weddingIds` (weddings assignés du membre actif).
- `UserPrincipalService.buildPrincipal` : charger les `weddingId` depuis `organizationMemberRepository.findByUser_IdAndActiveTrue`.
- `SecurityUtils.assertWeddingAccess(Long weddingId)` :
  - `SUPER_ADMIN` → OK.
  - `ORGANISATEUR` → OK (l'org est déjà vérifiée par `loadInOrgScope`).
  - Agent (`GESTIONNAIRE_INVITES` / `AGENT_ACCUEIL`) → OK **uniquement** si `weddingId` ∈ `principal.getWeddingIds()` ; sinon `SecurityException` (403).

### 3.4 Service OrganizationMember
- `addMember` :
  - Si `weddingId` fourni : valider que le mariage existe et appartient à l'org (`wedding.getOrganizationId() == orgId`).
  - Si `roleCode` ∈ {`GESTIONNAIRE_INVITES`, `AGENT_ACCUEIL`} et `weddingId` null → 400.
  - Si `email` existe : charger l'user existant (pas de création, pas de modification du mot de passe).
  - `userService.assignRole(user, roleCode)` (idempotent).
  - Créer `OrganizationMember(user, org, role, weddingId)` ; rejeter 409 si `(user, org, rôle, wedding)` existe déjà.
- Nouveaux endpoints :
  - `DELETE /api/organizations/{id}/members/{memberId}` → supprime l'affectation (hard delete ou soft delete via `BaseEntity.softDelete()`).
  - `PUT /api/organizations/{id}/members/{memberId}` avec `{ "weddingId": 43 }` → mute le mariage assigné (valide l'appartenance à l'org + contrainte unique).

### 3.5 Application du garde dans les services métier
Remplacer (ou compléter) `assertOrganizationAccess` par `assertWeddingAccess(weddingId)` dans :
- `InvitationService`
- `GuestService`
- `GuestCategoryService`
- `WeddingEventService`
- `CheckInService` (en plus du garde `assertSameWedding` existant)

**Laisser tels quels** (scope org suffisant pour l'ORGANISATEUR) :
- `WeddingService` (CRUD mariages)
- `WeddingDashboardService`
- `WeddingTableService` (ou scoper aussi selon besoin métier — à confirmer)

### 3.6 Tests
- **Scoping** : agent scopé au mariage 42 → 200 sur 42, **403** sur 43 (même org).
- **Non-régression** : `ORGANISATEUR` / `SUPER_ADMIN` conservent l'accès org-wide (200 sur tous leurs mariages, 0 403 supplémentaire).
- **addMember** :
  - `weddingId` manquant pour agent → 400.
  - Email existant → rattache (201, pas 409).
  - Doublon `(user, org, rôle, wedding)` → 409.
- **Tests d'intégration existants** à mettre à jour si création d'agent sans `weddingId` (ajouter le `weddingId` dans les setups).

### 3.7 Documentation
- `docs/FRONTEND_FLUTTER.md` : `POST /api/organizations/{id}/members` requiert `weddingId` pour les agents ; section « scoping par mariage ».
- `docs/STATUS.md` : retirer la limitation « agent non scopé au mariage » ; mentionner `DELETE/PUT members`.

## 4. Points d'attention / risques

- **`UserPrincipal.orgId` ambigu** : si un user est membre de 2 orgs, `orgId` principal = première ligne active. `assertWeddingAccess` ne doit **pas** se fier à `principal.getOrganizationId()` pour valider l'org ; il s'appuie sur `loadInOrgScope(weddingId)` qui résout l'org depuis le mariage.
- **Dashboard / Tables** : non scopés dans le plan actuel (réservés ORGANISATEUR). Si un agent doit y accéder, ajouter `assertWeddingAccess` également.
- **Migration Flyway V14** : l'index partiel `WHERE wedding_id IS NULL` est nécessaire pour prévenir les doublons de rôle org-wide (ex : deux lignes `(user, org, ORGANISATEUR, NULL)`).
- **Comptes multi-org** : `addMember` peut rattacher un user existant d'une autre org. `principal.orgId` devient celui de la première membership active — comportement pré-existant, mais le scoping mariage via `assertWeddingAccess` le rend moins critique.
- **Non-regression** : avec `NULL = legacy org-wide`, les agents existants ne cassent pas. Seuls les agents créés post-déploiement sont scopés (et `wedding_id` y est requis).

## 5. Résumé en une phrase

Backend : **Swagger public + check-in wedding-scopé + rotation QR + permissions dans login** sont terminés. Il reste **un seul chantier** : le **scoping wedding des agents** (migration V14 + `addMember` amélioré + `assertWeddingAccess` + endpoints de retrait/mutation + tests de non-régression).

# Couverture Backend ↔ Frontend (Flutter & Web)

> **État : le backend MariagePlus est fonctionnellement complet** pour le passage au web —
> toutes les routes du catalogue sont implémentées, testées (286 tests verts) et
> déployables. Ce document est la matrice de référence : **ce que le backend expose**,
> **ce que l’app Flutter consomme déjà** (`MariageApp-main`), et **ce qu’il restera à
> brancher côté web**.

Légende : 🟢 = implémenté · ⚪ = non consommé (à brancher côté client) · 🔐 = JWT requis · 🔓 = public.

---

## 1. Auth & profil

| Route | Backend | Flutter (`MariageApp`) | Web |
|---|---|---|---|
| `POST /auth/register` | 🟢 | 🟢 `auth_api` | à brancher |
| `POST /auth/login` | 🟢 | 🟢 `auth_api` | à brancher |
| `POST /auth/refresh` (body brut accepté : **ou** `{refreshToken}`) | 🟢 | 🟢 auto dans `ApiClient` | à brancher |
| `POST /auth/logout` 🔐 | 🟢 | 🟢 | à brancher |
| `GET /auth/me` 🔐 | 🟢 | 🟢 | à brancher |
| `GET /api/users/me` 🔐 | 🟢 | 🟢 | à brancher |
| `PUT /api/users/me` 🔐 | 🟢 | ⚪ | à brancher |
| `PUT /api/users/me/password` 🔐 | 🟢 | 🟢 `changePassword` | à brancher |

> Note Flutter : l'écran « Profil » (agent/gestionnaire) existe ; l'édition du profil
> (`PUT /me`) n'est pas exposée dans l'app.

---

## 2. Admin (SUPER_ADMIN)

| Route | Backend | Flutter | Web |
|---|---|---|---|
| `GET/POST /api/users` · `GET/PUT/DELETE /api/users/{id}` · `PATCH .../toggle-active` | 🟢 | 🟢 `admin_api` | à brancher |
| `GET/POST /api/roles` · `GET/PUT/DELETE /api/roles/{id}` · `PUT .../{id}/permissions` | 🟢 | 🟢 `admin_api` | à brancher |
| `GET /api/permissions` | 🟢 | 🟢 `admin_api` | à brancher |
| `GET/POST /api/organizations` · `GET/PUT/DELETE /api/organizations/{id}` · `PATCH .../toggle-active` | 🟢 | 🟢 `admin_api` | à brancher |
| `GET/POST /api/organizations/{id}/members` | 🟢 | 🟢 `admin_api` | à brancher |
| `PUT/DELETE /api/organizations/{id}/members/{memberId}` (**re-affectation wedding / retrait**) | 🟢 | 🟢 `admin_api` (`updateOrganizationMember` / `removeOrganizationMember`) | à brancher |

> NB : ce sont les endpoints **membres enrichis** du chantier scoping des agents
> (`weddingId` requis pour GESTIONNAIRE_INVITES / AGENT_ACCUEIL, compte réutilisé,
> DELETE/PUT mutes). L'app Flutter expose désormais list/add/update/remove + `weddingId`.

---

## 3. Espace organisateur / gestionnaire

### Mariages — `WEDDING_*`
| Route | Backend | Flutter | Web |
|---|---|---|---|
| `GET /api/weddings` · `POST` | 🟢 | 🟢 `wedding_api` | à brancher |
| `GET/PUT/DELETE /api/weddings/{id}` | 🟢 | 🟢 | à brancher |
| `PATCH /api/weddings/{id}/status` | 🟢 | 🟢 `updateStatus` | à brancher |

### Événements — `EVENT_*`
| Route | Backend | Flutter | Web |
|---|---|---|---|
| `GET/POST /api/weddings/{wid}/events` | 🟢 | 🟢 `wedding_event_api` | à brancher |
| `GET/PUT/DELETE .../events/{eventId}` | 🟢 | 🟢 (list, create, getById, **update**, delete) | à brancher |

### Catégories — `CATEGORY_*`
| Route | Backend | Flutter | Web |
|---|---|---|---|
| `GET/POST /api/weddings/{wid}/guest-categories` | 🟢 | 🟢 `guest_api` | à brancher |
| `GET/PUT/DELETE .../guest-categories/{id}` | 🟢 | 🟢 (list, create, update, delete) | à brancher |

### Invités — `GUEST_*`
| Route | Backend | Flutter | Web |
|---|---|---|---|
| `GET/POST /api/weddings/{wid}/guests` | 🟢 | 🟢 `guest_api` | à brancher |
| `GET/PUT/DELETE .../guests/{id}` | 🟢 | 🟢 (list, getById, create, update, delete) | à brancher |
| `POST .../guests/import` (CSV multipart) | 🟢 | 🟢 `importCsv` | à brancher |

## 4. Invitations — `INVITATION_*` (+ relances)

| Route | Backend | Flutter | Web |
|---|---|---|---|
| `GET/POST /api/weddings/{wid}/invitations` | 🟢 | 🟢 `invitation_api` | à brancher |
| `GET/PUT/DELETE .../invitations/{id}` | 🟢 | 🟢 | à brancher |
| `GET .../invitations/{id}/qr` | 🟢 | 🟢 `getQr` | à brancher |
| `POST .../invitations/{id}/qr/rotate` | 🟢 | 🟢 `rotateQr` | à brancher |
| `POST .../invitations/{id}/send` | 🟢 | 🟢 `send` | à brancher |
| `POST .../invitations/{id}/resend` | 🟢 | 🟢 `resend` | à brancher |
| `POST .../invitations/{id}/cancel` | 🟢 | 🟢 `cancel` | à brancher |
| `GET .../invitations/pending-rsvp` (**non-répondants, relance manuelle**) | 🟢 | 🟢 `listNonResponders` | à brancher |
| `GET .../invitations/pending-rsvp/count` | 🟢 | 🟢 `countNonResponders` | à brancher |

> Détail `InvitationResponse` (Flutter) : `id, weddingId, guestId, invitationCode,
> status, sentAt, lastSentAt, reminderCount, openedAt` — aligné sur le backend.
> **Badge « X non-répondants » dans la liste : à ajouter côté client.**

---

## 5. RSVP public + page web invité (lien envoyé à l'invité)

| Route | Backend | Consommé par |
|---|---|---|
| `GET /api/public/invitations/{publicToken}` | 🟢 (enrichi : photos, date/lieu, message, `maxAccepted`) | Flutter `checkin_api` + **page web serveur** |
| `POST /api/public/invitations/{publicToken}/rsvp` | 🟢 | Flutter `submitRsvp` + page web |
| `GET /invitations/{publicToken}` (**page HTML Thymeleaf** de l'invité) | 🟢 | Navigateur / WebView |

> Le backend **sert lui-même la page web publique** de l’invité (`/invitations/{token}`) :
> photo, noms, date/heure/lieu, message perso, boutons accepter/décliner + accompagnants.
> C’est le chemin idéal pour le web sans refaire un composant dédié.
> Expiration temporelle : après la date de l’événement → 404.

---

## 6. Check-in & tables

### Check-in — `CHECKIN_*`
| Route | Backend | Flutter | Web |
|---|---|---|---|
| `POST /api/checkins/scan` (avec `weddingId`) | 🟢 | 🟢 `checkin_api.scan` | à brancher |
| `POST /api/checkins` (avec `weddingId`) | 🟢 | 🟢 `checkin_api.checkIn` | à brancher |
| `DELETE /api/checkins/{checkInId}` | 🟢 | 🟢 `cancelCheckIn` | à brancher |

### Tables — `TABLE_*`
| Route | Backend | Flutter | Web |
|---|---|---|---|
| `GET/POST /api/weddings/{wid}/tables` | 🟢 | 🟢 `table_api` | à brancher |
| `GET/PUT/DELETE .../tables/{tableId}` | 🟢 | 🟢 | à brancher |
| `POST .../tables/{tableId}/assignments` | 🟢 | 🟢 `assign` | à brancher |
| `PUT/DELETE .../assignments/{assignmentId}` | 🟢 | 🟢 `move` / `remove` | à brancher |

---

## 7. Dashboard & RSVP admin

| Route | Backend | Flutter | Web |
|---|---|---|---|
| `GET /api/weddings/{wid}/dashboard` | 🟢 | 🟢 `dashboard_api` | à brancher |
| `GET /api/weddings/{wid}/rsvps` (réponses par invité, GESTIONNAIRE_INVITES) | 🟢 | 🟢 `rsvp_api.listForWedding` | à brancher |

---

## 8. Récapitulatif pour le passage au web

Le **backend est prêt** : toutes les routes existent, sont protégées par rôles/permissions et
testées. Pour le web, il reste **côté client** à reproduire l’équivalent des écrans
Flutter existants, en réutilisant le même contrat d’API (matrice ci-dessus).

Points bonus déjà inclus côté backend, à exploiter sur le web :
1. **Page web publique de l’invité** servie par Spring (`/invitations/{token}`) — zéro front à faire pour le RSVP invité.
2. **Email d’invitation enrichi** (date/lieu + fichier `.ics` + message perso) + **pièce jointe calendrier**.
3. **Relances** : liste manuelle des non-répondants + **relance auto programmée** (désactivable, à J‑X).
4. **Suivi** : `reminderCount` + `openedAt` sur chaque invitation.
5. **Scoping des agents par mariage** (`assertWeddingAccess`) appliqué aux services.
6. **Swagger public** en prod : `/swagger-ui.html` pour documenter l’API au web.


# Guide Frontend Mobile — Flutter (MariagePlus)

Interface officielle du backend Java/Spring Boot `com.mariageplus`.

> Ce guide est aligné sur le backend réel (endpoints, DTO, permissions, statuts HTTP). **Chaque écran / action est conditionné par la permission** : n’afficher que ce que l’utilisateur a le droit d’utiliser.

---

## 1. Vue d'ensemble

| Élément | Valeur |
|---|---|
| Backend | Spring Boot 3.2 / Java 17, H2 (local) / PostgreSQL (prod) |
| Authentification | JWT Bearer : `Authorization: Bearer <accessToken>` |
| Format des réponses | JSON ; erreurs → `{ "error": "<message>" }` |
| Pagination | `PageResponse` commun |

### URL de base
- **Local (H2)** : `http://localhost:8000` — lancement : `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`
  - Émulateur Android : `http://10.0.2.2:8000`
- **Production** : `https://<votre-domaine>`
- Swagger : `http://localhost:8000/swagger-ui.html` (ouvert sans JWT **uniquement** en profil `local`)

> En **profil local**, un SUPER_ADMIN de dev est créé : `admin@mariageplus.com` / `Admin@12345`. En production il n’est **pas** créé (`ADMIN_INIT_ENABLED=false`).

---

## 2. Authentification

### 2.1 Inscription : `POST /auth/register` (public) → **201**
```json
{
  "firstName": "Jean",
  "lastName": "Kabongo",
  "email": "jean@exemple.com",
  "phone": "+243810000000",
  "password": "motdepasse123",
  "organizationName": "Mon Organisation",
  "organizationEmail": "contact@exemple.com",
  "organizationPhone": "+243810000000",
  "organizationAddress": "Kinshasa"
}
```
- `password` : **min 8 caractères**.
- Réponse : `LoginResponse` (auto-connexion).

### 2.2 Connexion : `POST /auth/login` (public) → **200**
```json
{ "email": "jean@exemple.com", "password": "motdepasse123" }
```
Réponse `LoginResponse` :
```json
{
  "accessToken": "<JWT accès>",
  "refreshToken": "<JWT refresh>",
  "expiresIn": 900,
  "tokenType": "Bearer",
  "user": {
    "id": 1,
    "firstName": "Jean",
    "lastName": "Kabongo",
    "email": "jean@exemple.com",
    "phone": "+243810000000",
    "active": true,
    "emailVerified": false,
    "lastLoginAt": "2026-08-20T19:00:00",
    "roles": ["ORGANISATEUR"],
    "organizationId": 1
  }
}
```

`expiresIn` est en **secondes**. Défaut backend : **900** (15 min), pas 24 h. Le refresh dure 7 jours.

### 2.3 Rafraîchir : `POST /auth/refresh` (public)
Corps = le **refresh token en texte brut** (pas un objet JSON `{ "refreshToken": "..." }`).
Réponse : nouveau `LoginResponse` (rotation : l’ancien refresh est invalidé).

### 2.4 Déconnexion : `POST /auth/logout` (JWT requis) → **204**
Révoque les refresh tokens et incrémente `tokenVersion` (les JWT d’accès déjà émis sont refusés).

### 2.5 Profil : `GET /auth/me` (JWT requis)
Utilisateur connecté (rôles + organisation).

### 2.6 Stockage Flutter
- Stocker `accessToken`, `refreshToken` et `expiresIn` dans `flutter_secure_storage`.
- Sur **401** : tenter `/auth/refresh` ; si échec, vider le stockage et aller au login.
- Ne **jamais** stocker le mot de passe.

> Le serveur recharge rôles / permissions / organisation **à chaque requête**. Le cache local des permissions ne sert qu’à l’UI.

---

## 3. Cliente HTTP (dio)

```dart
final dio = Dio(BaseOptions(
  baseUrl: 'http://localhost:8000',
  connectTimeout: const Duration(seconds: 15),
  receiveTimeout: const Duration(seconds: 20),
  headers: {'Content-Type': 'application/json'},
));

dio.interceptors.add(InterceptorsWrapper(
  onRequest: (options, handler) async {
    final token = await secureStorage.read(key: 'accessToken');
    if (token != null && token.isNotEmpty) {
      options.headers['Authorization'] = 'Bearer $token';
    }
    handler.next(options);
  },
  onError: (e, handler) async {
    if (e.response?.statusCode == 401) {
      // tenter refresh, sinon delete accessToken + refreshToken et /login
    }
    handler.next(e);
  },
));
```

- Endpoints **protégés** : header `Authorization: Bearer <accessToken>`.
- Publics : `/auth/register`, `/auth/login`, `/auth/refresh`, `/api/public/**`, `/health`.

---

## 4. Permissions (afficher / cacher les boutons)

| Rôle | Usage typique |
|---|---|
| `SUPER_ADMIN` | Toute la plateforme |
| `ORGANISATEUR` | Ses mariages, équipe, tables, stats, catégories |
| `GESTIONNAIRE_INVITES` | Invités, **catégories**, invitations, RSVP, import |
| `AGENT_ACCUEIL` | Scan QR et check-in |

Une permission absente → **403** `{ "error": "..." }`. Cross-organisation → **403**.

---

## 5. Modèles (DTO → Dart)

Dates JSON : `2026-08-20T19:00:00` (`LocalDateTime`). Enums en **MAJUSCULES**.

### 5.1 `PageResponse<T>`
`content`, `currentPage`, `pageSize`, `totalElements`, `totalPages`

### 5.2 Login
```dart
class LoginResult {
  String accessToken;
  String refreshToken;
  int expiresIn;      // secondes, ex. 900
  String tokenType;   // "Bearer"
  User user;
}
```

### 5.3 Mariage
Statuts : `DRAFT → PUBLISHED → ACTIVE → COMPLETED → ARCHIVED` ; `CANCELLED` en sortie.
Changement de statut : **`PATCH`** `/api/weddings/{id}/status` avec `{ "status": "PUBLISHED" }` (pas PUT).

### 5.4 Invité / catégorie
Création invité : `firstName*`, `lastName*`, `phone?`, `email?`, `address?`, `categoryId?`, `allowedCompanions?`, `notes?`.

### 5.5 Invitation admin (`InvitationResponse`)
`id, weddingId, guestId, invitationCode, status, sentAt?, lastSentAt?, createdAt, updatedAt`  
Le **`publicToken` n’est pas** dans cette réponse.

Création : `{ "guestId": <id> }`.

Envoi / renvoi (`SendInvitationResponse`) :
```json
{
  "status": "SENT",
  "sentAt": "2026-08-20T19:10:00",
  "lastSentAt": "2026-08-20T19:10:00",
  "emailSent": false,
  "publicInviteUrl": "http://localhost:3000/invitations/<publicToken>"
}
```
- Invité **sans email** → 400.
- Sans SMTP : `emailSent: false` ; l’organisateur copie `publicInviteUrl`.
- SMTP en échec → **502**, statut inchangé.

### 5.6 Accès public
`GET /api/public/invitations/{publicToken}`  
`POST /api/public/invitations/{publicToken}/rsvp` `{ "status": "ACCEPTED", "numberOfAttendees": 2 }`
- `ACCEPTED` : `1 ≤ numberOfAttendees ≤ 1 + allowedCompanions`
- `DECLINED` : `numberOfAttendees = 0`

### 5.7 Import CSV
`POST /api/weddings/{wid}/guests/import` (`multipart`, champ `file`)  
Colonnes : `firstName,lastName,email,phone,address,allowedCompanions,categoryName,notes`  
Réponse : `{ "imported": 2, "skipped": 1, "errors": [{ "line": 3, "message": "..." }] }`

### 5.8 Check-in
- Scan : `POST /api/checkins/scan` `{ "qrToken": "<publicToken>", "weddingId": <wid> }`
- Entrée : `POST /api/checkins` `{ "qrToken": "...", "weddingId": <wid>, "numberOfAttendees": 1 }` → contient `checkInId`
- Annulation : `DELETE /api/checkins/{checkInId}` → **204** (places recréditées)

`weddingId` = mariage actif de l'appli (étanchéité jour J au sein d'une organisation). Un QR d'un autre mariage renvoie **404** (même message qu'inconnu, aucune fuite).

### 5.9 Tables
`id, name, description?, capacity, assignedCount, remainingCapacity`  
Affectation : `assignmentId, guestId, guestName, tableId, tableName, assignedAt`

### 5.10 QR
`{ "qrDataUri": "data:image/png;base64,...." }` — le token brut n’est pas renvoyé.
Récupération : `GET /api/weddings/{wid}/invitations/{id}/qr` (`INVITATION_VIEW`).
Rotation (fuite/erreur) : `POST /api/weddings/{wid}/invitations/{id}/qr/rotate` (`INVITATION_UPDATE`) → renvoie le **nouveau** QR et invalide l'ancien. Après rotation, renvoyer l'invitation (le lien RSVP public change aussi).

### 5.11 Dashboard
`GET /api/weddings/{wid}/dashboard` — `guests`, `invitations`, `attendance`, `tables`, `categories`.  
`responseRate` / `checkInRate` : doubles 0–100.

---

## 6. Catalogue des endpoints

Légende : 🔓 public · 🔐 JWT · permission absente → 403 · autre org → 403.

### 6.1 Auth
| Méthode | Chemin | Auth | Statuts |
|---|---|---|---|
| `POST` | `/auth/register` | 🔓 | **201**, 400, 409 |
| `POST` | `/auth/login` | 🔓 | 200, 401 |
| `POST` | `/auth/refresh` | 🔓 | 200, 404 |
| `POST` | `/auth/logout` | 🔐 | 204 |
| `GET` | `/auth/me` | 🔐 | 200, 401 |

### 6.2 Santé
`GET /health` 🔓 → 200

### 6.3 RSVP public
| Méthode | Path | Statuts |
|---|---|---|
| `GET` | `/api/public/invitations/{publicToken}` | 200, 404 (inconnu / CANCELLED / EXPIRED / supprimé) |
| `POST` | `/api/public/invitations/{publicToken}/rsvp` | 200, 400, 404 |

### 6.4 Mariages — `WEDDING_*`
| Méthode | URL | Permission | Statuts |
|---|---|---|---|
| `GET` | `/api/weddings` | `WEDDING_VIEW` | 200 |
| `POST` | `/api/weddings` | `WEDDING_CREATE` | 201, 400 |
| `GET` | `/api/weddings/{id}` | `WEDDING_VIEW` | 200, 403, 404 |
| `PUT` | `/api/weddings/{id}` | `WEDDING_UPDATE` | 200, 400, 403 |
| `PATCH` | `/api/weddings/{id}/status` | selon cible (`WEDDING_PUBLISH`, `WEDDING_ARCHIVE`…) | 200, 400, 409 |
| `DELETE` | `/api/weddings/{id}` | `WEDDING_DELETE` | 204, 403 |

### 6.5 Catégories — `CATEGORY_*`
`GET/POST /api/weddings/{wid}/guest-categories` · `GET/PUT/DELETE .../{id}`  
Aussi pour `GESTIONNAIRE_INVITES`.

### 6.6 Invités — `GUEST_*`
`GET/POST /api/weddings/{wid}/guests` · `GET/PUT/DELETE .../{guestId}`  
`POST /api/weddings/{wid}/guests/import` → `GUEST_IMPORT` (multipart `file`)

### 6.7 Événements — `EVENT_*`
`GET/POST /api/weddings/{wid}/events` · `GET/PUT/DELETE .../{eventId}`  
Types : `CIVIL_CEREMONY`, `RELIGIOUS_CEREMONY`, `RECEPTION`, `AFTER_PARTY`, `OTHER`.

### 6.8 Invitations — `INVITATION_*`
| Méthode | Path | Permission |
|---|---|---|
| `GET/POST` | `/api/weddings/{wid}/invitations` | `VIEW` / `CREATE` |
| `GET/PUT/DELETE` | `.../invitations/{id}` | `VIEW` / `UPDATE` / `DELETE` |
| `GET` | `.../invitations/{id}/qr` | `INVITATION_VIEW` |
| `POST` | `.../invitations/{id}/qr/rotate` | `INVITATION_UPDATE` |
| `POST` | `.../invitations/{id}/send` | `INVITATION_SEND` |
| `POST` | `.../invitations/{id}/resend` | `INVITATION_RESEND` |
| `POST` | `.../invitations/{id}/cancel` | `INVITATION_CANCEL` |

Send : `GENERATED` ou `DRAFT`. Resend : déjà `SENT`. Cancel : tout sauf `CANCELLED` / `EXPIRED`.

### 6.9 Check-in — `CHECKIN_*`
| Méthode | Path | Permission | Statuts |
|---|---|---|---|
| `POST` | `/api/checkins/scan` | `CHECKIN_SCAN` | 200, 403, 404 |
| `POST` | `/api/checkins` | `CHECKIN_CREATE` | 201, 400, 403, 409 |
| `DELETE` | `/api/checkins/{checkInId}` | `CHECKIN_CANCEL` | 204, 403, 404 |

### 6.10 Tables — `TABLE_*`
`GET/POST /api/weddings/{wid}/tables` · `GET/PUT/DELETE .../{tableId}`  
`POST .../tables/{tableId}/assignments` → `TABLE_ASSIGN_GUEST`  
`PUT/DELETE /api/weddings/{wid}/assignments/{assignmentId}` (déplacer / retirer)

### 6.11 Dashboard — `DASHBOARD_VIEW`
`GET /api/weddings/{wid}/dashboard`

---

## 7. Écran invité (lien public)

L’URL partagée est `{FRONTEND_URL}/invitations/{publicToken}`.  
L’app (ou une WebView) appelle uniquement `/api/public/invitations/...` **sans JWT**.

# État du projet — MariagePlus (backend)

Document de suivi : **où on s’arrête**, **limites actuelles**, **reste à faire**.  
Dernière mise à jour : août 2026 (fin du plan V1 backend).

Contrat Flutter : [FRONTEND_FLUTTER.md](FRONTEND_FLUTTER.md)

---

## 1. Ce qui est fait (backend V1)

| Domaine | État |
|---|---|
| Auth JWT + refresh + logout + lockout login | OK |
| Multi-tenant (organisation) + RBAC | OK |
| Mariages, événements, catégories, invités | OK |
| Invitations (création, QR, send / resend / cancel, **rotation du QR**) | OK |
| RSVP public par `publicToken` + rate-limit | OK |
| Check-in (scan, entrée, annulation) | OK |
| Tables + affectations | OK |
| Dashboard statistiques | OK |
| Import CSV invités | OK |
| Flyway V1–V13, Docker multi-stage, README, `./mvnw` | OK |
| Guide Flutter aligné sur l’API | OK |

Lancer en local : `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`  
Santé : `GET /health` · Swagger (profil `local`) : `/swagger-ui.html`

---

## 2. Limites actuelles (ce qu’il ne faut pas attendre)

### Produit / métier

- **Pas d’application Flutter dans ce dépôt** — seulement l’API + le guide d’intégration.
- **Pas de page web publique d’invitation** — le lien `{FRONTEND_URL}/invitations/{token}` suppose un front à construire ; le backend expose uniquement `/api/public/invitations/...`.
- **Email optionnel** — sans `SMTP_USERNAME` / `SMTP_PASSWORD`, l’envoi marque `SENT` et renvoie `publicInviteUrl` à partager à la main (`emailSent: false`). Pas de file d’attente ni de relance auto.
- **Photos couple / mariés** — champs URL uniquement ; **aucun upload** de fichiers (pas de stockage S3/disque branché sur l’API).
- **Pas d’export CSV** des invités (permission `GUEST_EXPORT` seedée, pas d’endpoint).
- **Pas de rapports PDF / export stats** (`REPORT_*` seedés, pas d’endpoint).
- **Pas d’envoi WhatsApp / SMS** natif — uniquement email SMTP ou lien à copier.
- **Import CSV** : pas de création auto de catégories inconnues ; pas de mise à jour d’invités existants (doublon email = erreur de ligne).

### Technique / ops

- **Maven n’est pas dans le PATH** sur certaines machines → utiliser **`./mvnw`**, pas `mvn`.
- **Profil `local`** : H2 en mémoire → **données perdues** à chaque redémarrage.
- **`ddl-auto: update`** encore actif en local (Flyway gère déjà le schéma) — risque de divergence H2 / Postgres.
- **Docker Compose** : secrets d’exemple (`JWT_SECRET`) — à changer avant toute mise en prod.
- **Railway** (`railway.json`) : startCommand sur un JAR déjà buildé ; le Dockerfile multi-stage est la voie Docker recommandée.
- **Refresh token** : corps de `POST /auth/refresh` = **chaîne brute** (pas `{ "refreshToken": "..." }`) — piège fréquent côté client.
- **Permissions JWT** : le token embarque des permissions, mais le serveur **recharge** le principal depuis la base à chaque requête (source de vérité = DB).

### Sécurité / conformité

- Soft-delete partout : les données restent en base (`deleted_at`).
- Pas de RGPD / export des données personnelles / anonymisation documentés.
- CORS strict : origines vides = pas de cross-origin (à configurer via `CORS_ALLOWED_ORIGINS`).

---

## 3. Permissions seedées sans feature (dette produit)

Présentes en base (Flyway V2+), **sans endpoint ou flux complet** :

| Permission | Manque |
|---|---|
| `GUEST_EXPORT` | Export CSV / Excel |
| `REPORT_VIEW` / `REPORT_EXPORT` | Rapports / export |
| `STATISTICS_VIEW` | Au-delà du dashboard actuel (si besoin séparé) |
| `SETTINGS_*` | Écran paramètres organisation / mariage |
| `GUEST_IMPORT` | **Implémenté** (ne plus considérer comme manque) |
| `INVITATION_SEND` / `RESEND` / `CANCEL` | **Implémentés** |
| `CHECKIN_CANCEL` | **Implémenté** |
| `CATEGORY_*` pour `GESTIONNAIRE_INVITES` | **Implémenté** (V13) |

---

## 4. Reste à faire (priorisé)

### P0 — pour une V1 utilisable « jour J »

1. **Front Flutter (ou Web)** organisateur : login, mariages, invités, import, envoi d’invitations, QR, tables, dashboard.
2. **Page / écran public RSVP** consommant `/api/public/invitations/{token}`.
3. **Configurer SMTP** en staging/prod si l’email doit partir vraiment.
4. **Secrets prod** : `JWT_SECRET`, DB, CORS, désactiver `ADMIN_INIT_ENABLED`.

### P1 — confort produit

5. **Export CSV** invités (`GUEST_EXPORT`).
6. **Upload photos** (marié / mariée / couple) + stockage (disque ou cloud).
7. **Envoi groupé** d’invitations (batch send) + suivi des échecs SMTP.
8. Notifications / relances RSVP (email).

### P2 — qualité / ops

9. Harmoniser local : `ddl-auto: validate` (comme prod) + éventuellement H2 fichier.
10. CI (tests Maven sur chaque PR).
11. Monitoring / logs structurés / alertes santé.
12. Documentation OpenAPI générée versionnée (en plus du guide Flutter).

### P3 — hors scope actuel

13. Paiements, multi-langues UI, app store, WhatsApp Business API.
14. Soft-delete → purge / anonymisation RGPD.
15. Rapports PDF imprimables (plan de table, listes d’accueil).

---

## 5. Où on s’arrête concrètement

Le **backend API** couvre le parcours cœur :

`register/login → mariage → invités (+ import) → invitation → send → RSVP public → check-in (+ cancel) → tables → dashboard`

Ce qui **n’est pas** dans ce dépôt et bloque une démo utilisateur finale :

- l’**UI mobile / web** ;
- une **vraie URL publique** d’invitation côté front ;
- l’**email** sans configuration SMTP.

Prochaine action recommandée : démarrer le **client Flutter** en suivant [FRONTEND_FLUTTER.md](FRONTEND_FLUTTER.md), ou brancher SMTP + une mini page RSVP web.

---

## 6. Fichiers de référence

| Fichier | Rôle |
|---|---|
| [README.md](../README.md) | Lancement local / Docker |
| [FRONTEND_FLUTTER.md](FRONTEND_FLUTTER.md) | Contrat API pour le mobile |
| [.env.example](../.env.example) | Variables d’environnement |
| `src/main/resources/db/migration/` | Schéma + seeds (jusqu’à V13) |

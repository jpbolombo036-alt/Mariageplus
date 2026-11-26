# Etat du projet - MariagePlus backend

Derniere mise a jour : novembre 2026.

Le backend Spring Boot est utilise avec le frontend web du depot separe `MariageWeb-main`.

## 1. Fonctionnalites validees

- Authentification JWT, refresh, logout et verrouillage de connexion.
- Multi-tenant par organisation et RBAC.
- Evenements, categories et invites.
- Invitations, QR codes, envoi, renvoi, annulation et rotation des QR.
- RSVP public avec rate limiting.
- Check-in et annulation de check-in.
- Tables et affectations.
- Dashboard et statistiques.
- Import CSV/XLSX des invites.
- Uploads d'images et stockage S3-compatible avec fallback base de donnees.
- Envoi groupe et notifications WhatsApp preparees.
- Migrations Flyway jusqu'a V54.
- Check-in hors ligne idempotent, galeries, boissons, rappels automatiques d'evenements, notifications in-app.
- Mot de passe oublie traite par le SUPER_ADMIN (sans SMTP).
- Module Salles (V54) : salles, clients, reservations sans double reservation, paiements USD/CDF, devis et recus PDF, tableau de bord, export Excel, evenement lie et acces client. Voir `docs/MODULE_SALLES.md`.
- Docker multi-stage et deploiement Railway.

Le parcours principal frontend/backend a ete teste :

`connexion -> evenement -> invites -> invitation -> RSVP -> check-in -> tables -> statistiques`

## 2. Securite recemment renforcee

- Les refresh tokens sont hashes en base depuis `V33__hash_refresh_tokens.sql`.
- Les anciens tokens restent compatibles temporairement et sont nettoyes lors de leur utilisation.
- Le frontend web utilise un cookie `HttpOnly` pour le refresh token.
- Le frontend web ne stocke plus les refresh tokens dans `localStorage`.
- Le rate limiter RSVP nettoie ses compteurs expires et limite sa croissance memoire.
- Le rate limiting distribue avec Redis reste a prevoir pour plusieurs instances.
- Les uploads d'images sont limites en taille et controles par signature binaire.

## 3. Etat du deploiement

- Backend : depot GitHub `Mariageplus`, branche `main`.
- Frontend : depot local/deploiement separe `MariageWeb-main`.
- CI GitHub Actions : `./mvnw -B test` sur chaque push (398 tests, verts au 26/11/2026).
- Flyway n'execute plus `repair()` a chaque demarrage ; `FLYWAY_REPAIR_ON_START=true` le reactive ponctuellement.

## 4. Limites actuelles

- L'interface web n'est pas dans ce depot backend ; elle est dans `MariageWeb-main`.
- L'application Flutter n'est pas dans ce depot.
- SMTP doit etre configure pour l'envoi email reel.
- Les exports CSV complets et rapports PDF restent a completer.
- Le suivi des ouvertures, rebonds et erreurs email reste limite.
- La purge et l'anonymisation RGPD restent a implementer.
- Le rate limiter est local a une instance sans Redis.

## 5. Priorites restantes

1. Verifier que `FLYWAY_REPAIR_ON_START` n'est pas active sur Railway.
2. Verifier `CORS_ALLOWED_ORIGINS`, `JWT_SECRET` et desactiver `ADMIN_INIT_ENABLED` en production.
3. Configurer SMTP si requis.
4. Etendre la CI au typecheck et au build frontend (les tests Maven y tournent deja).
5. Ajouter monitoring, alertes et sauvegardes PostgreSQL.
6. Ajouter Redis pour le rate limiting multi-instance et un verrou partage (ex. ShedLock) pour les taches `@Scheduled`.
7. Completer les exports, rapports et fonctions RGPD.

## 6. Lancement local

Backend :

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Frontend :

```bash
npm run dev
```

Pour utiliser le backend local depuis le frontend, definir `VITE_API_BASE_URL=http://localhost:8000` dans `.env.local` du frontend.

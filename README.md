# MariagePlus — backend

API Java/Spring Boot pour gérer des mariages : organisations, invités, invitations, RSVP, check-in QR et plan de tables.

- Java 17, Spring Boot 3.2, PostgreSQL (prod) / H2 (local)
- Port : **8000**
- Contrat Flutter : [docs/FRONTEND_FLUTTER.md](docs/FRONTEND_FLUTTER.md)
- Limites & reste à faire : [docs/STATUS.md](docs/STATUS.md)

## Lancer en local (H2, sans PostgreSQL)

Prérequis : **JDK 17**. Maven n’a pas besoin d’être installé : utilise le wrapper `./mvnw`.

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

- API : http://localhost:8000
- Santé : http://localhost:8000/health
- Swagger : http://localhost:8000/swagger-ui.html
- Compte admin de dev : `admin@mariageplus.com` / `Admin@12345`

## Lancer avec Docker (PostgreSQL)

Aucun JAR local n'est nécessaire : l'image compile le projet.

```bash
docker compose up --build
```

Puis http://localhost:8000/health

En production, **ne pas** laisser le `JWT_SECRET` d'exemple. Copier `.env.example` vers `.env` et renseigner les valeurs.

## Variables utiles

Voir [`.env.example`](.env.example). Les plus importantes :

| Variable | Rôle |
|---|---|
| `JWT_SECRET` | Clé HMAC ≥ 32 caractères (obligatoire hors profil `local`) |
| `SPRING_DATASOURCE_*` | PostgreSQL |
| `SMTP_USERNAME` / `SMTP_PASSWORD` | Envoi réel des invitations ; sinon le lien est à partager à la main |
| `FRONTEND_URL` | Base du lien d'invitation (ex. `http://localhost:3000`) |
| `ADMIN_INIT_ENABLED` | Créer le premier SUPER_ADMIN au démarrage (désactivé en prod) |

## Rôles

| Rôle | Usage |
|---|---|
| `SUPER_ADMIN` | Toute la plateforme |
| `ORGANISATEUR` | Ses mariages, équipe, tables, stats |
| `GESTIONNAIRE_INVITES` | Invités, catégories, invitations, RSVP |
| `AGENT_ACCUEIL` | Scan QR et check-in le jour J |

L'inscription (`POST /auth/register`) crée un organisateur et son organisation.

## Limites & suite

Le backend V1 (API) est en place. Ce dépôt **ne contient pas** l’app Flutter ni la page publique RSVP.

Détail (ce qui marche, ce qui est limité, priorités P0–P3) : **[docs/STATUS.md](docs/STATUS.md)**.

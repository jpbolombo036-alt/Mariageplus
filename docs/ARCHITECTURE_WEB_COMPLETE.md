# Architecture Web — Documentation Complète

> **Projets couverts :**
> - `MariagePlus` (backend Java/Spring Boot + web frontend) — `C:\Users\Ir John Peter\Downloads\Mariageplus-main`
> - `ScolaNote / GestBulletin` (frontend Vue 3 + backend Spring Boot distant) — `D:\Projet\scolanote`
>
> **Maquette web de référence** : `D:\Projet\maquette mariaplusweb`

---

# Partie 1 — MariagePlus

## 1. Vue d'ensemble

MariagePlus est une **plateforme de gestion des mariages et invitations** multi-tenant.  
Le backend expose une API REST sécurisée (JWT) et sert une page publique RSVP en Thymeleaf.  
Le frontend web officiel est en cours de développement et respecte la maquette de référence située dans `D:\Projet\maquette mariaplusweb`.

### Stack technique

| Couche | Technologie |
|---|---|
| Backend framework | Spring Boot 3.2 / Java 17 |
| ORM | Spring Data JPA (Hibernate) |
| Base de données | PostgreSQL (prod) / H2 (local, profil `local`) |
| Migrations | Flyway (V1 → V15+) |
| Sécurité | Spring Security + JWT (jjwt 0.12.3) |
| Templating public | Thymeleaf (page RSVP invité) |
| Documentation API | SpringDoc OpenAPI 2.3.0 (`/swagger-ui.html`) |
| Email | Spring Mail (SMTP optionnel) |
| QR Code | ZXing 3.5.2 (génération + lecture) |
| Build | Maven 3.x (`./mvnw`) |
| Tests | JUnit 5 + Spring Boot Test (286 tests verts) |
| Frontend web | Vue 3 + TypeScript + Vite + Tailwind CSS + Pinia + Vue Router 4 |
| Icons | Material Symbols Outlined |
| Police | Inter |
| Déploiement | Docker multi-stage + Railway |
| Auth client web | Axios + localStorage |

### Structure du backend

```
src/main/java/com/mariageplus/
├── MariagePlusApplication.java
├── config/          ← Configuration sécurité, CORS, beans
├── controller/      ← REST controllers (auth, wedding, guest, invitation, checkin, table, dashboard…)
├── dto/             ← Request / Response DTOs organisés par domaine
├── entity/          ← Entités JPA (User, Organization, Wedding, Guest, Invitation, CheckIn, Table…)
├── exception/       ← Gestion d'erreurs centralisée
├── mapper/          ← MapStruct mappers (entity ↔ DTO)
├── repository/      ← Spring Data JPA repositories
├── security/        ← JWT filter, UserPrincipal, SecurityUtils
├── service/         ← Logique métier, scoping agents par mariage
└── util/            ← Utilitaires divers

src/main/resources/
├── db/migration/    ← Scripts Flyway V1 → V15
├── application.yml  ← Configuration Spring Boot
├── application-local.yml
└── templates/       ← Thymeleaf : page publique RSVP invité
```

---

## 2. Maquette web de référence

### 2.1 Principe

Toute implémentation du frontend web doit **respecter la maquette** située dans `D:\Projet\maquette mariaplusweb`.  
La maquette définit :
- La structure de navigation
- Les écrans et leur contenu
- Le design system (couleurs, typographie, icônes)
- Le comportement responsive
- Les flux utilisateur

### 2.2 Dossier de maquette

```
D:\Projet\maquette mariaplusweb/
├── Dashboard oraganisateur/
│   └── Nouveau document texte.txt          ← Détail d'un événement (vue générale, bento grid, prestataires)
├── gestions des Invites/
│   ├── Invites.txt                         ← Liste des invités avec filtres et tableau
│   ├── suivis de rsvp.txt                  ← Suivi des réponses RSVP (donut, stats, filtres)
│   ├── check-in desk.txt                   ← Scan QR + recherche manuelle + stats check-in
│   └── check-in valider.txt                ← Validation check-in (profil invité, stepper)
└── Mes evenements/
    ├── Mes evenements.txt                  ← Liste des événements (bento grid)
    └── details de l'evenement ou Mariage.txt ← Détail événement (même structure que dashboard)
```

### 2.3 Design System (issu de la maquette)

#### Couleurs (Tailwind custom)

La maquette utilise un **système de couleurs Material Design 3** avec comme couleur primaire le violet `#4300b3`.

| Rôle | Couleur | Usage |
|---|---|---|
| `primary` | `#4300b3` | Actions principales, liens actifs, accents |
| `primary-container` | `#5b2ecc` | Conteneurs d'action primaire |
| `on-primary` | `#ffffff` | Texte sur fond primaire |
| `on-primary-container` | `#cdbdff` | Texte sur conteneur primaire |
| `secondary` | `#006c49` | Succès, confirmations |
| `secondary-container` | `#6cf8bb` | Badges succès |
| `tertiary` | `#563400` | Avertissements |
| `tertiary-container` | `#754900` | Badges avertissement |
| `error` | `#ba1a1a` | Erreurs, refus |
| `error-container` | `#ffdad6` | Badges erreur |
| `surface` | `#f8f9ff` | Fond de surface |
| `surface-container` | `#e5eeff` | Cartes, conteneurs |
| `surface-container-low` | `#eff4ff` | Fond alterné, hover |
| `surface-container-lowest` | `#ffffff` | Fond cartes principales |
| `surface-container-high` | `#dce9ff` | Surfaces élevées |
| `surface-container-highest` | `#d3e4fe` | Plus haut niveau de surface |
| `surface-variant` | `#d3e4fe` | Bordures, séparateurs |
| `surface-bright` | `#f8f9ff` | Zones claires |
| `surface-dim` | `#cbdbf5` | Zones atténuées |
| `surface-tint` | `#673dd8` | Teinte primaire |
| `background` | `#f8f9ff` | Fond global |
| `on-background` | `#0b1c30` | Texte sur fond |
| `on-surface` | `#0b1c30` | Texte principal |
| `on-surface-variant` | `#494454` | Texte secondaire |
| `outline` | `#7a7486` | Bordures, placeholders |
| `outline-variant` | `#cac3d7` | Bordures secondaires |
| `inverse-surface` | `#213145` | Surface inverse (overlays) |
| `inverse-on-surface` | `#eaf1ff` | Texte sur surface inverse |
| `inverse-primary` | `#cdbdff` | Primaire inverse |

#### Typographie

| Token | Valeur | Usage |
|---|---|---|
| `font-body-lg` | Inter 16px / 24px / 400 | Corps de texte principal |
| `font-body-sm` | Inter 14px / 20px / 400 | Texte secondaire |
| `font-display-lg` | Inter 32px / 40px / 700 | Titres page |
| `font-display-md` | Inter 28px / 36px / 700 | Titres section |
| `font-display-sm` | Inter 24px / 32px / 700 | Sous-titres |
| `font-display-md-mobile` | Inter 24px / 32px / 700 | Titres mobile |
| `font-title-md` | Inter 20px / 28px / 600 | Titres carte |
| `font-label-md` | Inter 12px / 16px / 600 | Labels, badges, boutons |

#### Icônes

- **Material Symbols Outlined** (Google Fonts)
- Poids : `FILL` 0 (outlined) ou `FILL 1` (filled)
- Taille standard : 24px (opsz 24)
- Classes CSS : `.material-symbols-outlined` et `.fill-icon`

#### Layout

| Token | Valeur |
|---|---|
| `sidebar-width` | 260px |
| `gutter` | 24px |
| `container-max` | 1440px |
| `spacing-sm` | 8px |
| `spacing-md` | 16px |
| `spacing-lg` | 24px |
| `spacing-xl` | 32px |
| `spacing-xs` | 4px |

---

## 3. Navigation & structure des écrans

### 3.1 Sidebar navigation (desktop)

La navigation principale est définie dans la maquette et doit être respectée :

| Icône | Écran | Rôle |
|---|---|---|
| `dashboard` | Dashboard | Vue d'ensemble |
| `event` | My Events | Mes événements |
| `group` | Guests | Gestion des invités |
| `category` | Categories | Catégories d'invités |
| `mail` | Invitations | Gestion des invitations |
| `how_to_reg` | RSVP | Suivi des réponses RSVP |
| `person_check` | Check-in | Pointage entrées |
| `grid_view` | Tables & Placements | Plan de tables |
| `business_center` | Internal Events | Événements internes |
| `analytics` | Statistics | Statistiques |

**Footer sidebar** : profil utilisateur (`account_circle`) + paramètres (`settings` / rôle `ORGANIZER`)

### 3.2 Top App Bar (header)

- **Logo** : MariagePlus + "Event Management" / "Guest Manager"
- **Recherche** : input arrondi avec icône `search`, width 64, placeholder contextuel
- **Notifications** : icône `notifications`
- **Avatar** : cercle 32px avec photo utilisateur

### 3.3 Bottom Navigation (mobile uniquement)

| Icône | Écran |
|---|---|
| `dashboard` | Dash |
| `group` | Invités |
| `how_to_reg` | RSVP |
| `menu` | Menu |

---

## 4. Écrans détaillés (maquette)

### 4.1 Mes événements (`Mes evenements.txt`)

**Route** : `/events` (à confirmer)

**Structure** :
- Header : titre "Mes événements" + bouton "Nouvel événement"
- Filtres : All / Upcoming / Past + select type (Wedding, Birthday, Graduation…)
- Grille bento responsive :

| Carte | Tailles | Contenu |
|---|---|---|
| Event featured | `md:col-span-8` | Image couverture, badge type, titre, statut, date, lieu, nb invités, lien "Gérer l'événement" |
| Stats mini | `md:col-span-4` | Total Guests Expected + contexte |
| Event standard | `md:col-span-6` | Badge type, statut, titre, date, lieu, nb invités |

**Badges de statut** :
- `ACTIF` : vert (`#006c49`)
- `DRAFT` : gris (`outline-variant`)
- `PUBLISHED` : violet (`primary`)
- `WEDDING` : orange coral (`#FF7F50` / `#FF4500`)
- `BIRTHDAY` : bleu (`#4169E1`)
- `GRADUATION` : violet (`#8A2BE2`)

### 4.2 Détail de l'événement (`Dashboard oraganisateur` / `details de l'evenement`)

**Route** : `/events/{id}` (à confirmer)

**Header canvas** :
- Image de fond plein largeur, hauteur 320px
- Dégradé overlay pour lisibilité
- Badges : type événement + statut (`Planifié`, `Actif`, etc.)
- Titre : `Sophie & Thomas - Cérémonie Laïque`
- Métadonnées : date, horaire, lieu
- Actions : `Modifier`, `Publier`, `More` (3 points)

**Tabs in-page** (scrollables horizontalement) :
1. Vue générale (active par défaut)
2. Invités
3. Invitations
4. RSVP
5. Check-in
6. Tables
7. Activités
8. Statistiques

**Vue générale — Bento Grid** (`grid-cols-12`, gap 24px) :

| Carte | Colonnes | Contenu |
|---|---|---|
| Participation | `md:col-span-4` | KPI invités (148/200), barre progression 74% |
| Budget Consommé | `md:col-span-4` | Montant dépensé / prévisionnel |
| Tâches Logistiques | `md:col-span-4` | 8 urgentes, 24/56 terminées |
| Progression Planning | `lg:col-span-8` | Placeholder Gantt + bouton "Ajouter des étapes" |
| Prestataires Clés | `lg:col-span-4` | Liste scrollable : icône, nom, statut, check/pending |

### 4.3 Gestion des invités (`Invites.txt`)

**Route** : `/events/{id}/guests`

**Header** : titre "Invités" + sous-titre + boutons `Importer CSV` + `Ajouter un invité`

**Filtres** (grid `md:grid-cols-4`) :
1. Recherche par nom
2. Select catégorie (Toutes, Famille, Amis, Collègues…)
3. Select RSVP (Tous, Accepté, En attente, Refusé)
4. Select Check-in (Statut Check-in, Check-in effectué, Non effectué)

**Tableau** (min-width 900px) :

| Colonne | Contenu |
|---|---|
| Invité | Avatar 32px + nom |
| Catégorie | Tag texte |
| Contact | Email |
| Accompagnateurs | Badge `+1 (Marc Laurent)` |
| RSVP | Badge `ACCEPTÉ` (vert), `EN ATTENTE` (orange), `REFUSÉ` (rouge) |
| Check-in | Badge `CHECK-IN EFFECTUÉ` ou bouton `Valider` |
| Actions | `more_vert` |

**Pagination** : format "Affichage 1 à 4 sur X invités" + numéros + chevrons

**Mobile** : bottom nav avec onglets Dash / Invités / RSVP / Menu

### 4.4 Suivi RSVP (`suivis de rsvp.txt`)

**Route** : `/events/{id}/rsvp`

**Vue d'ensemble** (grid `lg:grid-cols-3`) :

| Zone | Colonnes | Contenu |
|---|---|---|
| Stats principales | `lg:col-span-2` | Donut chart segmenté : Accepté (66%), En attente (25%), Refusé (8%) + légende avec compteurs |
| Action rapide | `lg:col-span-1` | Date limite d'inscription + bouton "Relancer les 'En attente'" |

**Filtres RSVP** : Tous (180) / Accepté (120) / En attente (45) / Refusé (15)

**Tableau RSVP** :

| Colonne | Contenu |
|---|---|
| Nom de l'invité | Avatar + nom |
| Groupe | Famille Mariée, Amis Marié, Collègues… |
| Nb. de personnes | entier |
| Date de réponse | date ou `-` |
| Statut | Badge Accepté / En attente / Refusé |
| Actions | `more_vert` |

### 4.5 Check-in Desk (`check-in desk.txt`)

**Route** : `/check-in` (écran dédié, mobile-first)

**Fonctionnalités** :
- Scan QR : frame avec coins décoratifs + icône `qr_code_scanner` + animation ligne de scan
- Recherche manuelle : input "Manual Guest Search…"
- Stats rapides : Checked In (98) / Total Guests (250)
- Activité récente : liste des derniers check-ins (nom, table, groupe, statut, temps)

**Bottom nav mobile** : Scan (actif) / Search / History

### 4.6 Validation Check-in (`check-in valider.txt`)

**Route** : écran transactionnel après scan/recherche

**Étapes** :
1. En-tête : bouton back + titre "Check-In Desk"
2. Succès : "Accès Autorisé" + "Billet validé avec succès"
3. Carte profil invité : avatar, nom, badge `RSVP ACCEPTÉ`
4. Détails : nb personnes attendues, table assignée
5. Stepper : confirmation du nombre exact de personnes (boutons `+` / `-`, input numérique)
6. Action flottante : bouton "Enregistrer l'entrée"

### 4.7 Dashboard organisateur

**Route** : `/dashboard`

**Contenu** :
- Canvas événement avec image de fond
- KPIs : Participation, Budget, Tâches
- Progression planning (placeholder Gantt)
- Prestataires clés (liste avec statuts)

---

## 5. Architecture frontend web — Patterns & conventions

### 5.1 Stack frontend

| Élément | Technologie |
|---|---|
| Framework | Vue 3 (Composition API, `<script setup>`) |
| Langage | TypeScript |
| Bundler | Vite 5 |
| UI | Tailwind CSS 3.4 (config custom MD3) |
| State management | Pinia 2 |
| Router | Vue Router 4 |
| HTTP client | Axios |
| Icons | Material Symbols Outlined (Google Fonts) |
| Police | Inter (Google Fonts) |
| Déploiement | Vercel / Railway |

### 5.2 Structure du projet frontend

```
src/
├── main.ts                 # Point d'entrée
├── App.vue                 # Layout racine
├── assets/styles/
│   └── main.css            # Styles globaux + fonts
├── config/
│   ├── env.ts              # Variables d'environnement (VITE_API_BASE_URL)
│   └── navigation.ts       # Configuration sidebar + permissions
├── types/                  # Interfaces TypeScript par domaine
├── api/                    # Modules Axios (29+ modules)
├── stores/                 # Stores Pinia
├── composables/            # useTheme, usePermissions, useNumeroOrdre
├── layouts/
│   └── MainLayout.vue      # Layout principal (sidebar + header + contenu)
├── pages/
│   ├── Landing.vue         # Page d'accueil publique
│   ├── Login.vue           # Connexion
│   ├── Dashboard.vue       # Dashboard organisateur
│   ├── events/             # Mes événements + détail
│   ├── guests/             # Gestion des invités
│   ├── rsvp/               # Suivi RSVP
│   ├── checkin/            # Check-in desk + validation
│   ├── invitations/        # Gestion des invitations
│   ├── tables/             # Tables & placements
│   ├── categories/         # Catégories d'invités
│   ├── statistics/         # Statistiques
│   ├── auth/               # Mot de passe oublié / réinitialisation
│   ├── profile/            # Profil utilisateur
│   └── admin/              # Rapports, exports, imports, sauvegardes
├── components/
│   ├── common/             # DataTableCard, ConfirmDialog, RoleGuard, Toast, LoadingSpinner, Pagination
│   ├── dashboard/          # Stats cards, bento cards, prestataires
│   ├── layout/             # Sidebar, Header, Breadcrumbs, BottomNav
│   ├── guests/             # Composants spécifiques invités
│   ├── rsvp/               # Donut chart, filtres RSVP
│   ├── checkin/            # QR scanner, stepper, activity feed
│   └── events/             # Event cards, bento grid
├── router/
│   └── index.ts            # Configuration Vue Router + guards
└── utils/                  # Utilitaires divers
```

### 5.3 Layout responsive

| Composant | Desktop | Mobile |
|---|---|---|
| Sidebar | Fixe gauche, 260px | Cachée |
| Header | Fixe haut, recherche + notifications | Header simplifié |
| Navigation | Sidebar | `BottomNav` (onglets bas) |
| Menu mobile | — | `MobileMenuDrawer` (overlay) |
| Contenu | `ml-[260px]`, max-width 1440px | Pleine largeur, padding réduit |

### 5.4 Thème

- Mode clair / sombre via classe `dark` sur `<html>`
- Variables Tailwind custom basées sur Material Design 3
- `useTheme()` : toggle + persistence `localStorage`

### 5.5 Composants réutilisables

#### `DataTableCard.vue`
- Header titre + actions
- Barre recherche
- Loading / Empty state
- Tableau responsive avec colonnes configurables
- Footer paginé (slot `#footer`)

#### `ConfirmDialog.vue`
- Dialog confirmation suppression

#### `RoleGuard.vue`
- Écran « Accès refusé » si `allowed=false`

#### `BottomNav.vue`
- Navigation mobile 4 onglets : Dash / Invités / RSVP / Menu

---

## 6. Flux métier principal (web)

```
Connexion
  ↓
Dashboard
  ↓
Mes événements
  ├── Créer événement
  ├── Voir détail
  │   ├── Vue générale (KPIs, planning, prestataires)
  │   ├── Invités (CRUD + import CSV + filtres)
  │   ├── Invitations (créer, envoyer, QR, suivi)
  │   ├── RSVP (suivi réponses, relances)
  │   ├── Check-in (scan QR + validation)
  │   ├── Tables (plan de table, assignations)
  │   ├── Activités
  │   └── Statistiques
  └── …
```

---

## 7. Authentification & autorisation

### 7.1 JWT

- **Access token** : durée 900 s (15 min), header `Authorization: Bearer <token>`
- **Refresh token** : durée 7 jours, rotation à chaque utilisation
- **Logout** : révoque refresh tokens + incrémente `tokenVersion`

### 7.2 Rôles & permissions (RBAC)

| Rôle | Portée |
|---|---|
| `SUPER_ADMIN` | Toute la plateforme |
| `ORGANISATEUR` | Organisation complète |
| `GESTIONNAIRE_INVITES` | Scopé à un ou plusieurs mariages |
| `AGENT_ACCUEIL` | Scopé à un ou plusieurs mariages |

### 7.3 Scoping par mariage (agents)

- `GESTIONNAIRE_INVITES` et `AGENT_ACCUEIL` limités à leur(s) mariage(s) assigné(s)
- `PUT /api/organizations/{id}/members/{memberId}` → réaffectation
- `DELETE /api/organizations/{id}/members/{memberId}` → retrait

---

## 8. Modèle de données principal

| Entité | Rôle |
|---|---|
| `User` | Compte utilisateur |
| `Organization` | Tenant |
| `OrganizationMember` | Appartenance + rôle + `weddingId` (scoping) |
| `Wedding` | Événement/mariage (DRAFT → PUBLISHED → ACTIVE → COMPLETED → ARCHIVED) |
| `WeddingEvent` | Événement(s) associés |
| `GuestCategory` | Catégorie d'invités |
| `Guest` | Invité (nom, email, téléphone, `allowedCompanions`) |
| `Invitation` | Lien Guest + Wedding, `publicToken`, statut, `reminderCount`, `openedAt` |
| `CheckIn` | Pointage d'entrée |
| `Table` | Table du plan de salle |
| `TableAssignment` | Affectation invité ↔ table |

---

## 9. Endpoints principaux

### Auth (public)

| Méthode | Path | Statuts |
|---|---|---|
| `POST` | `/auth/register` | 201, 400, 409 |
| `POST` | `/auth/login` | 200, 401 |
| `POST` | `/auth/refresh` | 200, 404 |
| `POST` | `/auth/logout` | 204 |
| `GET` | `/auth/me` | 200, 401 |

### RSVP public (public)

| Méthode | Path | Statuts |
|---|---|---|
| `GET` | `/api/public/invitations/{publicToken}` | 200, 404 |
| `POST` | `/api/public/invitations/{publicToken}/rsvp` | 200, 400, 404 |
| `GET` | `/invitations/{publicToken}` | Page HTML Thymeleaf |

### Mariages, invités, invitations, check-in, tables, dashboard

Voir le détail complet dans `docs/COUVERTURE_BACKEND_FRONTEND.md` et `docs/FRONTEND_FLUTTER.md`.

---

## 10. Page publique RSVP (Thymeleaf)

Le backend sert la page web de l'invité à `/invitations/{publicToken}` :
- Photo du couple, noms, date/heure/lieu
- Message personnalisé
- Formulaire RSVP + nombre d'accompagnants
- Expiration après date événement → 404

---

## 11. Email & notifications

- SMTP optionnel
- Sans SMTP : `emailSent: false` + `publicInviteUrl`
- Relance auto : job quotidien (désactivable)
- Relance manuelle : `GET /pending-rsvp` + `POST /resend`

---

## 12. Déploiement & configuration

### Docker

```dockerfile
# Multi-stage : build Maven → image runtime Java 17
FROM maven:3.9-eclipse-temurin-17 AS build
FROM eclipse-temurin:17-jre-alpine
```

### Variables d'environnement clés

| Variable | Rôle |
|---|---|
| `JWT_SECRET` | Secret JWT (OBLIGATOIRE en prod) |
| `DATABASE_URL` | URL JDBC PostgreSQL |
| `SMTP_USERNAME` / `SMTP_PASSWORD` | SMTP optionnel |
| `CORS_ALLOWED_ORIGINS` | Origines CORS autorisées |
| `ADMIN_INIT_ENABLED` | Créer SUPER_ADMIN dev (false en prod) |
| `INVITATION_REMINDER_ENABLED` | Relance auto (false par défaut) |

### Lancement local

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

- Santé : `GET /health`
- Swagger : `GET /swagger-ui.html` (profil `local` uniquement)

---

## 13. Qualité & tests

- **286 tests** (unitaires + intégration) → tous verts
- Couverture controllers via `@SpringBootTest` + `MockMvc`
- Tests d'intégration JWT, scoping agents, RSVP public, check-in, import CSV

---

# Partie 2 — ScolaNote / GestBulletin

## 1. Vue d'ensemble

ScolaNote (nom commercial : **GestBulletin**) est une application de **gestion des bulletins scolaires** pour les établissements de la République Démocratique du Congo (RDC).

Le frontend Vue 3 est dans `D:\Projet\scolanote`.  
Il consomme une API Spring Boot distante (production : `https://scolanote-production.up.railway.app`).

### Stack technique frontend

| Élément | Technologie |
|---|---|
| Framework | Vue 3 (Composition API, `<script setup>`) |
| Langage | TypeScript |
| Bundler | Vite 5 |
| UI | Tailwind CSS 3.4 |
| State management | Pinia 2 |
| Router | Vue Router 4 |
| HTTP client | Axios |
| Icons | Lucide Vue Next |
| Déploiement | Vercel (`vercel.json`) |
| Backend | Spring Boot (API distante Railway) |

---

## 2. Structure du projet frontend

```
src/
├── main.ts                 # Point d'entrée (app.createApp → Pinia → Router → mount)
├── App.vue                 # Layout racine (router-view + session-expired listener)
├── assets/styles/          # Styles globaux (main.css)
├── config/
│   ├── env.ts              # Variables d'environnement typées (VITE_API_BASE_URL)
│   └── navigation.ts       # Configuration sidebar + permissions par rôle
├── types/                  # Interfaces TypeScript par domaine
├── api/                    # Modules Axios (29 fichiers : auth, students, teachers, grades, report-cards…)
├── stores/                 # Stores Pinia (auth, school, academic-year)
├── composables/            # useTheme, usePermissions, useNumeroOrdre
├── layouts/
│   └── MainLayout.vue      # Layout principal (sidebar + header + contenu)
├── pages/                  # Pages de l'application (29 dossiers)
│   ├── Landing.vue         # Page d'accueil publique
│   ├── Login.vue           # Connexion
│   ├── Dashboard.vue       # Tableau de bord
│   ├── auth/               # Mot de passe oublié / réinitialisation
│   ├── students/           # CRUD élèves + détail
│   ├── teachers/           # CRUD enseignants + détail
│   ├── classrooms/         # CRUD salles / classes
│   ├── enrollments/        # Inscriptions
│   ├── grades/             # Saisie des notes
│   ├── assessments/        # Évaluations
│   ├── report-cards/       # Bulletins (génération, liste, détail, PDF)
│   │   └── annual/         # Bulletins annuels
│   ├── teaching-assignments/ # Affectations
│   ├── attendances/        # Présences
│   ├── disciplines/        # Disciplines
│   ├── curriculum/         # Programmes scolaires
│   ├── subjects/           # Matières
│   ├── admin/              # Rapports, exports, imports, sauvegardes
│   ├── profile/            # Profil utilisateur
│   ├── users/              # Gestion utilisateurs
│   └── roles/              # Gestion des rôles
├── components/
│   ├── common/             # DataTableCard, ConfirmDialog, RoleGuard, Toast, LoadingSpinner, Pagination
│   ├── dashboard/          # Stats cards, bulletins récents, mentions, activités
│   ├── layout/             # Sidebar, Header, Breadcrumbs
│   └── mobile/             # MobileHeader, MobileMenuDrawer, BottomNav
├── router/
│   └── index.ts            # Configuration Vue Router + guards
└── utils/                  # Utilitaires divers
```

---

## 3. Architecture frontend — Patterns & conventions

### 3.1 Authentification

- JWT stocké dans `localStorage` (`token`)
- Intercepteur Axios : injection automatique du header `Authorization: Bearer <token>`
- Sur **401** : suppression du token + event `session-expired` → redirection `/login`
- `authStore.fetchProfile()` rechargé après refresh navigateur

### 3.2 State management (Pinia)

Stores par domaine :

| Store | État géré |
|---|---|
| `useAuthStore` | `token`, `user`, `roles`, `permissions`, `schoolId`, `sessionLoaded`, `passwordResetRequired` |
| `useSchoolStore` | `items`, `current`, `loading`, `error` + CRUD écoles |
| `useAcademicYearStore` | `items`, `current`, `loading`, `error` + CRUD années scolaires |

### 3.3 Navigation & permissions

- `navigation.ts` : tableau `navItems` avec `requiredRoles` et `requiredPermissions`
- `hasAccess()` : logique d'accès basée sur rôles + permissions
- `getFilteredNavItems()` : filtre sidebar selon droits
- `canAccessPath()` : vérification router guard

### 3.4 Layout responsive

| Composant | Desktop | Mobile |
|---|---|---|
| Sidebar | Fixe gauche, collapsible | Cachée |
| Header | Fixe haut, recherche + profil | `MobileHeader` (hamburger) |
| Navigation | Sidebar | `BottomNav` (onglets bas) |
| Menu mobile | `MobileMenuDrawer` (overlay) | Drawer |

### 3.5 Thème

- `useTheme()` : toggle clair/sombre via `localStorage`
- Classes Tailwind `dark:` appliquées dynamiquement

---

## 4. Modules API (Axios)

29 modules dans `src/api/` :

| Module | Endpoints couverts |
|---|---|
| `auth.ts` | `/auth/token`, `/auth/register-agent`, `/auth/mot-de-passe-oublie`, `/auth/reinitialiser-mot-de-passe`, `/auth/me`, `/auth/change-password`, `/auth/activer-utilisateur` |
| `users.ts` | CRUD utilisateurs |
| `roles.ts` | CRUD rôles |
| `schools.ts` | CRUD écoles |
| `academic-years.ts` | CRUD années scolaires |
| `trimesters.ts` | CRUD trimestres |
| `periods.ts` | CRUD périodes |
| `classrooms.ts` | CRUD salles / classes |
| `levels.ts` | CRUD niveaux |
| `sections.ts` | CRUD sections |
| `options.ts` | CRUD options |
| `curriculums.ts` | CRUD programmes |
| `curriculum-subjects.ts` | CRUD matières de programme |
| `subjects.ts` | CRUD matières |
| `teachers.ts` | CRUD enseignants + détail |
| `students.ts` | CRUD élèves + détail |
| `enrollments.ts` | CRUD inscriptions |
| `teaching-assignments.ts` | CRUD affectations enseignants ↔ classes |
| `assessment-types.ts` | CRUD types d'évaluations |
| `assessments.ts` | CRUD évaluations |
| `grades.ts` | CRUD notes |
| `attendances.ts` | CRUD présences |
| `disciplines.ts` | CRUD disciplines |
| `report-cards.ts` | Génération bulletins, workflow validation, PDF, bulletins annuels async |
| `dashboard.ts` | Stats dashboard |
| `admin.ts` | Rapports, exports, imports, sauvegardes |
| `user-students.ts` | Relations utilisateur ↔ élèves |
| `user-teachers.ts` | Relations utilisateur ↔ enseignants |

---

## 5. Router

### Routes publiques

| Path | Page | Meta |
|---|---|---|
| `/` | `Landing.vue` | `public: true` |
| `/login` | `Login.vue` | `guest: true` |
| `/mot-de-passe-oublie` | `ForgotPasswordPage.vue` | `guest: true` |
| `/reinitialiser-mot-de-passe` | `ResetPasswordPage.vue` | `guest: true` |

### Routes protégées (dans `MainLayout`)

| Path | Page |
|---|---|
| `/dashboard` | `Dashboard.vue` |
| `/ecoles`, `/ecoles/form`, `/ecoles/form/:id` | CRUD écoles |
| `/annees-academiques` | Années scolaires |
| `/trimestres` | Trimestres |
| `/periodes` | Périodes |
| `/salles` | Classes / salles |
| `/niveaux` | Niveaux |
| `/sections` | Sections |
| `/options` | Options pédagogiques |
| `/programmes` | Programmes curriculaires |
| `/matieres-programme` | Matières de programme |
| `/matieres` | Matières |
| `/enseignants`, `/enseignants/:id` | Enseignants + détail |
| `/eleves`, `/eleves/:id` | Élèves + détail |
| `/inscriptions` | Inscriptions |
| `/attributions` | Affectations enseignants |
| `/types-evaluations` | Types d'évaluations |
| `/evaluations` | Évaluations |
| `/notes` | Notes |
| `/presences` | Présences |
| `/disciplines` | Disciplines |
| `/bulletins`, `/bulletins/nouveau`, `/bulletins/:id`, `/bulletins/mes-bulletins` | Bulletins |
| `/bulletins-annuels`, `/bulletins-annuels/nouveau`, `/bulletins-annuels/:id` | Bulletins annuels |
| `/rapports`, `/exports`, `/imports`, `/sauvegardes` | Admin |
| `/profil` | Profil utilisateur |
| `/users`, `/users/nouveau` | Gestion utilisateurs |
| `/roles` | Gestion des rôles |

---

## 6. Workflow de validation des bulletins (ScolaNote)

```
BROUILLON
   ↓ validerParPrefet()
VALIDE_PREFET
   ↓ validerParDirecteur()
VALIDE_DIRECTEUR
   ↓ signerBulletin()
SIGNE
   ↓ publierBulletin()
PUBLIE
```

---

## 7. Configuration & déploiement

### Variables d'environnement

```env
VITE_API_BASE_URL=https://scolanote-production.up.railway.app
VITE_APP_TITLE=ScolaNote
```

### Vite proxy (dev)

```ts
server: {
  port: 3000,
  proxy: {
    '/api': { target: 'https://scolanote-production.up.railway.app', changeOrigin: true },
    '/uploads': { target: 'https://scolanote-production.up.railway.app', changeOrigin: true },
    '/auth': { target: 'https://scolanote-production.up.railway.app', changeOrigin: true }
  }
}
```

### Déploiement

- **Frontend** : Vercel (`vercel.json`)
- **Backend** : Railway (`railway.json`)
- Build : `npm run build` → `dist/`
- Base prod : `https://app.gestbulletin.com`

### Scripts

```bash
npm install
npm run dev        # Vite sur :3000
npm run build      # Build production
npm run preview    # Preview build
npm run typecheck  # vue-tsc --noEmit
```

---

## 8. Comparaison des deux projets

| Aspect | MariagePlus | ScolaNote / GestBulletin |
|---|---|---|
| **Domaine** | Gestion des mariages & invitations | Gestion des bulletins scolaires |
| **Backend** | Spring Boot 3.2 / Java 17 (local + distant) | Spring Boot (distant Railway) |
| **Base de données** | PostgreSQL / H2 | (gérée par le backend) |
| **Frontend principal** | Vue 3 + TypeScript (web, maquette définie) | Vue 3 + TypeScript |
| **Frontend web** | En développement (maquette `D:\Projet\maquette mariaplusweb`) | Déployé (Vercel) |
| **Frontend mobile** | Flutter (`MariageApp-main`, hors dépôt) | — |
| **Auth** | JWT + refresh + logout + lockout | JWT + localStorage |
| **Multi-tenant** | Oui (organisations) | Oui (écoles / établissements) |
| **RBAC** | Rôles + permissions granulaires | Rôles + permissions granulaires |
| **Scoping** | Par mariage (agents) | Par école / établissement |
| **Upload fichiers** | Non (URLs seulement) | PDF bulletins (téléchargement blob) |
| **Email** | SMTP optionnel | (backend géré) |
| **UI** | Tailwind MD3 + Material Symbols | Tailwind + Lucide |
| **Design system** | Material Design 3 (couleurs MD3, Inter, Material Symbols) | Custom (Inter, Lucide) |
| **State front** | Pinia | Pinia |
| **Router** | Vue Router 4 | Vue Router 4 |
| **Thème** | Clair / Sombre (MD3) | Clair / Sombre |
| **Mobile** | Responsive (sidebar + bottom nav 4 onglets) | Responsive (sidebar + bottom nav) |

---

## 9. Points d'attention & dette technique

### MariagePlus

| Point | Détail |
|---|---|
| `ddl-auto: update` local | Risque de divergence H2 / Postgres → passer à `validate` |
| SMTP non configuré | `emailSent: false` sans config SMTP |
| Photos couple | Champs URL uniquement, pas d'upload |
| Pas d'export CSV invités | Permission `GUEST_EXPORT` seedée mais pas d'endpoint |
| Pas de PDF / rapports | Permissions `REPORT_*` seedées mais pas d'endpoint |
| CORS | Strict par défaut, configurer `CORS_ALLOWED_ORIGINS` en prod |
| `ADMIN_INIT_ENABLED` | Désactiver en prod |

### ScolaNote / GestBulletin

| Point | Détail |
|---|---|
| Auth sur localStorage | Pas de refresh token, pas de secure storage |
| Pas de logout API | Token supprimé localement mais pas révoqué côté serveur |
| Pas de tests front | Aucun test unitaire / e2e détecté |
| Profil après refresh | `fetchProfile()` requis car JWT ne contient pas tous les champs |

---

## 10. Recommandations

### Pour MariagePlus web

1. **Respecter la maquette** : implémenter chaque écran tel que défini dans `D:\Projet\maquette mariaplusweb`
2. **Design System** : utiliser la config Tailwind MD3 fournie dans la maquette (`colors`, `fontFamily`, `fontSize`, `spacing`, `borderRadius`)
3. **Icons** : Material Symbols Outlined uniquement (pas Lucide)
4. **Navigation** : respecter la sidebar 10 items + footer + bottom nav mobile 4 items
5. **Responsive** : desktop sidebar 260px, mobile bottom nav + hamburger menu
6. **Langue** : français exclusivement pour l'UI
7. **Flux check-in** : implémenter le scan QR + validation + stepper de confirmation
8. **Bento grid** : utiliser `grid-cols-12` avec les tailles de carte définies dans la maquette
9. **Import CSV** : respecter le format backend (`firstName,lastName,email,phone,address,allowedCompanions,categoryName,notes`)
10. **RSVP tracking** : implémenter le donut chart segmenté + filtres par statut

### Pour ScolaNote / GestBulletin

1. **Authentification renforcée** : refresh token + interceptor Axios dédié
2. **Tests frontend** : ajouter Vitest + Testing Library
3. **SSR / SEO** : considérer Nuxt 3 si landing page nécessite SEO
4. **PWA** : manifest + service worker
5. **Storybook** : documentation visuelle composants
6. **i18n** : prévoir système de traduction
7. **Monitoring** : Sentry ou équivalent
8. **CI/CD** : checks typecheck + lint sur chaque PR

---

*Document généré le 26 août 2026 — Mis à jour avec la maquette web MariagePlus*

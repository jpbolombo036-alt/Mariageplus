# 📐 Document de conception — Unification « Event » comme ressource racine

> **Statut :** Proposition à valider
> **Date :** 2026-08-29
> **Chantier :** Le mariage devient un *type* d'événement. `Event` remplace `Wedding` comme agrégat racine.

---

## 1. Contexte et motivation

### 1.1 Constat

Le modèle actuel considère le **mariage** comme l'agrégat racine de la plateforme :

```
Wedding (mariage)                          ← /api/weddings
 └── WeddingEvent (cérémonie civile,      ← /api/weddings/{weddingId}/events
     religieuse, réception, ...)
```

Or :
- La maquette frontend s'intitule déjà **« Event Management / Guest Manager »** (header) avec une entrée **« Mes événements »** dans la sidebar.
- L'enum `WeddingEventType` contient déjà `GRADUATION` — le produit a dépassé le seul mariage.
- La plateforme doit gérer d'autres types d'événements : **collation, anniversaire, baptême, graduation, etc.**

### 1.2 Décision conceptuelle

> **« Le mariage est aussi un événement. »**

Le mariage devient un **type** d'événement parmi d'autres. Tous les types partagent un socle commun (nom, date, lieu, invités, tables, RSVP, check-in). Le type **MARIAGE** ajoute des champs spécifiques (époux, épouse, détails propres au mariage).

### 1.3 Objectifs

| # | Objectif |
|---|---|
| O1 | `Event` = ressource racine unique, avec champ `type` |
| O2 | Type = MARIAGE → champs spécifiques mariage ; autres types → champs standards uniquement |
| O3 | Les sous-étapes d'un mariage (cérémonie civile, religieuse, réception) deviennent des `EventSession` |
| O4 | Migration **progressive et non-rupture** : l'ancien `/api/weddings` reste vivant pendant la coexistence |
| O5 | Le front bascule sur `/api/events` en fin de chantier, puis l'ancien est déprécié/supprimé |

### 1.4 Non-objectifs (explicitement exclus)

- ❌ **Moteur de champs dynamiques générique** (custom fields, JSON schema) — sur-ingeniering. On ajoute une table de détails par type quand le besoin arrive réellement.
- ❌ **Rattachement des invitations à une session** — on reste au niveau Event global comme aujourd'hui.
- ❌ Refonte du modèle Invités / Invitations / Tables / RSVP — seuls les chemins d'API changent.

---

## 2. Modèle de données cible

### 2.1 Vue d'ensemble

```
┌─────────────────────────────┐
│ Event                       │  ← socle commun, tous types
│ id, organization_id         │
│ name, type, description     │
│ event_date, start/end_time  │
│ venue_name, venue_address,  │
│ city, commune, country,     │
│ latitude, longitude, map_url│
│ status, display_order,      │
│ active                      │
└──────────┬──────────────────┘
           │ 1 ──────── 0..1
┌──────────▼──────────────────┐
│ WeddingDetails              │  ← UNIQUEMENT si type = WEDDING
│ event_id (FK, unique)       │
│ groom_name, bride_name      │
│ … (champs actuels de        │
│    Wedding spécifiques)     │
└──────────┬──────────────────┘
           │ 1 ──────── N
┌──────────▼──────────────────┐
│ EventSession                │  ← ex-WeddingEvent (sous-étapes)
│ event_id (FK, indexé)       │
│ name, type, description     │
│ session_date, start/end_time│
│ venue_*, city, commune,     │
│ country, lat/lng, map_url   │
│ display_order, active       │
└─────────────────────────────┘
```

### 2.2 Tables

#### `events` (nouvelle — remplace `weddings`)

| Colonne | Type | Contraintes | Notes |
|---|---|---|---|
| `id` | BIGINT | PK | |
| `organization_id` | BIGINT | NOT NULL, indexé | Multi-tenant (périmètre porté par Event) |
| `name` | VARCHAR(150) | NOT NULL | |
| `type` | VARCHAR(30) | NOT NULL | `WEDDING`, `COLLATION`, `ANNIVERSARY`, `BAPTISM`, `GRADUATION`, `OTHER` |
| `description` | VARCHAR(1000) | | |
| `event_date` | DATE | | Date principale |
| `start_time` / `end_time` | TIME | | |
| `venue_name` | VARCHAR(200) | | |
| `venue_address` | VARCHAR(255) | | |
| `city`, `commune`, `country` | VARCHAR(100) | | |
| `latitude`, `longitude` | DOUBLE | | |
| `map_url` | VARCHAR(1000) | | |
| `status` | VARCHAR(30) | NOT NULL | Reprend le cycle de vie actuel de `Wedding` |
| `display_order` | INT | | Tri dans la vue « Mes événements » |
| `active` | BOOLEAN | NOT NULL, default true | Suppression logique |
| `created_by`, `created_at`, `updated_at` | | | Hérités de `BaseEntity` |

**Index** : `(organization_id)`, `(organization_id, event_date)`, `(organization_id, type)`.

#### `wedding_details` (nouvelle — 1-1 avec Event)

Reprend **uniquement les champs actuels de `Wedding` spécifiques au mariage** (à confronter avec l'entité `Wedding.java` lors de l'implémentation).

| Colonne | Type | Contraintes |
|---|---|---|
| `id` | BIGINT | PK |
| `event_id` | BIGINT | FK → events.id, **UNIQUE**, NOT NULL |
| `groom_name` | VARCHAR(150) | époux |
| `bride_name` | VARCHAR(150) | épouse |
| *(autres champs spécifiques mariage issus de `Wedding`)* | | |

#### `event_sessions` (nouvelle — remplace `wedding_events`)

Structure identique à l'actuelle entité `WeddingEvent`, avec `event_id` à la place de `wedding_id`.
**Index** : `(event_id)`, `(event_id, session_date)`.

#### Tables filles (invités, catégories, tables, invitations, RSVP, check-ins)

**Aucun changement de logique** : la colonne `wedding_id` existante est renommée en `event_id` via Flyway (`ALTER TABLE … RENAME COLUMN`).

### 2.3 Enums

```java
public enum EventType {
    WEDDING,      // mariage — a des WeddingDetails + sessions
    COLLATION, ANNIVERSARY, BAPTISM, GRADUATION, OTHER
}

public enum EventSessionType {
    // reprend WeddingEventType actuel (CIVIL_CEREMONY, RELIGIOUS_CEREMONY, réception…)
    // + valeurs génériques si absentes : MAIN, RECEPTION, AFTER_PARTY
}
```

Renommé car une collation ou un anniversaire peuvent aussi avoir des sessions (vin d'honneur, soirée dansante…).


## 3. API cible

### 3.1 Routes racine — `/api/events`

| Méthode | Endpoint | Description |
|---|---|---|
| GET | `/api/events?page&size&sortBy&sortDir&type` | Liste paginée, filtre optionnel `?type=WEDDING` (= anciens mariages) |
| POST | `/api/events` | Créer. Body : champs communs + `type` + `weddingDetails` si type = WEDDING |
| GET | `/api/events/{id}` | Détail = champs communs + `weddingDetails: {...} \| null` |
| PUT | `/api/events/{id}` | Modifier |
| PATCH | `/api/events/{id}/status` | Transition de statut (mêmes règles que `WeddingController.updateStatus`) |
| DELETE | `/api/events/{id}` | Suppression logique |

### 3.2 Routes sessions — `/api/events/{eventId}/sessions`

| Méthode | Endpoint |
|---|---|
| GET | `/api/events/{eventId}/sessions` |
| POST | `/api/events/{eventId}/sessions` |
| GET | `/api/events/{eventId}/sessions/{sessionId}` |
| PUT | `/api/events/{eventId}/sessions/{sessionId}` |
| DELETE | `/api/events/{eventId}/sessions/{sessionId}` |

### 3.3 Routes filles renommées (logique inchangée)

| Ancien | Nouveau |
|---|---|
| `/api/weddings/{id}/guests` | `/api/events/{id}/guests` |
| `/api/weddings/{id}/guest-categories` | `/api/events/{id}/guest-categories` |
| `/api/weddings/{id}/tables` | `/api/events/{id}/tables` |
| `/api/weddings/{id}/invitations` | `/api/events/{id}/invitations` |
| `/api/weddings/{id}/rsvps` (admin) | `/api/events/{id}/rsvps` |
| `/api/weddings/{id}/checkins` | `/api/events/{id}/checkins` |
| `/api/weddings/{id}/export/...` | `/api/events/{id}/export/...` |
| `/api/weddings/{id}/dashboard` | `/api/events/{id}/dashboard` |

### 3.4 Exemples de payloads

**Création d'un mariage :**
```json
POST /api/events
{
  "name": "Mariage Jean & Marie",
  "type": "WEDDING",
  "eventDate": "2026-10-10",
  "startTime": "09:00",
  "venueName": "Salle Étoile",
  "city": "Abidjan",
  "weddingDetails": { "groomName": "Jean K.", "brideName": "Marie A." }
}
```

**Création d'une collation :**
```json
POST /api/events
{
  "name": "Collation des brevets",
  "type": "COLLATION",
  "eventDate": "2026-11-02",
  "venueName": "Jardin municipal"
}
```
→ Pas de `weddingDetails` requis/accepté (rejet 400 recommandé pour expliciter le contrat — décision D2).

**Réponse détaillée :**
```json
GET /api/events/42
{
  "id": 42,
  "name": "Mariage Jean & Marie",
  "type": "WEDDING",
  "status": "PLANNED",
  "eventDate": "2026-10-10",
  "weddingDetails": { "groomName": "Jean K.", "brideName": "Marie A." },
  "sessions": [ { "id": 7, "name": "Cérémonie civile", "type": "CIVIL_CEREMONY" } ]
}
```

---

## 4. Plan de migration en 4 phases (sans rupture)

> **Principe directeur : ne jamais casser l'existant.** `/api/weddings` reste vivant jusqu'à la phase 4.

| Phase | Contenu | Effort |
|---|---|---|
| **1. Coexistence** (back) | Entités `Event`/`WeddingDetails`/`EventSession` + enums, repositories, services, DTOs, mappers MapStruct, contrôleurs `/api/events` + `/sessions`. Tests. Les routes filles restent sous `/api/weddings` pendant cette phase | 2–3 j |
| **2. Migration données** | Flyway : `INSERT INTO events SELECT ... FROM weddings` (type=WEDDING), `wedding_details`, `event_sessions` (renommage wedding_id→event_id), `ALTER TABLE filles ... RENAME COLUMN wedding_id TO event_id`. Vérifications d'intégrité | 1 j |
| **3. Routes filles + front** | Back : contrôleurs filles sous `/api/events/{id}/...` (anciens chemins en alias temporaire). Front : bascule des appels, gestion du `type` (bento grid, badge type, formulaire dynamique : bloc commun + section mariage conditionnelle) | 2–4 j |
| **4. Dépréciation / suppression** | `@Deprecated` + header `Deprecation` sur `/api/weddings` (1 release de sécurité), puis suppression des contrôleurs/services/entités `Wedding*` | 0,5 j |

---

## 5. Points de vigilance

| # | Risque | Mitigation |
|---|---|---|
| V1 | Périmètre multi-tenant : aujourd'hui porté par `Wedding` | `Event` porte `organization_id` dès la création ; même logique d'isolation |
| V2 | Invitations/RSVP par session | **Hors périmètre** — reste au niveau Event global |
| V3 | Check-in / QR codes | Aucun changement logique ; seuls les identifiants de route changent |
| V4 | Front cassé pendant la bascule | Phase de coexistence ; alias d'anciennes routes si nécessaire |
| V5 | `weddingDetails` fourni pour un type ≠ WEDDING | Rejet 400 explicite |
| V6 | Statuts (`updateStatus`) : transitions validées | Reprise telle quelle du workflow actuel de `WeddingService` |
| V7 | Super admin (vue globale) | Même comportement que `WeddingController.list` |

---

## 6. Décisions à valider avant démarrage

- [ ] **D1** — Renommer la colonne `wedding_id` → `event_id` dans les tables filles (recommandé) ou garder le nom ?
- [ ] **D2** — `weddingDetails` pour un type ≠ WEDDING : rejet 400 (recommandé) ou ignoré silencieusement ?
- [ ] **D3** — Liste de l'enum `EventType` : `WEDDING, COLLATION, ANNIVERSARY, BAPTISM, GRADUATION, OTHER` — convient-elle ?
- [ ] **D4** — Front : quand la section « champs mariage » apparaît-elle (choix du type dans le stepper de création) — à préciser avec la maquette.
- [ ] **D5** — Durée de la période de dépréciation de `/api/weddings` (proposition : 1 release).

---

## 7. Résumé exécutif

> **Oui à `Event` comme racine, mais sobre :**
> 1. Table `events` commune + `wedding_details` séparée (1-1) — pas de dynamisme générique.
> 2. `EventSession` remplace `WeddingEvent` (les sous-étapes du mariage).
> 3. Invités/invitations/tables/RSVP restent au niveau Event global — logique inchangée, seuls les chemins changent.
> 4. Migration en 4 phases avec coexistence — `/api/weddings` n'est supprimé qu'en dernier.
>
> **Effort total estimé : ~1 semaine.**


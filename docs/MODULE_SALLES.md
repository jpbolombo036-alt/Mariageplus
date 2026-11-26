# Module « Salles » — gestion de salle de fête (V1)

Permet à un organisateur propriétaire de salle(s) de gérer ses clients, ses
réservations, ses encaissements (USD / CDF) et ses rapports, puis de créer
l'événement MariagePlus du client et de lui donner accès à ses invités.

## Activation

Le module est **désactivé par défaut**. Seul le SUPER_ADMIN l'active, par organisation :

```
PUT /api/admin/organizations/{organizationId}/settings
{ "venueModuleEnabled": true }
```

Réponse : `venueModuleEnabled` (valeur brute) et `effectiveVenueModule`.
Côté organisateur, `GET /api/venues/module-status` → `{ "enabled": true|false }`
pour afficher ou masquer le menu « Ma salle ». Module désactivé : toutes les
routes du module répondent **403**.

Pour « Créer l'événement MariagePlus », la création d'événements doit aussi être
autorisée pour l'organisation (`eventCreationEnabled`).

## Permissions (rôles ORGANISATEUR et SUPER_ADMIN, migration V54)

| Permission | Usage |
|---|---|
| `VENUE_VIEW` / `VENUE_MANAGE` | salles, tarifs, photo |
| `VENUE_CLIENT_MANAGE` | fiches clients |
| `BOOKING_VIEW` / `BOOKING_MANAGE` | réservations, calendrier, prestations, événement, accès client |
| `BOOKING_PAYMENT_RECORD` | enregistrer / annuler un paiement |
| `VENUE_REPORT_VIEW` | tableau de bord, export Excel |
| `BOOKING_REFUND` | rembourser un client, annuler un paiement (V57) |

**Rôle `AGENT_SALLE`** (caissier, V57) : `VENUE_VIEW`, `VENUE_CLIENT_MANAGE`, `BOOKING_VIEW`, `BOOKING_MANAGE`,
`BOOKING_PAYMENT_RECORD`. Il réserve et encaisse sur toutes les salles de l'organisation (jamais rattaché à un
événement), mais ne rembourse pas, n'annule pas de paiement, ne voit pas les rapports, ne gère ni les salles ni le
taux du jour, et ne crée ni l'événement MariagePlus ni l'accès client (`EVENT_CREATE`, `ORGANIZATION_MANAGE_MEMBERS`).

## Règles métier

- **Statuts** : `DEMANDE` → `OPTION` / `CONFIRMEE` ; `OPTION` → `CONFIRMEE` ;
  `CONFIRMEE` → `REALISEE` ; `ANNULEE` possible avant `REALISEE`.
- **Pas de double réservation** : `OPTION`, `CONFIRMEE` et `REALISEE` bloquent le
  créneau (intervalle `[startAt, endAt[` : une réservation peut commencer à l'heure
  exacte où la précédente finit). Conflit → **409** avec la réservation en cause.
  Les écritures se font sous verrou de la salle (`SELECT … FOR UPDATE`), donc
  deux agents qui réservent en même temps ne passent pas tous les deux.
- **Option** : expire après `VENUE_OPTION_DAYS` jours (7 par défaut, ou
  `optionDays` à la création). Une tâche horaire la passe en `ANNULEE` **si aucun
  paiement** n'a été enregistré.
- **Montants** : `total = basePrice + prestations − remise`, au centime.
  Une réservation a **une devise** (USD ou CDF). Un paiement dans l'autre devise
  exige `exchangeRate` = **CDF pour 1 USD** ; le montant converti sert au reste à
  payer. Un paiement qui dépasse le reste à payer est refusé (400).
- **Taux de change** : le gérant fixe le taux du jour (`POST /api/venues/exchange-rate { cdfPerUsd }`,
  historique conservé). Un paiement ou remboursement dans l'autre devise reprend ce taux, sauf si
  `exchangeRate` est saisi sur le mouvement. Sans taux du jour ni taux saisi : 400.
- **Remboursements** : seulement sur une réservation `ANNULEE`, total ou partiel, jamais plus que le net
  encaissé. `POST /cancel { reason, refund: { amount, currency, method, reference } }` annule et rembourse
  en une fois ; `POST /refunds` pour un remboursement ultérieur. Ce qui n'est pas remboursé est
  `retainedAmount` (frais d'annulation). Reçus `RMB-xxxxx` ; le tableau de bord déduit les remboursements
  de l'encaissé (`collected`) et les liste à part (`refunded`).
- **Suppression d'une salle** : refusée (409) tant qu'elle a des réservations à venir ; restauration via
  `GET /api/venues/deleted` et `POST /api/venues/{id}/restore`.
- **Téléphones** : `0812 345 678` → `+243812345678` (`VENUE_DEFAULT_COUNTRY_CODE`).
- Une réservation `ANNULEE` ou `REALISEE` n'est plus modifiable.
- Tout est tracé dans `audit_logs` (création, modification, paiements, annulations).

## Endpoints

### Salles — `/api/venues`
| Méthode | Route | |
|---|---|---|
| GET | `/api/venues` | salles + tarifs |
| POST | `/api/venues` | `{ name, description, address, commune, city, country, latitude, longitude, capacitySeated, capacityStanding, amenities }` |
| GET / PUT / DELETE | `/api/venues/{id}` | |
| POST | `/api/venues/{id}/rates` | `{ label, price, currency: "USD"\|"CDF" }` |
| PUT / DELETE | `/api/venues/{id}/rates/{rateId}` | |
| PUT (multipart `file`) / GET / DELETE | `/api/venues/{id}/image` | GET public |
| GET | `/api/venues/{id}/calendar?month=2027-03` | réservations du mois (hors annulées) |
| GET | `/api/venues/dashboard?from=&to=&venueId=` | défaut : mois courant |
| GET | `/api/venues/export.xlsx?from=&to=` | feuilles Réservations + Paiements |

### Clients — `/api/venue-clients`
| Méthode | Route | |
|---|---|---|
| GET | `/api/venue-clients?q=&page=&size=` | recherche nom / téléphone / email |
| POST | `/api/venue-clients` | `{ firstName, lastName, phone, email, notes }` |
| GET | `/api/venue-clients/{id}` | fiche + historique des réservations |
| PUT | `/api/venue-clients/{id}` | |

### Réservations — `/api/bookings`
| Méthode | Route | |
|---|---|---|
| GET | `/api/bookings?venueId=&clientId=&status=&from=&to=` | |
| POST | `/api/bookings` | voir exemple ci-dessous |
| GET / PUT | `/api/bookings/{id}` | détail avec `extras`, `payments`, `paidAmount`, `balance` |
| POST | `/api/bookings/{id}/confirm` \| `/complete` \| `/cancel` | cancel : `{ reason }` |
| POST / DELETE | `/api/bookings/{id}/extras[/{extraId}]` | `{ label, quantity, unitPrice }` |
| GET / POST | `/api/bookings/{id}/payments` | `{ amount, currency, exchangeRate, method, reference, paidAt, note }` |
| DELETE | `/api/bookings/{id}/payments/{paymentId}` | annule un paiement saisi par erreur |
| GET | `/api/bookings/{id}/quote.pdf` | devis |
| GET | `/api/bookings/{id}/payments/{paymentId}/receipt.pdf` | reçu |
| POST | `/api/bookings/{id}/event` | réservation `CONFIRMEE` ; `{ name, weddingDetails }` (noms des mariés requis si `eventType = WEDDING`) |
| POST | `/api/bookings/{id}/client-access` | `{ email?, firstName?, lastName? }` |

`method` : `ESPECES`, `MPESA`, `ORANGE_MONEY`, `AIRTEL_MONEY`, `VIREMENT`.

Exemple de création avec un nouveau client :

```json
POST /api/bookings
{
  "venueId": 3,
  "rateId": 7,
  "title": "Mariage Ilunga",
  "eventType": "WEDDING",
  "startAt": "2027-03-20T14:00:00",
  "endAt": "2027-03-20T23:00:00",
  "guestCount": 300,
  "client": { "firstName": "Patrick", "lastName": "Ilunga", "phone": "0812345678", "email": "patrick@example.com" }
}
```

## Lien avec l'événement MariagePlus

1. Réservation confirmée → `POST /api/bookings/{id}/event` : événement `DRAFT`
   créé **dans l'organisation de la salle**, pré-rempli (date, heures, salle,
   adresse, GPS). La salle le personnalise (photos, message, tenue, carte).
2. Changement de date ou de salle sur la réservation → l'événement suit.
   Annulation de la réservation → l'événement passe en `CANCELLED`.
3. `POST /api/bookings/{id}/client-access` : le client reçoit un compte
   `GESTIONNAIRE_INVITES` **limité à son événement**. Nouveau compte : la réponse
   contient `temporaryPassword` (affiché une seule fois) et `whatsappNumber` pour
   lui transmettre ses accès ; il doit changer ce mot de passe à la première
   connexion. Le client gère invités, invitations et RSVP ; il ne voit ni le
   module Salles ni les autres clients, et ne peut pas modifier l'événement.

## Reporté

- V1.1 : rôle `AGENT_SALLE` (réservations et paiements sans les rapports).
- V2 : factures numérotées, page publique de demande (`DEMANDE`), paiement Mobile Money en ligne.

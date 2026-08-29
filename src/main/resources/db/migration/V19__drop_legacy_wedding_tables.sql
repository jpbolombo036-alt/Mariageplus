-- V19 : Suppression du modele legacy (weddings / wedding_events)
-- Les FK des tables filles sont re-ciblees vers events(id).
-- Le contenu de weddings a deja ete copie dans events par V17, et wedding_events dans event_sessions.

-- 1. Re-ciblage des FK de organization_members
ALTER TABLE organization_members DROP CONSTRAINT fk_org_members_wedding;
ALTER TABLE organization_members
    ADD CONSTRAINT fk_org_members_event
    FOREIGN KEY (wedding_id) REFERENCES events(id);

-- 2. Re-ciblage des FK de wedding_tables
ALTER TABLE wedding_tables DROP CONSTRAINT fk_wedding_tables_wedding;
ALTER TABLE wedding_tables
    ADD CONSTRAINT fk_wedding_tables_event
    FOREIGN KEY (wedding_id) REFERENCES events(id);

-- 3. Re-ciblage des FK de guest_categories et guests
ALTER TABLE guest_categories DROP CONSTRAINT fk_guest_categories_wedding;
ALTER TABLE guest_categories
    ADD CONSTRAINT fk_guest_categories_event
    FOREIGN KEY (wedding_id) REFERENCES events(id);

ALTER TABLE guests DROP CONSTRAINT fk_guests_wedding;
ALTER TABLE guests
    ADD CONSTRAINT fk_guests_event
    FOREIGN KEY (wedding_id) REFERENCES events(id);

-- 4. Re-ciblage des FK de invitations
ALTER TABLE invitations DROP CONSTRAINT fk_invitations_wedding;
ALTER TABLE invitations
    ADD CONSTRAINT fk_invitations_event
    FOREIGN KEY (wedding_id) REFERENCES events(id);

-- 5. Suppression des tables legacy (wedding_events a ete migre vers event_sessions par V17)
DROP TABLE IF EXISTS wedding_events;
DROP TABLE IF EXISTS weddings;

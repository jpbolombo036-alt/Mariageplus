ALTER TABLE events ADD COLUMN invitation_card_template VARCHAR(20) NOT NULL DEFAULT 'STANDARD';
ALTER TABLE events ADD CONSTRAINT chk_event_invitation_card_template CHECK (invitation_card_template IN ('STANDARD', 'ROMANCE', 'PRESTIGE'));

CREATE TABLE IF NOT EXISTS event_galleries (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_event_galleries_event FOREIGN KEY (event_id) REFERENCES events(id)
);

CREATE TABLE IF NOT EXISTS gallery_photos (
    id BIGSERIAL PRIMARY KEY,
    gallery_id BIGINT NOT NULL,
    guest_category_id BIGINT,
    storage_key VARCHAR(500),
    image BYTEA,
    content_type VARCHAR(50) NOT NULL,
    original_filename VARCHAR(255),
    caption VARCHAR(500),
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_gallery_photos_gallery FOREIGN KEY (gallery_id) REFERENCES event_galleries(id),
    CONSTRAINT fk_gallery_photos_category FOREIGN KEY (guest_category_id) REFERENCES guest_categories(id)
);

CREATE INDEX IF NOT EXISTS idx_gallery_photos_gallery_order ON gallery_photos(gallery_id, display_order, id);
CREATE INDEX IF NOT EXISTS idx_gallery_photos_category ON gallery_photos(guest_category_id);

-- Posty pod wydarzeniem ("szukam ekipy na koncert").
--
-- Post pod wydarzeniem to zwykly post z odnosnikiem. Gdy import sprzata
-- minione wydarzenie, post zostaje, a odnosnik czysci baza (ON DELETE SET NULL).

ALTER TABLE posts ADD COLUMN event_id bigint;

CREATE INDEX idx_posts_event ON posts USING btree (event_id);

ALTER TABLE ONLY posts
    ADD CONSTRAINT fksajulkqtegco740ciioukphr5 FOREIGN KEY (event_id) REFERENCES music_events(id) ON DELETE SET NULL;

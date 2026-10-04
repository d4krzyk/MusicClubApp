-- GIF-y na czacie klanu - te same piec kolumn co w komentarzach i wiadomosciach (V15).
--
-- Adresy pochodza wylacznie z podpisanych wynikow wyszukiwania (GifService). Wiadomosc z samym GIF-em ma
-- pusta tresc: kolumna content zostaje NOT NULL, tak jak w komentarzach.

ALTER TABLE clan_messages
    ADD COLUMN gif_url         varchar(500),
    ADD COLUMN gif_preview_url varchar(500),
    ADD COLUMN gif_width       integer,
    ADD COLUMN gif_height      integer,
    ADD COLUMN gif_title       varchar(150);

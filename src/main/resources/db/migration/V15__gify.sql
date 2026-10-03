-- GIF-y w komentarzach i wiadomosciach (przegladarka GIF-ow, dostawca: KLIPY albo GIPHY).
--
-- Zapisujemy tylko adresy i wymiary - plik laduje przegladarka z serwera dostawcy. Adresy pochodza wylacznie
-- z podpisanych wynikow wyszukiwania (GifService), nigdy wprost od klienta. Wszystkie kolumny sa opcjonalne:
-- komentarz i wiadomosc bez GIF-a maja je puste. Komentarz z samym GIF-em ma pusta tresc (kolumna content
-- zostaje NOT NULL, wiec zapisujemy pusty napis).

ALTER TABLE comments
    ADD COLUMN gif_url         varchar(500),
    ADD COLUMN gif_preview_url varchar(500),
    ADD COLUMN gif_width       integer,
    ADD COLUMN gif_height      integer,
    ADD COLUMN gif_title       varchar(150);

ALTER TABLE messages
    ADD COLUMN gif_url         varchar(500),
    ADD COLUMN gif_preview_url varchar(500),
    ADD COLUMN gif_width       integer,
    ADD COLUMN gif_height      integer,
    ADD COLUMN gif_title       varchar(150);

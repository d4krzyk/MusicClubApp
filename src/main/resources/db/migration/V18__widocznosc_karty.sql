-- =============================================================================
--  Kto widzi karte profilu (zdjecia, "o mnie", "szukam", pytania) na profilu.
--
--  users.card_visibility - EVERYONE (kazdy, kto widzi szczegoly profilu), FRIENDS (tylko znajomi) albo
--                          DISCOVER_ONLY (na profilu nikt poza wlascicielem i administratorem - karta jest
--                          tylko w talii trybu Poznawaj). Talii to ustawienie nie dotyczy.
--  Wartosc domyslna = zachowanie sprzed tej migracji (karta na profilu dla kazdego, kto widzi profil).
--
--  Jak V1 - V17: zgodne ze zrzutem pg_dump schematu z ddl-auto=create (sprawdzone porownaniem).
--  Wypuszczonej migracji sie nie edytuje.
-- =============================================================================

ALTER TABLE users ADD COLUMN card_visibility character varying(20) DEFAULT 'EVERYONE'::character varying NOT NULL;

ALTER TABLE users ADD CONSTRAINT users_card_visibility_check
    CHECK (((card_visibility)::text = ANY ((ARRAY['EVERYONE'::character varying, 'FRIENDS'::character varying, 'DISCOVER_ONLY'::character varying])::text[])));

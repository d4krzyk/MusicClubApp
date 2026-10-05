-- =============================================================================
--  Usuwanie wlasnych wiadomosci i podglad linkow w czacie klanu.
--
--  messages.deleted_at       - nadawca usunal wiadomosc u obu stron; tresc i zalaczniki sa czyszczone, wiersz zostaje
--                              jako slad "wiadomosc usunieta" (druga strona dostaje to przy odpytywaniu).
--  clan_messages.deleted_at  - to samo na czacie klanu (autor, zarzad klanu albo administrator aplikacji).
--  clan_messages.music_*     - nagranie rozpoznane w tresci (link do YouTube, Spotify, Apple Music) - te same szesc
--                              kolumn i te same CHECK-i co w messages i posts.
--
--  Jak V1 - V18: zgodne ze zrzutem pg_dump schematu z ddl-auto=create (porownane w calosci).
--  Wypuszczonej migracji sie nie edytuje.
-- =============================================================================

ALTER TABLE messages ADD COLUMN deleted_at timestamp(6) without time zone;

ALTER TABLE clan_messages ADD COLUMN deleted_at timestamp(6) without time zone;
ALTER TABLE clan_messages ADD COLUMN music_start_seconds integer;
ALTER TABLE clan_messages ADD COLUMN music_kind character varying(16);
ALTER TABLE clan_messages ADD COLUMN music_provider character varying(16);
ALTER TABLE clan_messages ADD COLUMN music_external_id character varying(300);
ALTER TABLE clan_messages ADD COLUMN music_title character varying(300);
ALTER TABLE clan_messages ADD COLUMN music_thumbnail_url character varying(500);

ALTER TABLE clan_messages ADD CONSTRAINT clan_messages_music_kind_check
    CHECK (((music_kind)::text = ANY ((ARRAY['TRACK'::character varying, 'ALBUM'::character varying, 'ARTIST'::character varying, 'PLAYLIST'::character varying])::text[])));
ALTER TABLE clan_messages ADD CONSTRAINT clan_messages_music_provider_check
    CHECK (((music_provider)::text = ANY ((ARRAY['SPOTIFY'::character varying, 'YOUTUBE'::character varying, 'APPLE_MUSIC'::character varying])::text[])));

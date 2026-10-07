-- =============================================================================
--  Opis wykonawcy od Ticketmastera: pola description / additionalInfo wykonawcy (na ticketmaster.pl sekcja
--  "About" na stronie artysty). Do tej pory nie czytalismy ich wcale.
--
--  performer_about - jeden opis na nazwe wykonawcy sprowadzona przez NameKeys (jak performer_links). Z importu
--                    wydarzen albo z pytania o jednego wykonawce przy "Kim jest?"; pusty about = Ticketmaster nic
--                    o nim nie ma (tez zapamietane na 30 dni, zeby nie pytac przy kazdym rozwinieciu).
--
--  Dane o wykonawcach - zadnych danych uzytkownikow.
--  Jak V1 - V23: zgodne ze zrzutem pg_dump schematu z ddl-auto=create (porownane w calosci).
--  Wypuszczonej migracji sie nie edytuje.
-- =============================================================================


CREATE TABLE performer_about (
    checked_at timestamp(6) without time zone NOT NULL,
    lang character varying(8),
    attraction_id character varying(64),
    name_key character varying(200) NOT NULL,
    page_url character varying(500),
    about character varying(2000)
);

ALTER TABLE ONLY performer_about
    ADD CONSTRAINT performer_about_pkey PRIMARY KEY (name_key);

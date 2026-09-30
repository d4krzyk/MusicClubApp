-- =============================================================================
--  Regulamin i polityka prywatnosci.
--
--  users.terms_version     - wersja dokumentow, ktora osoba zaakceptowala (data zmiany tresci).
--  users.terms_accepted_at - kiedy.
--  Konta sprzed regulaminu maja tu NULL: aplikacja pokazuje im prosbe o akceptacje.
-- =============================================================================

ALTER TABLE users ADD COLUMN terms_accepted_at timestamp(6) without time zone;
ALTER TABLE users ADD COLUMN terms_version character varying(20);

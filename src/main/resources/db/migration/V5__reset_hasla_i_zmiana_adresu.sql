-- =============================================================================
--  Reset hasla i bezpieczniejsza zmiana adresu e-mail.
--
--  email_tokens.purpose   - do czego jest link: potwierdzenie adresu (VERIFY),
--                           zgoda ze starego adresu na zmiane (APPROVE_CHANGE),
--                           reset hasla (PASSWORD_RESET). Dotychczasowe linki
--                           to potwierdzenia - stad DEFAULT 'VERIFY'.
--  users.pending_email_old_approved_at
--  users.pending_email_new_verified_at
--                         - zmiana adresu wymaga zgody ze starej skrzynki
--                           i potwierdzenia nowej; tu widac, czego brakuje.
--  users.security_stamp   - znacznik bezpieczenstwa; jego zmiana wylogowuje
--                           inne sesje i uniewaznia "zapamietaj mnie".
--                           Pusty (konta sprzed migracji) dziala jak "".
--
--  Jak V1-V4: zgodne ze zrzutem pg_dump schematu z ddl-auto=create
--  (sprawdzone porownaniem blok po bloku).
-- =============================================================================

ALTER TABLE email_tokens ADD COLUMN purpose character varying(20) DEFAULT 'VERIFY'::character varying NOT NULL;

ALTER TABLE email_tokens ADD CONSTRAINT email_tokens_purpose_check
    CHECK (((purpose)::text = ANY ((ARRAY['VERIFY'::character varying, 'APPROVE_CHANGE'::character varying, 'PASSWORD_RESET'::character varying])::text[])));

ALTER TABLE users ADD COLUMN pending_email_new_verified_at timestamp(6) without time zone;
ALTER TABLE users ADD COLUMN pending_email_old_approved_at timestamp(6) without time zone;
ALTER TABLE users ADD COLUMN security_stamp character varying(32);

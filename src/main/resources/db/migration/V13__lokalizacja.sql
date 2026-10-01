-- Miasto w profilu: po nim aplikacja stawia wyzej ludzi, koncerty i klany z okolicy.
--
-- Tylko miasto - nigdy dokladny adres ani pozycja z telefonu. Wspolrzedne to srodek miasta z listy
-- (geo/miasta.csv w aplikacji), puste dla miasta wpisanego spoza listy. Istniejace konta nie maja
-- miasta; show_city = true to domyslne "miasto widac na profilu", gdy ktos je ustawi.

ALTER TABLE users ADD COLUMN city character varying(60);
ALTER TABLE users ADD COLUMN city_key character varying(100);
ALTER TABLE users ADD COLUMN city_lat double precision;
ALTER TABLE users ADD COLUMN city_lon double precision;
ALTER TABLE users ADD COLUMN show_city boolean DEFAULT true NOT NULL;

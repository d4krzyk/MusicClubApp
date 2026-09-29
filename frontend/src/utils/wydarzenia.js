/*
 * Pomocnicze funkcje zakladki Wydarzenia: daty, godziny, miasta.
 */

/**
 * Data wydarzenia jako obiekt Date w LOKALNEJ strefie.
 *
 * Serwer przysyla sam dzien, np. "2026-09-29". new Date("2026-09-29")
 * czyta to jako polnoc w UTC. Na telefonie ustawionym na strefe na zachod
 * od Greenwich (np. Nowy Jork) polnoc UTC to jeszcze poprzedni wieczor -
 * i koncert pokazalby sie dzien wczesniej.
 */
export function dzienWydarzenia(tekst) {
  const [rok, miesiac, dzien] = String(tekst).split('-').map(Number);
  return new Date(rok, miesiac - 1, dzien);
}

/** "19:00:00" -> "19:00". Pusta godzina zostaje pusta. */
export function godzina(tekst) {
  return tekst ? String(tekst).slice(0, 5) : '';
}

/** Ile dni od dzis: 0 = dzis, 1 = jutro. */
function dniOdDzis(tekst) {
  const dzis = new Date();
  dzis.setHours(0, 0, 0, 0);
  return Math.round((dzienWydarzenia(tekst) - dzis) / 86_400_000);
}

/**
 * Naglowek grupy na liscie: "Dziś", "Jutro", potem "czwartek, 1 października".
 * Rok tylko wtedy, gdy to nie jest ten biezacy.
 */
export function naglowekDnia(tekst, jezyk, t) {
  const roznica = dniOdDzis(tekst);
  if (roznica === 0) {
    return t('events.today');
  }
  if (roznica === 1) {
    return t('events.tomorrow');
  }

  const data = dzienWydarzenia(tekst);
  return new Intl.DateTimeFormat(jezyk, {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    ...(data.getFullYear() !== new Date().getFullYear() && { year: 'numeric' }),
  }).format(data);
}

/** Pelna data na strone wydarzenia: "wtorek, 29 września 2026". */
export function pelnaData(tekst, jezyk) {
  return new Intl.DateTimeFormat(jezyk, {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  }).format(dzienWydarzenia(tekst));
}

/** Plakietka na zdjeciu: dzien i skrot miesiaca ("29", "wrz"). */
export function plakietka(tekst, jezyk) {
  const data = dzienWydarzenia(tekst);
  return {
    dzien: data.getDate(),
    miesiac: new Intl.DateTimeFormat(jezyk, { month: 'short' }).format(data).replace('.', ''),
  };
}

/*
 * Polskie nazwy miast.
 *
 * Ticketmaster podaje miasta po angielsku i bez polskich znakow: "Krakow",
 * "Wroclaw", "Warsaw". Klucz to ta sama nazwa sprowadzona przez serwer
 * do malych liter bez znakow diakrytycznych - tu tylko dokladamy z powrotem
 * postac, jakiej uzywa czlowiek.
 *
 * Po angielsku zostawiamy polskie nazwy wszedzie poza Warszawa: "Kraków"
 * czy "Łódź" to nazwy wlasne i tak pisze je tez angielskojezyczny swiat.
 */
const MIASTA = {
  warsaw: 'Warszawa',
  krakow: 'Kraków',
  wroclaw: 'Wrocław',
  gdansk: 'Gdańsk',
  gdynia: 'Gdynia',
  sopot: 'Sopot',
  poznan: 'Poznań',
  lodz: 'Łódź',
  katowice: 'Katowice',
  chorzow: 'Chorzów',
  gliwice: 'Gliwice',
  zabrze: 'Zabrze',
  sosnowiec: 'Sosnowiec',
  'bielsko-biala': 'Bielsko-Biała',
  czestochowa: 'Częstochowa',
  szczecin: 'Szczecin',
  swinoujscie: 'Świnoujście',
  koszalin: 'Koszalin',
  bydgoszcz: 'Bydgoszcz',
  torun: 'Toruń',
  wloclawek: 'Włocławek',
  grudziadz: 'Grudziądz',
  lublin: 'Lublin',
  bialystok: 'Białystok',
  rzeszow: 'Rzeszów',
  przemysl: 'Przemyśl',
  kielce: 'Kielce',
  radom: 'Radom',
  olsztyn: 'Olsztyn',
  elblag: 'Elbląg',
  ostroda: 'Ostróda',
  opole: 'Opole',
  'zielona gora': 'Zielona Góra',
  'gorzow wielkopolski': 'Gorzów Wielkopolski',
  'jelenia gora': 'Jelenia Góra',
  walbrzych: 'Wałbrzych',
  legnica: 'Legnica',
  kalisz: 'Kalisz',
  konin: 'Konin',
  plock: 'Płock',
  tarnow: 'Tarnów',
  'nowy sacz': 'Nowy Sącz',
  zakopane: 'Zakopane',
  slupsk: 'Słupsk',
  suwalki: 'Suwałki',
  lomza: 'Łomża',
  zamosc: 'Zamość',
  chelm: 'Chełm',
  siedlce: 'Siedlce',
  pila: 'Piła',
  jarocin: 'Jarocin',
};

/** Nazwa miasta do pokazania. Nieznane miasto - tak, jak przyszlo z serwera. */
export function nazwaMiasta(klucz, nazwaZSerwera, jezyk) {
  if (klucz === 'warsaw' && String(jezyk).startsWith('en')) {
    return 'Warsaw';
  }
  return MIASTA[klucz] ?? nazwaZSerwera ?? '';
}

/**
 * Wykonawcy, ktorych NIE ma juz w nazwie wydarzenia.
 *
 * Ticketmaster czesto wpisuje caly sklad w nazwe ("Nachtmahr | Special
 * guests: Cygnosic, Bagger 258"). Powtarzanie tych samych nazwisk wiersz
 * nizej tylko zabieraloby miejsce na telefonie.
 */
export function wykonawcySpozaNazwy(nazwa, wykonawcy) {
  const wNazwie = String(nazwa).toLocaleLowerCase();
  return (wykonawcy ?? []).filter((w) => !wNazwie.includes(String(w).toLocaleLowerCase()));
}

/** Adres do mapy - na Androidzie otwiera aplikacje Map Google. */
export function adresMapy(wydarzenie) {
  const czesci = [wydarzenie.venueName, wydarzenie.address, wydarzenie.city].filter(Boolean);
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(czesci.join(', '))}`;
}

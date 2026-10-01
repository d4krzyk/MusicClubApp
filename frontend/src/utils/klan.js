/** Reakcje na wiadomosci czatu klanu, w stalej kolejnosci - taka sama dla wszystkich (serwer trzyma nazwy, nie znaki). */
export const EMOJI = {
  THUMBS_UP: '👍',
  HEART: '❤️',
  LAUGH: '😂',
  FIRE: '🔥',
  MUSIC: '🎵',
  WOW: '😮',
};

/** Zdarzenie, po ktorym menu (licznik nieprzeczytanych w klanie) dopytuje serwer od razu. */
export const ODSWIEZ_LICZNIK = 'mc-klan-odswiez';

/** Kolor nowego klanu, dopoki jego czlonkowie nie zaglosuja (to samo co ClanColor.DEFAULT na serwerze). */
export const DOMYSLNY_KOLOR_KLANU = '#7c3aed';

/** Ile gatunkow klan moze o sobie podac i ile znakow ma haslo i miasto - te same limity co na serwerze. */
export const MAKS_GATUNKOW = 3;
export const MAKS_HASLO = 80;
export const MAKS_MIASTO = 60;
export const MAKS_GATUNEK = 30;

/** Podpowiedzi do pola gatunkow - mozna wpisac cokolwiek, to tylko skrot. */
export const PODPOWIEDZI_GATUNKOW = [
  'rock', 'pop', 'hip-hop', 'metal', 'jazz', 'electronic', 'indie', 'punk', 'folk', 'classical',
  'reggae', 'r&b', 'techno', 'blues', 'country', 'soul', 'funk', 'house', 'ambient', 'k-pop',
];

/** Poziomy aktywnosci czatu (z serwera) w kolejnosci od najspokojniejszego - do paska i sortowania. */
export const POZIOMY_AKTYWNOSCI = ['NONE', 'LOW', 'MEDIUM', 'HIGH'];

/** Etykiety poziomow ranking-u (0-4) - klucze tlumaczen `clans.ranking.levels.N`. */
export const POZIOMY_RANKINGU = [0, 1, 2, 3, 4];

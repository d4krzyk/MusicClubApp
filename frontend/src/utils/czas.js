/**
 * Zamiana czasu utworu miedzy zapisem "1:23" a liczba sekund.
 *
 * <p>Uzytkownik mysli w minutach i sekundach, a backend przechowuje sekundy -
 * te dwie funkcje sa mostkiem miedzy jednym a drugim.</p>
 */

/**
 * "1:23" -&gt; 83. Przyjmuje tez sam zapis sekundowy ("83").
 *
 * @returns liczba sekund albo {@code null}, gdy pole jest puste lub bledne
 */
export function naSekundy(tekst) {
  if (!tekst || !tekst.trim()) {
    return null;
  }

  const czesci = tekst.trim().split(':');

  // Same sekundy, np. "90"
  if (czesci.length === 1) {
    const s = Number(czesci[0]);
    return Number.isFinite(s) && s >= 0 ? Math.floor(s) : null;
  }

  if (czesci.length === 2) {
    const min = Number(czesci[0]);
    const sek = Number(czesci[1]);
    if (!Number.isFinite(min) || !Number.isFinite(sek) || min < 0 || sek < 0 || sek > 59) {
      return null;
    }
    return Math.floor(min) * 60 + Math.floor(sek);
  }

  return null;
}

/** 83 -&gt; "1:23". Sekundy zawsze dwucyfrowe, zeby nie wychodzilo "1:3". */
export function naMinuty(sekundy) {
  if (sekundy == null || !Number.isFinite(sekundy)) {
    return '';
  }
  const min = Math.floor(sekundy / 60);
  const sek = sekundy % 60;
  return `${min}:${String(sek).padStart(2, '0')}`;
}

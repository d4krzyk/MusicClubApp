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
export function toSeconds(text) {
  if (!text || !text.trim()) {
    return null;
  }

  const parts = text.trim().split(':');

  // Same sekundy, np. "90"
  if (parts.length === 1) {
    const s = Number(parts[0]);
    return Number.isFinite(s) && s >= 0 ? Math.floor(s) : null;
  }

  if (parts.length === 2) {
    const minutes = Number(parts[0]);
    const seconds = Number(parts[1]);
    if (!Number.isFinite(minutes) || !Number.isFinite(seconds) || minutes < 0 || seconds < 0 || seconds > 59) {
      return null;
    }
    return Math.floor(minutes) * 60 + Math.floor(seconds);
  }

  return null;
}

/** 83 -&gt; "1:23". Sekundy zawsze dwucyfrowe, zeby nie wychodzilo "1:3". */
export function toMinutes(seconds) {
  if (seconds == null || !Number.isFinite(seconds)) {
    return '';
  }
  const minutes = Math.floor(seconds / 60);
  const restSeconds = seconds % 60;
  return `${minutes}:${String(restSeconds).padStart(2, '0')}`;
}

import { linkiWTekscie, skrocAdres } from '../utils/linki';

/**
 * Tresc wiadomosci na czacie z klikalnymi linkami. Tylko http(s); adres skracany do czytelnej postaci
 * (bez "https://" i "www.", najwyzej 42 znaki), pelny w podpowiedzi. Zadnego HTML-a z tresci - linki sa
 * elementami Reacta, a reszta zwyklym tekstem.
 *
 * {@code ukryjSamLink}: gdy wiadomosc ma podglad nagrania, a cala tresc to sam ten link, tekst nie jest
 * potrzebny - karta z odtwarzaczem mowi wiecej niz dlugi adres.
 */
export default function TekstWiadomosci({ tekst, ukryjSamLink = false }) {
  if (!tekst) {
    return null;
  }
  const czesci = linkiWTekscie(tekst);
  const linki = czesci.filter((c) => c.link);
  const samLink = linki.length === 1 && czesci.every((c) => c.link || !c.tekst.trim());
  if (ukryjSamLink && samLink) {
    return null;
  }
  return (
    <p className="bubble-text">
      {czesci.map((c, i) => (c.link ? (
        // eslint-disable-next-line react/no-array-index-key
        <a key={i} href={c.link} target="_blank" rel="noopener noreferrer nofollow" className="bubble-link" title={c.link}>
          {skrocAdres(c.link)}
        </a>
      ) : c.tekst))}
    </p>
  );
}

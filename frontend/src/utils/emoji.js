/*
 * Czy system umie narysowac dany emotikon.
 *
 * Nowsze emotikony (np. przytulajace sie ludziki z 2020 r.) nie wszedzie sa:
 * Windows 10 i starsze Androidy pokazuja w ich miejscu puste prostokaty. Rysujemy
 * znak na malym plotnie i patrzymy, czy wyszedl w kolorze - kolorowe emotikony
 * daja kolorowe piksele, a "brak znaku" (prostokat, pusto) tylko czarne i szare.
 */

const pamiec = new Map();

export function czyEmojiDziala(znak) {
  if (pamiec.has(znak)) {
    return pamiec.get(znak);
  }
  let wynik;
  try {
    const plotno = document.createElement('canvas');
    plotno.width = 32;
    plotno.height = 32;
    const kontekst = plotno.getContext('2d', { willReadFrequently: true });
    kontekst.textBaseline = 'top';
    kontekst.font = '24px "Apple Color Emoji", "Segoe UI Emoji", "Noto Color Emoji", sans-serif';
    kontekst.fillStyle = '#000';
    kontekst.fillText(znak, 2, 2);
    const piksele = kontekst.getImageData(0, 0, 32, 32).data;
    wynik = false;
    for (let i = 0; i < piksele.length; i += 4) {
      if (piksele[i + 3] > 0 && (piksele[i] !== piksele[i + 1] || piksele[i + 1] !== piksele[i + 2])) {
        wynik = true;
        break;
      }
    }
  } catch {
    // Bez plotna (albo z zablokowanym odczytem pikseli) nie da sie sprawdzic - zostaje zapasowa ikona
    wynik = false;
  }
  pamiec.set(znak, wynik);
  return wynik;
}

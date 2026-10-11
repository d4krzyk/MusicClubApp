// Tekst z obcego serwisu (opisy z Ticketmastera) jako zwykly tekst: akapity zostaja, znaczniki w zdaniu znikaja bez sladu,
// encje rozkodowane PO zdjeciu znacznikow.
const ZNACZNIK = /<\/?[A-Za-z][^>]*>/;
const ENCJE = { amp: '&', lt: '<', gt: '>', quot: '"', apos: "'", nbsp: ' ', ndash: '–', mdash: '—', hellip: '…',
  laquo: '«', raquo: '»', bdquo: '„', ldquo: '“', rdquo: '”', rsquo: '’', lsquo: '‘' };

export function unescapeHtml(t) {
  return t.replace(/&(#x[0-9a-f]+|#\d+|[a-z]+);/gi, (m, e) => {
    if (e[0] === '#') {
      const n = e[1].toLowerCase() === 'x' ? parseInt(e.slice(2), 16) : parseInt(e.slice(1), 10);
      return Number.isFinite(n) && n > 0 && n < 0x110000 ? String.fromCodePoint(n) : m;
    }
    return ENCJE[e.toLowerCase()] ?? m;
  });
}

export function plain(tekst) {
  if (tekst == null || !String(tekst).trim()) return null;
  let t = String(tekst).replace(/\r\n?/g, '\n');
  if (ZNACZNIK.test(t) || t.includes('<!--')) {
    t = t.replace(/<(script|style)\b[^>]*>[\s\S]*?<\/\1\s*>/gi, '')
      .replace(/<!--[\s\S]*?-->/g, '')
      .replace(/<br\s*\/?>/gi, '\n')
      .replace(/<\/?(p|div|h[1-6]|ul|ol|blockquote|table|tr|section|article)\b[^>]*>/gi, '\n\n')
      .replace(/<li\b[^>]*>/gi, '\n')
      .replace(/<\/?[A-Za-z][^>]*>/g, '');
  }
  t = unescapeHtml(t).replaceAll(' ', ' ');
  const gotowy = t.split('\n').map((l) => l.replace(/[ \t\f\v]+/g, ' ').trim()).join('\n').replace(/\n{3,}/g, '\n\n').trim();
  return gotowy || null;
}

const klucz = (t) => t.toLowerCase().replace(/\s+/g, ' ').trim();

// Kilka tekstow o tym samym w jednym: bez pustych i bez powtorzen (tekst zawarty w innym odpada).
export function joined(...teksty) {
  const czyste = teksty.map(plain).filter(Boolean);
  const klucze = czyste.map(klucz);
  const wynik = czyste.filter((_, i) => !czyste.some((__, j) => j !== i
    && (klucze[j] === klucze[i] ? j < i : klucze[j].includes(klucze[i]))));
  return wynik.length ? wynik.join('\n\n') : null;
}

// Najwyzej max znakow - ucinane na granicy slowa, z wielokropkiem.
export function cut(tekst, max) {
  if (tekst == null || tekst.length <= max) return tekst;
  const granica = Math.max(tekst.lastIndexOf(' ', max - 1), tekst.lastIndexOf('\n', max - 1));
  return `${tekst.slice(0, granica > max / 2 ? granica : max - 1).trimEnd()}…`;
}

import { nameKey } from './nameKeys.js';
import { cityByName } from './location.js';
import { normalizeTag } from './genres.js';

// Wydarzenia z twoich kalendarzy (glownie Facebook) bywaja koncertami, ale tez protestami, spacerami, wystawami.
// Tu zgadujemy: czy to koncert, kto gra (z tytulu) i jakie gatunki padaja w tekscie. Ticketmaster i Bandsintown
// sa zawsze muzyczne - tego nie oceniamy.

// Gatunki rozpoznawane w tytule i opisie: [wzorzec (poczatek slowa), jak pokazac, tagi do porownania z Last.fm].
const GATUNKI = [
  [/post[- ]?punk/, 'post punk', ['postpunk']], [/death[- ]?rock/, 'deathrock', ['deathrock']],
  [/goth(?!am)\p{L}*|gotyck\p{L}*/, 'goth', ['goth', 'gothicrock']], [/dark[- ]?wave/, 'darkwave', ['darkwave']],
  [/cold[- ]?wave/, 'coldwave', ['coldwave']], [/minimal[- ]?wave/, 'minimal wave', ['minimalwave']],
  [/new[- ]?wave/, 'new wave', ['newwave']], [/synth[- ]?pop/, 'synthpop', ['synthpop']], [/synth[- ]?wave/, 'synthwave', ['synthwave']],
  [/shoegaze/, 'shoegaze', ['shoegaze']], [/dream[- ]?pop/, 'dream pop', ['dreampop']], [/swamp[- ]?punk/, 'swamp punk', ['swamppunk', 'punk']],
  [/punk/, 'punk', ['punk']], [/hard[- ]?core/, 'hardcore', ['hardcore']], [/grunge/, 'grunge', ['grunge']],
  [/(black|death|doom|thrash|heavy|sludge|post|nu)[- ]metal/, 'metal', ['metal']], [/metal(?!ow)\p{L}*/, 'metal', ['metal']],
  [/stoner/, 'stoner', ['stonerrock']], [/psychodel|psychedel|psych[- ]rock/, 'psychodelia', ['psychedelicrock', 'psychedelic']],
  [/rock(?!ef)\p{L}*/, 'rock', ['rock']], [/indie/, 'indie', ['indie']], [/alternatyw|alternative/, 'alternatywa', ['alternative']],
  [/elektronik|electronic/, 'elektronika', ['electronic']], [/techno/, 'techno', ['techno']], [/house\b/, 'house', ['house']],
  [/drum[ ']?(n|and|&)[ ']?bass|\bdnb\b/, 'drum and bass', ['drumandbass']], [/ambient/, 'ambient', ['ambient']],
  [/\bebm\b/, 'ebm', ['ebm']], [/industrial/, 'industrial', ['industrial']], [/noise/, 'noise', ['noise']],
  [/eksperyment|experimental/, 'eksperymentalna', ['experimental']], [/hip[- ]?hop/, 'hip-hop', ['hiphop']],
  [/\brap\b|\braper/, 'rap', ['rap']], [/\btrap\b/, 'trap', ['trap']], [/jazz/, 'jazz', ['jazz']], [/blues/, 'blues', ['blues']],
  [/\bfolk/, 'folk', ['folk']], [/reggae/, 'reggae', ['reggae']], [/\bska\b/, 'ska', ['ska']], [/\bdub\b/, 'dub', ['dub']],
  [/disco(?!\s?polo)/, 'disco', ['disco']], [/disco\s?polo/, 'disco polo', ['discopolo']], [/\bfunk/, 'funk', ['funk']],
  [/\bsoul\b/, 'soul', ['soul']], [/r&b|\brnb\b/, 'r&b', ['rnb']], [/\bpop\b/, 'pop', ['pop']], [/klasyczn|classical/, 'klasyczna', ['classical']],
  [/singer[- ]songwriter/, 'singer-songwriter', ['singersongwriter']], [/emo\b/, 'emo', ['emo']], [/\bmath[- ]?rock/, 'math rock', ['mathrock']],
];

// Slowa, ktore mowia "to koncert" (+3) i ktore mowia "to cos innego" (-4). Poczatki slow, male litery.
const MUZYCZNE = [/\bkoncert/, /\bconcert/, /\bgig\b/, /\blive\b/, /\bsupport\b/, /\bline[- ]?up/, /\bdj\b/, /\bdj[- ]?set/, /\bwystąpi/,
  /\bzagra(ją|)\b/, /\bzagrają/, /\bgrają\b/, /\btrasa\b/, /\btour\b/, /\bpremier\p{L}* (płyty|albumu|ep)/, /\balbum/, /\bpłyt/,
  /\bjam session/, /\bopen mic/, /\brave\b/, /\bshowcase/, /\bakustyczn/, /\bzespół\b/, /\bzespołu\b/, /\bband\b/, /\bsetlist/,
  /\bbandcamp\.com/, /\bsoundcloud\.com/, /\bopen\.spotify\.com/, /\bmusic\.apple\.com/, /\bfestiwal muzyczn/, /\bmusic festival/,
  /\bdo tańca\b|\bpotańc/, /\bsoundsystem/, /\bnowej płycie/];
const NIEMUZYCZNE = [/\bprotest/, /\bmanifestacj/, /\bdemonstracj/, /\bstrajk/, /\bpikiet/, /\bmarsz\b/, /\bwiec\b/, /\baborcj/,
  /\bwykład/, /\bwarsztat/, /\bspacer/, /\bbieg\b/, /\bmaraton/, /\bgra (miejska|terenowa)/, /\bquiz/, /\bturniej/, /\bkiermasz/,
  /\btargi\b/, /\bkonferencj/, /\bwebinar/, /\bspotkanie autorskie/, /\bwernisaż/, /\bwystaw[ay]\b/, /\bspektakl/, /\bteatr/,
  /\bkabaret/, /\bstand[- ]?up/, /\bseans/, /\bpokaz filmu/, /\bkino\b/, /\bjoga/, /\byoga/, /\bmedytacj/, /\bzbiórk/, /\bdebat/,
  /\bpanel dyskusyjn/, /\bnawiedzon/, /\bstraszy/, /\bescape room/, /\batrakcj/, /\bmecz\b/, /\bpchli targ/, /\bgiełd/];

// Czesci tytulu, ktore nie sa wykonawcami.
const OGOLNE = new Set(['koncert', 'concert', 'live', 'tour', 'trasa', 'support', 'special guests', 'special guest', 'goście', 'gość',
  'presents', 'prezentuje', 'zaprasza', 'bilety', 'wstęp wolny', 'free', 'premiera', 'release party', 'party', 'impreza', 'noc',
  'wieczór', 'afterparty', 'after party', 'dj set', 'festiwal', 'festival', 'edycja', 'sold out', 'nowy termin', 'zmiana terminu',
  'odwołane', 'przeniesione', 'klub', 'club']);

const male = (t) => String(t ?? '').toLowerCase();

// \b w JavaScripcie zna tylko litery ASCII - "zespół\b" nigdy by nie pasowalo. Granica slowa liczona po literach Unicode.
const B = '(?:(?<![\\p{L}\\p{N}])(?=[\\p{L}\\p{N}])|(?<=[\\p{L}\\p{N}])(?![\\p{L}\\p{N}]))';
const U = (re, poczatek = false) => new RegExp(`${poczatek ? '(?<![\\p{L}\\p{N}])' : ''}(?:${re.source.replaceAll('\\b', B)})`, 'u');
const GAT = GATUNKI.map(([w, nazwa, tagi]) => [U(w, true), nazwa, tagi]);
const GAT_CALE = GATUNKI.map(([w]) => U(new RegExp(w.source), true));
const MUZ = MUZYCZNE.map((r) => U(r));
const NIE = NIEMUZYCZNE.map((r) => U(r));

// Gatunki wymienione w tekscie (bez powtorzen, w kolejnosci listy).
export function textGenres(tekst) {
  const t = ` ${male(tekst)} `;
  const wynik = [];
  const widziane = new Set();
  for (const [re, nazwa, tagi] of GAT) {
    if (re.test(t) && !widziane.has(nazwa)) {
      // "post punk" zawiera "punk" - ogolniejszego nie dokladamy, gdy jest szczegolowy z tym samym slowem
      if (nazwa === 'punk' && wynik.some((g) => /punk/.test(g.name))) continue;
      if (nazwa === 'metal' && widziane.has('metal')) continue;
      if (nazwa === 'rock' && wynik.some((g) => /rock/.test(g.name))) continue;
      widziane.add(nazwa);
      wynik.push({ name: nazwa, tags: tagi });
    }
  }
  return wynik;
}

// Kandydaci na wykonawcow z tytulu: "A (Argentyna), B + C / 13.12.26 / Klub, Poznan" -> [A, B, C].
export function performerCandidates(event) {
  const tytul = String(event.name ?? '');
  const sala = nameKey(event.venueName);
  const czesci = tytul.split(/\s*[,+/|&]\s*|\s+[-–—:]\s+|\s+(?:x|vs\.?|feat\.?|ft\.?|w\/|support:?)\s+|\s*\bsupport:\s*/iu);
  const wynik = [];
  for (let c of czesci) {
    c = c.replace(/\([^)]*\)|\[[^\]]*\]/g, ' ')      // (Argentyna), [PL]
      .replace(/\b\d{1,2}[./]\d{1,2}(?:[./]\d{2,4})?\.?/g, ' ') // 13.12.26, 31.10.
      .replace(/\b\d+\b/g, ' ')
      .replace(/["„”«»'*!]+/g, ' ')
      .replace(/\s+/g, ' ').trim();
    // miasto w czesci ("Ścieżka Duchów BYDGOSZCZ") - wyciete
    c = c.split(' ').filter((s) => !cityByName(s)).join(' ').trim();
    if (c.length < 2 || c.split(' ').length > 5) continue;
    const k = nameKey(c);
    if (!k || OGOLNE.has(c.toLowerCase()) || cityByName(c)) continue;
    if (sala && (k === sala || sala.includes(k) || k.includes(sala))) continue;
    // sama lista gatunkow ("post punk", "goth") to nie wykonawca
    let reszta = c.toLowerCase();
    for (const re of GAT_CALE) reszta = reszta.replace(new RegExp(re.source, 'gu'), ' ');
    if (reszta.replace(/[^\p{L}]/gu, '').length < 3) continue;
    if (!wynik.some((w) => nameKey(w) === k)) wynik.push(c);
    if (wynik.length >= 6) break;
  }
  return wynik;
}

const trwaGodzin = (e) => (e.startUtc && e.endUtc ? (Date.parse(e.endUtc) - Date.parse(e.startUtc)) / 3600e3 : null);

// Czy to koncert: punkty za slowa muzyczne, gatunki, potwierdzonych wykonawcow; minus za slowa niemuzyczne
// i za wydarzenie trwajace ponad dobe (atrakcje, wystawy) - chyba ze to festiwal.
export function classify(event, performerTags = {}) {
  const tekst = male(`${event.name}\n${event.description ?? ''}`);
  const powody = [];
  let punkty = 0;
  const muz = MUZ.filter((r) => r.test(tekst)).length;
  if (muz) { punkty += 3 * Math.min(2, muz); powody.push('slowa'); }
  const gat = textGenres(tekst);
  if (gat.length) { punkty += 2 * Math.min(2, gat.length); powody.push('gatunki'); }
  const potwierdzeni = performerCandidates(event).filter((c) => (performerTags[nameKey(c)] ?? []).length > 0);
  if (potwierdzeni.length) { punkty += 2 * Math.min(2, potwierdzeni.length); powody.push('wykonawcy'); }
  const nie = NIE.filter((r) => r.test(tekst)).length;
  if (nie) { punkty -= 4 * Math.min(2, nie); powody.push('inne'); }
  const h = trwaGodzin(event);
  if (h != null && h > 24 && !/festiwal|festival/.test(tekst)) { punkty -= 3; powody.push('dlugie'); }
  return { music: punkty >= 2, score: punkty, why: powody, performers: potwierdzeni, genres: gat };
}

// Wydarzenie z kalendarza uzupelnione o to, co z niego wyczytalismy. override: 'music' / 'other' od uzytkownika.
export function enrich(event, performerTags = {}, override) {
  if (event.source !== 'ics') return event;
  const k = classify(event, performerTags);
  const music = override === 'music' ? true : override === 'other' ? false : k.music;
  return {
    ...event,
    music,
    musicGuess: k.music,
    performers: event.performers?.length ? event.performers : k.performers.map((name) => ({ id: null, name, links: {} })),
    candidates: performerCandidates(event),
    textGenres: k.genres.map((g) => g.name),
    genreTags: [...new Set(k.genres.flatMap((g) => g.tags))],
  };
}

export { normalizeTag };

// Porownywanie gatunkow z dwoch zrodel: tagow Last.fm i etykiet Ticketmastera ("Hip-Hop/Rap").
const NIE_GATUNKI = new Set([
  'polish', 'polska', 'british', 'american', 'german', 'french', 'swedish', 'norwegian', 'canadian', 'australian',
  'irish', 'italian', 'spanish', 'femalevocalists', 'malevocalists', 'femalevocalist', 'malevocalist', 'singer',
  '50s', '60s', '70s', '80s', '90s', '00s', '2000s', '2010s', '2020s', 'seenlive', 'favorites', 'favourites',
  'love', 'beautiful', 'awesome', 'undefined', 'other', 'music']);

const RODZINY = {
  'hip-hop': ['hiphop', 'rap', 'trap', 'grime', 'drill'],
  metal: ['metal', 'metalcore', 'deathcore', 'grindcore'],
  punk: ['punk', 'hardcore', 'emo', 'screamo'],
  rock: ['rock', 'grunge', 'shoegaze'],
  indie: ['indie', 'alternative', 'altpop', 'lofi'],
  pop: ['pop', 'discopolo', 'schlager'],
  electronic: ['electronic', 'electro', 'techno', 'house', 'trance', 'edm', 'dubstep', 'drumandbass', 'dnb', 'ambient',
    'synth', 'idm', 'breakbeat'],
  jazz: ['jazz', 'swing', 'bebop', 'fusion'],
  classical: ['classical', 'opera', 'baroque', 'orchestral', 'neoclassical', 'chamber', 'symphon', 'piano'],
  blues: ['blues'],
  soul: ['soul', 'rnb', 'funk', 'gospel', 'motown'],
  folk: ['folk', 'country', 'singersongwriter', 'acoustic', 'americana'],
  reggae: ['reggae', 'ska', 'dancehall'],
  latin: ['latin', 'reggaeton', 'salsa', 'bachata', 'tango', 'flamenco'],
};

// "Hip-Hop", "hip hop" i "hiphop" to to samo; "R&B" to "rnb".
export function normalizeTag(tag) {
  if (tag == null) return '';
  return String(tag).toLowerCase().replaceAll('&', 'n').replaceAll('drum and bass', 'drumandbass').replace(/[^a-z0-9]/g, '');
}

// Etykieta rozbita na tagi, bez tych, ktore gatunkiem nie sa. "Hip-Hop/Rap" to dwa tagi.
export function tags(label) {
  const wynik = new Set();
  if (label == null) return wynik;
  for (const czesc of String(label).split('/')) {
    const t = normalizeTag(czesc);
    if (t && !NIE_GATUNKI.has(t)) wynik.add(t);
  }
  return wynik;
}

export function families(tagsOrOne) {
  const wynik = new Set();
  const lista = typeof tagsOrOne === 'string' ? [tagsOrOne] : [...tagsOrOne];
  for (const tag of lista) {
    for (const [rodzina, slowa] of Object.entries(RODZINY)) {
      if (slowa.some((s) => tag.includes(s))) wynik.add(rodzina);
    }
  }
  return wynik;
}

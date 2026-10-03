/*
 * Zrzuty ekranu do README.
 *
 * Prawdziwa przeglądarka (Chromium sterowany Playwrightem) na prawdziwym backendzie i PostgreSQL. Dane
 * demonstracyjne zakładamy przez zwykłe API aplikacji; tylko to, czego API nie umie (ulubieni artyści bez
 * Deezera, koncerty bez Ticketmastera, daty „sprzed godziny"), idzie wprost SQL-em.
 *
 * Wszystko jest fikcyjne: osoby, zespoły, kluby i koncerty. Zdjęcia w postach, plakaty koncertów i „GIF-y"
 * to obrazki rysowane na płótnie przeglądarki - chodzi o układ strony, a nie o ilustracje.
 *
 * Wymaga SWIEZEJ bazy (skrypt rejestruje konta) i backendu uruchomionego tak, żeby GIF-y działały na udawanym
 * dostawcy, który ten skrypt sam stawia na porcie 9099:
 *
 *   GIF_PROVIDER=klipy GIF_API_KEY=demo \
 *   java -jar target/musicclubapp-0.0.1-SNAPSHOT.jar \
 *        --app.gifs.klipy.base-url=http://127.0.0.1:9099/api/v1 --app.gifs.searches-per-minute=200
 *
 * Uruchomienie (z korzenia repozytorium; domyślnie frontend z docker-compose na :3000, a SQL przez kontener db):
 *
 *   ZRZUTY_URL=http://localhost:3000 node docs/zrzuty/zrzuty-do-readme.mjs
 *
 * Zmienne: ZRZUTY_URL (adres aplikacji), ZRZUTY_PSQL (polecenie psql do bazy aplikacji; domyślnie
 * `docker compose exec -T db psql -U musicclub -At -d musicclub`), ZRZUTY_OUT (katalog na pliki),
 * ZRZUTY_ADMIN_HASLO (hasło konta `admin`, domyślnie admin12345), ZRZUTY_PLAYWRIGHT (moduł Playwrighta),
 * CHROMIUM (ścieżka do przeglądarki, gdy Playwright nie ma własnej).
 */
import { execSync } from 'node:child_process';
import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const { chromium, request } = await import(process.env.ZRZUTY_PLAYWRIGHT ?? 'playwright');

const BASE = process.env.ZRZUTY_URL ?? 'http://localhost:3000';
const PSQL = process.env.ZRZUTY_PSQL ?? 'docker compose exec -T db psql -U musicclub -At -d musicclub';
const OUT = process.env.ZRZUTY_OUT ?? path.dirname(fileURLToPath(import.meta.url));
const PORT_DOSTAWCY = 9099;
const HASLO = 'Muzyka12345';
const HASLO_ADMINA = process.env.ZRZUTY_ADMIN_HASLO ?? 'admin12345';
const sql = (q) => execSync(PSQL, { input: q }).toString().trim();

/* ------------------------------------------------------------------------------------------------ */
/*  Obrazki rysowane na płótnie przeglądarki                                                          */
/* ------------------------------------------------------------------------------------------------ */

const przegladarka = await chromium.launch(process.env.CHROMIUM ? { executablePath: process.env.CHROMIUM } : {});
const plotno = await (await przegladarka.newContext()).newPage();

/** Gradient z kółkami i podpisem; zwraca bajty JPEG albo PNG. */
async function rysuj(szer, wys, odcien, podpis, format = 'image/jpeg') {
  const base64 = await plotno.evaluate(([w, h, hue, tekst, mime]) => {
    const c = document.createElement('canvas');
    c.width = w; c.height = h;
    const g = c.getContext('2d');
    const t = g.createLinearGradient(0, 0, w, h);
    t.addColorStop(0, `hsl(${hue}, 78%, 62%)`);
    t.addColorStop(1, `hsl(${(hue + 55) % 360}, 80%, 48%)`);
    g.fillStyle = t; g.fillRect(0, 0, w, h);
    g.globalAlpha = 0.22;
    g.fillStyle = '#fff';
    for (let i = 0; i < 6; i += 1) {
      g.beginPath();
      g.arc(((i * 0.23 + 0.1) % 1) * w, ((i * 0.37 + 0.2) % 1) * h, (0.08 + (i % 3) * 0.07) * Math.min(w, h), 0, 7);
      g.fill();
    }
    g.globalAlpha = 1;
    g.fillStyle = 'rgba(0,0,0,.55)';
    let rozmiar = Math.round(Math.min(w, h) / 9);
    g.font = `700 ${rozmiar}px sans-serif`;
    while (g.measureText(tekst).width > w * 0.88 && rozmiar > 10) {
      rozmiar -= 2;
      g.font = `700 ${rozmiar}px sans-serif`;
    }
    g.textAlign = 'center';
    g.fillText(tekst, w / 2, h * 0.9);
    return c.toDataURL(mime, 0.82).split(',')[1];
  }, [szer, wys, odcien, podpis, format]);
  return Buffer.from(base64, 'base64');
}

const ZDJECIA = {
  koncert: await rysuj(900, 900, 285, 'Nocne Pociągi na żywo'),
  sala: await rysuj(900, 900, 20, 'Próba przed sobotą'),
  winyl: await rysuj(900, 900, 190, 'Zdobycz z giełdy'),
  plakat1: await rysuj(900, 500, 285, ''),
  plakat2: await rysuj(900, 500, 160, ''),
  plakat3: await rysuj(900, 500, 35, ''),
  plakat4: await rysuj(900, 500, 330, ''),
  plakat5: await rysuj(900, 500, 215, ''),
};

/* „GIF-y" dostawcy: te same obrazki w dwóch rozmiarach (PNG pod adresem .gif - przeglądarka rozpozna) */
const GIFY = [
  [1, 'Kot na pianinie', 320, 180, 285], [2, 'Pies na gitarze', 300, 300, 200], [3, 'Taniec', 320, 240, 140],
  [4, 'Koncert', 360, 150, 35], [5, 'Winyl', 240, 320, 330], [6, 'Mikrofon', 320, 200, 20],
];
const pliki = new Map();
for (const [id, tytul, w, h, odcien] of GIFY) {
  pliki.set(`/img/${id}-md.gif`, await rysuj(w, h, odcien, tytul, 'image/png'));
  pliki.set(`/img/${id}-sm.gif`, await rysuj(Math.round(w * 0.62), Math.round(h * 0.62), odcien, tytul, 'image/png'));
}
for (const [nazwa, bajty] of Object.entries(ZDJECIA)) {
  pliki.set(`/photo/${nazwa}.jpg`, bajty);
}

/* Udawany KLIPY: /api/v1/{klucz}/gifs/search|trending + pliki powyżej */
const dostawca = http.createServer((req, res) => {
  const u = new URL(req.url, `http://127.0.0.1:${PORT_DOSTAWCY}`);
  if (pliki.has(u.pathname)) {
    res.writeHead(200, { 'content-type': u.pathname.endsWith('.jpg') ? 'image/jpeg' : 'image/gif' });
    res.end(pliki.get(u.pathname));
    return;
  }
  const m = u.pathname.match(/^\/api\/v1\/[^/]+\/gifs\/(search|trending)$/);
  if (!m) { res.writeHead(404); res.end(); return; }
  const q = (u.searchParams.get('q') || '').toLowerCase();
  const strona = Number(u.searchParams.get('page') || 1);
  const lista = !q ? GIFY : GIFY.filter((x) => x[1].toLowerCase().includes(q));
  const od = (strona - 1) * 4;
  const baza = `http://127.0.0.1:${PORT_DOSTAWCY}/img`;
  res.writeHead(200, { 'content-type': 'application/json' });
  res.end(JSON.stringify({
    result: true,
    data: {
      data: lista.slice(od, od + 4).map(([id, title, w, h]) => ({
        id, title,
        file: {
          md: { gif: { url: `${baza}/${id}-md.gif`, width: w, height: h } },
          sm: { gif: { url: `${baza}/${id}-sm.gif`, width: Math.round(w * 0.62), height: Math.round(h * 0.62) } },
        },
      })),
      current_page: strona, per_page: 4, has_next: od + 4 < lista.length,
    },
  }));
});
await new Promise((ok) => dostawca.listen(PORT_DOSTAWCY, '127.0.0.1', ok));
const zdjecie = (nazwa) => `http://127.0.0.1:${PORT_DOSTAWCY}/photo/${nazwa}.jpg`;

/* ------------------------------------------------------------------------------------------------ */
/*  Klient API                                                                                        */
/* ------------------------------------------------------------------------------------------------ */

async function apiUser(login, { admin = false } = {}) {
  const ctx = await request.newContext({ baseURL: BASE });
  const token = async () => (await ctx.storageState()).cookies.find((c) => c.name === 'XSRF-TOKEN')?.value;
  await ctx.get('/api/auth/me');
  if (!admin) {
    const r = await ctx.post('/api/auth/register', {
      headers: { 'X-XSRF-TOKEN': await token() },
      data: { username: login, email: `${login}@example.com`, password: HASLO, confirmPassword: HASLO, acceptTerms: true },
    });
    if (![200, 201].includes(r.status())) throw new Error(`rejestracja ${login}: ${r.status()} ${await r.text()}`);
  }
  const l = await ctx.post('/api/auth/login', {
    headers: { 'X-XSRF-TOKEN': await token() },
    data: { username: login, password: admin ? HASLO_ADMINA : HASLO, rememberMe: false },
  });
  if (!l.ok()) throw new Error(`logowanie ${login}: ${l.status()}`);

  const wyslij = async (metoda, adres, data) => {
    const r = await ctx[metoda](`/api${adres}`, { headers: { 'X-XSRF-TOKEN': await token() }, data });
    let json = null;
    try { json = await r.json(); } catch { /* puste */ }
    if (r.status() >= 400) throw new Error(`${metoda} ${adres} -> ${r.status()} ${JSON.stringify(json)}`);
    return json;
  };
  return {
    login,
    get: async (a) => { const r = await ctx.get(`/api${a}`); return r.json(); },
    post: (a, d) => wyslij('post', a, d),
    put: (a, d) => wyslij('put', a, d),
    stan: () => ctx.storageState(),
    async napisz(tresc, { foto = null, eventId = null, clanId = null } = {}) {
      const multipart = { post: { name: 'post.json', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify({ content: tresc, visibility: 'PUBLIC', eventId, clanId })) } };
      if (foto) multipart.images = { name: 'foto.jpg', mimeType: 'image/jpeg', buffer: ZDJECIA[foto] };
      const r = await ctx.post('/api/posts', { headers: { 'X-XSRF-TOKEN': await token() }, multipart });
      if (r.status() !== 201) throw new Error(`post: ${r.status()} ${await r.text()}`);
      return (await r.json()).id;
    },
    async gif(fraza = 'taniec') {
      const strona = await this.get(`/gifs/search?q=${encodeURIComponent(fraza)}`);
      return strona.items[0].token;
    },
  };
}

async function znajomi(a, b) {
  await a.post('/friends/requests', { username: b.login });
  const zaproszenie = (await b.get('/friends/requests')).incoming.find((z) => z.username === a.login);
  await b.post(`/friends/requests/${zaproszenie.id}/accept`);
}

const wiek = (tabela, id, kolumna, interwal) => sql(`UPDATE ${tabela} SET ${kolumna} = now() - interval '${interwal}' WHERE id = ${id};`);

/* ------------------------------------------------------------------------------------------------ */
/*  Dane demonstracyjne                                                                               */
/* ------------------------------------------------------------------------------------------------ */

console.log('Zakładam konta...');
const MIASTA = {
  ola: 'Poznań', marek: 'Poznań', basia: 'Gdańsk', kuba: 'Poznań', zosia: 'Luboń', tomek: 'Kraków', ewa: 'Wrocław', igor: 'Poznań',
};
const u = {};
for (const [login, miasto] of Object.entries(MIASTA)) {
  u[login] = await apiUser(login);
  await u[login].put('/profile/location', { city: miasto });
}
const dodatkowi = [];
for (let i = 1; i <= 8; i += 1) dodatkowi.push(await apiUser(`fan${i}`));
const admin = await apiUser('admin', { admin: true });

await znajomi(u.marek, u.ola);
await znajomi(u.basia, u.ola);
await znajomi(u.marek, u.kuba);

/* Ulubieni artyści - bez Deezera, wprost w bazie */
const ULUBIENI = {
  ola: ['Khruangbin', 'Tame Impala', 'Massive Attack', 'Nocne Pociągi', 'Boards of Canada', 'Fisz Emade Tworzywo', 'Trio Parasol', 'DJ Bursztyn', 'Latarnia'],
  igor: ['Khruangbin', 'Tame Impala', 'Massive Attack', 'Radiohead'],
  tomek: ['Khruangbin', 'Boards of Canada', 'Kaliber 44'],
  kuba: ['Tame Impala', 'Nocne Pociągi', 'Daft Punk'],
  marek: ['Nocne Pociągi', 'Massive Attack', 'Daft Punk'],
  basia: ['Radiohead', 'Daft Punk'],
  ewa: ['Radiohead', 'Kaliber 44'],
};
const wszyscyArtysci = [...new Set(Object.values(ULUBIENI).flat())];
sql(wszyscyArtysci.map((n, i) => `INSERT INTO artists (external_id, name) VALUES ('demo-${i}', '${n.replace(/'/g, "''")}');`).join('\n'));
for (const [login, lista] of Object.entries(ULUBIENI)) {
  sql(`INSERT INTO user_favorite_artists (user_id, artist_id)
       SELECT us.id, ar.id FROM users us, artists ar WHERE us.username = '${login}' AND ar.name IN (${lista.map((n) => `'${n.replace(/'/g, "''")}'`).join(',')});`);
}

/* Koncerty (jak z importu Ticketmastera, ale fikcyjne) */
console.log('Zakładam koncerty...');
const KONCERTY = [
  ['Nocne Pociągi — trasa „Ostatni peron”', 'Klub Zielony Gramofon', 'Poznań', 'poznan', 52.4064, 16.9252, 9, '20:00', 'Rock alternatywny', 'plakat1', ['Nocne Pociągi', 'Kasztany']],
  ['Wieczór jazzowy: Trio Parasol', 'Piwnica Pod Kasztanem', 'Poznań', 'poznan', 52.4090, 16.9300, 4, '19:30', 'Jazz', 'plakat2', ['Trio Parasol']],
  ['Festiwal Nad Wartą', 'Plaża Miejska', 'Swarzędz', 'swarzedz', 52.4120, 17.0750, 16, '16:00', 'Festiwal', 'plakat3', ['Fisz Emade Tworzywo', 'Orkiestra Dęta Luboń']],
  ['Złote Lata — wieczór z winylem', 'Klub Pod Zegarem', 'Kraków', 'krakow', 50.0647, 19.9450, 6, '20:30', 'Soul / funk', 'plakat4', ['DJ Bursztyn']],
  ['Morze Dźwięków — dzień pierwszy', 'Hala Zatoka', 'Gdańsk', 'gdansk', 54.3520, 18.6466, 22, '18:00', 'Elektronika', 'plakat5', ['Massive Attack tribute', 'Latarnia']],
  ['Elektryczny Ogród', 'Stara Fabryka', 'Wrocław', 'wroclaw', 51.1079, 17.0385, 11, '21:00', 'Elektronika', 'plakat1', ['Boards of Canada tribute']],
];
KONCERTY.forEach(([nazwa, klub, miasto, klucz, lat, lon, dni, godz, gatunek, plakat, sklad], i) => {
  sql(`INSERT INTO music_events (external_id, name, series_key, start_date, start_time, status, city, city_key, venue_name,
         address, latitude, longitude, genre, image_url, thumb_url, ticket_url, country_code, last_seen_at)
       VALUES ('demo-event-${i}', '${nazwa}', 'demo-seria-${i}', current_date + ${dni}, '${godz}', 'SCHEDULED', '${miasto}', '${klucz}',
         '${klub}', 'ul. Przykładowa ${i + 1}', ${lat}, ${lon}, '${gatunek}', '${zdjecie(plakat)}', '${zdjecie(plakat)}',
         'https://example.com/bilety/${i}', 'PL', now());`);
  const id = sql(`SELECT id FROM music_events WHERE external_id = 'demo-event-${i}';`);
  sklad.forEach((nazwaWykonawcy, k) => sql(`INSERT INTO music_event_performers (event_id, performer_order, name) VALUES (${id}, ${k}, '${nazwaWykonawcy}');`));
});
const idKoncertu = (i) => Number(sql(`SELECT id FROM music_events WHERE external_id = 'demo-event-${i}';`));
await u.marek.put(`/events/${idKoncertu(0)}/participation`, { status: 'GOING' });
await u.kuba.put(`/events/${idKoncertu(0)}/participation`, { status: 'GOING' });
await u.basia.put(`/events/${idKoncertu(0)}/participation`, { status: 'INTERESTED' });
await u.ola.put(`/events/${idKoncertu(1)}/participation`, { status: 'INTERESTED' });
await u.ola.put(`/events/${idKoncertu(0)}/participation`, { status: 'GOING' });

/* Posty */
console.log('Piszę posty...');
const p = {};
p.marek = await u.marek.napisz('Wczoraj w Zielonym Gramofonie grali Nocne Pociągi — dawno nie słyszałem takiego basu. Ktoś na sobotnie wydanie „Ostatniego peronu”? Szukam ekipy, mam bilety dla trzech osób.', { eventId: idKoncertu(0) });
p.basia = await u.basia.napisz('Środowe jam sessions w Gdańsku wracają od przyszłego tygodnia. Przynieście własne instrumenty — pianino jest na miejscu.');
p.ola = await u.ola.napisz('Układam jesienną playlistę: Khruangbin, Tame Impala i coś polskiego na zakończenie. Macie coś, co koniecznie powinno się tam znaleźć?');
p.zosia = await u.zosia.napisz('Pierwszy raz na koncercie plenerowym nad Wartą. Zimno, ale warto było — polecam wszystkim z okolicy Poznania.');
p.kuba = await u.kuba.napisz('Szukam basisty do zespołu w Poznaniu — gramy coś pomiędzy Tame Impala a polskim post-rockiem. Próby w czwartki, odezwijcie się.');
p.tomek = await u.tomek.napisz('Nowa płyta Khruangbin brzmi jak popołudnie w sierpniu. Polecam do słuchania przy gotowaniu i długich jazdach.');
p.ewa = await u.ewa.napisz('Wygrzebałam na giełdzie winylowej pierwsze tłoczenie Kalibra 44. Stan idealny, nie wierzę we własne szczęście!', { foto: 'winyl' });
p.igor = await u.igor.napisz('Czy ktoś jeszcze słucha Massive Attack na deszczowe wieczory w Poznaniu? Pytam jako człowiek z za dużą kolekcją winyli.');

wiek('posts', p.marek, 'created_at', '3 hours');
wiek('posts', p.basia, 'created_at', '1 day');
wiek('posts', p.ola, 'created_at', '5 hours');
wiek('posts', p.zosia, 'created_at', '90 minutes');
wiek('posts', p.kuba, 'created_at', '6 hours');
wiek('posts', p.tomek, 'created_at', '8 hours');
wiek('posts', p.ewa, 'created_at', '2 days');
wiek('posts', p.igor, 'created_at', '10 hours');

/* Reakcje: post Ewy robi się „popularny", reszta dostaje po kilka */
const TYPY = ['FIRE', 'FIRE', 'FIRE', 'MID', 'FIRE', 'MEH', 'FIRE', 'FIRE'];
for (const [i, fan] of dodatkowi.entries()) await fan.put(`/posts/${p.ewa}/reaction`, { type: TYPY[i] });
for (const [i, fan] of dodatkowi.slice(0, 5).entries()) await fan.put(`/posts/${p.marek}/reaction`, { type: TYPY[i] });
for (const fan of dodatkowi.slice(0, 3)) await fan.put(`/posts/${p.ola}/reaction`, { type: 'FIRE' });
await u.ola.put(`/posts/${p.marek}/reaction`, { type: 'FIRE' });
await u.marek.put(`/posts/${p.ola}/reaction`, { type: 'FIRE' });

/* Komentarze: wątek pod postem Marka (z oznaczeniem, odpowiedzią i GIF-em) i pod postem Oli */
console.log('Komentarze i rozmowy...');
const k1 = (await u.basia.post(`/posts/${p.marek}/comments`, { content: 'Byłam na poprzedniej trasie — to był najlepszy koncert roku! @marek, daj znać, czy zostały jeszcze bilety.' })).id;
await u.marek.post(`/posts/${p.marek}/comments`, { content: '@basia zostały trzy, bierzemy wszyscy!', parentId: k1 });
await u.kuba.post(`/posts/${p.marek}/comments`, { content: 'Ja się piszę, a basista z naszego zespołu też chętnie dołączy.', parentId: k1 });
const k2 = (await u.kuba.post(`/posts/${p.marek}/comments`, { content: '', gif: await u.kuba.gif('Taniec') })).id;
const k3 = (await u.ola.post(`/posts/${p.marek}/comments`, { content: 'Zarezerwowałam wieczór. @marek, kto zabiera aparat?', gif: await u.ola.gif('Koncert') })).id;
await u.marek.post(`/posts/${p.marek}/comments`, { content: '@ola ja, tylko niech ktoś mi przypomni o naładowaniu baterii.', parentId: k3 });

const k4 = (await u.igor.post(`/posts/${p.ola}/comments`, { content: 'Dorzuć „Teardrop” — na jesień idealne. @ola, podeślij potem link do playlisty!' })).id;
await u.ola.post(`/posts/${p.ola}/comments`, { content: '@igor dodane! Jakbyś miał jeszcze coś w tym klimacie, wrzucaj śmiało.', parentId: k4 });
await u.tomek.post(`/posts/${p.ola}/comments`, { content: 'Mogę polecić coś z nowej płyty Khruangbin, @ola.' });
await u.marek.post(`/posts/${p.ewa}/comments`, { content: 'Zazdroszczę! Kaliber na winylu to marzenie.' });
await u.kuba.post(`/posts/${p.ewa}/comments`, { content: 'Gratulacje, stan idealny to rzadkość.' });
await u.basia.post(`/posts/${p.ewa}/comments`, { content: 'Podeślesz zdjęcie okładki z bliska?' });

/* Rozmowy */
await u.marek.post('/messages/with/ola', { content: 'Cześć! Widziałaś, że Nocne Pociągi grają w sobotę?' });
await u.ola.post('/messages/with/marek', { content: 'Tak, już się zapisałam. Bierzesz aparat?' });
await u.marek.post('/messages/with/ola', { content: 'Jasne. Do zobaczenia pod klubem o 19:30!' });
await u.marek.post('/messages/with/ola', { gif: await u.marek.gif('Koncert') });
await u.basia.post('/messages/with/ola', { content: 'Dzięki za polecenie tej płyty — leci w kółko.' });

/* Klany */
console.log('Zakładam klany...');
const klan = await u.marek.post('/clans', {
  name: 'Winylowa Ekipa', tag: 'WNL', description: 'Poznańska ekipa kolekcjonerów winyli, koncertowiczów i muzyków od jazzu po alternatywę.',
  motto: 'Igła w rowku, ucho na scenie', city: 'Poznań', genres: ['jazz', 'soul', 'alternatywa'], joinPolicy: 'REQUESTS', listed: true,
});
const IDK = klan.id;
for (const login of ['ola', 'kuba', 'zosia']) {
  await u.marek.post(`/clans/${IDK}/invitations`, { username: login });
  const zapr = (await u[login].get('/clans/mine')).invitations;
  await u[login].post(`/clans/invitations/${zapr[0].id}/accept`);
}
await u.marek.put(`/clans/${IDK}`, {
  announcement: 'W sobotę idziemy razem na Nocne Pociągi — zbiórka o 19:30 przed Zielonym Gramofonem.',
  rules: 'Szanujemy się nawzajem. Spoilery z płyt oznaczamy, a handel winylami zostawiamy na giełdę.',
});
await u.marek.put(`/clans/${IDK}/color`, { color: 'VIOLET' });
await u.ola.put(`/clans/${IDK}/color`, { color: 'VIOLET' });
await u.kuba.put(`/clans/${IDK}/color`, { color: 'INDIGO' });

const DRUGIE = [
  ['Basowe Brzegi', 'BAS', 'Gdańsk', ['elektronika', 'dub'], 'Dla tych, którzy czują bas w klatce piersiowej', u.basia, 'REQUESTS'],
  ['Krakowska Scena Garażowa', 'KSG', 'Kraków', ['rock', 'punk'], 'Głośno, szybko i bez kompromisów', u.tomek, 'REQUESTS'],
  ['Elektroniczny Ogród', 'ELO', 'Wrocław', ['elektronika', 'ambient'], 'Syntezatory, kable i wiele cierpliwości', u.ewa, 'INVITE_ONLY'],
  ['Poznań Alternatywnie', 'PAL', 'Poznań', ['alternatywa', 'indie'], 'Co tydzień nowa płyta do posłuchania', u.igor, 'REQUESTS'],
];
for (const [nazwa, tag, miasto, gatunki, motto, zalozyciel, polityka] of DRUGIE) {
  await zalozyciel.post('/clans', { name: nazwa, tag, motto, city: miasto, genres: gatunki, joinPolicy: polityka, listed: true });
}

const czat = [
  [u.marek, 'Dzień dobry, winylomani! Ktoś po drodze na giełdę w niedzielę?'],
  [u.kuba, 'Ja będę od rana — szukam czegoś z lat 70.'],
  [u.zosia, 'Zabieram aparat, więc będą zdjęcia z łowów.'],
  [u.ola, 'Rezerwuję dla siebie wszystko, co ma w nazwie „Khruangbin” 😄'],
  [u.marek, 'Przypominam: w sobotę Nocne Pociągi, zbiórka o 19:30.'],
  [u.kuba, 'Będę! Biorę też kolegę basistę.'],
  [u.ola, 'Super, do zobaczenia!'],
];
for (const [kto, tresc] of czat) await kto.post(`/clans/${IDK}/chat`, { content: tresc });

await u.marek.post(`/clans/${IDK}/polls`, { question: 'Gdzie spotkamy się po koncercie?', options: ['Pod klubem', 'W Piwnicy Pod Kasztanem', 'Idziemy na winyle do Marka'], days: 7 });
const ankieta = (await u.marek.get(`/clans/${IDK}/polls`))[0];
await u.ola.put(`/clans/${IDK}/polls/${ankieta.id}/vote`, { optionId: ankieta.options[1].id });
await u.kuba.put(`/clans/${IDK}/polls/${ankieta.id}/vote`, { optionId: ankieta.options[1].id });
await u.zosia.put(`/clans/${IDK}/polls/${ankieta.id}/vote`, { optionId: ankieta.options[0].id });
const tytul1 = await u.marek.post(`/clans/${IDK}/titles`, { name: 'Kolekcjoner winyli', color: 'PINK', mode: 'MANUAL' });
await u.marek.post(`/clans/${IDK}/titles`, { name: 'Gaduła', color: 'TEAL', mode: 'AUTO', metric: 'MESSAGES', threshold: 2 });
await u.marek.post(`/clans/${IDK}/titles`, { name: 'Łowca okazji', color: 'AMBER', mode: 'SELF' });
await u.marek.put(`/clans/${IDK}/members/ola/titles/${tytul1.id}`);

/* Powiadomienia dla Oli - komentarze pod jej postem i odpowiedź na jej komentarz zostały już wysłane wyżej */

/* Zgłoszenie do panelu administratora */
const trollKomentarz = (await dodatkowi[0].post(`/posts/${p.ewa}/comments`, { content: 'Kup moje płyty taniej na tanieplyty.example — klikaj teraz!!!' })).id;
await u.basia.post('/reports/on/fan1', { reason: 'SPAM', context: 'COMMENT', commentId: trollKomentarz, description: 'Komentarz reklamowy z linkiem do sklepu, wklejony pod cudzym postem.' });

/* Wszystko, co powstało przed chwilą, ma wyglądać jak zwykły dzień w aplikacji */
sql(`UPDATE comments SET created_at = created_at - interval '2 hours';
     UPDATE messages SET created_at = created_at - interval '50 minutes';
     UPDATE clan_messages SET created_at = created_at - interval '3 hours';
     UPDATE notifications SET created_at = created_at - interval '40 minutes';
     UPDATE reports SET created_at = created_at - interval '80 minutes';`);
await admin.post('/profile/terms/accept');

/* ------------------------------------------------------------------------------------------------ */
/*  Zrzuty                                                                                            */
/* ------------------------------------------------------------------------------------------------ */

console.log('Robię zrzuty...');
const zapis = async (strona, nazwa, opcje = {}) => {
  await strona.screenshot({ path: path.join(OUT, `${nazwa}.jpg`), type: 'jpeg', quality: 80, ...opcje });
  console.log(`  ${nazwa}.jpg`);
};
const kontekst = async (uzytkownik, { szer = 1280, wys = 860, ciemny = false, telefon = false, caly_kraj = false } = {}) => {
  const ctx = await przegladarka.newContext({
    viewport: { width: szer, height: wys }, deviceScaleFactor: telefon ? 2 : 1, colorScheme: ciemny ? 'dark' : 'light',
    storageState: await uzytkownik.stan(), locale: 'pl-PL',
  });
  if (caly_kraj) {
    // zasięg „w okolicy": 0 = cały kraj (wspólny dla wydarzeń i klanów)
    await ctx.addInitScript(() => localStorage.setItem('zasieg', '0'));
  }
  return ctx;
};
const otworz = async (ctx, adres, czekaj = '.page-title, h1') => {
  const strona = await ctx.newPage();
  strona.setDefaultTimeout(10000);
  await strona.goto(`${BASE}${adres}`);
  await strona.waitForSelector(czekaj);
  await strona.waitForLoadState('networkidle');
  await strona.waitForTimeout(1100);
  return strona;
};

/* 1. Tablica - ciemna i jasna, komputer */
let ctx = await kontekst(u.ola, { ciemny: true, wys: 1420 });
let s = await otworz(ctx, '/', '.post-card .post-content');
await zapis(s, 'tablica-ciemna');
await ctx.close();

ctx = await kontekst(u.ola, { wys: 900 });
s = await otworz(ctx, '/', '.post-card .post-content');
const granica = await s.locator('.feed-divider').evaluate((el) => el.getBoundingClientRect().top + window.scrollY);
await s.evaluate((y) => window.scrollTo(0, y - 330), granica);
await s.waitForTimeout(800);
await zapis(s, 'tablica-jasna');
await ctx.close();

/* 2. Tablica na telefonie */
ctx = await kontekst(u.ola, { szer: 390, wys: 844, telefon: true });
s = await otworz(ctx, '/', '.post-card .post-content');
await zapis(s, 'tablica-telefon');

/* 3. Komentarze i przeglądarka GIF-ów (telefon) */
/* Sekcja komentarzy tuż pod paskiem u góry (pasek jest przyklejony i zasłaniałby tekst posta) */
const doKomentarzy = async (margines) => {
  const y = await s.locator('.post-komentarze-pasek').evaluate((el) => el.getBoundingClientRect().top + window.scrollY);
  await s.evaluate((pozycja) => window.scrollTo(0, pozycja), y - margines);
  await s.waitForTimeout(800);
};
s = await otworz(ctx, `/post/${p.marek}`, '.komentarz');
await doKomentarzy(75);
await zapis(s, 'komentarze-telefon');
await s.evaluate(() => window.scrollTo(0, 0));
await s.getByRole('button', { name: 'Dodaj GIF' }).first().click();
await s.locator('.gif-picker-kafelek').first().waitFor();
await s.waitForLoadState('networkidle');
await doKomentarzy(75);
await zapis(s, 'gify-telefon');
await ctx.close();

/* 4. Wydarzenia */
ctx = await kontekst(u.ola, { wys: 1100, caly_kraj: true });
s = await otworz(ctx, '/wydarzenia', '.wydarzenia-lista');
await zapis(s, 'wydarzenia');

/* 5. Klany: przeglądarka i strona klanu */
s = await otworz(ctx, '/klany', 'h1, .page-title');
await s.waitForTimeout(600);
await zapis(s, 'klany');
s = await otworz(ctx, '/klan', 'h1, .page-title');
await s.waitForTimeout(800);
await zapis(s, 'klan', { fullPage: true });

/* 6. Znajomi (propozycje z okolicy) */
s = await otworz(ctx, '/znajomi', 'h1, .page-title');
await s.waitForTimeout(600);
await zapis(s, 'znajomi', { clip: { x: 200, y: 60, width: 880, height: 600 } });

/* 7. Ustawienia - karta miasta */
s = await otworz(ctx, '/settings', 'h1, .page-title');
const karta = s.locator('.card', { hasText: 'Podaj miasto' }).last();
await karta.scrollIntoViewIfNeeded();
await s.waitForTimeout(500);
await karta.screenshot({ path: path.join(OUT, 'ustawienia-miasto.jpg'), type: 'jpeg', quality: 82 });
console.log('  ustawienia-miasto.jpg');

/* 8. Powiadomienia i okienko reakcji */
s = await otworz(ctx, '/', '.post-card .post-content');
await s.locator('.bell').click();
await s.locator('.bell-panel .bell-item').first().waitFor();
await s.waitForTimeout(500);
await zapis(s, 'powiadomienia', { clip: { x: 640, y: 0, width: 640, height: 640 } });
await s.keyboard.press('Escape');
await s.locator('.bell').click().catch(() => {});

/* 9. Czat z GIF-em i lista rozmów */
s = await otworz(ctx, '/', '.post-card .post-content');
await s.locator('.chat-launcher').click();
await s.locator('.chat-row').first().waitFor();
await s.waitForTimeout(600);
await zapis(s, 'czat-lista', { clip: { x: 886, y: 0, width: 394, height: 860 } });
await s.locator('.chat-row', { hasText: 'marek' }).click();
await s.locator('.bubble .gif-obrazek').waitFor();
await s.waitForLoadState('networkidle');
await s.waitForTimeout(600);
await zapis(s, 'czat-gif', { clip: { x: 886, y: 0, width: 394, height: 860 } });
await ctx.close();

/* 10. Panel administratora */
ctx = await kontekst(admin, { wys: 900 });
s = await otworz(ctx, '/zgloszenia', 'h1, .page-title');
await s.getByRole('button', { name: 'Pokaż dowody' }).first().click();
await s.waitForTimeout(700);
await zapis(s, 'panel-zgloszen');
await ctx.close();

/* 11. Nowe konto: pusty stan */
const nowy = await apiUser('nowy_sluchacz');
ctx = await kontekst(nowy, { wys: 760 });
s = await otworz(ctx, '/znajomi', 'h1, .page-title');
await zapis(s, 'pusty-stan');
await ctx.close();

await przegladarka.close();
dostawca.close();
console.log('Gotowe.');

// Kod uruchamiany przez Androida w tle (Capacitor Background Runner), co kilka godzin - bez widoku aplikacji, w osobnym,
// okrojonym silniku JavaScript (jest fetch, setTimeout, CapacitorKV, CapacitorNotifications; nie ma DOM, URL, Intl).
// Budowany osobno do dist/runners/background.js (scripts/runner.mjs).
//
// Zdarzenia:
//  - "stan": aplikacja przekazuje klucz, gust i to, co juz znasz (po kazdym odswiezeniu w aplikacji),
//  - "sprawdz": cykliczne sprawdzenie (co interval z capacitor.config.json) - nowe koncerty i zmiany w "Biore udzial",
//  - "test": probne powiadomienie z ustawien.
import { fetchCountry } from '../lib/ticketmaster.js';
import { newForYou, goingChanges, messages, testMessage } from '../lib/powiadomienia.js';

const KLUCZ = 'musicclub.stan';
const IKONA = 'ic_stat_musicclub';

function czytaj() {
  try { return JSON.parse(CapacitorKV.get(KLUCZ)?.value ?? 'null'); } catch { return null; }
}
function zapisz(stan) {
  CapacitorKV.set(KLUCZ, JSON.stringify(stan));
}
function pokaz(lista) {
  if (lista.length) CapacitorNotifications.schedule(lista.map((m) => ({ ...m, smallIcon: IKONA, autoCancel: true })));
}
const dzis = () => {
  const d = new Date();
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
};

export async function sprawdz(fetchFn = fetch) {
  const stan = czytaj();
  if (!stan || !stan.tmKey || (!stan.nowe && !stan.zmiany)) return 'wylaczone';
  // tylko 3 miesiace - w tle liczy sie czas (Android daje zwykle ok. 30 s)
  const { events } = await fetchCountry({ key: stan.tmKey, country: stan.country || 'PL', months: Math.min(3, stan.months || 3),
    fetchFn, pauseMs: 0 });
  const nowe = stan.nowe ? newForYou(events, stan, dzis()) : [];
  const zmiany = stan.zmiany ? goingChanges(events, stan.going) : [];
  pokaz(messages(nowe, zmiany, stan.lang));
  // to, o czym juz powiedzielismy, nie wraca w nastepnym sprawdzeniu
  stan.seen = [...new Set([...(stan.seen ?? []), ...nowe.map((r) => r.event.seriesKey)])];
  for (const z of zmiany) stan.going[z.event.id] = { date: z.event.date, time: z.event.time ?? null, status: z.event.status };
  stan.lastCheck = new Date().toISOString();
  stan.lastResult = { nowe: nowe.length, zmiany: zmiany.length };
  zapisz(stan);
  return stan.lastResult;
}

addEventListener('stan', (resolve, reject, args) => {
  try {
    const poprzedni = czytaj();
    // stan od aplikacji; "znane" laczymy, zeby nie powiadamiac drugi raz o czyms, o czym juz bylo powiadomienie
    const seen = [...new Set([...(args.seen ?? []), ...((poprzedni?.seen) ?? [])])].slice(-5000);
    zapisz({ ...args, seen, lastCheck: poprzedni?.lastCheck ?? null, lastResult: poprzedni?.lastResult ?? null });
    resolve({ ok: true });
  } catch (e) {
    reject(String(e?.message ?? e));
  }
});

addEventListener('sprawdz', (resolve) => {
  sprawdz().then((wynik) => resolve(wynik), (e) => {
    console.error(`MusicClub: sprawdzenie w tle nie udalo sie: ${e?.message ?? e}`);
    resolve({ blad: String(e?.kod ?? 'ERR') });
  });
});

addEventListener('test', (resolve) => {
  const stan = czytaj();
  pokaz([testMessage(stan?.lang)]);
  resolve({ ok: true });
});

addEventListener('info', (resolve) => {
  const stan = czytaj();
  resolve({ lastCheck: stan?.lastCheck ?? null, lastResult: stan?.lastResult ?? null });
});

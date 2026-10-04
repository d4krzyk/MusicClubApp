/*
 * Testy geometrii edytora zdjec. Uruchamia je wbudowany w Node test runner:
 *   npm test
 * Bez przegladarki i bez dodatkowych bibliotek - kadr.js to same liczby.
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  UCHWYTY,
  bezZmian,
  dopasujProporcje,
  macierzObrotu,
  najwiekszyKadr,
  minimalnyBok,
  nastepnyObrot,
  obrocKadr,
  obroconeWymiary,
  pelnyKadr,
  przesun,
  wGranicach,
  wPikselach,
  zmienRozmiar,
} from './kadr.js';

const blisko = (a, b, opis) => assert.ok(Math.abs(a - b) < 1e-6, `${opis}: ${a} != ${b}`);

function wObrazie(kadr, W, H, opis = '') {
  const e = 1e-6;
  assert.ok(kadr.x >= -e && kadr.y >= -e, `${opis} wychodzi w lewo/gore: ${JSON.stringify(kadr)}`);
  assert.ok(kadr.x + kadr.w <= W + e, `${opis} wychodzi w prawo: ${JSON.stringify(kadr)} W=${W}`);
  assert.ok(kadr.y + kadr.h <= H + e, `${opis} wychodzi w dol: ${JSON.stringify(kadr)} H=${H}`);
  assert.ok(kadr.w > 0 && kadr.h > 0, `${opis} pusty: ${JSON.stringify(kadr)}`);
  for (const v of Object.values(kadr)) {
    assert.ok(Number.isFinite(v), `${opis} NaN/Infinity: ${JSON.stringify(kadr)}`);
  }
}

/** Prosty, powtarzalny generator liczb (zeby nieudany przypadek dalo sie odtworzyc). */
function losowe(ziarno) {
  let s = ziarno >>> 0;
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0;
    return s / 2 ** 32;
  };
}

const zastosuj = ([a, b, c, d, e, f], x, y) => [a * x + c * y + e, b * x + d * y + f];

test('wymiary po obrocie i kolejne obroty', () => {
  assert.deepEqual(obroconeWymiary(4000, 3000, 0), { w: 4000, h: 3000 });
  assert.deepEqual(obroconeWymiary(4000, 3000, 90), { w: 3000, h: 4000 });
  assert.deepEqual(obroconeWymiary(4000, 3000, 180), { w: 4000, h: 3000 });
  assert.deepEqual(obroconeWymiary(4000, 3000, 270), { w: 3000, h: 4000 });
  assert.equal(nastepnyObrot(0, 1), 90);
  assert.equal(nastepnyObrot(270, 1), 0);
  assert.equal(nastepnyObrot(0, -1), 270);
  assert.equal(nastepnyObrot(90, -1), 0);
});

test('pelny kadr: caly obraz albo najwiekszy wysrodkowany o zadanych proporcjach', () => {
  assert.deepEqual(pelnyKadr(4000, 3000), { x: 0, y: 0, w: 4000, h: 3000 });
  assert.deepEqual(pelnyKadr(4000, 3000, 1), { x: 500, y: 0, w: 3000, h: 3000 });
  assert.deepEqual(pelnyKadr(4000, 3000, 3 / 4), { x: 875, y: 0, w: 2250, h: 3000 });
  const panorama = pelnyKadr(4000, 3000, 3);
  blisko(panorama.w, 4000, 'w');
  blisko(panorama.h, 4000 / 3, 'h');
  blisko(panorama.y, (3000 - 4000 / 3) / 2, 'y');
});

test('macierz obrotu przenosi obraz dokladnie w prostokat po obrocie', () => {
  const W0 = 400;
  const H0 = 300;
  for (const obrot of [0, 90, 180, 270]) {
    const m = macierzObrotu(obrot, W0, H0);
    const { w, h } = obroconeWymiary(W0, H0, obrot);
    const rogi = [[0, 0], [W0, 0], [0, H0], [W0, H0]].map(([x, y]) => zastosuj(m, x, y));
    const xs = rogi.map((r) => r[0]);
    const ys = rogi.map((r) => r[1]);
    assert.deepEqual([Math.min(...xs), Math.max(...xs), Math.min(...ys), Math.max(...ys)], [0, w, 0, h], `obrot ${obrot}`);
  }
  // Lewy gorny rog zdjecia po obrocie w prawo jest w prawym gornym rogu
  assert.deepEqual(zastosuj(macierzObrotu(90, W0, H0), 0, 0), [H0, 0]);
  // ...a po obrocie w lewo - w lewym dolnym
  assert.deepEqual(zastosuj(macierzObrotu(270, W0, H0), 0, 0), [0, W0]);
});

test('kadr obraca sie tak samo jak obraz', () => {
  const W = 400;
  const H = 300;
  const kadr = { x: 50, y: 20, w: 100, h: 60 };
  // W prawo: ten sam fragment po przeksztalceniu macierza 90 stopni
  const m90 = macierzObrotu(90, W, H);
  const [ax, ay] = zastosuj(m90, kadr.x, kadr.y);
  const [bx, by] = zastosuj(m90, kadr.x + kadr.w, kadr.y + kadr.h);
  assert.deepEqual(obrocKadr(kadr, W, H, 1),
    { x: Math.min(ax, bx), y: Math.min(ay, by), w: Math.abs(bx - ax), h: Math.abs(by - ay) });
  // W lewo: macierz 270 stopni
  const m270 = macierzObrotu(270, W, H);
  const [cx, cy] = zastosuj(m270, kadr.x, kadr.y);
  const [dx, dy] = zastosuj(m270, kadr.x + kadr.w, kadr.y + kadr.h);
  assert.deepEqual(obrocKadr(kadr, W, H, -1),
    { x: Math.min(cx, dx), y: Math.min(cy, dy), w: Math.abs(dx - cx), h: Math.abs(dy - cy) });

  // Cztery obroty w prawo albo prawo + lewo wracaja do punktu wyjscia
  let k = kadr;
  let [w, h] = [W, H];
  for (let i = 0; i < 4; i++) {
    k = obrocKadr(k, w, h, 1);
    [w, h] = [h, w];
    wObrazie(k, w, h, `obrot ${i}`);
  }
  assert.deepEqual(k, kadr);
  assert.deepEqual(obrocKadr(obrocKadr(kadr, W, H, 1), H, W, -1), kadr);
});

test('przesuwanie zatrzymuje sie na krawedziach', () => {
  const kadr = { x: 100, y: 100, w: 200, h: 100 };
  assert.deepEqual(przesun(kadr, -500, -500, 400, 300), { x: 0, y: 0, w: 200, h: 100 });
  assert.deepEqual(przesun(kadr, 500, 500, 400, 300), { x: 200, y: 200, w: 200, h: 100 });
  assert.deepEqual(przesun(kadr, 10, -20, 400, 300), { x: 110, y: 80, w: 200, h: 100 });
});

test('dowolny kadr: krawedz przeciwlegla stoi, a kadr nie przechodzi na druga strone', () => {
  const W = 400;
  const H = 300;
  const kadr = { x: 100, y: 100, w: 200, h: 100 };
  assert.deepEqual(zmienRozmiar(kadr, 'se', 1000, 1000, W, H), { x: 100, y: 100, w: 300, h: 200 });
  assert.deepEqual(zmienRozmiar(kadr, 'nw', -1000, -1000, W, H), { x: 0, y: 0, w: 300, h: 200 });
  // Przeciagniecie lewej krawedzi daleko w prawo - kadr ma minimum, nie ujemna szerokosc
  const min = minimalnyBok(W, H);
  assert.deepEqual(zmienRozmiar(kadr, 'w', 1000, 0, W, H), { x: 300 - min, y: 100, w: min, h: 100 });
  assert.deepEqual(zmienRozmiar(kadr, 'n', 0, 1000, W, H), { x: 100, y: 200 - min, w: 200, h: min });
  // Uchwyt krawedzi rusza tylko swoja os
  assert.deepEqual(zmienRozmiar(kadr, 'e', 50, 999, W, H), { x: 100, y: 100, w: 250, h: 100 });
  // Nieznany uchwyt nic nie robi
  assert.deepEqual(zmienRozmiar(kadr, 'xx', 50, 50, W, H), kadr);
});

test('stale proporcje: rog trzyma przeciwlegly rog, a kadr zostaje w obrazie', () => {
  const W = 400;
  const H = 300;
  const kadr = { x: 100, y: 50, w: 100, h: 100 };
  const wiekszy = zmienRozmiar(kadr, 'se', 400, 10, W, H, 1);
  assert.deepEqual(wiekszy, { x: 100, y: 50, w: 250, h: 250 });
  const zLewej = zmienRozmiar(kadr, 'nw', -400, -400, W, H, 1);
  assert.deepEqual(zLewej, { x: 50, y: 0, w: 150, h: 150 }, 'rosnie do gornej krawedzi, prawy dolny rog stoi');
  // Krawedz boczna: druga os rosnie po rowno i zsuwa sie od krawedzi
  const przyKrawedzi = { x: 0, y: 0, w: 100, h: 100 };
  const wDol = zmienRozmiar(przyKrawedzi, 's', 0, 100, W, H, 1);
  assert.deepEqual(wDol, { x: 0, y: 0, w: 200, h: 200 });
});

test('na chybil trafil: kazda operacja zostawia poprawny kadr', () => {
  const los = losowe(20261004);
  const uchwyty = Object.keys(UCHWYTY);
  const proporcje = [null, 1, 3 / 4, 4 / 3, 16 / 9, 3];
  for (let i = 0; i < 20000; i++) {
    const W = 1 + Math.floor(los() * 5000);
    const H = 1 + Math.floor(los() * 5000);
    const p = proporcje[Math.floor(los() * proporcje.length)];
    let kadr = dopasujProporcje({ x: los() * W, y: los() * H, w: los() * W, h: los() * H }, p, W, H);
    wObrazie(kadr, W, H, `dopasuj #${i}`);
    if (p) {
      blisko(kadr.w / kadr.h, p, `proporcje po dopasowaniu #${i}`);
    }
    const uchwyt = uchwyty[Math.floor(los() * uchwyty.length)];
    const dx = (los() - 0.5) * 3 * W;
    const dy = (los() - 0.5) * 3 * H;
    const przed = kadr;
    kadr = zmienRozmiar(kadr, uchwyt, dx, dy, W, H, p);
    wObrazie(kadr, W, H, `rozmiar #${i} ${uchwyt}`);
    const min = minimalnyBok(W, H);
    if (p) {
      blisko(kadr.w / kadr.h, p, `proporcje po zmianie rozmiaru #${i} ${uchwyt}`);
    } else {
      assert.ok(Math.min(kadr.w, kadr.h) >= Math.min(min, przed.w, przed.h) - 1e-6, `za maly #${i}`);
    }
    // Rog przeciwlegly do uchwytu stoi
    const [sx, sy] = UCHWYTY[uchwyt];
    if (sx === 1) blisko(kadr.x, przed.x, `lewa krawedz #${i}`);
    if (sx === -1) blisko(kadr.x + kadr.w, przed.x + przed.w, `prawa krawedz #${i}`);
    if (sy === 1) blisko(kadr.y, przed.y, `gorna krawedz #${i}`);
    if (sy === -1) blisko(kadr.y + kadr.h, przed.y + przed.h, `dolna krawedz #${i}`);

    kadr = przesun(kadr, (los() - 0.5) * 3 * W, (los() - 0.5) * 3 * H, W, H);
    wObrazie(kadr, W, H, `przesun #${i}`);

    const obrocony = obrocKadr(kadr, W, H, los() < 0.5 ? 1 : -1);
    wObrazie(obrocony, H, W, `obrot #${i}`);

    const piksele = wPikselach(kadr, W, H);
    wObrazie(piksele, W, H, `piksele #${i}`);
    assert.ok(Number.isInteger(piksele.x + piksele.y + piksele.w + piksele.h), `calkowite #${i}`);
  }
});

test('zmiana proporcji zachowuje srodek i miesci sie w obrazie', () => {
  const zPelnego = dopasujProporcje({ x: 0, y: 0, w: 4000, h: 3000 }, 1, 4000, 3000);
  assert.deepEqual(zPelnego, { x: 500, y: 0, w: 3000, h: 3000 });
  // Waski pasek zamieniony na pion nie robi sie kreska
  const pasek = dopasujProporcje({ x: 0, y: 1400, w: 4000, h: 10 }, 3 / 4, 4000, 3000);
  assert.ok(pasek.w >= minimalnyBok(4000, 3000));
  blisko(pasek.w / pasek.h, 3 / 4, 'proporcje');
  // Bez proporcji - tylko pilnowanie granic
  assert.deepEqual(dopasujProporcje({ x: -10, y: 0, w: 50, h: 50 }, null, 100, 100), { x: 0, y: 0, w: 50, h: 50 });
});

test('bez zmian: tylko przy zerowym obrocie i pelnym kadrze', () => {
  assert.equal(bezZmian({ x: 0, y: 0, w: 400, h: 300 }, 0, 400, 300), true);
  assert.equal(bezZmian({ x: 0.2, y: 0.1, w: 399.6, h: 299.8 }, 0, 400, 300), true);
  assert.equal(bezZmian({ x: 0, y: 0, w: 400, h: 300 }, 180, 400, 300), false);
  assert.equal(bezZmian({ x: 1, y: 0, w: 399, h: 300 }, 0, 400, 300), false);
  assert.equal(bezZmian({ x: 0, y: 0, w: 300, h: 300 }, 0, 400, 300), false);
});

test('piksele: zaokraglony kadr nie wychodzi poza obraz nawet z ulamkow na krawedzi', () => {
  assert.deepEqual(wPikselach({ x: 3999.7, y: 0, w: 0.3, h: 3000 }, 4000, 3000), { x: 3999, y: 0, w: 1, h: 3000 });
  assert.deepEqual(wPikselach({ x: 0.4, y: 0.6, w: 399.6, h: 299.4 }, 400, 300), { x: 0, y: 1, w: 400, h: 299 });
  assert.deepEqual(wPikselach({ x: 0, y: 0, w: 1, h: 1 }, 1, 1), { x: 0, y: 0, w: 1, h: 1 });
  assert.deepEqual(wGranicach({ x: 390, y: -5, w: 50, h: 400 }, 400, 300), { x: 350, y: 0, w: 50, h: 300 });
});

test('wybor proporcji: najwiekszy kadr wokol obecnego srodka - przelaczanie tam i z powrotem nie zmniejsza', () => {
  const W = 400;
  const H = 600;
  let kadr = najwiekszyKadr({ x: 0, y: 0, w: W, h: H }, 1, W, H);
  assert.deepEqual(kadr, { x: 0, y: 100, w: 400, h: 400 });
  kadr = najwiekszyKadr(kadr, 16 / 9, W, H);
  blisko(kadr.w, 400, 'w 16:9');
  blisko(kadr.h, 225, 'h 16:9');
  kadr = najwiekszyKadr(kadr, 1, W, H);
  assert.deepEqual(kadr, { x: 0, y: 100, w: 400, h: 400 });
  // Srodek przy krawedzi: kadr przesuwa sie do srodka, zeby sie zmiescil
  const przyKrawedzi = najwiekszyKadr({ x: 0, y: 0, w: 50, h: 50 }, 1, W, H);
  assert.deepEqual(przyKrawedzi, { x: 0, y: 0, w: 400, h: 400 });
  // Bez proporcji - caly obraz
  assert.deepEqual(najwiekszyKadr({ x: 10, y: 10, w: 50, h: 50 }, null, W, H), { x: 0, y: 0, w: 400, h: 600 });
});

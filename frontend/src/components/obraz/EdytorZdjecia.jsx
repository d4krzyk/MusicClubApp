import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Modal from 'react-bootstrap/Modal';
import Spinner from 'react-bootstrap/Spinner';
import {
  UCHWYTY,
  bezZmian,
  dopasujProporcje,
  macierzObrotu,
  najwiekszyKadr,
  nastepnyObrot,
  obrocKadr,
  obroconeWymiary,
  pelnyKadr,
  przesun,
  wGranicach,
  wPikselach,
  zmienRozmiar,
} from '../../utils/kadr';
import {
  czyAnimowanyGif,
  edycjaPliku,
  szerokoscObrazu,
  wczytaj,
  wysokoscObrazu,
  zapamietajEdycje,
  zapiszEdycje,
  zwolnij,
} from '../../utils/obrazy';
import { IconCrop, IconRotateLeft, IconRotateRight, IconUndo } from '../Icons';
import { RODZAJE_KADRU } from './rodzajeKadru';

/** Odstep obrazu od brzegu sceny - zeby uchwyty przy krawedzi dalo sie zlapac palcem. */
const MARGINES = 18;
/** Kadr mniejszy niz tyle pikseli na ekranie ma tylko uchwyty w rogach (krawedziowe by je zaslonily). */
const MALY_KADR = 64;
/** Ponizej tylu pikseli wyniku ostrzegamy, ze zdjecie wyjdzie nieostre. */
const MALY_WYNIK = 160;
/**
 * Przez tyle milisekund od pojawienia sie obrazu przyciski w stopce sa nieaktywne. W kolejce nastepny obraz
 * wskakuje w to samo okno, wiec drugie klikniecie podwojnego "Pomin" trafialo juz w jego przycisk.
 */
const BLOKADA_PO_ZMIANIE = 400;
/** Tak samo jak przy zapisie (utils/obrazy.js): dluzszy bok wyniku najwyzej tyle. */
const MAKS_BOK = 2048;

/** Kazdy plik dostaje wlasny numer - edycja nastepnego zaczyna sie od czystego stanu. */
const numery = new WeakMap();
let ostatniNumer = 0;
function numerPliku(plik) {
  if (!numery.has(plik)) {
    ostatniNumer += 1;
    numery.set(plik, ostatniNumer);
  }
  return numery.get(plik);
}

/**
 * Edytor zalaczonego obrazu: kadrowanie (przeciaganie kadru i jego uchwytow - mysza, palcem albo
 * strzalkami) i obrot co 90 stopni. Wspolny dla wszystkich miejsc, w ktorych zalacza sie obraz;
 * `rodzaj` mowi, w jakich proporcjach (patrz rodzajeKadru.js).
 *
 * - plik: obraz do edycji; null = okno zamkniete. Plik juz raz edytowany otwiera sie od oryginalu
 *   z poprzednim kadrem (utils/obrazy.js: edycjaPliku).
 * - onGotowe(plik): wynik. Bez zmian - oddaje oryginal nietkniety (animowany GIF zostaje animowany).
 * - onAnuluj(): rezygnacja z tego pliku (przy kolejce: "Pomin").
 * - onPrzerwij(): opcjonalnie, przy kolejce kilku plikow - zamkniecie okna konczy cala kolejke.
 * - licznik: { n, z } - "Zdjecie n z z" w tytule.
 */
export default function EdytorZdjecia({ plik, rodzaj = 'post', licznik, onGotowe, onAnuluj, onPrzerwij }) {
  const { t } = useTranslation();
  const [zajety, setZajety] = useState(false);
  const zamknij = onPrzerwij ?? onAnuluj;
  const tytul = licznik && licznik.z > 1
    ? t('imageEditor.titleOf', { n: licznik.n, count: licznik.z })
    : t('imageEditor.title');

  return (
    <Modal
      show={Boolean(plik)}
      onHide={() => { if (!zajety) zamknij(); }}
      fullscreen="sm-down"
      size="lg"
      centered
      // Klikniecie w tlo nie zamyka: przeciaganie uchwytu przy krawedzi latwo konczy sie poza oknem
      backdrop="static"
      className="edytor-modal"
      aria-labelledby="edytor-tytul"
    >
      <Modal.Header closeButton={!zajety}>
        <Modal.Title id="edytor-tytul" className="h5 d-flex align-items-center gap-2">
          <IconCrop size={18} />{tytul}
        </Modal.Title>
      </Modal.Header>
      {plik && (
        <Edycja
          key={numerPliku(plik)}
          plik={plik}
          rodzaj={RODZAJE_KADRU[rodzaj] ? rodzaj : 'post'}
          kolejka={Boolean(onPrzerwij)}
          onGotowe={onGotowe}
          onAnuluj={onAnuluj}
          onZajety={setZajety}
        />
      )}
    </Modal>
  );
}

function Edycja({ plik, rodzaj, kolejka, onGotowe, onAnuluj, onZajety }) {
  const { t } = useTranslation();
  const ustawienia = RODZAJE_KADRU[rodzaj];
  const opcje = ustawienia.proporcje;
  const startowe = opcje[0].wartosc;

  // Plik juz raz edytowany - pracujemy na oryginale, z poprzednimi ustawieniami
  const [zrodlo] = useState(() => {
    const poprzednia = edycjaPliku(plik);
    return poprzednia ? { oryginal: poprzednia.oryginal, poprzednia } : { oryginal: plik, poprzednia: null };
  });
  const { oryginal } = zrodlo;

  const [obraz, setObraz] = useState(null);
  const [stan, setStan] = useState('laduje');
  const [animowany, setAnimowany] = useState(false);
  const [obrot, setObrot] = useState(0);
  const [proporcje, setProporcje] = useState(startowe);
  const [kadr, setKadr] = useState(null);
  const [zapisywanie, setZapisywanie] = useState(false);
  const [bladZapisu, setBladZapisu] = useState(false);
  const [scena, setScena] = useState({ w: 0, h: 0 });
  const [ciagnie, setCiagnie] = useState(false);
  const [swiezy, setSwiezy] = useState(true);

  const scenaRef = useRef(null);
  const plotnoRef = useRef(null);
  const przeciaganie = useRef(null);
  const zyje = useRef(true);
  /* Wynik oddajemy raz: podwojne klikniecie "Gotowe"/"Pomin" w kolejce przeskakiwaloby nastepne zdjecie */
  const oddane = useRef(false);
  const zapisuje = useRef(false);
  const oddaj = (fn, ...args) => {
    if (!oddane.current) {
      oddane.current = true;
      fn(...args);
    }
  };

  useEffect(() => {
    zyje.current = true;
    return () => { zyje.current = false; };
  }, []);

  useEffect(() => {
    onZajety(zapisywanie);
  }, [zapisywanie, onZajety]);

  useEffect(() => {
    const zegar = setTimeout(() => setSwiezy(false), BLOKADA_PO_ZMIANIE);
    return () => clearTimeout(zegar);
  }, []);

  /* Wczytanie obrazu; bitmapa zwalniana przy zamknieciu albo przejsciu do nastepnego pliku */
  useEffect(() => {
    let aktualne = true;
    let wczytany = null;
    wczytaj(oryginal)
      .then((o) => {
        if (!aktualne) {
          zwolnij(o);
          return;
        }
        const W0 = szerokoscObrazu(o);
        const H0 = wysokoscObrazu(o);
        if (!W0 || !H0) {
          zwolnij(o);
          throw new Error('Obraz bez wymiarow');
        }
        wczytany = o;
        const p = zrodlo.poprzednia;
        const znane = p && opcje.some((op) => op.wartosc === p.proporcje);
        if (znane) {
          const { w, h } = obroconeWymiary(W0, H0, p.obrot);
          setObrot(p.obrot);
          setProporcje(p.proporcje);
          setKadr(p.proporcje ? dopasujProporcje(p.kadr, p.proporcje, w, h) : wGranicach(p.kadr, w, h));
        } else {
          setKadr(pelnyKadr(W0, H0, startowe));
        }
        setObraz(o);
        setStan('gotowy');
      })
      .catch(() => {
        if (aktualne) {
          setStan('blad');
        }
      });
    czyAnimowanyGif(oryginal).then((a) => { if (aktualne) setAnimowany(a); });
    return () => {
      aktualne = false;
      zwolnij(wczytany);
    };
    // Raz na plik - Edycja dostaje nowy klucz przy kazdym pliku
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /* Rozmiar sceny - na telefonie zmienia sie przy obrocie ekranu i chowaniu paska adresu */
  useLayoutEffect(() => {
    const el = scenaRef.current;
    if (!el) {
      return undefined;
    }
    const zmierz = () => setScena({ w: el.clientWidth, h: el.clientHeight });
    zmierz();
    if (typeof ResizeObserver !== 'function') {
      window.addEventListener('resize', zmierz);
      return () => window.removeEventListener('resize', zmierz);
    }
    const obserwator = new ResizeObserver(zmierz);
    obserwator.observe(el);
    return () => obserwator.disconnect();
  }, [stan]);

  const W0 = obraz ? szerokoscObrazu(obraz) : 0;
  const H0 = obraz ? wysokoscObrazu(obraz) : 0;
  const { w: W, h: H } = obroconeWymiary(W0, H0, obrot);
  const dostepneW = Math.max(1, scena.w - 2 * MARGINES);
  const dostepneH = Math.max(1, scena.h - 2 * MARGINES);
  const skala = W && H ? Math.min(dostepneW / W, dostepneH / H) : 0;
  const widokW = W * skala;
  const widokH = H * skala;
  const odX = (scena.w - widokW) / 2;
  const odY = (scena.h - widokH) / 2;

  /* Rysowanie podgladu: plotno wielkosci widoku (z gestoscia ekranu, najwyzej 2x) */
  useEffect(() => {
    const plotno = plotnoRef.current;
    if (!plotno || !obraz || widokW < 1 || widokH < 1) {
      return;
    }
    const gestosc = Math.min(window.devicePixelRatio || 1, 2);
    plotno.width = Math.max(1, Math.round(widokW * gestosc));
    plotno.height = Math.max(1, Math.round(widokH * gestosc));
    const kontekst = plotno.getContext('2d');
    if (!kontekst) {
      return;
    }
    try {
      kontekst.setTransform(1, 0, 0, 1, 0, 0);
      kontekst.clearRect(0, 0, plotno.width, plotno.height);
      kontekst.imageSmoothingQuality = 'high';
      kontekst.scale(plotno.width / W, plotno.height / H);
      kontekst.transform(...macierzObrotu(obrot, W0, H0));
      kontekst.drawImage(obraz, 0, 0, W0, H0);
    } catch {
      // Bitmapa zwolniona w trakcie zamykania - nie ma czego rysowac
    }
  }, [obraz, obrot, widokW, widokH, W, H, W0, H0]);

  /* --- Gesty: jeden wskaznik naraz; przesuniecie liczone od stanu z chwili nacisniecia --- */

  function zacznij(e, uchwyt) {
    if (zapisywanie || przeciaganie.current || !kadr) {
      return;
    }
    if (e.pointerType === 'mouse' && e.button !== 0) {
      return;
    }
    e.preventDefault();
    e.stopPropagation();
    try {
      e.currentTarget.setPointerCapture(e.pointerId);
    } catch {
      // Bez przechwycenia tez dziala - ruch i tak dochodzi do sceny
    }
    przeciaganie.current = { id: e.pointerId, uchwyt, x: e.clientX, y: e.clientY, kadr };
    setCiagnie(true);
  }

  function ruch(e) {
    const p = przeciaganie.current;
    if (!p || p.id !== e.pointerId || !skala) {
      return;
    }
    const dx = (e.clientX - p.x) / skala;
    const dy = (e.clientY - p.y) / skala;
    setKadr(p.uchwyt
      ? zmienRozmiar(p.kadr, p.uchwyt, dx, dy, W, H, proporcje)
      : przesun(p.kadr, dx, dy, W, H));
  }

  function puszczone(e) {
    const p = przeciaganie.current;
    if (p && p.id === e.pointerId) {
      przeciaganie.current = null;
      setCiagnie(false);
    }
  }

  function klawisz(e) {
    if (!kadr || zapisywanie) {
      return;
    }
    const kroki = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] };
    const krok = kroki[e.key];
    if (!krok) {
      return;
    }
    e.preventDefault();
    const o = Math.max(1, Math.round(Math.min(W, H) / 50));
    setKadr(e.shiftKey
      ? zmienRozmiar(kadr, 'se', krok[0] * o, krok[1] * o, W, H, proporcje)
      : przesun(kadr, krok[0] * o, krok[1] * o, W, H));
  }

  /* --- Przyciski --- */

  function obroc(kierunek) {
    if (!kadr) {
      return;
    }
    const nowy = nastepnyObrot(obrot, kierunek);
    const { w, h } = obroconeWymiary(W0, H0, nowy);
    const obrocony = obrocKadr(kadr, W, H, kierunek);
    setKadr(proporcje ? dopasujProporcje(obrocony, proporcje, w, h) : wGranicach(obrocony, w, h));
    setObrot(nowy);
  }

  function wybierzProporcje(wartosc) {
    setProporcje(wartosc);
    if (kadr && wartosc) {
      setKadr(najwiekszyKadr(kadr, wartosc, W, H));
    }
  }

  function przywroc() {
    setObrot(0);
    setProporcje(startowe);
    setKadr(pelnyKadr(W0, H0, startowe));
  }

  async function zastosuj() {
    if (stan !== 'gotowy' || !kadr || oddane.current || zapisuje.current) {
      return;
    }
    if (bezZmian(kadr, obrot, W, H)) {
      oddaj(onGotowe, oryginal);
      return;
    }
    zapisuje.current = true;
    setZapisywanie(true);
    setBladZapisu(false);
    try {
      const wynik = await zapiszEdycje(obraz, { obrot, kadr }, oryginal);
      zapamietajEdycje(wynik, { oryginal, obrot, kadr, proporcje });
      if (zyje.current) {
        setZapisywanie(false);
      }
      oddaj(onGotowe, wynik);
    } catch {
      if (zyje.current) {
        setBladZapisu(true);
        setZapisywanie(false);
      }
    } finally {
      zapisuje.current = false;
    }
  }

  const piksele = kadr && W ? wPikselach(kadr, W, H) : null;
  const zmniejszenie = piksele ? Math.min(1, MAKS_BOK / Math.max(piksele.w, piksele.h)) : 1;
  const wynikW = piksele ? Math.max(1, Math.round(piksele.w * zmniejszenie)) : 0;
  const wynikH = piksele ? Math.max(1, Math.round(piksele.h * zmniejszenie)) : 0;
  const gotowy = stan === 'gotowy' && kadr && skala > 0;

  return (
    <>
      <Modal.Body className="edytor-cialo">
        {animowany && stan !== 'blad' && (
          <Alert variant="warning" className="py-2 small mb-2">{t('imageEditor.animated')}</Alert>
        )}
        {bladZapisu && (
          <Alert variant="danger" className="py-2 small mb-2">{t('imageEditor.saveFailed')}</Alert>
        )}

        {stan === 'blad' ? (
          <Alert variant="danger" className="mb-0">{t('imageEditor.loadFailed')}</Alert>
        ) : (
          <div
            ref={scenaRef}
            className={`edytor-scena${ciagnie ? ' is-ciagnie' : ''}`}
            onPointerMove={ruch}
            onPointerUp={puszczone}
            onPointerCancel={puszczone}
            onLostPointerCapture={puszczone}
          >
            {stan === 'laduje' && (
              <div className="edytor-ladowanie">
                <Spinner animation="border" size="sm" className="me-2" />{t('imageEditor.loading')}
              </div>
            )}
            {gotowy && (
              <>
                <canvas
                  ref={plotnoRef}
                  className="edytor-obraz"
                  style={{ left: odX, top: odY, width: widokW, height: widokH }}
                  aria-hidden="true"
                />
                <div
                  className={`edytor-kadr${ustawienia.ksztalt ? ` is-${ustawienia.ksztalt}` : ''}${
                    Math.min(kadr.w, kadr.h) * skala < MALY_KADR ? ' is-maly' : ''}`}
                  style={{
                    left: odX + kadr.x * skala,
                    top: odY + kadr.y * skala,
                    width: kadr.w * skala,
                    height: kadr.h * skala,
                  }}
                  tabIndex={0}
                  role="group"
                  aria-roledescription={t('imageEditor.crop')}
                  aria-label={t('imageEditor.cropLabel', { w: wynikW, h: wynikH })}
                  aria-describedby="edytor-podpowiedz"
                  onPointerDown={(e) => zacznij(e, null)}
                  onKeyDown={klawisz}
                >
                  <span className="edytor-siatka" aria-hidden="true" />
                  {Object.keys(UCHWYTY).map((u) => (
                    <span
                      key={u}
                      className={`edytor-uchwyt is-${u}`}
                      data-uchwyt={u}
                      aria-hidden="true"
                      onPointerDown={(e) => zacznij(e, u)}
                    />
                  ))}
                </div>
              </>
            )}
          </div>
        )}

        {stan !== 'blad' && (
          <div className="edytor-narzedzia">
            <div className="edytor-obroty" role="group" aria-label={t('imageEditor.rotate')}>
              <Button variant="outline-secondary" size="sm" onClick={() => obroc(-1)} disabled={!gotowy || zapisywanie}
                aria-label={t('imageEditor.rotateLeft')} title={t('imageEditor.rotateLeft')}>
                <IconRotateLeft />
              </Button>
              <Button variant="outline-secondary" size="sm" onClick={() => obroc(1)} disabled={!gotowy || zapisywanie}
                aria-label={t('imageEditor.rotateRight')} title={t('imageEditor.rotateRight')}>
                <IconRotateRight />
              </Button>
              <Button variant="outline-secondary" size="sm" onClick={przywroc} disabled={!gotowy || zapisywanie}
                title={t('imageEditor.reset')}>
                <IconUndo className="me-1" />{t('imageEditor.reset')}
              </Button>
            </div>

            {opcje.length > 1 ? (
              <div className="edytor-proporcje" role="group" aria-label={t('imageEditor.aspect')}>
                {opcje.map((op) => (
                  <button
                    key={op.klucz}
                    type="button"
                    aria-pressed={proporcje === op.wartosc}
                    className={`edytor-proporcja${proporcje === op.wartosc ? ' is-wybrana' : ''}`}
                    onClick={() => wybierzProporcje(op.wartosc)}
                    disabled={!gotowy || zapisywanie}
                  >
                    {op.wartosc ? op.klucz : t('imageEditor.free')}
                  </button>
                ))}
              </div>
            ) : (
              <span className="edytor-staly small text-body-secondary">{t(`imageEditor.fixed.${rodzaj}`)}</span>
            )}
          </div>
        )}

        {stan !== 'blad' && (
          <p className="edytor-info small mb-0">
            <span id="edytor-podpowiedz" className="text-body-secondary">{t('imageEditor.hint')}</span>
            {gotowy && (
              <span className={`edytor-wynik${Math.min(wynikW, wynikH) < MALY_WYNIK ? ' text-warning-emphasis' : ''}`}>
                {t('imageEditor.result', { w: wynikW, h: wynikH })}
                {Math.min(wynikW, wynikH) < MALY_WYNIK && ` — ${t('imageEditor.small')}`}
              </span>
            )}
          </p>
        )}
      </Modal.Body>

      <Modal.Footer className="edytor-stopka">
        <Button variant="outline-secondary" onClick={() => oddaj(onAnuluj)} disabled={zapisywanie || swiezy}>
          {kolejka ? t('imageEditor.skip') : t('common.cancel')}
        </Button>
        {(stan === 'blad' || bladZapisu) && (
          <Button variant="outline-primary" onClick={() => oddaj(onGotowe, oryginal)} disabled={zapisywanie || swiezy}>
            {t('imageEditor.useOriginal')}
          </Button>
        )}
        {stan !== 'blad' && (
          <Button variant="primary" onClick={zastosuj} disabled={!gotowy || zapisywanie || swiezy}>
            {zapisywanie && <Spinner animation="border" size="sm" className="me-2" />}
            {zapisywanie ? t('imageEditor.saving') : t('imageEditor.done')}
          </Button>
        )}
      </Modal.Footer>
    </>
  );
}

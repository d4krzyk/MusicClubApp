import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import 'leaflet/dist/leaflet.css';

/** Kafelki OpenStreetMap - licencja wymaga podpisu na mapie (atrybucja w rogu). */
const KAFELKI = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
const PODPIS_OSM = '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">OpenStreetMap</a>';

/** Pinezka w kolorze marki - wlasne SVG, bez obrazkow Leafleta (te gubia sie przy budowaniu). */
const PINEZKA = '<svg viewBox="0 0 24 32" width="30" height="40" aria-hidden="true"><path fill="var(--mc-pinezka, #c026d3)" '
  + 'stroke="#fff" stroke-width="1.6" d="M12 1C6 1 1.5 5.4 1.5 11.2 1.5 19 12 31 12 31s10.5-12 10.5-19.8C22.5 5.4 18 1 12 1z"/>'
  + '<circle cx="12" cy="11.2" r="4.2" fill="#fff"/></svg>';

/**
 * Mapa z jednym punktem (OpenStreetMap przez Leaflet).
 *
 * - Leaflet laduje sie dopiero, gdy mapa wjedzie na ekran (osobny kawalek paczki - strona bez mapy go nie pobiera).
 * - Tryb wyboru ({@code onWybierz}): stukniecie w mape albo przeciagniecie pinezki ustawia punkt.
 * - Na telefonie mapy nie da sie przesuwac jednym palcem (przewijanie strony nie moze utknac na mapie);
 *   przyblizanie przyciskami albo dwoma palcami. W trybie wyboru przesuwanie jest wlaczone.
 * - Gdy zadna kafelka sie nie zaladuje (brak sieci), zamiast szarej plamy jest komunikat.
 */
export default function MapaPunktu({
  lat, lon, podpis, zoom = 15, wysokosc = 220, onWybierz = null, className = '',
}) {
  const { t } = useTranslation();
  const pojemnik = useRef(null);
  const mapa = useRef(null);
  const pinezka = useRef(null);
  const leaflet = useRef(null);
  const wybierz = useRef(onWybierz);
  // Najnowszy punkt - mapa tworzy sie asynchronicznie, wiec nie moze czytac go z domkniecia sprzed importu
  const punkt = useRef({ lat, lon, podpis });
  punkt.current = { lat, lon, podpis };
  const [widoczna, setWidoczna] = useState(false);
  const [bezKafelkow, setBezKafelkow] = useState(false);
  const [blad, setBlad] = useState(false);
  const maPunkt = Number.isFinite(lat) && Number.isFinite(lon);

  useEffect(() => { wybierz.current = onWybierz; }, [onWybierz]);

  // Leniwie: dopiero gdy mapa jest blisko ekranu
  useEffect(() => {
    const el = pojemnik.current;
    if (!el || widoczna) {
      return undefined;
    }
    if (typeof IntersectionObserver !== 'function') {
      setWidoczna(true);
      return undefined;
    }
    const obserwator = new IntersectionObserver((wpisy) => {
      if (wpisy.some((w) => w.isIntersecting)) {
        setWidoczna(true);
      }
    }, { rootMargin: '200px' });
    obserwator.observe(el);
    return () => obserwator.disconnect();
  }, [widoczna]);

  // Tworzenie mapy (raz)
  useEffect(() => {
    if (!widoczna || mapa.current) {
      return undefined;
    }
    let anulowane = false;
    import('leaflet').then((modul) => {
      if (anulowane || !pojemnik.current) {
        return;
      }
      const L = modul.default ?? modul;
      leaflet.current = L;
      const wybor = Boolean(wybierz.current);
      const m = L.map(pojemnik.current, {
        zoomControl: true,
        attributionControl: false,
        scrollWheelZoom: false,
        dragging: wybor || !L.Browser.mobile,
        tap: false,
      });
      L.control.attribution({ prefix: false }).addAttribution(PODPIS_OSM).addTo(m);
      let udane = 0;
      let nieudane = 0;
      L.tileLayer(KAFELKI, { maxZoom: 19, crossOrigin: false })
        .on('tileload', () => { udane += 1; setBezKafelkow(false); })
        .on('tileerror', () => { nieudane += 1; if (udane === 0 && nieudane >= 4) setBezKafelkow(true); })
        .addTo(m);
      // Polska, gdy punktu jeszcze nie ma (wybor miejsca spotkania)
      const p = punkt.current;
      const jest = Number.isFinite(p.lat) && Number.isFinite(p.lon);
      m.setView(jest ? [p.lat, p.lon] : [52.07, 19.48], jest ? zoom : 5);
      if (wybor) {
        m.on('click', (e) => wybierz.current?.(round(e.latlng.lat), round(e.latlng.lng)));
      }
      mapa.current = m;
      // Pojemnik mogl zmienic rozmiar przed pierwszym rysowaniem (np. okno dialogowe w trakcie animacji)
      setTimeout(() => m.invalidateSize(), 250);
      ustawPinezke();
    }).catch(() => !anulowane && setBlad(true));
    return () => { anulowane = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [widoczna]);

  // Sprzatanie przy odmontowaniu
  useEffect(() => () => { mapa.current?.remove(); mapa.current = null; }, []);

  function ustawPinezke() {
    const L = leaflet.current;
    const m = mapa.current;
    if (!L || !m) {
      return;
    }
    const { lat: la, lon: lo, podpis: nazwa } = punkt.current;
    if (!(Number.isFinite(la) && Number.isFinite(lo))) {
      pinezka.current?.remove();
      pinezka.current = null;
      return;
    }
    if (!pinezka.current) {
      const ikona = L.divIcon({ className: 'mapa-pinezka', html: PINEZKA, iconSize: [30, 40], iconAnchor: [15, 39] });
      pinezka.current = L.marker([la, lo], {
        icon: ikona, draggable: Boolean(wybierz.current), keyboard: false, title: nazwa ?? '',
      }).addTo(m);
      pinezka.current.on('dragend', (e) => {
        const p = e.target.getLatLng();
        wybierz.current?.(round(p.lat), round(p.lng));
      });
    } else {
      pinezka.current.setLatLng([la, lo]);
    }
  }

  // Zmiana punktu z zewnatrz (np. "moja lokalizacja") - pinezka i widok ida za nim
  useEffect(() => {
    if (!mapa.current) {
      return;
    }
    ustawPinezke();
    if (maPunkt) {
      mapa.current.setView([lat, lon], Math.max(mapa.current.getZoom(), wybierz.current ? 16 : zoom));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [lat, lon]);

  return (
    <div className={`mapa-punktu ${className}`.trim()} style={{ height: wysokosc }}>
      <div
        ref={pojemnik}
        className="mapa-punktu-plotno"
        role="region"
        aria-label={podpis ? t('map.regionNamed', { name: podpis }) : t('map.region')}
        data-lat={maPunkt ? lat : undefined}
        data-lon={maPunkt ? lon : undefined}
      />
      {(bezKafelkow || blad) && (
        <div className="mapa-punktu-brak" role="status">{t('map.unavailable')}</div>
      )}
    </div>
  );
}

/** 6 miejsc po przecinku to ok. 10 cm - wiecej nie ma sensu, a krocej sie zapisuje. */
function round(v) {
  return Math.round(v * 1e6) / 1e6;
}

/** Trasa w Mapach Google (na telefonie otwiera aplikacje map), do punktu albo do nazwy miejsca. */
export function adresTrasy(lat, lon, nazwa) {
  const cel = Number.isFinite(lat) && Number.isFinite(lon) ? `${lat},${lon}` : nazwa;
  return `https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(cel)}`;
}

/** Punkt (albo nazwa miejsca) w Mapach Google. */
export function adresPunktu(lat, lon, nazwa) {
  const cel = Number.isFinite(lat) && Number.isFinite(lon) ? `${lat},${lon}` : nazwa;
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(cel)}`;
}

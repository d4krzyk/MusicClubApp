import { useTranslation } from 'react-i18next';

/**
 * "Od organizatora": to, co zrodla wydarzen mowia poza nazwa i data - kto organizuje, ile kosztuja bilety, kiedy
 * rusza sprzedaz, ograniczenie wiekowe, dostepnosc i wazne uwagi. Pokazuje tylko to, co jest; nic = nic.
 * Wszystko jako zwykly tekst (tresc z obcych serwisow).
 */
export default function OdOrganizatora({ w }) {
  const { t, i18n } = useTranslation();
  const jezyk = i18n.language;
  const ceny = cena(w, jezyk, t);
  const sprzedaz = w.salesStart && new Date(w.salesStart) > new Date()
    ? new Intl.DateTimeFormat(jezyk, { day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit' }).format(new Date(w.salesStart))
    : null;

  const fakty = [
    w.promoter && { klucz: 'promoter', etykieta: t('events.organizer.promoter'), tresc: w.promoter },
    ceny && { klucz: 'ceny', etykieta: t('events.organizer.prices'), tresc: ceny },
    sprzedaz && { klucz: 'sprzedaz', etykieta: t('events.organizer.salesStart'), tresc: sprzedaz },
    w.ageRestricted && { klucz: 'wiek', etykieta: t('events.organizer.age'), tresc: t('events.organizer.adultsOnly') },
    w.accessibility && { klucz: 'dostepnosc', etykieta: t('events.organizer.accessibility'), tresc: w.accessibility },
  ].filter(Boolean);

  if (fakty.length === 0 && !w.pleaseNote) {
    return null;
  }

  return (
    <section className="mb-4 od-organizatora" aria-labelledby="od-organizatora">
      <h2 id="od-organizatora" className="h6 wydarzenie-sekcja">{t('events.organizer.title')}</h2>
      {fakty.length > 0 && (
        <dl className="od-organizatora-fakty">
          {fakty.map((f) => (
            <div key={f.klucz} className="od-organizatora-fakt">
              <dt>{f.etykieta}</dt>
              <dd>{f.tresc}</dd>
            </div>
          ))}
        </dl>
      )}
      {w.pleaseNote && (
        <div className="od-organizatora-uwaga">
          <strong>{t('events.organizer.note')}</strong>
          <p className="mb-0">{w.pleaseNote}</p>
        </div>
      )}
    </section>
  );
}

function cena(w, jezyk, t) {
  if (w.priceMin == null && w.priceMax == null) {
    return null;
  }
  const f = (v) => new Intl.NumberFormat(jezyk, {
    style: 'currency', currency: w.priceCurrency || 'PLN', maximumFractionDigits: v % 1 === 0 ? 0 : 2,
  }).format(v);
  if (w.priceMin != null && w.priceMax != null && w.priceMax > w.priceMin) {
    return t('events.organizer.priceRange', { od: f(w.priceMin), do: f(w.priceMax) });
  }
  return t('events.organizer.priceFrom', { od: f(w.priceMin ?? w.priceMax) });
}

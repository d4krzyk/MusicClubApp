import { useTranslation } from 'react-i18next';

/**
 * Dopasowanie gustu 0-4 jako cztery kreski i podpis ("Podobny gust"). Kreski, a nie procenty - serwer liczy
 * wspolnych wykonawcow, utwory i gatunki, a nie cos, co da sie uczciwie podac z dokladnoscia do procenta.
 */
export default function MiernikGustu({ poziom }) {
  const { t } = useTranslation();
  return (
    <div className={`pz-miernik pz-poziom-${poziom}`}>
      <span className="pz-kreski" aria-hidden="true">
        {[1, 2, 3, 4].map((k) => <i key={k} className={k <= poziom ? 'is-pelna' : ''} />)}
      </span>
      <span className="pz-miernik-podpis">{t(`discover.taste.${poziom}`)}</span>
    </div>
  );
}

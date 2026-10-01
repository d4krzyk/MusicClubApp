import { useTranslation } from 'react-i18next';
import { POZIOMY_AKTYWNOSCI } from '../../utils/klan';

/**
 * Jak ozywiony jest czat klanu: trzy kreski i slowo. Serwer podaje tylko poziom (z liczby wiadomosci
 * z ostatnich 7 dni), nigdy tresc ani liczbe wiadomosci.
 */
export default function Aktywnosc({ poziom }) {
  const { t } = useTranslation();
  const ile = POZIOMY_AKTYWNOSCI.indexOf(poziom);
  if (ile < 0) {
    return null;
  }
  return (
    <span className="klan-aktywnosc" title={t('clans.activity.hint')}>
      <span className="klan-aktywnosc-kreski" aria-hidden="true">
        {[1, 2, 3].map((n) => <i key={n} className={n <= ile ? 'is-on' : ''} />)}
      </span>
      {t(`clans.activity.${poziom}`)}
    </span>
  );
}

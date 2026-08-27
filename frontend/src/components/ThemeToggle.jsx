import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme/ThemeContext';
import { IconMoon, IconSun } from './Icons';

/**
 * Przelacznik motywu: slonce i ksiezyc wymieniaja sie obrotem.
 *
 * <p><b>Obie ikony sa w DOM-ie przez caly czas</b>, jedna nad druga. Gdyby
 * React podmienial je warunkowo, nie byloby czego animowac - element znika
 * i pojawia sie nowy, wiec przejscie nie ma punktu zaczepienia. Tak jedna
 * wyjezdza obrotem w gore, druga wjezdza z dolu, i widac, ze to ta sama
 * rzecz zmienila stan.</p>
 *
 * <p>Pokazujemy ikone tego, co WLACZYMY po kliknieciu (slonce = "wlacz
 * jasny"), a nie tego, co jest teraz - przycisk ma mowic, co zrobi.</p>
 */
export default function ThemeToggle() {
  const { t } = useTranslation();
  const { dark, toggle } = useTheme();

  const opis = dark ? t('menu.themeLight') : t('menu.themeDark');

  return (
    <button
      type="button"
      className={`theme-toggle${dark ? ' is-dark' : ''}`}
      onClick={toggle}
      title={opis}
      aria-label={opis}
      aria-pressed={dark}
    >
      <span className="theme-icon theme-icon-sun" aria-hidden="true">
        <IconSun />
      </span>
      <span className="theme-icon theme-icon-moon" aria-hidden="true">
        <IconMoon />
      </span>
    </button>
  );
}

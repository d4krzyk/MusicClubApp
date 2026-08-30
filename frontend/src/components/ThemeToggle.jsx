import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme/ThemeContext';
import { IconMoon, IconSun } from './Icons';

/** Przelacznik motywu: slonce i ksiezyc wymieniaja sie obrotem. */
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

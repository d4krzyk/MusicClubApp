import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { IconClan } from './Icons';

/**
 * Plakietka klanu obok loginu: kolor klanu, ikona (albo domyslna tarcza) i skrot.
 * Klikniecie prowadzi na strone klanu. Kolor pochodzi z serwera - wszystkie kolory
 * z palety sa dosc ciemne, zeby bialy napis mial czytelny kontrast.
 */
export default function ClanBadge({ clan, className = '', link = true }) {
  const { t } = useTranslation();
  if (!clan) {
    return null;
  }

  const zawartosc = (
    <>
      {clan.iconUrl ? (
        <img src={clan.iconUrl} alt="" className="klan-plakietka-ikona" />
      ) : (
        <IconClan size={12} className="klan-plakietka-tarcza" />
      )}
      <span className="klan-plakietka-skrot">{clan.tag}</span>
    </>
  );
  const styl = { '--klan': clan.colorHex };
  const opis = t('clans.badgeTitle', { name: clan.name });

  return link ? (
    <Link to={`/klany/${clan.id}`} className={`klan-plakietka ${className}`.trim()} style={styl} title={opis}>
      {zawartosc}
    </Link>
  ) : (
    <span className={`klan-plakietka ${className}`.trim()} style={styl} title={opis}>{zawartosc}</span>
  );
}

import { useTranslation } from 'react-i18next';
import { timeAgo } from '../utils/dates';

/** Kropka "online" / "offline" z podpowiedzia, kiedy ktos byl ostatnio. */
export default function PresenceDot({ presence, withLabel = false }) {
  const { t, i18n } = useTranslation();

  if (!presence) {
    return null;
  }

  const label = presence.online
    ? t('presence.online')
    : presence.lastSeenAt
      ? t('presence.lastSeen', { when: timeAgo(presence.lastSeenAt, i18n.language) })
      : t('presence.never');

  return (
    <span className={`presence${presence.online ? ' is-online' : ''}`} title={label}>
      {/* Kropka jest ozdobna dla czytnika ekranu - cala tresc niesie tekst obok albo etykieta. */}
      <span className="presence-dot" aria-hidden="true" />

      {withLabel
        ? <span className="presence-label">{label}</span>
        : <span className="visually-hidden">{label}</span>}
    </span>
  );
}

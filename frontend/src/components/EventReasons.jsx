import { useTranslation } from 'react-i18next';
import { IconFriends, IconNote } from './Icons';

/**
 * Dlaczego wydarzenie pasuje do profilu: "Sluchasz: Mrozu", "Gatunek: indie".
 *
 * Serwer przysyla sam rodzaj i wartosc - zdanie wokol skladamy tutaj,
 * w jezyku uzytkownika.
 */
export default function EventReasons({
  powody, maks, zawijaj = false, className = '',
}) {
  const { t } = useTranslation();
  const lista = maks ? (powody ?? []).slice(0, maks) : (powody ?? []);

  if (lista.length === 0) {
    return null;
  }

  return (
    <ul className={`wydarzenie-powody list-unstyled${zawijaj ? ' is-zawijane' : ''} ${className}`}>
      {lista.map((p) => (
        <li key={`${p.kind}-${p.value}`} className={`wydarzenie-powod wydarzenie-powod-${p.kind.toLowerCase()}`}>
          {p.kind === 'FRIENDS' ? <IconFriends size={11} /> : <IconNote size={11} />}
          {/*
            Tekst we wlasnym spanie: w kontenerze flex goly tekst staje sie
            osobnym elementem i "text-overflow: ellipsis" na nim nie dziala -
            byl ucinany w pol slowa bez wielokropka.
          */}
          <span className="wydarzenie-powod-tekst">
            {p.kind === 'FRIENDS'
              ? t('events.reason.FRIENDS', { count: Number(p.value) })
              : t(`events.reason.${p.kind}`, { value: p.value })}
          </span>
        </li>
      ))}
    </ul>
  );
}

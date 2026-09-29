import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import Button from 'react-bootstrap/Button';
import { describeError } from '../api/client';
import * as znajomi from '../api/znajomi';
import { IconPersonCheck, IconPersonPlus, IconCross } from './Icons';

/** Przycisk relacji na cudzym profilu: zapros / przyjmij / usun ze znajomych. */
export default function FriendshipButton({ profile, onChange }) {
  const { t } = useTranslation();
  const [wysylanie, setWysylanie] = useState(false);
  const [error, setError] = useState(null);

  // Na wlasnym profilu nie ma czego zapraszac
  if (profile.friendshipStatus === 'SELF') {
    return null;
  }

  async function run(akcja) {
    setWysylanie(true);
    setError(null);
    try {
      await akcja();
      // Profil przeladowuje komponent nadrzedny - stamtad przychodzi nowy status
      await onChange();
    } catch (error) {
      const details = describeError(error);
      setError(details.message);
    } finally {
      setWysylanie(false);
    }
  }

  const invite = () => run(() => znajomi.zapros(profile.username));

  const remove = () => run(() => znajomi.usunZnajomego(profile.username));

  /*
   * Przyjecie wymaga NUMERU zaproszenia, ktorego profil nie zawiera - musimy je najpierw odnalezc
   * na liscie przychodzacych.
   */
  const accept = () => run(async () => {
    const lista = await znajomi.zaproszenia();
    const mine = lista.incoming.find((z) => z.username === profile.username);
    if (mine) {
      await znajomi.przyjmij(mine.id);
    }
  });

  const anuluj = () => run(async () => {
    const lista = await znajomi.zaproszenia();
    const mine = lista.outgoing.find((z) => z.username === profile.username);
    if (mine) {
      await znajomi.odrzuc(mine.id);
    }
  });

  return (
    <div className="text-end">
      {/* canInvite: ustawienia tej osoby ("nikt", "znajomi znajomych") i blokady - liczy serwer */}
      {profile.friendshipStatus === 'NONE' && profile.canInvite !== false && (
        <Button size="sm" disabled={wysylanie} onClick={invite}>
          <IconPersonPlus /> {t('friends.invite')}
        </Button>
      )}
      {profile.friendshipStatus === 'NONE' && profile.canInvite === false && !profile.blockedByMe && (
        <div className="small text-body-secondary">{t('friends.notAcceptingInvites')}</div>
      )}

      {profile.friendshipStatus === 'REQUEST_SENT' && (
        <Button variant="outline-secondary" size="sm" disabled={wysylanie} onClick={anuluj}>
          <IconCross /> {t('friends.cancelInvite')}
        </Button>
      )}

      {profile.friendshipStatus === 'REQUEST_RECEIVED' && (
        <Button size="sm" disabled={wysylanie} onClick={accept}>
          <IconPersonCheck /> {t('friends.accept')}
        </Button>
      )}

      {profile.friendshipStatus === 'FRIENDS' && (
        <Button
          variant="outline-secondary"
          size="sm"
          disabled={wysylanie}
          onClick={remove}
          title={t('friends.remove')}
        >
          <IconPersonCheck /> {t('friends.areFriends')}
        </Button>
      )}

      {error && <div className="text-danger small mt-1">{error}</div>}
    </div>
  );
}

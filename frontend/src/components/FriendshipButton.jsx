import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import Button from 'react-bootstrap/Button';
import client, { describeError } from '../api/client';
import { IconPersonCheck, IconPersonPlus, IconCross } from './Icons';

/**
 * Przycisk relacji na cudzym profilu: zapros / przyjmij / usun ze znajomych.
 *
 * <p><b>Co narysowac, decyduje pole {@code friendshipStatus} z SERWERA.</b>
 * Nie skladamy tego z kilku osobnych flag w przegladarce - jeden enum nie da
 * sie ustawic w sprzeczny stan (np. "znajomy" i jednoczesnie "zaproszenie
 * czeka"), a trzy niezalezne pola owszem.</p>
 *
 * <p>Backend i tak sprawdza uprawnienia przy kazdej operacji, wiec podmiana
 * tej wartosci w przegladarce niczego nie odblokuje - to tylko rysowanie.</p>
 */
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
      setError(details.message ?? (details.messageKey ? t(details.messageKey) : null));
    } finally {
      setWysylanie(false);
    }
  }

  const invite = () => run(
    () => client.post('/friends/requests', { username: profile.username }));

  const remove = () => run(
    () => client.delete(`/friends/${encodeURIComponent(profile.username)}`));

  /*
   * Przyjecie wymaga NUMERU zaproszenia, ktorego profil nie zawiera - musimy
   * je najpierw odnalezc na liscie przychodzacych. Alternatywa byloby dokladanie
   * id zaproszenia do kazdego profilu, mimo ze przydaje sie w jednym przypadku
   * na piec.
   */
  const accept = () => run(async () => {
    const { data } = await client.get('/friends/requests');
    const mine = data.incoming.find((z) => z.username === profile.username);
    if (mine) {
      await client.post(`/friends/requests/${mine.id}/accept`);
    }
  });

  const anuluj = () => run(async () => {
    const { data } = await client.get('/friends/requests');
    const mine = data.outgoing.find((z) => z.username === profile.username);
    if (mine) {
      await client.delete(`/friends/requests/${mine.id}`);
    }
  });

  return (
    <div className="text-end">
      {profile.friendshipStatus === 'NONE' && (
        <Button size="sm" disabled={wysylanie} onClick={invite}>
          <IconPersonPlus /> {t('friends.invite')}
        </Button>
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

import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import Button from 'react-bootstrap/Button';
import client, { opiszBlad } from '../api/client';
import { IkonaOsobaCheck, IkonaOsobaPlus, IkonaKrzyzyk } from './Ikony';

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
export default function PrzyciskZnajomosci({ profil, onZmiana }) {
  const { t } = useTranslation();
  const [wysylanie, setWysylanie] = useState(false);
  const [blad, setBlad] = useState(null);

  // Na wlasnym profilu nie ma czego zapraszac
  if (profil.friendshipStatus === 'SELF') {
    return null;
  }

  async function wykonaj(akcja) {
    setWysylanie(true);
    setBlad(null);
    try {
      await akcja();
      // Profil przeladowuje komponent nadrzedny - stamtad przychodzi nowy status
      await onZmiana();
    } catch (error) {
      const opis = opiszBlad(error);
      setBlad(opis.message ?? (opis.messageKey ? t(opis.messageKey) : null));
    } finally {
      setWysylanie(false);
    }
  }

  const zapros = () => wykonaj(
    () => client.post('/friends/requests', { username: profil.username }));

  const usun = () => wykonaj(
    () => client.delete(`/friends/${encodeURIComponent(profil.username)}`));

  /*
   * Przyjecie wymaga NUMERU zaproszenia, ktorego profil nie zawiera - musimy
   * je najpierw odnalezc na liscie przychodzacych. Alternatywa byloby dokladanie
   * id zaproszenia do kazdego profilu, mimo ze przydaje sie w jednym przypadku
   * na piec.
   */
  const przyjmij = () => wykonaj(async () => {
    const { data } = await client.get('/friends/requests');
    const moje = data.incoming.find((z) => z.username === profil.username);
    if (moje) {
      await client.post(`/friends/requests/${moje.id}/accept`);
    }
  });

  const anuluj = () => wykonaj(async () => {
    const { data } = await client.get('/friends/requests');
    const moje = data.outgoing.find((z) => z.username === profil.username);
    if (moje) {
      await client.delete(`/friends/requests/${moje.id}`);
    }
  });

  return (
    <div className="text-end">
      {profil.friendshipStatus === 'NONE' && (
        <Button size="sm" disabled={wysylanie} onClick={zapros}>
          <IkonaOsobaPlus /> {t('friends.invite')}
        </Button>
      )}

      {profil.friendshipStatus === 'REQUEST_SENT' && (
        <Button variant="outline-secondary" size="sm" disabled={wysylanie} onClick={anuluj}>
          <IkonaKrzyzyk /> {t('friends.cancelInvite')}
        </Button>
      )}

      {profil.friendshipStatus === 'REQUEST_RECEIVED' && (
        <Button size="sm" disabled={wysylanie} onClick={przyjmij}>
          <IkonaOsobaCheck /> {t('friends.accept')}
        </Button>
      )}

      {profil.friendshipStatus === 'FRIENDS' && (
        <Button
          variant="outline-secondary"
          size="sm"
          disabled={wysylanie}
          onClick={usun}
          title={t('friends.remove')}
        >
          <IkonaOsobaCheck /> {t('friends.areFriends')}
        </Button>
      )}

      {blad && <div className="text-danger small mt-1">{blad}</div>}
    </div>
  );
}

import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Spinner from 'react-bootstrap/Spinner';
import client from '../api/client';
import Avatar from './Avatar';
import { IconBell } from './Icons';
import { timeAgo } from '../utils/dates';

/** Co ile odswiezamy licznik nieprzeczytanych. */
const REFRESH_MS = 60_000;

/**
 * Dzwonek powiadomien w gornym pasku.
 *
 * <p><b>Licznik i lista sa pobierane osobno</b>, i to celowo. Liczba
 * nieprzeczytanych jest potrzebna na kazdej stronie, a lista - dopiero po
 * kliknieciu. Jedno zapytanie na oba cele oznaczaloby ciagniecie
 * kilkunastu wpisow z avatarami tylko po to, zeby narysowac kropke.</p>
 *
 * <p><b>Kazde powiadomienie gdzies prowadzi.</b> Adres wylicza serwer
 * (pole {@code link}) - patrz {@code NotificationMapper}. Frontend go tylko
 * wykonuje, wiec dolozenie nowego rodzaju powiadomienia nie wymaga tutaj
 * zadnej zmiany.</p>
 */
export default function NotificationsBell() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();

  const [open, setOpen] = useState(false);
  const [unread, setUnread] = useState(0);
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(false);

  const wrapper = useRef(null);

  const loadCount = useCallback(async () => {
    try {
      const { data } = await client.get('/notifications/unread-count');
      setUnread(data.count);
    } catch {
      // Licznik to dodatek - gdy sie nie uda, nie psujemy calego paska
      setUnread(0);
    }
  }, []);

  /*
   * Licznik odswiezamy przy kazdej zmianie adresu (to najprostszy moment,
   * w ktorym cos moglo sie zdarzyc) oraz co minute, gdy ktos siedzi na
   * jednej stronie. Bez tego drugiego powiadomienie o reakcji pojawiloby
   * sie dopiero przy nastepnym przejsciu miedzy stronami.
   */
  useEffect(() => {
    loadCount();
    const timer = setInterval(loadCount, REFRESH_MS);
    return () => clearInterval(timer);
  }, [loadCount, location.pathname]);

  async function loadList() {
    setLoading(true);
    try {
      const { data } = await client.get('/notifications', { params: { size: 15 } });
      setItems(data.content);
    } finally {
      setLoading(false);
    }
  }

  function toggle() {
    const next = !open;
    setOpen(next);
    if (next) {
      loadList();
    }
  }

  /* Klikniecie poza panelem go zamyka - tak zachowuje sie kazde menu */
  useEffect(() => {
    if (!open) return undefined;

    function onDocumentClick(event) {
      if (wrapper.current && !wrapper.current.contains(event.target)) {
        setOpen(false);
      }
    }
    function onEscape(event) {
      if (event.key === 'Escape') setOpen(false);
    }

    document.addEventListener('mousedown', onDocumentClick);
    document.addEventListener('keydown', onEscape);
    return () => {
      document.removeEventListener('mousedown', onDocumentClick);
      document.removeEventListener('keydown', onEscape);
    };
  }, [open]);

  async function openNotification(notification) {
    setOpen(false);

    /*
     * Na oznaczenie CZEKAMY, mimo ze kusi, zeby przejsc od razu.
     *
     * Powod jest konkretny: zaraz po przejsciu zmienia sie adres, a to
     * uruchamia ponowne pobranie licznika. Przy wyslaniu "w tle" serwer
     * czesto nie zdazyl jeszcze zapisac oznaczenia i wracala STARA liczba -
     * kropka wracala na dzwonek zaraz po tym, jak z niego znikla.
     *
     * Blad przy oznaczaniu nie moze zablokowac przejscia: to powiadomienie
     * juz zostalo otwarte, a nieprzeczytany wpis jest mniejszym problemem
     * niz klikniecie, ktore nigdzie nie prowadzi.
     */
    if (!notification.read) {
      setUnread((n) => Math.max(0, n - 1));
      await client.post(`/notifications/${notification.id}/read`).catch(() => {});
    }

    navigate(notification.link);
  }

  async function markAllRead() {
    setUnread(0);
    setItems((previous) => previous.map((n) => ({ ...n, read: true })));
    try {
      await client.post('/notifications/read-all');
    } catch {
      loadCount();   // nie udalo sie - wracamy do prawdziwego stanu
    }
  }

  /**
   * Tresc powiadomienia.
   *
   * <p>Tekst sklada frontend z klucza tlumaczenia, a nie serwer - inaczej
   * komunikaty nie dalyby sie przetlumaczyc na drugi jezyk bez wysylania
   * ich z backendu w obu wersjach naraz.</p>
   */
  function text(notification) {
    switch (notification.type) {
      case 'REACTION':
        return t('notifications.reaction', {
          username: notification.actorUsername,
          emoji: t(`reactions.emoji.${notification.reactionType}`),
        });
      case 'FRIEND_REQUEST':
        return t('notifications.friendRequest', { username: notification.actorUsername });
      case 'FRIEND_ACCEPTED':
        return t('notifications.friendAccepted', { username: notification.actorUsername });
      default:
        return notification.actorUsername;
    }
  }

  return (
    <div className="bell-wrapper" ref={wrapper}>
      <button
        type="button"
        className={`bell${open ? ' is-open' : ''}${unread > 0 ? ' has-unread' : ''}`}
        onClick={toggle}
        aria-label={t('notifications.title')}
        aria-expanded={open}
        title={t('notifications.title')}
      >
        <IconBell size={18} />
        {unread > 0 && (
          <span className="bell-badge">{unread > 99 ? '99+' : unread}</span>
        )}
      </button>

      {open && (
        <div className="bell-panel" role="dialog" aria-label={t('notifications.title')}>
          <div className="bell-header">
            <span className="fw-semibold">{t('notifications.title')}</span>
            {unread > 0 && (
              <button type="button" className="bell-mark-all" onClick={markAllRead}>
                {t('notifications.markAllRead')}
              </button>
            )}
          </div>

          <div className="bell-list">
            {loading && (
              <div className="text-center text-body-secondary small py-3">
                <Spinner animation="border" size="sm" className="me-2" />
                {t('common.loading')}
              </div>
            )}

            {!loading && items.length === 0 && (
              <p className="text-body-secondary small text-center py-4 mb-0">
                {t('notifications.empty')}
              </p>
            )}

            {!loading && items.map((notification) => (
              <button
                key={notification.id}
                type="button"
                className={`bell-item${notification.read ? '' : ' is-unread'}`}
                onClick={() => openNotification(notification)}
              >
                <Avatar
                  avatarUrl={notification.actorAvatarUrl}
                  username={notification.actorUsername}
                  size={36}
                />

                <span className="bell-item-body">
                  <span className="bell-item-text">{text(notification)}</span>

                  {/* Poczatek posta - zeby dalo sie poznac, ktorego dotyczy */}
                  {notification.postExcerpt && (
                    <span className="bell-item-excerpt">„{notification.postExcerpt}"</span>
                  )}

                  <span className="bell-item-time">
                    {timeAgo(notification.createdAt, i18n.language)}
                  </span>
                </span>

                {!notification.read && <span className="bell-dot" aria-hidden="true" />}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

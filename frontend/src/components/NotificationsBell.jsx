import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Spinner from 'react-bootstrap/Spinner';
import * as powiadomienia from '../api/powiadomienia';
import Avatar from './Avatar';
import DoladujWiecej from './DoladujWiecej';
import { IconBell, IconCalendar, IconClan, IconCross } from './Icons';
import { timeAgo } from '../utils/dates';
import useOdswiezanie from '../hooks/useOdswiezanie';

/** Co ile odswiezamy licznik nieprzeczytanych. */
const REFRESH_MS = 60_000;

/** Ile powiadomien pobieramy naraz - reszta czeka na "pokaz starsze". */
const LIST_SIZE = 15;

/** Dzwonek powiadomien w gornym pasku. */
export default function NotificationsBell() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();

  const [open, setOpen] = useState(false);
  const [unread, setUnread] = useState(0);
  const [items, setItems] = useState([]);
  const [strona, setStrona] = useState(0);
  const [ostatnia, setOstatnia] = useState(true);
  const [wszystkich, setWszystkich] = useState(0);
  const [loading, setLoading] = useState(false);
  const [doladowywanie, setDoladowywanie] = useState(false);

  const wrapper = useRef(null);

  const loadCount = useCallback(async () => {
    try {
      setUnread(await powiadomienia.licznik());
    } catch {
      // Licznik to dodatek - gdy sie nie uda, nie psujemy calego paska
      setUnread(0);
    }
  }, []);

  /*
   * Licznik odswiezamy przy kazdej zmianie adresu - to najprostszy moment, w ktorym cos moglo sie
   * zdarzyc.
   */
  useEffect(() => {
    loadCount();
  }, [loadCount, location.pathname]);

  /* ...oraz co minute, gdy ktos siedzi na jednej stronie. */
  useOdswiezanie(loadCount, REFRESH_MS);

  /* "dolacz" odroznia doladowanie starszych od pobrania listy od nowa. */
  async function loadList(numer, dolacz) {
    if (dolacz) {
      setDoladowywanie(true);
    } else {
      setLoading(true);
    }
    try {
      const data = await powiadomienia.lista({ strona: numer, rozmiar: LIST_SIZE });

      setItems((poprzednie) => (dolacz ? [...poprzednie, ...data.content] : data.content));
      setStrona(data.number);
      setOstatnia(data.last);
      setWszystkich(data.totalElements);
    } finally {
      setLoading(false);
      setDoladowywanie(false);
    }
  }

  /* Kazde otwarcie zaczyna od najnowszych - panel nie pamieta poprzedniego przewijania. */
  function toggle() {
    const next = !open;
    setOpen(next);
    if (next) {
      loadList(0, false);
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

    /* Na oznaczenie CZEKAMY, mimo ze kusi, zeby przejsc od razu. */
    if (!notification.read) {
      setUnread((n) => Math.max(0, n - 1));
      await powiadomienia.oznaczPrzeczytane(notification.id).catch(() => {});
    }

    navigate(notification.link);
  }

  /** Kasuje jedno powiadomienie. */
  async function removeNotification(id) {
    const removed = items.find((n) => n.id === id);
    setItems((previous) => previous.filter((n) => n.id !== id));
    setWszystkich((n) => Math.max(0, n - 1));
    if (removed && !removed.read) {
      setUnread((n) => Math.max(0, n - 1));
    }

    try {
      await powiadomienia.usun(id);
    } catch {
      loadList(0, false);
      loadCount();
    }
  }

  async function markAllRead() {
    setUnread(0);
    setItems((previous) => previous.map((n) => ({ ...n, read: true })));
    try {
      await powiadomienia.oznaczWszystkie();
    } catch {
      loadCount();   // nie udalo sie - wracamy do prawdziwego stanu
    }
  }

  /** Tresc powiadomienia. */
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
      /*
       * Powiadomienie o zgloszeniu NIE zdradza, kogo zgloszono - widac tylko, ze cos czeka w
       * panelu.
       */
      case 'REPORT':
        return t('notifications.report');
      case 'REPORT_RESOLVED':
        return t('notifications.reportResolved');
      case 'CLAN_INVITE':
        return t('notifications.clanInvite', { username: notification.actorUsername, name: notification.clanName });
      case 'CLAN_KICKED':
        return t('notifications.clanKicked', { name: notification.clanName });
      case 'CLAN_JOIN_REQUEST':
        return t('notifications.clanJoinRequest', { username: notification.actorUsername, name: notification.clanName });
      case 'CLAN_REQUEST_ACCEPTED':
        return t('notifications.clanRequestAccepted', { name: notification.clanName });
      case 'POST_COMMENT':
        return t('notifications.postComment', { username: notification.actorUsername });
      case 'COMMENT_REPLY':
        return t('notifications.commentReply', { username: notification.actorUsername });
      case 'COMMENT_MENTION':
        return t('notifications.commentMention', { username: notification.actorUsername });
      case 'EVENT_REMINDER':
        if (notification.daysLeft === 0) {
          return t('notifications.reminderToday', { name: notification.eventName });
        }
        if (notification.daysLeft === 1) {
          return t('notifications.reminderTomorrow', { name: notification.eventName });
        }
        return t('notifications.reminderDays', { count: notification.daysLeft, name: notification.eventName });
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

            {/* Wiersz sklada sie z DWOCH przyciskow obok siebie, a nie z jednego. */}
            {!loading && items.map((notification) => (
              <div key={notification.id} className="bell-item-row">
              <button
                type="button"
                className={`bell-item${notification.read ? '' : ' is-unread'}`}
                onClick={() => openNotification(notification)}
              >
                {/* Przypomnienie nie ma sprawcy - pisze je aplikacja, wiec zamiast awatara kalendarz */}
                {notification.actorUsername ? (
                  <Avatar
                    avatarUrl={notification.actorAvatarUrl}
                    username={notification.actorUsername}
                    size={36}
                  />
                ) : (
                  <span className="bell-item-icon" aria-hidden="true">
                    {notification.clanId ? <IconClan size={16} /> : <IconCalendar size={16} />}
                  </span>
                )}

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

              {/*
                Kasowanie to co innego niz "przeczytane": przeczytane gasi kropke, ale wpis
                zostaje i przy kilkudziesieciu powiadomieniach nowe gina wsrod starych.
              */}
              <button
                type="button"
                className="bell-delete"
                onClick={() => removeNotification(notification.id)}
                aria-label={t('notifications.delete')}
                title={t('notifications.delete')}
              >
                <IconCross size={14} />
              </button>
              </div>
            ))}

            {/*
              Kasowanie w otwartym panelu przesuwa liste o jedna pozycje, wiec kolejna strona
              moze wtedy pominac jeden wpis - zamkniecie i otwarcie dzwonka buduje ja od nowa.
            */}
            {!loading && items.length > 0 && wszystkich > LIST_SIZE && (
              <DoladujWiecej
                etykieta={t('notifications.loadMore')}
                pokazano={items.length}
                wszystkich={wszystkich}
                ostatnia={ostatnia}
                ladowanie={doladowywanie}
                rozmiar="sm"
                onClick={() => loadList(strona + 1, true)}
              />
            )}
          </div>
        </div>
      )}
    </div>
  );
}

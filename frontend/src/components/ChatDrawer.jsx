import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Modal from 'react-bootstrap/Modal';
import { describeError } from '../api/client';
import { rozmowy, usunRozmowe } from '../api/czat';
import { useAuth } from '../auth/AuthContext';
import { useChat } from '../chat/ChatContext';
import Avatar from './Avatar';
import ChatThread from './ChatThread';
import EmptyState from './EmptyState';
import PeopleSkeleton from './PeopleSkeleton';
import PresenceDot from './PresenceDot';
import {
  IconArrowLeft, IconChat, IconCross, IconPersonPlus, IconTrash,
} from './Icons';
import { timeAgo } from '../utils/dates';
import useOdswiezanie from '../hooks/useOdswiezanie';

/** Co ile odswiezamy liste rozmow, gdy jest widoczna. */
const LIST_REFRESH_MS = 10_000;

/* Wyszukiwarka stoi w panelu ZAWSZE, gdy jest kogokolwiek szukac. */

/** Panel czatu wysuwany z prawej strony. */
export default function ChatDrawer() {
  const { t, i18n } = useTranslation();
  const { user } = useAuth();
  const {
    open, activeUsername, closeChat, openConversation, backToList, refreshUnread,
  } = useChat();

  const [conversations, setConversations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  /* Blad pobierania trzymamy OSOBNO od pustej listy - i to nie jest ozdobnik. */
  const [error, setError] = useState(null);

  /** Z kim rozmowe kasujemy - null znaczy "okienko zamkniete". */
  const [doUsuniecia, setDoUsuniecia] = useState(null);
  const [usuwanie, setUsuwanie] = useState(false);

  const panel = useRef(null);
  /** Element, ktory mial ognisko przed otwarciem - wraca do niego po zamknieciu. */
  const returnFocusTo = useRef(null);

  const loadConversations = useCallback(async () => {
    try {
      const data = await rozmowy();
      setConversations(data);
      setError(null);
    } catch (problem) {
      /* Poprzedniej listy NIE kasujemy. */
      const details = describeError(problem, 'chat.listFailed');
      setError(details.message);
    } finally {
      setLoading(false);
    }
  }, [t]);

  /*
   * Liste odswiezamy tylko wtedy, gdy jest WIDOCZNA - czyli panel jest otwarty i nie zaslania jej
   * otwarta rozmowa.
   */
  useEffect(() => {
    if (!open || !user) {
      return undefined;
    }

    loadConversations();
    return undefined;
  }, [open, user, activeUsername, loadConversations]);

  useOdswiezanie(
    loadConversations, LIST_REFRESH_MS, open && Boolean(user) && !activeUsername,
  );

  /* Escape zamyka - tak zachowuje sie kazde okno w tej aplikacji */
  useEffect(() => {
    if (!open) return undefined;

    function onEscape(event) {
      if (event.key === 'Escape') {
        closeChat();
      }
    }
    document.addEventListener('keydown', onEscape);
    return () => document.removeEventListener('keydown', onEscape);
  }, [open, closeChat]);

  /* Ognisko klawiatury. */
  useEffect(() => {
    if (open) {
      returnFocusTo.current = document.activeElement;
      panel.current?.focus();
    } else if (returnFocusTo.current instanceof HTMLElement) {
      returnFocusTo.current.focus();
      returnFocusTo.current = null;
      // Zamkniecie panelu czysci szukanie - inaczej po ponownym otwarciu
      // lista wygladalaby na krotsza, niz jest, bez widocznego powodu
      setSearch('');
    }
  }, [open]);

  /*
   * Obie funkcje sa zapamietane (useCallback), zeby nie powstawaly na nowo przy kazdym rysowaniu
   * panelu.
   */

  /** Zdejmuje kropki z rozmowy od razu po jej otwarciu - bez czekania na serwer. */
  const markReadLocally = useCallback((username) => {
    setConversations((current) => current.map(
      (c) => (c.username === username ? { ...c, unread: 0 } : c)));
    refreshUnread();
  }, [refreshUnread]);

  /** Zapamietuje obecnosc przyniesiona przez otwarta rozmowe. */
  const updatePresence = useCallback((username, presence) => {
    setConversations((current) => current.map(
      (c) => (c.username === username ? { ...c, presence } : c)));
  }, []);

  /** Kasuje rozmowe u siebie i wraca na liste. */
  async function usunIWroc() {
    const partner = doUsuniecia;
    setUsuwanie(true);
    try {
      await usunRozmowe(partner);
      setDoUsuniecia(null);
      backToList();
      await loadConversations();
      refreshUnread();
    } catch (problem) {
      setError(describeError(problem, 'chat.deleteConversationFailed').message);
      setDoUsuniecia(null);
    } finally {
      setUsuwanie(false);
    }
  }

  if (!user) {
    return null;
  }

  const active = conversations.find((c) => c.username === activeUsername);

  /* Lista ma DWIE czesci i to jest sedno tej poprawki. */
  const matching = conversations.filter(
    (c) => c.username.toLowerCase().includes(search.trim().toLowerCase()));

  const threads = matching.filter((c) => c.lastMessage);
  const silent = matching.filter((c) => !c.lastMessage);

  // Ukrywamy ja tylko wtedy, gdy nie ma w czym szukac
  const showSearch = conversations.length > 0;

  return (
    <>
      {/* Przycisk-tlo. */}
      <button
        type="button"
        className={`chat-backdrop${open ? ' is-open' : ''}`}
        onClick={closeChat}
        tabIndex={open ? 0 : -1}
        aria-hidden={!open}
        aria-label={t('common.close')}
      />

      {/* Panel jest w drzewie ZAWSZE, tylko przesuniety poza ekran. */}
      <aside
        ref={panel}
        className={`chat-drawer${open ? ' is-open' : ''}`}
        role="dialog"
        aria-modal="false"
        aria-label={t('chat.title')}
        aria-hidden={!open}
        tabIndex={-1}
      >
        <header className="chat-head">
          {activeUsername ? (
            <>
              <button
                type="button"
                className="chat-head-button"
                onClick={backToList}
                aria-label={t('chat.backToList')}
                title={t('chat.backToList')}
              >
                <IconArrowLeft size={16} />
              </button>

              <Link
                to={`/profil/${activeUsername}`}
                className="chat-head-person"
                onClick={closeChat}
                title={t('profile.visit', { username: activeUsername })}
              >
                <Avatar
                  avatarUrl={active?.avatarUrl}
                  username={activeUsername}
                  size={30}
                />
                <span className="chat-head-text">
                  <span className="chat-head-name">{activeUsername}</span>
                  <PresenceDot presence={active?.presence} withLabel />
                </span>
              </Link>
            </>
          ) : (
            <span className="chat-head-title">
              <IconChat size={16} />
              {t('chat.title')}
            </span>
          )}

          {activeUsername && (
            <button
              type="button"
              className="chat-head-button ms-auto"
              onClick={() => setDoUsuniecia(activeUsername)}
              aria-label={t('chat.deleteConversation')}
              title={t('chat.deleteConversation')}
            >
              <IconTrash size={16} />
            </button>
          )}

          <button
            type="button"
            className={`chat-head-button${activeUsername ? '' : ' ms-auto'}`}
            onClick={closeChat}
            aria-label={t('common.close')}
            title={t('common.close')}
          >
            <IconCross size={16} />
          </button>
        </header>

        <div className="chat-body">
          {activeUsername ? (
            <ChatThread
              username={activeUsername}
              avatarUrl={active?.avatarUrl}
              friend={active?.friend ?? true}
              onPresence={updatePresence}
              onRead={markReadLocally}
            />
          ) : (
            <div className="chat-list">
              {loading && <PeopleSkeleton count={5} variant="suggestion" />}

              {/* Awaria pobierania listy. */}
              {!loading && error && (
                <Alert variant="danger" className="m-3 py-2 small">
                  {error}
                  <div className="mt-2">
                    <Button size="sm" variant="outline-danger" onClick={loadConversations}>
                      {t('common.retry')}
                    </Button>
                  </div>
                </Alert>
              )}

              {!loading && !error && conversations.length === 0 && (
                /* Czat bez znajomych to nie jest awaria - to jest poczatek. */
                <EmptyState
                  icon={IconChat}
                  title={t('chat.noFriends')}
                  text={t('chat.noFriendsHint')}
                  action={
                    <Link to="/znajomi" className="btn btn-primary" onClick={closeChat}>
                      <IconPersonPlus /> {t('friends.findPeople')}
                    </Link>
                  }
                />
              )}

              {!loading && conversations.length > 0 && (
                <>
                  {showSearch && (
                    <div className="chat-search">
                      <input
                        type="search"
                        className="form-control form-control-sm"
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                        placeholder={t('chat.searchPlaceholder')}
                        aria-label={t('chat.searchPlaceholder')}
                      />
                    </div>
                  )}

                  {/* Naglowki pokazujemy tylko wtedy, gdy jest co rozdzielac. */}
                  {threads.length > 0 && silent.length > 0 && (
                    <p className="chat-group">{t('chat.groupThreads')}</p>
                  )}
                  {threads.map((conversation) => (
                    <ChatRow
                      key={conversation.username}
                      conversation={conversation}
                      onOpen={openConversation}
                      language={i18n.language}
                      t={t}
                    />
                  ))}

                  {silent.length > 0 && threads.length > 0 && (
                    <p className="chat-group">{t('chat.groupFriends')}</p>
                  )}
                  {silent.map((conversation) => (
                    <ChatRow
                      key={conversation.username}
                      conversation={conversation}
                      onOpen={openConversation}
                      language={i18n.language}
                      t={t}
                    />
                  ))}

                  {matching.length === 0 && (
                    <p className="chat-empty">{t('chat.noMatches', { search })}</p>
                  )}
                </>
              )}
            </div>
          )}
        </div>
      </aside>

      {/* Potwierdzenie: bez hasla, bo rozmowa znika tylko u pytajacego. */}
      <Modal show={Boolean(doUsuniecia)} onHide={() => setDoUsuniecia(null)} centered>
        <Modal.Header closeButton>
          <Modal.Title className="h6">{t('chat.deleteConversationTitle')}</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {t('chat.deleteConversationText', { username: doUsuniecia })}
        </Modal.Body>
        <Modal.Footer>
          <Button variant="outline-secondary" onClick={() => setDoUsuniecia(null)}>
            {t('common.cancel')}
          </Button>
          <Button variant="danger" onClick={usunIWroc} disabled={usuwanie}>
            {usuwanie ? t('settings.working') : t('chat.deleteConversation')}
          </Button>
        </Modal.Footer>
      </Modal>
    </>
  );
}

/** Jedna linijka podgladu ostatniej wiadomosci. */
function preview(message, t) {
  if (!message) {
    return <span className="chat-row-nothing">{t('chat.noMessagesYet')}</span>;
  }

  const prefix = message.mine ? `${t('chat.you')}: ` : '';

  if (message.content) {
    return `${prefix}${message.content}`;
  }
  if (message.gif) {
    return `${prefix}${t('chat.gifPreview')}`;
  }
  // Wiadomosc bez tekstu i bez GIF-a to sam utwor - pokazujemy jego tytul
  return `${prefix}♪ ${message.musicTitle ?? t(`posts.musicKinds.${message.musicKind}`)}`;
}

/** Jeden wiersz listy - rozmowa albo znajomy, z ktorym jeszcze nic nie napisano. */
function ChatRow({ conversation, onOpen, language, t }) {
  return (
    <button
      type="button"
      className={`chat-row${conversation.unread > 0 ? ' is-unread' : ''}`}
      onClick={() => onOpen(conversation.username)}
    >
      <span className="chat-row-avatar">
        <Avatar
          avatarUrl={conversation.avatarUrl}
          username={conversation.username}
          size={40}
        />
        <PresenceDot presence={conversation.presence} />
      </span>

      <span className="chat-row-body">
        <span className="chat-row-top">
          <span className="chat-row-name">{conversation.username}</span>
          {conversation.lastMessage && (
            <span className="chat-row-time">
              {timeAgo(conversation.lastMessage.createdAt, language)}
            </span>
          )}
        </span>

        <span className="chat-row-preview">
          {/*
            Byly znajomy zostaje na liscie, ale musi byc od razu odrozniony - inaczej ktos zaczyna
            pisac i dopiero po otwarciu rozmowy dowiaduje sie, ze nie moze.
          */}
          {conversation.friend === false
            ? <span className="chat-row-former">{t('chat.formerFriend')}</span>
            : preview(conversation.lastMessage, t)}
        </span>
      </span>

      {conversation.unread > 0 && (
        <span className="chat-row-badge">
          {conversation.unread > 99 ? '99+' : conversation.unread}
        </span>
      )}
    </button>
  );
}

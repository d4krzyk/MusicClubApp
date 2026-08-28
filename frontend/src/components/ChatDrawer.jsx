import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import client, { describeError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { useChat } from '../chat/ChatContext';
import Avatar from './Avatar';
import ChatThread from './ChatThread';
import EmptyState from './EmptyState';
import PeopleSkeleton from './PeopleSkeleton';
import PresenceDot from './PresenceDot';
import { IconArrowLeft, IconChat, IconCross, IconPersonPlus } from './Icons';
import { timeAgo } from '../utils/dates';

/** Co ile odswiezamy liste rozmow, gdy jest widoczna. */
const LIST_REFRESH_MS = 10_000;

/**
 * Od ilu znajomych pokazujemy wyszukiwarke.
 *
 * <p>Przy trzech osobach pole wyszukiwania jest tylko zajetym miejscem -
 * cala lista i tak miesci sie na ekranie. Przy kilkunastu zaczyna byc
 * szybsze niz przewijanie.</p>
 */
const SEARCH_FROM = 6;

/**
 * Panel czatu wysuwany z prawej strony.
 *
 * <p><b>Panel, a nie osobna strona</b> - i to jest cala idea. Rozmowa toczy
 * sie <i>obok</i> tego, co sie akurat oglada: mozna napisac o poscie, ktory
 * ma sie przed oczami, nie tracac go z widoku. Osobna strona zmuszalaby do
 * skakania tam i z powrotem.</p>
 *
 * <p><b>Dwa widoki, jedno okno.</b> Lista znajomych i otwarty watek zajmuja
 * to samo miejsce, a nie stoja obok siebie. Panel ma dwadziescia kilka
 * centymetrow szerokosci - podzial na dwie kolumny zostawilby na wiadomosci
 * pasek na piec slow.</p>
 *
 * <p><b>Strona pod spodem NIE jest blokowana.</b> Popularne rozwiazanie -
 * zablokowanie przewijania na czas otwarcia okna - dokleja przegladarce
 * margines w miejscu paska przewijania i cala strona przeskakuje w bok
 * w chwili otwarcia. Ten sam problem naprawialismy juz przy oknach
 * Bootstrapa (patrz {@code styles.css}); nie ma powodu wprowadzac go tutaj
 * z powrotem.</p>
 */
export default function ChatDrawer() {
  const { t, i18n } = useTranslation();
  const { user } = useAuth();
  const {
    open, activeUsername, closeChat, openConversation, backToList, refreshUnread,
  } = useChat();

  const [conversations, setConversations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  /*
   * Blad pobierania trzymamy OSOBNO od pustej listy - i to nie jest
   * ozdobnik. Wczesniej nieudane zapytanie ustawialo pusta liste, wiec
   * awaria serwera wygladala identycznie jak "nie masz jeszcze znajomych":
   * uzytkownik z kilkunastoma znajomymi widzial komunikat, ze nie ma z kim
   * pisac, i nie mial zadnej wskazowki, ze cokolwiek sie zepsulo.
   * Dwa rozne stany swiata musza dawac dwa rozne ekrany.
   */
  const [error, setError] = useState(null);

  const panel = useRef(null);
  /** Element, ktory mial ognisko przed otwarciem - wraca do niego po zamknieciu. */
  const returnFocusTo = useRef(null);

  const loadConversations = useCallback(async () => {
    try {
      const { data } = await client.get('/messages/conversations');
      setConversations(data);
      setError(null);
    } catch (problem) {
      /*
       * Poprzedniej listy NIE kasujemy. Zapytanie powtarza sie co kilka
       * sekund, wiec jedna nieudana proba (chwilowy brak sieci) nie ma
       * prawa czyscic ekranu komus, kto wlasnie czyta rozmowe.
       */
      const details = describeError(problem);
      setError(details.message
        ?? (details.messageKey ? t(details.messageKey) : t('chat.listFailed')));
    } finally {
      setLoading(false);
    }
  }, [t]);

  /*
   * Liste odswiezamy tylko wtedy, gdy jest WIDOCZNA - czyli panel jest
   * otwarty i nie zaslania jej otwarta rozmowa. Przy otwartym watku
   * odpytywanie i tak chodzi w nim samym; dwa liczniki naraz pytalyby
   * o to samo.
   */
  useEffect(() => {
    if (!open || !user) {
      return undefined;
    }

    loadConversations();

    if (activeUsername) {
      return undefined;
    }
    const timer = setInterval(loadConversations, LIST_REFRESH_MS);
    return () => clearInterval(timer);
  }, [open, user, activeUsername, loadConversations]);

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

  /*
   * Ognisko klawiatury. Po otwarciu przenosimy je do panelu, po zamknieciu
   * wraca tam, skad przyszlo. Bez tego osoba korzystajaca z klawiatury
   * po zamknieciu czatu ladowala na poczatku strony i musiala przejsc
   * cala nawigacje od nowa.
   */
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
   * Obie funkcje sa zapamietane (useCallback), zeby nie powstawaly na nowo
   * przy kazdym rysowaniu panelu. Watek rozmowy broni sie przed tym takze
   * po swojej stronie, ale przekazywanie w dol funkcji zmieniajacej sie
   * co render to zaproszenie do dokladnie tego bledu, ktory juz raz tu byl.
   *
   * Stoja TU, a nie nizej przy uzyciu - hooki musza byc wywolane przy kazdym
   * rysowaniu w tej samej kolejnosci, a nizej jest juz wyjscie "brak
   * zalogowanego". Wywolanie hooka po takim wyjsciu to blad, ktory objawia
   * sie dopiero przy wylogowaniu, i to komunikatem o niczym.
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

  if (!user) {
    return null;
  }

  const active = conversations.find((c) => c.username === activeUsername);

  /*
   * Lista ma DWIE czesci i to jest sedno tej poprawki. Wczesniej wszyscy
   * znajomi stali w jednym ciagu, posortowani od najnowszej rozmowy - przez
   * co osoby, z ktorymi jeszcze nic nie napisano, ladowaly na samym dole,
   * za wszystkimi watkami. Przy kilkunastu rozmowach nie bylo ich po prostu
   * widac, a to wlasnie ich szuka ktos, kto chce ZACZAC rozmowe.
   */
  const matching = conversations.filter(
    (c) => c.username.toLowerCase().includes(search.trim().toLowerCase()));

  const threads = matching.filter((c) => c.lastMessage);
  const silent = matching.filter((c) => !c.lastMessage);

  // Wyszukiwarka ma sens dopiero przy liscie, ktorej nie widac naraz
  const showSearch = conversations.length > SEARCH_FROM;

  return (
    <>
      {/*
        Przycisk-tlo. Klikniecie obok panelu go zamyka - jak w kazdym oknie.
        Jest <button>, a nie <div>, zeby dalo sie tu dojsc klawiatura.
      */}
      <button
        type="button"
        className={`chat-backdrop${open ? ' is-open' : ''}`}
        onClick={closeChat}
        tabIndex={open ? 0 : -1}
        aria-hidden={!open}
        aria-label={t('common.close')}
      />

      {/*
        Panel jest w drzewie ZAWSZE, tylko przesuniety poza ekran. Gdyby
        powstawal dopiero przy otwarciu, przegladarka nie mialaby czego
        animowac - element pojawialby sie od razu na miejscu.
      */}
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

          <button
            type="button"
            className="chat-head-button ms-auto"
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
              onPresence={updatePresence}
              onRead={markReadLocally}
            />
          ) : (
            <div className="chat-list">
              {loading && <PeopleSkeleton count={5} variant="suggestion" />}

              {/*
                Awaria pobierania listy. Stoi NAD lista, a nie zamiast niej:
                jesli poprzednie pobranie sie udalo, rozmowy zostaja na
                ekranie i mozna dalej pisac - komunikat mowi tylko, ze
                to, co widac, moze byc nieaktualne.
              */}
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
                /*
                  Czat bez znajomych to nie jest awaria - to jest poczatek.
                  Zamiast "brak rozmow" mowimy, co zrobic, i dajemy jedno
                  klikniecie; ta sama zasada co przy pustej liscie znajomych.
                */
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

                  {/*
                    Naglowki pokazujemy tylko wtedy, gdy jest co rozdzielac.
                    Napis "Rozmowy" nad jedyna sekcja na liscie nie niesie
                    zadnej informacji, a zabiera wiersz.
                  */}
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
    </>
  );
}

/**
 * Jedna linijka podgladu ostatniej wiadomosci.
 *
 * <p>Sklada ja przegladarka, a nie serwer - inaczej "Ty: " i nazwa rodzaju
 * nagrania musialyby przyjsc z backendu w obu jezykach naraz. Ta sama
 * zasada co przy tresci powiadomien.</p>
 */
function preview(message, t) {
  if (!message) {
    return <span className="chat-row-nothing">{t('chat.noMessagesYet')}</span>;
  }

  const prefix = message.mine ? `${t('chat.you')}: ` : '';

  if (message.content) {
    return `${prefix}${message.content}`;
  }
  // Wiadomosc bez tekstu to sam utwor - pokazujemy jego tytul
  return `${prefix}♪ ${message.musicTitle ?? t(`posts.musicKinds.${message.musicKind}`)}`;
}

/**
 * Jeden wiersz listy - rozmowa albo znajomy, z ktorym jeszcze nic nie napisano.
 *
 * <p>Wydzielony, bo rysujemy go w dwoch miejscach (obie sekcje listy).
 * Powtorzony dwadziescia linijek dalej rozjechalby sie przy pierwszej
 * zmianie wygladu.</p>
 */
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
          {preview(conversation.lastMessage, t)}
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

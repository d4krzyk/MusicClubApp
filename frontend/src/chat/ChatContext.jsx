import {
  createContext, useCallback, useContext, useEffect, useMemo, useState,
} from 'react';
import { licznikNieprzeczytanych } from '../api/czat';
import { useAuth } from '../auth/AuthContext';
import useOdswiezanie from '../hooks/useOdswiezanie';

/** Co ile odswiezamy licznik nieprzeczytanych przy ZAMKNIETYM czacie. */
const IDLE_REFRESH_MS = 60_000;

const ChatContext = createContext(null);

/** Stan czatu wspolny dla calej aplikacji. */
export function ChatProvider({ children }) {
  const { user } = useAuth();

  const [open, setOpen] = useState(false);
  const [activeUsername, setActiveUsername] = useState(null);
  const [unread, setUnread] = useState(0);

  const refreshUnread = useCallback(async () => {
    try {
      setUnread(await licznikNieprzeczytanych());
    } catch {
      // Licznik to dodatek - gdy sie nie uda, nie psujemy paska
      setUnread(0);
    }
  }, []);

  /* Odpytujemy tylko wtedy, gdy czat jest ZAMKNIETY. */
  useEffect(() => {
    if (!user) {
      setUnread(0);
      setOpen(false);
      setActiveUsername(null);
      return undefined;
    }

    refreshUnread();
    return undefined;
  }, [user, open, refreshUnread]);

  useOdswiezanie(refreshUnread, IDLE_REFRESH_MS, Boolean(user) && !open);

  /**
   * Sprzetowy "wstecz" na Androidzie ma zamykac czat, a nie wychodzic z aplikacji.
   * Otwarcie czatu dokłada wpis do historii przegladarki; "wstecz" go zdejmuje,
   * a my na to reagujemy zamknieciem panelu.
   */
  useEffect(() => {
    if (!open) {
      return undefined;
    }

    window.history.pushState({ czat: true }, '');

    function naWstecz() {
      setOpen(false);
    }
    window.addEventListener('popstate', naWstecz);

    return () => {
      window.removeEventListener('popstate', naWstecz);
      /*
       * Zamkniecie z interfejsu musi zdjac wpis, ktory sami dolozylismy - inaczej
       * pierwsze "wstecz" po wyjsciu z czatu nic by nie robilo. Warunek pilnuje,
       * zeby nie cofnac cudzego wpisu: gdy czat zamknal sie przez "wstecz" albo
       * przez przejscie na profil rozmowcy, na wierzchu nie ma juz naszego stanu.
       */
      if (window.history.state?.czat) {
        window.history.back();
      }
    };
  }, [open]);

  /** Otwiera czat - z konkretna osoba albo na liscie rozmow. */
  const openChat = useCallback((username = null) => {
    setActiveUsername(username);
    setOpen(true);
  }, []);

  const closeChat = useCallback(() => setOpen(false), []);

  const value = useMemo(() => ({
    open,
    activeUsername,
    unread,
    openChat,
    closeChat,
    toggleChat: () => setOpen((was) => !was),
    openConversation: setActiveUsername,
    backToList: () => setActiveUsername(null),
    setUnread,
    refreshUnread,
  }), [open, activeUsername, unread, openChat, closeChat, refreshUnread]);

  return <ChatContext.Provider value={value}>{children}</ChatContext.Provider>;
}

/** Dostep do stanu czatu. */
export function useChat() {
  const context = useContext(ChatContext);
  if (!context) {
    throw new Error('useChat dziala tylko wewnatrz <ChatProvider>');
  }
  return context;
}

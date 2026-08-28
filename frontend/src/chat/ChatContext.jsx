import {
  createContext, useCallback, useContext, useEffect, useMemo, useState,
} from 'react';
import client from '../api/client';
import { useAuth } from '../auth/AuthContext';

/** Co ile odswiezamy licznik nieprzeczytanych przy ZAMKNIETYM czacie. */
const IDLE_REFRESH_MS = 60_000;

const ChatContext = createContext(null);

/**
 * Stan czatu wspolny dla calej aplikacji.
 *
 * <p><b>Po co osobny kontekst, skoro czat to jeden panel.</b> Bo otwiera go
 * kilka miejsc naraz: ikona w gornym pasku, przycisk "Napisz" na profilu
 * znajomego, a docelowo takze klikniecie w awatar. Bez wspolnego stanu kazde
 * z nich musialoby przekazywac "otworz czat na tej osobie" przez wszystkie
 * komponenty po drodze - a profil i pasek nie maja ze soba nic wspolnego
 * poza tym, ze oba stoja na stronie.</p>
 *
 * <p><b>Licznik nieprzeczytanych tez jest tutaj</b>, a nie w samej ikonie.
 * Zmienia go otwarty watek rozmowy (przeczytanie kasuje kropki), a rysuje
 * ikona w pasku - to dwa rozne komponenty, ktore musza widziec te sama
 * liczbe.</p>
 */
export function ChatProvider({ children }) {
  const { user } = useAuth();

  const [open, setOpen] = useState(false);
  const [activeUsername, setActiveUsername] = useState(null);
  const [unread, setUnread] = useState(0);

  const refreshUnread = useCallback(async () => {
    try {
      const { data } = await client.get('/messages/unread-count');
      setUnread(data.count);
    } catch {
      // Licznik to dodatek - gdy sie nie uda, nie psujemy paska
      setUnread(0);
    }
  }, []);

  /*
   * Odpytujemy tylko wtedy, gdy czat jest ZAMKNIETY. Przy otwartym panelu
   * licznik i tak przychodzi razem z kazda odpowiedzia o nowe wiadomosci
   * (patrz ConversationSyncResponse) - dodatkowe zapytanie co minute byloby
   * pytaniem o cos, co wlasnie przyszlo.
   */
  useEffect(() => {
    if (!user) {
      setUnread(0);
      setOpen(false);
      setActiveUsername(null);
      return undefined;
    }

    refreshUnread();

    if (open) {
      return undefined;
    }
    const timer = setInterval(refreshUnread, IDLE_REFRESH_MS);
    return () => clearInterval(timer);
  }, [user, open, refreshUnread]);

  /**
   * Otwiera czat - z konkretna osoba albo na liscie rozmow.
   *
   * <p>Ponowne klikniecie w ikone przy otwartym panelu go zamyka; to samo
   * zachowanie ma dzwonek powiadomien obok.</p>
   */
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

/**
 * Dostep do stanu czatu.
 *
 * <p>Rzuca zrozumialym bledem zamiast oddawac {@code undefined} - inaczej
 * uzycie poza dostawca konczy sie komunikatem o odczycie pola z niczego,
 * kilka warstw dalej.</p>
 */
export function useChat() {
  const context = useContext(ChatContext);
  if (!context) {
    throw new Error('useChat dziala tylko wewnatrz <ChatProvider>');
  }
  return context;
}

import { useTranslation } from 'react-i18next';
import { useChat } from '../chat/ChatContext';
import { IconChat } from './Icons';

/**
 * Ikona czatu w gornym pasku, z liczba nieprzeczytanych.
 *
 * <p>Stoi przy dzwonku powiadomien, bo jedno i drugie dotyczy MNIE -
 * w odroznieniu od jezyka i motywu, ktore dotycza calej strony.</p>
 *
 * <p>Sam licznik trzyma {@code ChatContext}, a nie ten komponent: zmienia go
 * otwarta rozmowa (przeczytanie kasuje kropki), a rysuje ikona. To dwa rozne
 * miejsca, ktore musza widziec te sama liczbe.</p>
 */
export default function ChatLauncher() {
  const { t } = useTranslation();
  const { open, unread, toggleChat } = useChat();

  return (
    <button
      type="button"
      className={`chat-launcher${open ? ' is-open' : ''}${unread > 0 ? ' has-unread' : ''}`}
      onClick={toggleChat}
      aria-label={t('chat.title')}
      aria-expanded={open}
      title={t('chat.title')}
    >
      <IconChat size={18} />

      {/* Stale "0" przy ikonie to szum - liczba pojawia sie, gdy jest co liczyc */}
      {unread > 0 && (
        <span className="chat-launcher-badge">{unread > 99 ? '99+' : unread}</span>
      )}
    </button>
  );
}

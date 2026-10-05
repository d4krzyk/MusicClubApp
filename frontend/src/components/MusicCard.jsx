import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { IconNote } from './Icons';
import { playerHeight } from '../utils/player';
import { toMinutes } from '../utils/time';

/**
 * Nagranie dolaczone do wiadomosci - najpierw wizytowka, dopiero po kliknieciu odtwarzacz.
 *
 * Karta i odtwarzacz maja wlasna szerokosc (style/czat.css: 20rem, najwyzej caly dymek). Wczesniej odtwarzacz mial
 * "100%" dymka, a dymek dopasowuje sie do tresci - wiec film z YouTube'a kurczyl sie do szerokosci podpisu
 * (zmierzone: 104x59 px). YouTube dostaje karte 16:9: miniatura z oEmbed ma 4:3 z czarnymi pasami, a kadr 16:9
 * z object-fit: cover ucina dokladnie te pasy (480x360 -> 480x270).
 */
export default function MusicCard({ message }) {
  const { t } = useTranslation();
  const [playing, setPlaying] = useState(false);

  if (!message.musicEmbedUrl) {
    return null;
  }

  const height = playerHeight(message.musicProvider, message.musicKind);
  // YouTube dostaje proporcje 16:9 zamiast sztywnej wysokosci - patrz utils/player.js
  const hasVideo = height === null;
  const tytul = message.musicTitle ?? t(`posts.musicKinds.${message.musicKind}`);

  if (playing) {
    return (
      <div className={`player-frame chat-player${hasVideo ? ' ratio ratio-16x9' : ''}`}>
        <iframe
          src={hasVideo ? zAutoodtwarzaniem(message.musicEmbedUrl) : message.musicEmbedUrl}
          title={message.musicTitle ?? t(`posts.providers.${message.musicProvider}`)}
          height={hasVideo ? undefined : height}
          loading="lazy"
          allow="autoplay; encrypted-media; clipboard-write; fullscreen; picture-in-picture"
          style={hasVideo ? undefined : { width: '100%', border: 0 }}
        />
      </div>
    );
  }

  if (hasVideo) {
    return (
      <div className="music-card is-wideo">
        <button
          type="button"
          className="music-card-kadr"
          onClick={() => setPlaying(true)}
          aria-label={t('chat.playVideo', { title: tytul })}
          title={t('chat.play')}
        >
          {message.musicThumbnailUrl ? (
            <img src={message.musicThumbnailUrl} alt="" loading="lazy" referrerPolicy="no-referrer" />
          ) : (
            <span className="music-card-kadr-pusty" aria-hidden="true"><IconNote size={28} /></span>
          )}
          <span className="music-card-odtworz" aria-hidden="true">
            <svg viewBox="0 0 16 16" width="18" height="18" fill="currentColor"><path d="M5 3.5v9l7.5-4.5z" /></svg>
          </span>
        </button>
        <div className="music-card-podpis">
          <span className="music-card-text">
            <span className="music-card-title">{tytul}</span>
            <span className="music-card-provider">
              {t('chat.youtube')}
              {message.musicStartSeconds > 0 && ` · ${toMinutes(message.musicStartSeconds)}`}
            </span>
          </span>
          <a href={message.musicUrl} target="_blank" rel="noreferrer noopener" className="music-card-open"
            title={t('chat.openInService')} aria-label={t('chat.openInService')}>
            ↗
          </a>
        </div>
      </div>
    );
  }

  return (
    <div className="music-card">
      <button
        type="button"
        className="music-card-play"
        onClick={() => setPlaying(true)}
        aria-label={t('chat.play')}
        title={t('chat.play')}
      >
        {message.musicThumbnailUrl ? (
          <img src={message.musicThumbnailUrl} alt="" className="music-card-cover" referrerPolicy="no-referrer" />
        ) : (
          /*
           * Zastepnik, gdy serwis nie oddal miniaturki (Apple Music nie ma publicznego oEmbed).
           */
          <span className="music-card-cover music-card-cover-empty" aria-hidden="true">
            <IconNote size={18} />
          </span>
        )}

        <span className="music-card-text">
          <span className="music-card-title">{tytul}</span>
          <span className="music-card-provider">
            {t(`posts.providers.${message.musicProvider}`)}
            {message.musicStartSeconds > 0 && ` · ${toMinutes(message.musicStartSeconds)}`}
          </span>
        </span>
      </button>

      {/* Link do serwisu OBOK przycisku odtwarzania, a nie zamiast niego. */}
      <a
        href={message.musicUrl}
        target="_blank"
        rel="noreferrer noopener"
        className="music-card-open"
        title={t('chat.openInService')}
      >
        ↗
      </a>
    </div>
  );
}

/** Kto kliknal "odtworz" na karcie, ten chce sluchac - bez autoplay YouTube kazalby kliknac drugi raz. */
function zAutoodtwarzaniem(adres) {
  return `${adres}${adres.includes('?') ? '&' : '?'}autoplay=1`;
}

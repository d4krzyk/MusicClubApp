import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { IconNote } from './Icons';
import { playerHeight } from '../utils/player';
import { toMinutes } from '../utils/time';

/** Nagranie dolaczone do wiadomosci - najpierw wizytowka, dopiero po kliknieciu odtwarzacz. */
export default function MusicCard({ message }) {
  const { t } = useTranslation();
  const [playing, setPlaying] = useState(false);

  if (!message.musicEmbedUrl) {
    return null;
  }

  const height = playerHeight(message.musicProvider, message.musicKind);
  // YouTube dostaje proporcje 16:9 zamiast sztywnej wysokosci - patrz utils/player.js
  const hasVideo = height === null;

  if (playing) {
    return (
      <div className={`player-frame chat-player${hasVideo ? ' ratio ratio-16x9' : ''}`}>
        <iframe
          src={message.musicEmbedUrl}
          title={message.musicTitle ?? t(`posts.providers.${message.musicProvider}`)}
          height={hasVideo ? undefined : height}
          loading="lazy"
          allow="encrypted-media; clipboard-write; fullscreen; picture-in-picture"
          style={hasVideo ? undefined : { width: '100%', border: 0 }}
        />
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
          <img src={message.musicThumbnailUrl} alt="" className="music-card-cover" />
        ) : (
          /*
           * Zastepnik, gdy serwis nie oddal miniaturki (Apple Music nie ma publicznego oEmbed).
           */
          <span className="music-card-cover music-card-cover-empty" aria-hidden="true">
            <IconNote size={18} />
          </span>
        )}

        <span className="music-card-text">
          <span className="music-card-title">
            {message.musicTitle ?? t(`posts.musicKinds.${message.musicKind}`)}
          </span>
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

import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { IconNote } from './Icons';
import { playerHeight } from '../utils/player';
import { toMinutes } from '../utils/time';

/**
 * Nagranie dolaczone do wiadomosci - <b>najpierw wizytowka, dopiero
 * po kliknieciu odtwarzacz</b>.
 *
 * <p><b>Dlaczego nie od razu odtwarzacz, tak jak w postach.</b> Bo to sa dwie
 * rozne sytuacje. Post oglada sie pojedynczo, przewijajac tablice; rozmowa to
 * kilkanascie dymkow naraz na waskim panelu. Dziesiec osadzonych ramek
 * Spotify w jednej rozmowie oznacza dziesiec obcych stron ladowanych
 * jednoczesnie - panel staje sie ociezaly, a przewijanie skacze, bo kazda
 * z nich dochodzi w swoim czasie i dopiero wtedy zajmuje miejsce.</p>
 *
 * <p>Wizytowka ma stala wysokosc, wiec rozmowa nie podskakuje. Odtwarzacz
 * pojawia sie dopiero tam, gdzie ktos naprawde chce posluchac.</p>
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
             Zastepnik, gdy serwis nie oddal miniaturki (Apple Music nie ma
             publicznego oEmbed). Pusta ramka wygladalaby jak blad ladowania.
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

      {/*
        Link do serwisu OBOK przycisku odtwarzania, a nie zamiast niego.
        Czesc ludzi slucha w aplikacji Spotify, a nie w przegladarce - i dla
        nich osadzony odtwarzacz jest bezuzyteczny.
      */}
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
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

/**
 * GIF w komentarzu albo wiadomosci. Wymiary z serwera rezerwuja miejsce jeszcze przed zaladowaniem, wiec lista
 * nie skacze; gdy plik zniknie u dostawcy, zostaje podpis zamiast pustego prostokata.
 */
export default function GifObrazek({ gif, className = '' }) {
  const { t } = useTranslation();
  const [zepsuty, setZepsuty] = useState(false);

  if (!gif) {
    return null;
  }

  if (zepsuty) {
    return <span className={`gif-obrazek-brak ${className}`}>{t('gifs.gone')}</span>;
  }

  return (
    <img
      className={`gif-obrazek ${className}`}
      src={gif.url}
      width={gif.width || undefined}
      height={gif.height || undefined}
      alt={gif.title || t('gifs.alt')}
      loading="lazy"
      decoding="async"
      referrerPolicy="no-referrer"
      onError={() => setZepsuty(true)}
    />
  );
}

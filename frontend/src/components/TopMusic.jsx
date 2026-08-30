import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { topMuzyka } from '../api/profil';

/** Ile pozycji pokazuje zestawienie - stad "top 5" w nazwie sekcji. */
const TOP_LIMIT = 5;

/** "Najczesciej wrzucane" - top 5 nagran z postow uzytkownika. */
export default function TopMusic({ username, refresh }) {
  const { t } = useTranslation();
  const [items, setItems] = useState([]);

  const fetch = useCallback(async () => {
    try {
      setItems(await topMuzyka(username, 'TRACK', TOP_LIMIT));
    } catch {
      // Zestawienie to dodatek - gdy padnie, reszta profilu ma dzialac dalej
      setItems([]);
    }
  }, [username]);

  useEffect(() => {
    fetch();
  }, [fetch, refresh]);

  if (items.length === 0) {
    return null;   // nikt jeszcze nic nie wrzucil - nie pokazujemy pustej sekcji
  }

  return (
    <div className="profil-panel mb-4">
      <h2 className="h5 mb-2">{t('posts.topTracks')}</h2>

      <ol className="top-list">
        {items.map((p, i) => (
          <li key={`${p.provider}-${p.url}`} className="d-flex align-items-center gap-2 py-1">
            <span className="top-number text-body-secondary">{i + 1}</span>

            {p.thumbnailUrl && (
              <img
                src={p.thumbnailUrl}
                alt=""
                className="top-thumb rounded"
                /* Gdy obrazek zniknie z serwisu, chowamy go - lepiej pusto niz brzydko */
                onError={(e) => { e.currentTarget.style.display = 'none'; }}
              />
            )}

            <a
              href={p.url}
              target="_blank"
              rel="noopener noreferrer"
              className="flex-grow-1 text-truncate text-decoration-none text-body"
            >
              {p.title ?? t(`posts.providers.${p.provider}`)}
            </a>

            <span className="text-body-secondary small text-nowrap">
              {t('posts.postedTimes', { count: p.postCount })}
            </span>
          </li>
        ))}
      </ol>
    </div>
  );
}

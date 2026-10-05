import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { describeError } from '../../api/client';
import { profilArtysty } from '../../api/wydarzenia';
import { IconExternal, IconStar } from '../Icons';

/** Kolejnosc i nazwy linkow artysty (z Ticketmastera i innych zrodel). */
const LINKI = ['HOMEPAGE', 'SPOTIFY', 'YOUTUBE', 'INSTAGRAM', 'FACEBOOK', 'TIKTOK', 'BANDCAMP', 'SOUNDCLOUD', 'WIKI'];

/**
 * Sklad z odpowiedzia na "kim to jest": gatunki i linki od razu (z bazy), a bio z Last.fm dopiero po rozwinieciu
 * (serwer trzyma je w pamieci podrecznej, wiec drugi raz nie pyta Last.fm). Bio to zwykly tekst - nigdy HTML - i ma
 * przypisanie z linkiem, jak wymaga licencja tresci Last.fm (CC BY-SA).
 */
export default function Wykonawcy({ sklad }) {
  const { t, i18n } = useTranslation();
  const [otwarte, setOtwarte] = useState({});
  const [profile, setProfile] = useState({});

  async function przelacz(nazwa) {
    const nowe = !otwarte[nazwa];
    setOtwarte((o) => ({ ...o, [nazwa]: nowe }));
    if (nowe && !profile[nazwa]) {
      setProfile((p) => ({ ...p, [nazwa]: { ladowanie: true } }));
      try {
        const dane = await profilArtysty(nazwa, i18n.language);
        setProfile((p) => ({ ...p, [nazwa]: dane }));
      } catch (problem) {
        setProfile((p) => ({ ...p, [nazwa]: { blad: describeError(problem).message } }));
      }
    }
  }

  return (
    <ul className="wykonawcy list-unstyled">
      {sklad.map((w, i) => {
        const p = profile[w.name];
        const otwarty = Boolean(otwarte[w.name]);
        const idPanelu = `wykonawca-${i}`;
        return (
          <li key={`${w.name}-${i}`} className={`wykonawca${i === 0 ? ' is-pierwszy' : ''}`}>
            <div className="wykonawca-glowa">
              <span className="wykonawca-inicjal" aria-hidden="true">{w.name.charAt(0).toUpperCase()}</span>
              <span className="wykonawca-opis">
                <span className="wykonawca-nazwa">
                  {w.name}
                  {w.favorite && (
                    <span className="wykonawca-ulubiony" title={t('events.artist.favorite')}>
                      <IconStar size={12} /> <span className="visually-hidden">{t('events.artist.favorite')}</span>
                    </span>
                  )}
                </span>
                {w.tags?.length > 0 && <span className="wykonawca-tagi">{w.tags.slice(0, 3).join(' · ')}</span>}
              </span>
              <button
                type="button"
                className="btn btn-sm btn-link wykonawca-przycisk"
                aria-expanded={otwarty}
                aria-controls={idPanelu}
                onClick={() => przelacz(w.name)}
              >
                {otwarty ? t('events.artist.hide') : t('events.artist.who')}
              </button>
            </div>

            {otwarty && (
              <div id={idPanelu} className="wykonawca-panel">
                {p?.ladowanie && <div className="wykonawca-szkielet" aria-busy="true" aria-label={t('common.loading')} />}
                {p?.blad && <p className="small text-danger mb-0">{p.blad}</p>}
                {p && !p.ladowanie && !p.blad && (
                  <>
                    {p.bio ? (
                      <p className="wykonawca-bio">{p.bio}</p>
                    ) : (
                      <p className="small text-body-secondary mb-2">{t('events.artist.noBio')}</p>
                    )}
                    <ul className="wykonawca-fakty list-unstyled">
                      {p.listeners > 0 && (
                        <li>{t('events.artist.listeners', { count: p.listeners, liczba: new Intl.NumberFormat(i18n.language).format(p.listeners) })}</li>
                      )}
                      {p.similar?.length > 0 && (
                        <li>{t('events.artist.similar')}: {p.similar.slice(0, 5).join(', ')}</li>
                      )}
                    </ul>
                    {p.bio && p.bioUrl && (
                      <p className="wykonawca-zrodlo small mb-2">
                        <a href={p.bioUrl} target="_blank" rel="noopener noreferrer">
                          {t('events.artist.readMore')} <IconExternal size={10} />
                        </a>
                        <span className="text-body-secondary"> · {t('events.artist.license')}</span>
                      </p>
                    )}
                  </>
                )}
                {(() => {
                  const linki = [...(w.links ?? []), ...((p?.links ?? []).filter((l) => !(w.links ?? []).some((x) => x.kind === l.kind)))]
                    .filter((l) => LINKI.includes(l.kind))
                    .sort((a, b) => LINKI.indexOf(a.kind) - LINKI.indexOf(b.kind));
                  return linki.length > 0 && (
                    <div className="wykonawca-linki">
                      {linki.map((l) => (
                        <a key={l.kind} href={l.url} target="_blank" rel="noopener noreferrer nofollow" className="wykonawca-link">
                          {t(`events.artist.links.${l.kind}`)} <IconExternal size={10} />
                        </a>
                      ))}
                    </div>
                  );
                })()}
              </div>
            )}
          </li>
        );
      })}
    </ul>
  );
}

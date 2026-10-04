import { forwardRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import ClanBadge from '../ClanBadge';
import { IconInfo, IconPin } from '../Icons';
import MiernikGustu from './MiernikGustu';

/** Odcien tla karty bez zdjec - staly dla loginu, zeby ta sama osoba zawsze wygladala tak samo. */
export function odcien(login) {
  let h = 0;
  for (const znak of login) {
    h = (h * 31 + znak.charCodeAt(0)) % 360;
  }
  return h;
}

/**
 * Karta osoby w trybie Poznawaj: na gorze zdjecia (stukniecie w prawa czesc - nastepne, w lewa - poprzednie),
 * pod nimi to, co ta osoba o sobie napisala, i to, co was laczy. Karta przewija sie w pionie; przesuwanie w bok
 * obsluguje talia ({@link PrzesuwanaKarta}). Ta sama karta sluzy za podglad wlasnej (bez czesci "wspolnej").
 */
const KartaPoznawaj = forwardRef(function KartaPoznawaj({ karta, podglad = false, przyciski = null, onSzczegoly }, ref) {
  const { t } = useTranslation();
  const [ktore, setKtore] = useState(0);

  /* Bez zdjec w galerii okladka jest awatar; bez awatara - kolorowe tlo z inicjalem */
  const zdjecia = karta.photos.length > 0 ? karta.photos : (karta.avatarUrl ? [karta.avatarUrl] : []);
  const biezace = Math.min(ktore, Math.max(0, zdjecia.length - 1));

  function stuknij(e) {
    if (zdjecia.length < 2) {
      return;
    }
    const obszar = e.currentTarget.getBoundingClientRect();
    const wLewo = e.clientX - obszar.left < obszar.width / 3;
    setKtore((i) => Math.max(0, Math.min(zdjecia.length - 1, i + (wLewo ? -1 : 1))));
  }

  const miejsce = karta.city
    ? (karta.proximity === 'SAME_CITY'
      ? t('discover.proximity.SAME_CITY')
      : [karta.city, karta.proximity ? t(`discover.proximity.${karta.proximity}`) : null].filter(Boolean).join(' · '))
    : null;

  return (
    <article className="pz-karta" ref={ref} aria-label={t('discover.cardOf', { username: karta.username })}>
      <div
        className="pz-okladka"
        onClick={stuknij}
        style={zdjecia.length === 0 ? { '--pz-odcien': odcien(karta.username) } : undefined}
      >
        {zdjecia.length > 0 ? (
          <img
            key={zdjecia[biezace]}
            src={zdjecia[biezace]}
            alt={t('discover.photoOf', { username: karta.username, n: biezace + 1, count: zdjecia.length })}
            className="pz-zdjecie"
            draggable={false}
          />
        ) : (
          <div className="pz-zastepcze" aria-hidden="true">
            <span>{karta.username.charAt(0)}</span>
          </div>
        )}

        {/* Paski u gory: ile zdjec i ktore teraz widac */}
        {zdjecia.length > 1 && (
          <div className="pz-paski" aria-hidden="true">
            {zdjecia.map((url, i) => <span key={url} className={i === biezace ? 'is-teraz' : ''} />)}
          </div>
        )}

        <div className="pz-nakladka">
          <div className="pz-imie">
            <h2 className="pz-login">{karta.username}</h2>
            {karta.newcomer && <span className="pz-nowa">{t('discover.newcomer')}</span>}
          </div>
          {karta.clan && <ClanBadge clan={karta.clan} link={false} className="pz-klan" />}
          {miejsce && (
            <div className="pz-miejsce"><IconPin size={12} /> {miejsce}</div>
          )}
          {!podglad && <MiernikGustu poziom={karta.tasteLevel} />}
          {onSzczegoly && (
            <button
              type="button"
              className="pz-wiecej"
              onClick={(e) => { e.stopPropagation(); onSzczegoly(); }}
              aria-label={t('discover.details')}
              title={t('discover.details')}
            >
              <IconInfo size={18} />
            </button>
          )}
        </div>
      </div>

      <div className="pz-opis">
        {/* Co laczy - najpierw, bo po to jest ta karta */}
        {!podglad && (karta.sharedArtists.length > 0 || karta.sharedGenres.length > 0) && (
          <section className="pz-sekcja">
            <h3 className="pz-naglowek">{t('discover.inCommon')}</h3>
            {karta.sharedArtists.length > 0 && (
              <ul className="pz-artysci list-unstyled">
                {karta.sharedArtists.map((a) => <Artysta key={a.name} artysta={a} wspolny />)}
                {karta.sharedArtistCount > karta.sharedArtists.length && (
                  <li className="pz-artysta-wiecej">
                    +{karta.sharedArtistCount - karta.sharedArtists.length}
                  </li>
                )}
              </ul>
            )}
            {karta.sharedGenres.length > 0 && (
              <div className="genre-pills mt-2">
                {karta.sharedGenres.map((g) => <span key={g} className="genre-pill">{g}</span>)}
              </div>
            )}
            {karta.sharedTrackCount > 0 && (
              <p className="pz-drobne mb-0 mt-2">{t('discover.sharedTracks', { count: karta.sharedTrackCount })}</p>
            )}
          </section>
        )}

        {karta.bio && (
          <section className="pz-sekcja">
            <h3 className="pz-naglowek">{t('card.bio')}</h3>
            <p className="pz-bio mb-0">{karta.bio}</p>
          </section>
        )}

        {karta.lookingFor.length > 0 && (
          <section className="pz-sekcja">
            <h3 className="pz-naglowek">{t('card.lookingFor')}</h3>
            <div className="pz-szukam">
              {karta.lookingFor.map((l) => <span key={l} className="pz-szukam-chip">{t(`card.looking.${l}`)}</span>)}
            </div>
          </section>
        )}

        {karta.prompts.map((p) => (
          <section key={p.prompt} className="pz-sekcja pz-pytanie">
            <h3 className="pz-pytanie-tresc">{t(`card.prompt.${p.prompt}`)}</h3>
            <p className="pz-odpowiedz mb-0">{p.answer}</p>
          </section>
        ))}

        {karta.otherArtists.length > 0 && (
          <section className="pz-sekcja">
            <h3 className="pz-naglowek">{podglad ? t('discover.favorites') : t('discover.alsoListens')}</h3>
            <ul className="pz-artysci list-unstyled">
              {karta.otherArtists.map((a) => <Artysta key={a.name} artysta={a} />)}
            </ul>
          </section>
        )}

        {!podglad && karta.mutualFriends > 0 && (
          <p className="pz-drobne">{t('friends.mutual', { count: karta.mutualFriends })}</p>
        )}

        {!podglad && (
          <p className="pz-drobne">
            <Link to={`/profil/${encodeURIComponent(karta.username)}`} onClick={(e) => e.stopPropagation()}>
              {t('discover.openProfile')}
            </Link>
          </p>
        )}

        {przyciski && <div className="pz-moderacja">{przyciski}</div>}
      </div>
    </article>
  );
});

export default KartaPoznawaj;

/** Wykonawca na karcie: okladka (albo inicjal) i nazwa; wspolny ma obwodke w kolorze marki. */
function Artysta({ artysta, wspolny = false }) {
  return (
    <li className={`pz-artysta${wspolny ? ' is-wspolny' : ''}`}>
      {artysta.imageUrl ? (
        <img src={artysta.imageUrl} alt="" loading="lazy" draggable={false} referrerPolicy="no-referrer" />
      ) : (
        <span className="pz-artysta-inicjal" aria-hidden="true">{artysta.name.charAt(0)}</span>
      )}
      <span className="pz-artysta-nazwa">{artysta.name}</span>
    </li>
  );
}

import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import useInfoSerwera from '../hooks/useInfoSerwera';
import regulamin from '../legal/regulamin';
import polityka from '../legal/polityka';

const DOKUMENTY = { regulamin, polityka };

/**
 * Regulamin albo polityka prywatnosci. Tresc siedzi w src/legal (PL i EN), a dane administratora,
 * dostawcow i wersja - w konfiguracji serwera, wiec zmiana kontaktu nie wymaga przebudowy.
 * Strona jest publiczna: czyta ja ktos, kto dopiero zaklada konto.
 */
export default function PrawnaPage({ dokument }) {
  const { t, i18n } = useTranslation();
  const info = useInfoSerwera();
  const jezyk = String(i18n.language).startsWith('en') ? 'en' : 'pl';
  const tresc = DOKUMENTY[dokument][jezyk];
  const brak = t('legal.missing');

  /* {{klucz}} -> wartosc z serwera; gdy pusta, widoczna prosba o uzupelnienie zamiast dziury w tekscie */
  const wartosci = {
    administrator: info?.controller || brak,
    kontakt: info?.contactEmail || brak,
    hosting: info?.hosting || brak,
    poczta: info?.mailProvider || brak,
    wersja: info?.termsVersion ?? '…',
  };
  const wypelnij = (tekst) => tekst.replace(/\{\{(\w+)\}\}/g, (calosc, klucz) => wartosci[klucz] ?? calosc);

  const uzupelnione = info && info.controller && info.contactEmail;

  return (
    <article className="prawna-strona mx-auto tiles-in">
      <h1 className="h3 mb-1">{tresc.tytul}</h1>
      <p className="text-body-secondary small mb-3">
        {t('legal.version', { version: info?.termsVersion ?? '…' })}
        {' · '}
        {dokument === 'regulamin'
          ? <Link to="/polityka-prywatnosci">{t('legal.privacy')}</Link>
          : <Link to="/regulamin">{t('legal.terms')}</Link>}
      </p>

      {info && !uzupelnione && (
        <Alert variant="warning" className="small">{t('legal.unfilled')}</Alert>
      )}

      <nav className="prawna-spis card card-body mb-4" aria-label={t('legal.toc')}>
        <ol className="mb-0 ps-3 small">
          {tresc.sekcje.map((s, i) => (
            <li key={s.tytul}><a href={`#sekcja-${i + 1}`}>{s.tytul.replace(/^\d+\.\s*/, '')}</a></li>
          ))}
        </ol>
      </nav>

      {tresc.sekcje.map((s, i) => (
        <section key={s.tytul} id={`sekcja-${i + 1}`} className="mb-4">
          <h2 className="h5">{s.tytul}</h2>
          {s.akapity.map((a, j) => (typeof a === 'string'
            // eslint-disable-next-line react/no-array-index-key
            ? <p key={j}>{wypelnij(a)}</p>
            // eslint-disable-next-line react/no-array-index-key
            : <ul key={j}>{a.lista.map((punkt) => <li key={punkt} className="mb-1">{wypelnij(punkt)}</li>)}</ul>))}
        </section>
      ))}
    </article>
  );
}

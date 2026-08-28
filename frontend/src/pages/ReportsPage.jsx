import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Card from 'react-bootstrap/Card';
import Button from 'react-bootstrap/Button';
import ButtonGroup from 'react-bootstrap/ButtonGroup';
import Form from 'react-bootstrap/Form';
import Alert from 'react-bootstrap/Alert';
import Badge from 'react-bootstrap/Badge';
import client, { describeError } from '../api/client';
import Avatar from '../components/Avatar';
import EmptyState from '../components/EmptyState';
import PostSkeleton from '../components/PostSkeleton';
import { IconCheckCircle, IconFlag, IconShieldAlert } from '../components/Icons';
import { formatDate, timeAgo } from '../utils/dates';

/** Filtry w kolejnosci przydatnosci: najpierw to, co czeka na decyzje. */
const FILTERS = ['OPEN', 'RESOLVED', 'DISMISSED', 'ALL'];

/** Te same okresy co w panelu kont - kara ma znaczyc wszedzie to samo. */
const BAN_HOURS = [1, 24, 168, 720];

/** Wartosc pozycji "na zawsze" - patrz komentarz w UsersPage. */
const FOREVER = 'forever';

/**
 * Panel zgloszen - <b>tylko dla administratora</b>.
 *
 * <p>Strona otwiera sie na zgloszeniach OTWARTYCH, bo to jedyne, z ktorymi
 * jest cos do zrobienia. Zamkniete sa dostepne pod filtrem: potrzebne przy
 * sprawdzaniu historii konta, ale nie przy codziennej pracy.</p>
 *
 * <p><b>Dowody sa doczytywane na zadanie.</b> Lista pokazuje naglowki,
 * a migawke rozmowy pobieramy dopiero po rozwinieciu jednego zgloszenia -
 * dwadziescia rozmow po dwadziescia wiadomosci to kilkaset linijek tekstu
 * na jedno wejscie na strone.</p>
 */
export default function ReportsPage() {
  const { t, i18n } = useTranslation();

  const [filter, setFilter] = useState('OPEN');
  const [reports, setReports] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const { data } = await client.get('/reports/admin', {
        params: { status: filter === 'ALL' ? undefined : filter, size: 30 },
      });
      setReports(data.content);
    } catch (problem) {
      const details = describeError(problem);
      setError(details.message ?? (details.messageKey ? t(details.messageKey) : null));
      setReports([]);
    } finally {
      setLoading(false);
    }
  }, [filter, t]);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <div className="reports-page">
      {/*
        Tytul w osobnym wierszu. Klasa .page-title jest inline-block (podkreslenie
        ma miec szerokosc tekstu, a nie calej strony) - bez tego opakowania
        grupa przyciskow ustawia sie OBOK niego i przy dluzszym tytule oba
        elementy na siebie wchodza. Widac to bylo dopiero na zrzucie.
      */}
      <div>
        <h1 className="page-title">{t('reports.panelTitle')}</h1>
      </div>

      <ButtonGroup size="sm" className="mb-3">
        {FILTERS.map((code) => (
          <Button
            key={code}
            variant={filter === code ? 'primary' : 'outline-secondary'}
            onClick={() => setFilter(code)}
            aria-pressed={filter === code}
          >
            {t(`reports.filters.${code}`)}
          </Button>
        ))}
      </ButtonGroup>

      {error && <Alert variant="danger">{error}</Alert>}

      {loading && <PostSkeleton count={3} />}

      {!loading && reports.length === 0 && (
        <EmptyState
          icon={filter === 'OPEN' ? IconCheckCircle : IconShieldAlert}
          title={filter === 'OPEN' ? t('reports.emptyOpen') : t('reports.emptyOther')}
          text={filter === 'OPEN' ? t('reports.emptyOpenHint') : null}
        />
      )}

      {!loading && reports.map((report) => (
        <ReportCard
          key={report.id}
          report={report}
          language={i18n.language}
          t={t}
          onResolved={load}
        />
      ))}
    </div>
  );
}

/** Jedno zgloszenie: naglowek, opis, dowody i decyzja. */
function ReportCard({ report, language, t, onResolved }) {
  const [expanded, setExpanded] = useState(false);
  const [details, setDetails] = useState(null);
  const [note, setNote] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState(null);

  /*
   * Domyslnie NIC nie robimy z kontem. To najlagodniejsza z mozliwosci
   * i dlatego jest domyslna: kara ma byc swiadomym wyborem, a nie skutkiem
   * nieprzestawienia listy.
   */
  const [action, setAction] = useState('NONE');
  const [duration, setDuration] = useState('24');

  const open = report.status === 'OPEN';

  /* Czas trwania dotyczy tylko zakazow - usuniecie konta trwa zawsze */
  const needsDuration = action === 'BAN_POSTING' || action === 'BAN_MESSAGING';

  /*
   * Kasowanie posta pokazujemy WYLACZNIE przy zgloszeniu posta. Przy
   * zgloszeniu profilu albo rozmowy nie ma czego kasowac, a pozycja
   * prowadzaca do komunikatu o bledzie jest zaproszeniem do pomylki.
   */
  const actions = ['NONE',
    ...(report.postId ? ['DELETE_POST'] : []),
    'BAN_POSTING', 'BAN_MESSAGING', 'DELETE_ACCOUNT'];

  /** Dowody pobieramy dopiero przy rozwinieciu - patrz komentarz przy stronie. */
  async function toggle() {
    const next = !expanded;
    setExpanded(next);

    if (next && !details) {
      try {
        const { data } = await client.get(`/reports/admin/${report.id}`);
        setDetails(data);
      } catch {
        setDetails({ ...report, evidence: [] });
      }
    }
  }

  async function decide(decision) {
    /*
     * Usuniecie konta jest nieodwracalne, wiec pytamy jeszcze raz - i to
     * PRZED wyslaniem, a nie po. Pozostale dzialania da sie cofnac
     * (zakaz mozna zdjac, posta i tak juz nie ma), wiec tam dodatkowe
     * klikniecie tylko przeszkadzaloby w codziennej pracy.
     */
    if (action === 'DELETE_ACCOUNT'
      && !window.confirm(t('reports.confirmDeleteAccount', { username: report.reportedUsername }))) {
      return;
    }

    setSending(true);
    setError(null);
    try {
      await client.post(`/reports/admin/${report.id}/resolve`, {
        decision,
        note: note.trim(),
        action,
        // Godziny wysylamy TYLKO przy karze czasowej - przy pozostalych
        // dzialaniach nie znacza nic i tylko myliłyby w zapisie zapytania
        hours: needsDuration && duration !== FOREVER ? Number(duration) : null,
        forever: needsDuration && duration === FOREVER,
      });
      onResolved();
    } catch (problem) {
      const problems = describeError(problem);
      setError(problems.message
        ?? (problems.messageKey ? t(problems.messageKey) : t('reports.failed')));
    } finally {
      setSending(false);
    }
  }

  const evidence = details?.evidence ?? [];

  return (
    <Card className={`mb-3 report-card${open ? ' is-open' : ''}`}>
      <Card.Body>
        <div className="d-flex align-items-start gap-3 flex-wrap">
          <Link to={`/profil/${report.reportedUsername}`} className="text-decoration-none">
            <Avatar
              avatarUrl={report.reportedAvatarUrl}
              username={report.reportedUsername}
              size={48}
            />
          </Link>

          <div className="flex-grow-1 min-width-0">
            <div className="d-flex align-items-center gap-2 flex-wrap">
              <Link
                to={`/profil/${report.reportedUsername}`}
                className="fw-semibold text-decoration-none text-body"
              >
                {report.reportedUsername}
              </Link>

              <Badge bg="danger-subtle" text="danger-emphasis">
                {t(`reports.reasons.${report.reason}`)}
              </Badge>
              <Badge bg="secondary-subtle" text="secondary-emphasis">
                {t(`reports.contexts.${report.context}`)}
              </Badge>

              {/*
                Historia konta. Jedno zgloszenie moze byc nieporozumieniem,
                piate zasadne to juz wzorzec - i zupelnie inna decyzja.
                Dlatego liczba stoi przy naglowku, a nie w szczegolach.
              */}
              {report.priorResolved > 0 && (
                <Badge bg="warning-subtle" text="warning-emphasis">
                  {t('reports.priorResolved', { count: report.priorResolved })}
                </Badge>
              )}
            </div>

            <div className="text-body-secondary small mt-1">
              {t('reports.filedBy', { username: report.reporterUsername })}
              {' · '}
              {timeAgo(report.createdAt, language)}
            </div>

            <p className="mt-2 mb-0 report-description">{report.description}</p>
          </div>

          {!open && (
            <Badge bg={report.status === 'RESOLVED' ? 'success' : 'secondary'}>
              {t(`reports.statuses.${report.status}`)}
            </Badge>
          )}
        </div>

        {/* Post prowadzi wprost do tresci - administrator nie musi go szukac */}
        {report.postId && (
          <div className="mt-2">
            <Link to={`/post/${report.postId}`} className="small">
              {t('reports.openPost')}
            </Link>
          </div>
        )}

        <div className="mt-3">
          <Button variant="outline-secondary" size="sm" onClick={toggle}>
            {expanded ? t('reports.hideDetails') : t('reports.showDetails')}
          </Button>
        </div>

        {expanded && (
          <div className="mt-3">
            {report.context === 'CONVERSATION' && (
              <p className="text-body-secondary small">{t('reports.evidenceHint')}</p>
            )}

            {evidence.length === 0 ? (
              <p className="text-body-secondary small mb-0">{t('reports.noEvidence')}</p>
            ) : (
              <div className="evidence">
                {evidence.map((line, index) => (
                  <div
                    key={`${line.author}-${index}`}
                    className={`evidence-line${
                      line.author === report.reportedUsername ? ' is-reported' : ''}`}
                  >
                    <span className="evidence-author">{line.author}</span>
                    <span className="evidence-text">{line.text}</span>
                    {line.sentAt && (
                      <span className="evidence-time">
                        {formatDate(line.sentAt, language)}
                      </span>
                    )}
                  </div>
                ))}
              </div>
            )}

            {!open && (
              <div className="mt-3 small">
                <div className="fw-semibold">
                  {t('reports.decisionBy', {
                    username: report.resolvedBy,
                    when: formatDate(report.resolvedAt, language),
                  })}
                </div>
                <div className="text-body-secondary">{report.resolutionNote}</div>
              </div>
            )}
          </div>
        )}

        {open && (
          <div className="mt-3 report-decision">
            {error && <Alert variant="danger" className="py-2">{error}</Alert>}

            <Form.Label htmlFor={`note-${report.id}`} className="small fw-semibold">
              {t('reports.note')}
            </Form.Label>
            <Form.Control
              id={`note-${report.id}`}
              as="textarea"
              rows={2}
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder={t('reports.notePlaceholder')}
              className="mb-2"
            />
            <p className="text-body-secondary small">{t('reports.noteHint')}</p>

            {/*
              Dzialanie wybieramy TUTAJ, razem z decyzja - a nie osobno
              w panelu kont. Wczesniej zamkniecie sprawy bylo sama notatka
              i trzeba bylo zapamietac nazwe konta, przejsc na inna strone,
              odszukac je i dopiero tam ukarac. Dowody sa tutaj, wiec
              decyzja tez powinna zapadac tutaj.
            */}
            <div className="d-flex gap-2 flex-wrap align-items-end mb-3">
              <div style={{ minWidth: '15rem' }}>
                <Form.Label htmlFor={`action-${report.id}`} className="small fw-semibold">
                  {t('reports.action')}
                </Form.Label>
                <Form.Select
                  id={`action-${report.id}`}
                  size="sm"
                  value={action}
                  onChange={(e) => setAction(e.target.value)}
                >
                  {actions.map((code) => (
                    <option key={code} value={code}>{t(`reports.actions.${code}`)}</option>
                  ))}
                </Form.Select>
              </div>

              {/* Czas trwania pokazuje sie tylko wtedy, gdy cokolwiek znaczy */}
              {needsDuration && (
                <div style={{ minWidth: '11rem' }}>
                  <Form.Label htmlFor={`duration-${report.id}`} className="small fw-semibold">
                    {t('reports.duration')}
                  </Form.Label>
                  <Form.Select
                    id={`duration-${report.id}`}
                    size="sm"
                    value={duration}
                    onChange={(e) => setDuration(e.target.value)}
                  >
                    {BAN_HOURS.map((h) => (
                      <option key={h} value={h}>{t('users.banFor', { count: h })}</option>
                    ))}
                    <option value={FOREVER}>{t('users.banForever')}</option>
                  </Form.Select>
                </div>
              )}
            </div>

            {action === 'DELETE_ACCOUNT' && (
              <Alert variant="danger" className="py-2 small">
                {t('reports.deleteAccountWarning')}
              </Alert>
            )}

            <div className="d-flex gap-2 flex-wrap">
              <Button
                variant="success"
                size="sm"
                disabled={sending || !note.trim()}
                onClick={() => decide('RESOLVED')}
              >
                <IconCheckCircle className="me-1" /> {t('reports.resolve')}
              </Button>
              <Button
                variant="outline-secondary"
                size="sm"
                disabled={sending || !note.trim()}
                onClick={() => decide('DISMISSED')}
              >
                <IconFlag className="me-1" /> {t('reports.dismiss')}
              </Button>

              {/*
                Skrot do panelu kont. Zostaje mimo listy dzialan obok, bo
                panel potrafi rzeczy, ktorych tu nie ma i miec nie powinno -
                przede wszystkim podejrzenie multikont i blokade adresu.
                To sa decyzje wykraczajace poza jedno zgloszenie.
              */}
              <Link to="/users" className="btn btn-outline-danger btn-sm">
                {t('reports.goToPanel')}
              </Link>
            </div>
          </div>
        )}
      </Card.Body>
    </Card>
  );
}

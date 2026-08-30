import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import Badge from 'react-bootstrap/Badge';
import Card from 'react-bootstrap/Card';
import Col from 'react-bootstrap/Col';
import Row from 'react-bootstrap/Row';
import { describeError } from '../api/client';
import { mojeZgloszenia } from '../api/moderacja';
import DoladujWiecej from '../components/DoladujWiecej';
import EmptyState from '../components/EmptyState';
import PostSkeleton from '../components/PostSkeleton';
import { IconFlag } from '../components/Icons';
import { formatDateTime, timeAgo } from '../utils/dates';

/** Ile wlasnych zgloszen pobieramy naraz. */
const ROZMIAR_STRONY = 20;

/** Wlasne zgloszenia uzytkownika razem z decyzja administratora. */
export default function MojeZgloszeniaPage() {
  const { t, i18n } = useTranslation();

  const [reports, setReports] = useState([]);
  const [strona, setStrona] = useState(0);
  const [ostatnia, setOstatnia] = useState(true);
  const [wszystkich, setWszystkich] = useState(0);
  const [loading, setLoading] = useState(true);
  const [doladowywanie, setDoladowywanie] = useState(false);
  const [error, setError] = useState(null);

  /* "dolacz" odroznia doladowanie starszych od pobrania listy od nowa. */
  const load = useCallback(async (numer, dolacz) => {
    if (dolacz) {
      setDoladowywanie(true);
    } else {
      setLoading(true);
    }
    setError(null);
    try {
      const data = await mojeZgloszenia({ strona: numer, rozmiar: ROZMIAR_STRONY });

      setReports((poprzednie) => (dolacz ? [...poprzednie, ...data.content] : data.content));
      setStrona(data.number);
      setOstatnia(data.last);
      setWszystkich(data.totalElements);
    } catch (problem) {
      setError(describeError(problem, 'myReports.loadFailed').message);
      // Nieudane doladowanie nie moze skasowac tego, co juz widac
      if (!dolacz) {
        setReports([]);
      }
    } finally {
      setLoading(false);
      setDoladowywanie(false);
    }
  }, []);

  useEffect(() => {
    load(0, false);
  }, [load]);

  return (
    <Row className="justify-content-center">
      <Col lg={8} className="tiles-in">
        <h1 className="h4 page-title mb-2">{t('myReports.title')}</h1>
        <p className="text-body-secondary small">{t('myReports.intro')}</p>

        {error && <Alert variant="danger">{error}</Alert>}

        {loading && <PostSkeleton count={2} />}

        {!loading && reports.length === 0 && !error && (
          <EmptyState
            icon={IconFlag}
            title={t('myReports.empty')}
            text={t('myReports.emptyHint')}
          />
        )}

        {reports.map((report) => (
          <Card key={report.id} className="mb-3">
            <Card.Body>
              <div className="d-flex align-items-start justify-content-between gap-2 flex-wrap mb-2">
                <div>
                  <span className="fw-semibold">{t('myReports.reported')}: </span>
                  {report.reportedUsername}
                  <div className="text-body-secondary small">
                    {t(`reports.reasons.${report.reason}`)}
                    {' · '}
                    {t('myReports.filed')} {timeAgo(report.createdAt, i18n.language)}
                  </div>
                </div>

                <Stan status={report.status} />
              </div>

              {report.status === 'OPEN' ? (
                <p className="text-body-secondary small mb-0">{t('myReports.openHint')}</p>
              ) : (
                <>
                  <p className="fw-semibold small mb-1">{t('myReports.note')}</p>
                  <p className="mb-2" style={{ whiteSpace: 'pre-wrap' }}>
                    {report.resolutionNote}
                  </p>
                  <p className="text-body-secondary small mb-0">
                    {t('myReports.resolvedAt')}: {formatDateTime(report.resolvedAt, i18n.language)}
                    {' · '}
                    {t('myReports.noPunishmentInfo')}
                  </p>
                </>
              )}
            </Card.Body>
          </Card>
        ))}

        {!loading && reports.length > 0 && wszystkich > ROZMIAR_STRONY && (
          <DoladujWiecej
            etykieta={t('reports.loadMore')}
            pokazano={reports.length}
            wszystkich={wszystkich}
            ostatnia={ostatnia}
            ladowanie={doladowywanie}
            onClick={() => load(strona + 1, true)}
          />
        )}
      </Col>
    </Row>
  );
}

/** Trzy stany sprawy, kazdy w swoim kolorze. */
function Stan({ status }) {
  const { t } = useTranslation();

  if (status === 'OPEN') {
    return (
      <Badge bg="warning-subtle" text="warning-emphasis">{t('myReports.open')}</Badge>
    );
  }
  return status === 'RESOLVED'
    ? <Badge bg="success">{t('myReports.resolved')}</Badge>
    : <Badge bg="secondary">{t('myReports.dismissed')}</Badge>;
}

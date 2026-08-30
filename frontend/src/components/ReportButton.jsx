import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import Button from 'react-bootstrap/Button';
import Modal from 'react-bootstrap/Modal';
import Form from 'react-bootstrap/Form';
import Alert from 'react-bootstrap/Alert';
import { describeError } from '../api/client';
import { zglos } from '../api/moderacja';
import { IconFlag } from './Icons';

/** Powody w kolejnosci od najczestszego. */
const REASONS = ['HARASSMENT', 'SPAM', 'HATE', 'INAPPROPRIATE', 'IMPERSONATION', 'OTHER'];

/** Minimalna dlugosc opisu - ta sama liczba, ktorej pilnuje serwer. */
const MIN_DESCRIPTION = 10;

/** Przycisk „Zgłoś" razem z okienkiem wyboru powodu. */
export default function ReportButton({
  username, contexts = ['PROFILE'], postId = null, compact = false,
}) {
  const { t } = useTranslation();

  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState(REASONS[0]);
  const [context, setContext] = useState(contexts[0]);
  const [description, setDescription] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState(null);
  const [done, setDone] = useState(false);

  function close() {
    setOpen(false);
    /* Stan czyscimy przy ZAMYKANIU, a nie przy otwieraniu. */
    setReason(REASONS[0]);
    setContext(contexts[0]);
    setDescription('');
    setError(null);
    setDone(false);
  }

  async function send(event) {
    event.preventDefault();
    setSending(true);
    setError(null);

    try {
      await zglos(username, {
        reason,
        context,
        postId: context === 'POST' ? postId : null,
        description: description.trim(),
      });
      setDone(true);
    } catch (problem) {
      const details = describeError(problem, 'reports.failed');
      setError(details.message);
    } finally {
      setSending(false);
    }
  }

  const tooShort = description.trim().length < MIN_DESCRIPTION;

  return (
    <>
      <Button
        variant="outline-secondary"
        size="sm"
        className={compact ? 'report-button-compact' : undefined}
        onClick={() => setOpen(true)}
        title={t('reports.reportUser', { username })}
        aria-label={t('reports.reportUser', { username })}
      >
        <IconFlag />
        {!compact && <span className="ms-1">{t('reports.report')}</span>}
      </Button>

      <Modal show={open} onHide={close} centered>
        <Modal.Header closeButton>
          <Modal.Title as="h5">{t('reports.reportUser', { username })}</Modal.Title>
        </Modal.Header>

        {done ? (
          /* Po wyslaniu pokazujemy potwierdzenie, a nie zamykamy okienka od razu. */
          <>
            <Modal.Body>
              <Alert variant="success" className="mb-0">
                {t('reports.sent')}
              </Alert>
            </Modal.Body>
            <Modal.Footer>
              <Button variant="primary" onClick={close}>{t('common.close')}</Button>
            </Modal.Footer>
          </>
        ) : (
          <Form onSubmit={send}>
            <Modal.Body>
              {error && <Alert variant="danger">{error}</Alert>}

              <Form.Group className="mb-3">
                <Form.Label htmlFor="report-reason">{t('reports.reason')}</Form.Label>
                <Form.Select
                  id="report-reason"
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                >
                  {REASONS.map((code) => (
                    <option key={code} value={code}>{t(`reports.reasons.${code}`)}</option>
                  ))}
                </Form.Select>
              </Form.Group>

              {/* Wybor rodzaju TYLKO wtedy, gdy jest z czego wybierac */}
              {contexts.length > 1 && (
                <Form.Group className="mb-3">
                  <Form.Label htmlFor="report-context">{t('reports.context')}</Form.Label>
                  <Form.Select
                    id="report-context"
                    value={context}
                    onChange={(e) => setContext(e.target.value)}
                  >
                    {contexts.map((code) => (
                      <option key={code} value={code}>{t(`reports.contexts.${code}`)}</option>
                    ))}
                  </Form.Select>
                  <Form.Text>{t(`reports.contextHints.${context}`)}</Form.Text>
                </Form.Group>
              )}

              <Form.Group>
                <Form.Label htmlFor="report-description">
                  {t('reports.description')}
                </Form.Label>
                <Form.Control
                  id="report-description"
                  as="textarea"
                  rows={4}
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder={t('reports.descriptionPlaceholder')}
                  maxLength={1000}
                />
                <Form.Text>{t('reports.descriptionHint')}</Form.Text>
              </Form.Group>
            </Modal.Body>

            <Modal.Footer>
              <Button variant="outline-secondary" onClick={close}>
                {t('common.cancel')}
              </Button>
              <Button type="submit" variant="danger" disabled={sending || tooShort}>
                {sending ? t('reports.sending') : t('reports.send')}
              </Button>
            </Modal.Footer>
          </Form>
        )}
      </Modal>
    </>
  );
}

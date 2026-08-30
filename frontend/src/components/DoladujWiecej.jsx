import Button from 'react-bootstrap/Button';
import Spinner from 'react-bootstrap/Spinner';
import { useTranslation } from 'react-i18next';

/**
 * Stopka listy, ktora ma dalsze strony: licznik "pokazano X z Y" i przycisk po starsze.
 * Licznik zostaje takze na ostatniej stronie - inaczej po ostatnim kliknieciu znikalby
 * jedyny slad tego, ile wpisow w ogole jest.
 */
export default function DoladujWiecej({
  etykieta, pokazano, wszystkich, ostatnia, ladowanie, onClick, rozmiar,
}) {
  const { t } = useTranslation();

  return (
    <div className="text-center my-3">
      {!ostatnia && (
        <Button variant="outline-secondary" size={rozmiar} onClick={onClick} disabled={ladowanie}>
          {ladowanie && (
            <Spinner animation="border" size="sm" className="me-2" aria-hidden="true" />
          )}
          {ladowanie ? t('common.loading') : etykieta}
        </Button>
      )}

      {/* aria-live: czytnik ekranu ma powiedziec, ze lista urosla */}
      <div className="text-body-secondary small mt-2" aria-live="polite">
        {t('common.shownOf', { shown: pokazano, total: wszystkich })}
      </div>
    </div>
  );
}

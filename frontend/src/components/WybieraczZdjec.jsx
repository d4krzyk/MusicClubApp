import { useId, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import Button from 'react-bootstrap/Button';
import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';
import { IkonaKrzyzyk, IkonaPlus, IkonaZdjecie } from './Ikony';

/**
 * Wybor zdjec do posta - z podgladem i mozliwoscia dokladania oraz usuwania
 * pojedynczych plikow.
 *
 * <p><b>Problem, ktory to rozwiazuje.</b> Zwykly {@code <input type="file" multiple>}
 * przy kazdym otwarciu okna ZASTEPUJE poprzedni wybor. Zdjecia z dwoch roznych
 * folderow trzeba wiec bylo wybrac za jednym razem, a pomylka oznaczala
 * zaczynanie od poczatku. Tutaj pliki DOKLADAJA sie do listy, kazdy ma
 * podglad i wlasny krzyzyk do usuniecia.</p>
 *
 * <p>Lista plikow jest trzymana w komponencie nadrzednym (formularzu posta),
 * bo to on wysyla ja na serwer - tutaj tylko ja pokazujemy i modyfikujemy.</p>
 */
export default function WybieraczZdjec({ pliki, onZmiana, maks = 10, blad }) {
  const { t } = useTranslation();
  const inputRef = useRef(null);
  const idInput = useId();

  function dodaj(e) {
    const nowe = Array.from(e.target.files ?? []);

    /*
     * Odsiewamy pliki, ktore juz sa na liscie. Bez tego dwukrotne wybranie
     * tego samego zdjecia wgraloby je dwa razy. Porownujemy nazwe i rozmiar -
     * obiekty File nie sa rowne nawet dla tego samego pliku.
     */
    const juzJest = (plik) =>
      pliki.some((p) => p.name === plik.name && p.size === plik.size);

    const doDodania = nowe.filter((p) => !juzJest(p));
    onZmiana([...pliki, ...doDodania].slice(0, maks));

    /*
     * Czyscimy input. Bez tego wybranie tego samego pliku drugi raz (np. po
     * usunieciu go z listy) nie wywolaloby zdarzenia onChange - przegladarka
     * uznaje, ze wartosc sie nie zmienila.
     */
    e.target.value = '';
  }

  function usun(indeks) {
    onZmiana(pliki.filter((_, i) => i !== indeks));
  }

  const pelno = pliki.length >= maks;

  return (
    <div className="mb-3">
      <div className="d-flex align-items-center gap-2 flex-wrap">
        <Button
          type="button"
          variant="outline-secondary"
          size="sm"
          disabled={pelno}
          onClick={() => inputRef.current?.click()}
        >
          {pliki.length === 0 ? (
            <>
              <IkonaZdjecie /> {t('posts.chooseImages')}
            </>
          ) : (
            <>
              <IkonaPlus /> {t('posts.addMoreImages')}
            </>
          )}
        </Button>

        {pliki.length > 0 && (
          <>
            <span className="text-body-secondary small">
              {t('posts.imagesSelected', { count: pliki.length, max: maks })}
            </span>
            <Button
              type="button"
              variant="link"
              size="sm"
              className="text-danger p-0"
              onClick={() => onZmiana([])}
            >
              {t('posts.clearImages')}
            </Button>
          </>
        )}
      </div>

      {/* Prawdziwy input jest ukryty - klikamy w niego przez przycisk wyzej */}
      <input
        ref={inputRef}
        id={idInput}
        type="file"
        accept="image/*"
        multiple
        className="d-none"
        onChange={dodaj}
      />

      {blad ? (
        <div className="text-danger small mt-1">{blad}</div>
      ) : (
        <div className="form-text">{t('posts.imagesHint', { max: maks })}</div>
      )}

      {pliki.length > 0 && (
        <Row className="g-2 mt-1">
          {pliki.map((plik, i) => (
            <Col key={`${plik.name}-${plik.size}-${i}`} xs={4} sm={3} md={2}>
              <div className="position-relative">
                <img
                  /*
                   * createObjectURL robi lokalny adres do pliku z dysku -
                   * podglad dziala bez wysylania czegokolwiek na serwer.
                   */
                  src={URL.createObjectURL(plik)}
                  alt={plik.name}
                  className="miniatura-postu rounded border"
                  style={{ cursor: 'default' }}
                />
                <Button
                  type="button"
                  variant="danger"
                  size="sm"
                  className="position-absolute top-0 end-0 m-1 py-0 px-1 lh-1"
                  onClick={() => usun(i)}
                  aria-label={`${t('common.delete')}: ${plik.name}`}
                  title={plik.name}
                >
                  <IkonaKrzyzyk rozmiar={12} />
                </Button>
              </div>
            </Col>
          ))}
        </Row>
      )}
    </div>
  );
}

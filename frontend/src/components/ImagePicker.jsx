import { useId, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Button from 'react-bootstrap/Button';
import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';
import Spinner from 'react-bootstrap/Spinner';
import { IconCross, IconCrop, IconPlus, IconImage } from './Icons';
import EdytorZdjecia from './obraz/EdytorZdjecia';
import useAdresPodgladu from '../hooks/useAdresPodgladu';
import {
  czyDaSieZmniejszyc,
  czyZaDuzy,
  formatujRozmiar,
  zmniejsz,
} from '../utils/obrazy';

/**
 * Wybor zdjec do posta - z podgladem i mozliwoscia dokladania oraz usuwania pojedynczych plikow.
 *
 * Za duze zdjecie nie jest bledem, tylko czyms do zalatwienia: kafelek robi
 * sie klikalny i jedno klikniecie zmniejsza plik do limitu. Kto tego nie
 * zrobi, i tak nie zobaczy bledu - to samo dzieje sie przy wysylaniu.
 *
 * Kazde zdjecie mozna przyciac i obrocic (przycisk w rogu miniatury, wspolny edytor zdjec).
 */
export default function ImagePicker({ files, onChange, maks = 10, error }) {
  const { t } = useTranslation();
  const inputRef = useRef(null);
  const idInput = useId();

  /* Indeksy zdjec, ktore wlasnie sa przerabiane. */
  const [wPracy, setWPracy] = useState([]);

  /* Numer zdjecia otwartego w edytorze (kadr i obrot). */
  const [edytowane, setEdytowane] = useState(null);

  /*
   * Rozmiary sprzed zmniejszenia, zeby dalo sie pokazac "8,2 MB -> 1,1 MB".
   * Klucz to nazwa i rozmiar juz PO zmianie, bo indeksy przesuwaja sie
   * przy usuwaniu zdjec z listy.
   */
  const [przed, setPrzed] = useState({});

  function add(e) {
    const nowe = Array.from(e.target.files ?? []);

    /* Odsiewamy pliki, ktore juz sa na liscie. */
    const juzJest = (file) =>
      files.some((p) => p.name === file.name && p.size === file.size);

    const toAdd = nowe.filter((p) => !juzJest(p));
    onChange([...files, ...toAdd].slice(0, maks));

    /* Czyscimy input. */
    e.target.value = '';
  }

  function remove(indeks) {
    onChange(files.filter((_, i) => i !== indeks));
  }

  async function skompresuj(indeks) {
    const oryginal = files[indeks];
    setWPracy((lista) => [...lista, indeks]);

    try {
      const mniejszy = await zmniejsz(oryginal);
      onChange(files.map((plik, i) => (i === indeks ? mniejszy : plik)));
      setPrzed((stan) => ({
        ...stan,
        [`${mniejszy.name}-${mniejszy.size}`]: oryginal.size,
      }));
    } catch {
      /* Nie udalo sie - plik zostaje jaki byl, a przy wysylaniu odezwie sie serwer. */
    } finally {
      setWPracy((lista) => lista.filter((i) => i !== indeks));
    }
  }

  function poEdycji(nowy) {
    const indeks = edytowane;
    setEdytowane(null);
    if (indeks !== null && files[indeks] && nowy !== files[indeks]) {
      onChange(files.map((plik, i) => (i === indeks ? nowy : plik)));
    }
  }

  const pelno = files.length >= maks;
  const zaDuzych = files.filter(czyZaDuzy).length;

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
          {files.length === 0 ? (
            <>
              <IconImage /> {t('posts.chooseImages')}
            </>
          ) : (
            <>
              <IconPlus /> {t('posts.addMoreImages')}
            </>
          )}
        </Button>

        {files.length > 0 && (
          <>
            <span className="text-body-secondary small">
              {t('posts.imagesSelected', { count: files.length, max: maks })}
            </span>
            <Button
              type="button"
              variant="link"
              size="sm"
              className="text-danger p-0"
              onClick={() => onChange([])}
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
        onChange={add}
      />

      {error ? (
        <div className="text-danger small mt-1">{error}</div>
      ) : (
        <div className="form-text">{t('posts.imagesHint', { max: maks })}</div>
      )}

      {zaDuzych > 0 && (
        <div className="alert alert-warning py-2 px-3 small mt-2 mb-0">
          {t('posts.tooLargeHint', { count: zaDuzych, limit: formatujRozmiar(5 * 1024 * 1024) })}
        </div>
      )}

      {files.length > 0 && (
        <Row className="g-2 mt-1">
          {files.map((file, i) => {
            const duzy = czyZaDuzy(file);
            const daSie = czyDaSieZmniejszyc(file);
            const pracuje = wPracy.includes(i);
            const przedtem = przed[`${file.name}-${file.size}`];

            return (
              <Col key={`${file.name}-${file.size}-${i}`} xs={4} sm={3} md={2}>
                <div className="position-relative">
                  <Miniatura plik={file} duzy={duzy} />

                  {/* Nakladka tylko na za duzych - klikniecie zmniejsza plik */}
                  {duzy && daSie && (
                    <button
                      type="button"
                      className="zdjecie-kompresuj"
                      onClick={() => skompresuj(i)}
                      disabled={pracuje}
                      title={t('posts.compressAction')}
                    >
                      {pracuje ? (
                        <Spinner animation="border" size="sm" />
                      ) : (
                        <>
                          <span className="zdjecie-kompresuj-rozmiar">
                            {formatujRozmiar(file.size)}
                          </span>
                          <span className="zdjecie-kompresuj-napis">
                            {t('posts.compressAction')}
                          </span>
                        </>
                      )}
                    </button>
                  )}

                  {/* GIF-a nie ruszamy, zeby nie zabic animacji */}
                  {duzy && !daSie && (
                    <span className="zdjecie-kompresuj zdjecie-kompresuj-blokada">
                      <span className="zdjecie-kompresuj-rozmiar">
                        {formatujRozmiar(file.size)}
                      </span>
                      <span className="zdjecie-kompresuj-napis">
                        {t('posts.compressImpossible')}
                      </span>
                    </span>
                  )}

                  {!pracuje && (
                    <button
                      type="button"
                      className="zdjecie-edytuj"
                      onClick={() => setEdytowane(i)}
                      aria-label={t('imageEditor.editNamed', { name: file.name })}
                      title={t('imageEditor.edit')}
                    >
                      <IconCrop size={14} />
                    </button>
                  )}

                  <Button
                    type="button"
                    variant="danger"
                    size="sm"
                    className="position-absolute top-0 end-0 m-1 py-0 px-1 lh-1"
                    onClick={() => remove(i)}
                    aria-label={`${t('common.delete')}: ${file.name}`}
                    title={file.name}
                  >
                    <IconCross size={12} />
                  </Button>
                </div>

                <div className="zdjecie-rozmiar small text-body-secondary mt-1">
                  {przedtem ? (
                    <span className="text-success">
                      {formatujRozmiar(przedtem)} → {formatujRozmiar(file.size)}
                    </span>
                  ) : (
                    formatujRozmiar(file.size)
                  )}
                </div>
              </Col>
            );
          })}
        </Row>
      )}

      <EdytorZdjecia
        plik={edytowane !== null ? files[edytowane] ?? null : null}
        rodzaj="post"
        onGotowe={poEdycji}
        onAnuluj={() => setEdytowane(null)}
      />
    </div>
  );
}

/**
 * Podglad pliku z dysku - nic nie idzie na serwer. Adres jest zwalniany, gdy zdjecie znika z listy
 * (wczesniej powstawal nowy przy kazdym odswiezeniu formularza i zaden nie byl zwalniany).
 */
function Miniatura({ plik, duzy }) {
  const adres = useAdresPodgladu(plik);
  return (
    <img
      src={adres ?? undefined}
      alt={plik.name}
      className={`post-thumb rounded border${duzy ? ' zdjecie-za-duze' : ''}`}
    />
  );
}

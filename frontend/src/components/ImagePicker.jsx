import { useId, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import Button from 'react-bootstrap/Button';
import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';
import { IconCross, IconPlus, IconImage } from './Icons';

/**
 * Wybor zdjec do posta - z podgladem i mozliwoscia dokladania oraz usuwania pojedynczych plikow.
 */
export default function ImagePicker({ files, onChange, maks = 10, error }) {
  const { t } = useTranslation();
  const inputRef = useRef(null);
  const idInput = useId();

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

  const pelno = files.length >= maks;

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

      {files.length > 0 && (
        <Row className="g-2 mt-1">
          {files.map((file, i) => (
            <Col key={`${file.name}-${file.size}-${i}`} xs={4} sm={3} md={2}>
              <div className="position-relative">
                <img
                  /*
                   * createObjectURL robi lokalny adres do pliku z dysku - podglad dziala bez
                   * wysylania czegokolwiek na serwer.
                   */
                  src={URL.createObjectURL(file)}
                  alt={file.name}
                  className="post-thumb rounded border"
                  style={{ cursor: 'default' }}
                />
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
            </Col>
          ))}
        </Row>
      )}
    </div>
  );
}

import { useTranslation } from 'react-i18next';
import Form from 'react-bootstrap/Form';
import ButtonGroup from 'react-bootstrap/ButtonGroup';
import Button from 'react-bootstrap/Button';
import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';
import Pole from './Pole';
import { bladLinku } from '../utils/linkiMuzyczne';

/**
 * Wybor nagrania do posta: rodzaj, link i (tylko dla utworu) moment startu.
 *
 * <p><b>Rodzaj wybiera uzytkownik, my sprawdzamy zgodnosc.</b> Moglibysmy
 * rozpoznawac go z samego adresu, ale wtedy pomylka konczy sie cicha
 * niespodzianka - "wrzucalem album, a wyszedl utwor". Przy jawnym wyborze
 * niezgodnosc to blad, ktory widac od razu.</p>
 *
 * <p><b>Pole momentu startu POKAZUJE SIE tylko przy utworze.</b> Album to
 * wiele nagran, a profil artysty w ogole nie jest nagraniem - "zacznij
 * od 1:30" nic tam nie znaczy. Zamiast tlumaczyc to napisem, po prostu
 * chowamy pole.</p>
 *
 * <p>Blad linku pokazujemy juz przy wpisywaniu, ale <b>o wysylce decyduje
 * i tak serwer</b> - ta walidacja jest wygoda, nie zabezpieczeniem.</p>
 */

/** Kolejnosc na przelaczniku - od najczestszego przypadku. */
const RODZAJE = ['TRACK', 'ALBUM', 'ARTIST', 'PLAYLIST'];

export default function WyborMuzyki({
  rodzaj, onRodzaj,
  link, onLink,
  moment, onMoment,
  bledySerwera = {},
}) {
  const { t } = useTranslation();

  const kluczBledu = bladLinku(link, rodzaj);
  // Blad z serwera ma pierwszenstwo - jest ostateczny
  const bladPolaLinku = bledySerwera.musicUrl ?? (kluczBledu ? t(kluczBledu) : undefined);

  const momentDozwolony = rodzaj === 'TRACK';

  function zmienRodzaj(nowy) {
    onRodzaj(nowy);
    // Przy zmianie na album/artyste moment startu traci sens - czyscimy go,
    // zeby nie poszedl na serwer jako "ukryta" wartosc
    if (nowy !== 'TRACK') {
      onMoment('');
    }
  }

  return (
    <div className="mb-3">
      <Form.Label className="d-block">{t('posts.music')}</Form.Label>

      <ButtonGroup size="sm" className="mb-2" aria-label={t('posts.musicKind')}>
        {RODZAJE.map((kod) => (
          <Button
            key={kod}
            type="button"
            variant={rodzaj === kod ? 'primary' : 'outline-secondary'}
            onClick={() => zmienRodzaj(kod)}
            aria-pressed={rodzaj === kod}
          >
            {t(`posts.musicKinds.${kod}`)}
          </Button>
        ))}
      </ButtonGroup>

      <Row>
        <Col md={momentDozwolony ? 8 : 12}>
          <Pole
            id="musicUrl"
            label={t('posts.musicUrl')}
            wartosc={link}
            onChange={onLink}
            blad={bladPolaLinku}
            podpowiedz={t(`posts.musicHints.${rodzaj}`)}
            placeholder={t('posts.musicPlaceholder')}
            wymagane={false}
          />
        </Col>

        {/* Moment startu WYLACZNIE przy utworze */}
        {momentDozwolony && (
          <Col md={4}>
            <Pole
              id="musicStartSeconds"
              label={t('posts.startAt')}
              wartosc={moment}
              onChange={onMoment}
              blad={bledySerwera.musicStartSeconds}
              podpowiedz={t('posts.startAtHint')}
              placeholder="1:23"
              wymagane={false}
            />
          </Col>
        )}
      </Row>
    </div>
  );
}

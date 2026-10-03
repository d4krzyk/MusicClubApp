import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Button from 'react-bootstrap/Button';
import Avatar from '../Avatar';
import ClanBadge from '../ClanBadge';
import GifObrazek from '../gif/GifObrazek';
import { IconReply, IconTrash } from '../Icons';
import ReportButton from '../ReportButton';
import { timeAgo } from '../../utils/dates';
import KomentarzForm from './KomentarzForm';
import TrescKomentarza from './TrescKomentarza';

/**
 * Jeden komentarz razem z odpowiedziami pod nim. Odpowiedzi sa jednopoziomowe: odpowiedz na odpowiedz
 * wisi pod tym samym komentarzem nadrzednym, z oznaczeniem osoby, do ktorej jest.
 * `watek` to stan odpowiedzi trzymany wyzej: { lista, wiecej, ladowanie }.
 */
export default function Komentarz({
  komentarz, idPosta, watek, onUsun, onOdpowiedzDodana, onPokazOdpowiedzi, onWiecejOdpowiedzi,
  podswietlony = false, jestOdpowiedzia = false,
}) {
  const { t, i18n } = useTranslation();
  const [odpowiada, setOdpowiada] = useState(null);
  const korzen = komentarz.parentId ?? komentarz.id;

  return (
    <li id={`komentarz-${komentarz.id}`}
      className={`komentarz${jestOdpowiedzia ? ' is-odpowiedz' : ''}${podswietlony ? ' is-podswietlony' : ''}`}>
      <Avatar avatarUrl={komentarz.authorAvatarUrl} username={komentarz.authorUsername} size={jestOdpowiedzia ? 24 : 32} />
      <div className="komentarz-glowna">
        <div className="komentarz-babelek">
          <div className="d-flex align-items-center gap-2 flex-wrap">
            <Link to={`/profil/${encodeURIComponent(komentarz.authorUsername)}`} className="fw-semibold komentarz-autor">
              {komentarz.authorUsername}
            </Link>
            <ClanBadge clan={komentarz.authorClan} className="flex-shrink-0" />
          </div>
          {komentarz.replyToUsername && jestOdpowiedzia && !komentarz.content.startsWith(`@${komentarz.replyToUsername}`) && (
            <div className="small text-body-secondary komentarz-do">
              <IconReply size={11} /> {t('comments.replyTo', { username: komentarz.replyToUsername })}
            </div>
          )}
          {komentarz.content && (
            <div className="komentarz-tekst">
              <TrescKomentarza tresc={komentarz.content} oznaczeni={komentarz.mentions} />
            </div>
          )}
          <GifObrazek gif={komentarz.gif} />
        </div>

        <div className="komentarz-akcje small">
          <span className="text-body-secondary">{timeAgo(komentarz.createdAt, i18n.language)}</span>
          <button type="button" className="komentarz-akcja"
            onClick={() => setOdpowiada(odpowiada ? null : komentarz.authorUsername)}>
            {t('comments.reply')}
          </button>
          {komentarz.canDelete && (
            <button type="button" className="komentarz-akcja is-niebezpieczna" onClick={() => onUsun(komentarz)}
              aria-label={t('comments.delete')} title={t('comments.delete')}>
              <IconTrash size={12} />
            </button>
          )}
          {!komentarz.mine && (
            <ReportButton username={komentarz.authorUsername} contexts={['COMMENT']} commentId={komentarz.id} compact />
          )}
        </div>

        {odpowiada && (
          <div className="komentarz-odpowiedz-forma">
            <KomentarzForm
              idPosta={idPosta}
              idRodzica={komentarz.id}
              poczatek={`@${odpowiada} `}
              autoFocus
              onAnuluj={() => setOdpowiada(null)}
              onDodano={(dodany) => {
                setOdpowiada(null);
                onOdpowiedzDodana(korzen, dodany);
              }}
            />
          </div>
        )}

        {/* Odpowiedzi - tylko pod komentarzem nadrzednym */}
        {!jestOdpowiedzia && komentarz.replyCount > 0 && !watek?.lista && (
          <Button variant="link" size="sm" className="p-0 komentarz-pokaz" onClick={() => onPokazOdpowiedzi(komentarz)}
            disabled={watek?.ladowanie}>
            {t('comments.showReplies', { count: komentarz.replyCount })}
          </Button>
        )}
        {!jestOdpowiedzia && watek?.lista && (
          <ul className="komentarze-lista komentarze-odpowiedzi list-unstyled">
            {watek.lista.map((o) => (
              <Komentarz
                key={o.id}
                komentarz={o}
                idPosta={idPosta}
                watek={null}
                jestOdpowiedzia
                onUsun={onUsun}
                onOdpowiedzDodana={onOdpowiedzDodana}
                onPokazOdpowiedzi={onPokazOdpowiedzi}
                onWiecejOdpowiedzi={onWiecejOdpowiedzi}
              />
            ))}
            {watek.wiecej && (
              <li>
                <Button variant="link" size="sm" className="p-0 komentarz-pokaz" onClick={() => onWiecejOdpowiedzi(komentarz)}
                  disabled={watek.ladowanie}>
                  {t('comments.moreReplies')}
                </Button>
              </li>
            )}
          </ul>
        )}
      </div>
    </li>
  );
}

import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Button from 'react-bootstrap/Button';
import { describeError } from '../api/client';
import * as klany from '../api/klany';
import { IconClan } from './Icons';

/**
 * "Zapros do klanu" na czyimś profilu - tylko gdy ja jestem w klanie, a ta osoba w zadnym.
 * Czy ona MOZE dostac zaproszenie, rozstrzyga serwer (jej ustawienia prywatnosci, blokady);
 * przy odmowie pokazuje neutralny komunikat, ktory nie zdradza powodu.
 */
export default function ClanInviteButton({ username }) {
  const { t } = useTranslation();
  const [klan, setKlan] = useState(null);
  const [zajety, setZajety] = useState(false);
  const [wynik, setWynik] = useState(null);

  useEffect(() => {
    let anulowane = false;
    klany.moj()
      .then((moj) => !anulowane && setKlan(moj.clan))
      .catch(() => {});
    return () => { anulowane = true; };
  }, []);

  if (!klan) {
    return null;
  }

  async function zapros() {
    setZajety(true);
    setWynik(null);
    try {
      await klany.zapros(klan.id, username);
      setWynik({ ok: true, tekst: t('clans.members.invited', { username }) });
    } catch (problem) {
      setWynik({ ok: false, tekst: describeError(problem).message });
    } finally {
      setZajety(false);
    }
  }

  return (
    <div className="d-flex flex-column align-items-end gap-1">
      <Button variant="outline-secondary" size="sm" onClick={zapros} disabled={zajety}>
        <IconClan className="me-1" size={13} />
        {t('clans.inviteToClan', { name: klan.name })}
      </Button>
      {wynik && <span className={`small ${wynik.ok ? 'text-success' : 'text-danger'}`} role="status">{wynik.tekst}</span>}
    </div>
  );
}

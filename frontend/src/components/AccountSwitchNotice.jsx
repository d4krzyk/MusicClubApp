import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';

/** Wyjasnia, dlaczego strona przeladowala sie sama. */
export default function AccountSwitchNotice() {
  const { t } = useTranslation();
  const [swap, setSwap] = useState(null);

  useEffect(() => {
    try {
      const stored = sessionStorage.getItem('accountSwitch');
      if (stored) {
        sessionStorage.removeItem('accountSwitch');
        setSwap(JSON.parse(stored));
      }
    } catch {
      // Tryb prywatny albo uszkodzony wpis - brak komunikatu nic nie psuje
    }
  }, []);

  if (!swap) {
    return null;
  }

  return (
    <Alert variant="warning" dismissible onClose={() => setSwap(null)} className="mb-3">
      <Alert.Heading as="h6">{t('account.switchTitle')}</Alert.Heading>
      <p className="mb-1">
        {t('account.switchText', { previous: swap.previous, current: swap.current })}
      </p>
      {/*
        Podpowiedz jest tu celowo, a nie w dokumentacji: czlowiek czyta ten komunikat dokladnie w
        chwili, w ktorej probuje uzywac dwoch kont naraz.
      */}
      <p className="mb-0 small">{t('account.switchHint')}</p>
    </Alert>
  );
}

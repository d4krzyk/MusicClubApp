import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';

/**
 * Wyjasnia, dlaczego strona przeladowala sie sama.
 *
 * <p><b>Sytuacja, ktora to wymusila.</b> Ciasteczko sesji nalezy do calej
 * przegladarki, a nie do pojedynczej karty. Gdy w drugiej karcie ktos zaloguje
 * sie na inne konto, <b>ta</b> karta od tej chwili rozmawia z serwerem juz jako
 * to drugie konto - choc na ekranie ma jeszcze poprzednie. Wygladalo to tak,
 * ze na profilu jednej osoby pojawiali sie znajomi zupelnie innej.</p>
 *
 * <p>Klient HTTP wykrywa taka podmiane i przeladowuje aplikacje, zeby karta
 * pokazywala to konto, na ktore naprawde jest zalogowana. Samo przeladowanie
 * bez slowa wyjasnienia wygladaloby jednak jak usterka - i stad ten komunikat.</p>
 *
 * <p><b>Znika po przeczytaniu.</b> Powod trzymamy w {@code sessionStorage}
 * (bo tylko tak przezyje przeladowanie) i kasujemy natychmiast po odczytaniu.
 * Inaczej pokazywalby sie przy kazdym kolejnym wejsciu na strone, dawno po
 * tym, jak przestal cokolwiek znaczyc.</p>
 */
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
        Podpowiedz jest tu celowo, a nie w dokumentacji: czlowiek czyta ten
        komunikat dokladnie w chwili, w ktorej probuje uzywac dwoch kont naraz.
      */}
      <p className="mb-0 small">{t('account.switchHint')}</p>
    </Alert>
  );
}

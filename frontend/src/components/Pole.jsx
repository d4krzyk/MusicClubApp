/**
 * Pojedyncze pole formularza: etykieta, input, podpowiedz i komunikat bledu.
 *
 * <p>Wydzielone do osobnego komponentu, bo formularze logowania i rejestracji
 * maja razem siedem takich pol - bez tego ten sam kod bylby przepisany
 * siedem razy.</p>
 *
 * <p>Gdy {@code blad} jest ustawiony, pole dostaje czerwona ramke oraz
 * {@code aria-invalid}, dzieki czemu czytniki ekranu tez zglaszaja problem.</p>
 */
export default function Pole({
  id,
  label,
  typ = 'text',
  wartosc,
  onChange,
  blad,
  podpowiedz,
  autoComplete,
  wymagane = true,
}) {
  const idPodpowiedzi = podpowiedz ? `${id}-hint` : undefined;
  const idBledu = blad ? `${id}-error` : undefined;

  return (
    <div className="pole">
      <label htmlFor={id}>{label}</label>

      <input
        id={id}
        name={id}
        type={typ}
        value={wartosc}
        onChange={(e) => onChange(e.target.value)}
        autoComplete={autoComplete}
        required={wymagane}
        className={blad ? 'input blad' : 'input'}
        aria-invalid={blad ? 'true' : undefined}
        aria-describedby={[idPodpowiedzi, idBledu].filter(Boolean).join(' ') || undefined}
      />

      {podpowiedz && !blad && (
        <small id={idPodpowiedzi} className="podpowiedz">
          {podpowiedz}
        </small>
      )}

      {blad && (
        <small id={idBledu} className="komunikat-bledu" role="alert">
          {blad}
        </small>
      )}
    </div>
  );
}

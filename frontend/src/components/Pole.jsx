import Form from 'react-bootstrap/Form';

/**
 * Pojedyncze pole formularza: etykieta, input, podpowiedz i komunikat bledu.
 *
 * <p>Po przejsciu na Bootstrapa korzystamy z gotowych komponentow
 * {@code Form.Control} i {@code Form.Control.Feedback}. Wlasnosc
 * {@code isInvalid} sama dokleja czerwona ramke i pokazuje komunikat -
 * wczesniej trzeba bylo to obslugiwac recznie klasami CSS.</p>
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
  jakoObszarTekstu = false,
  wiersze = 4,
  placeholder,
}) {
  return (
    <Form.Group className="mb-3" controlId={id}>
      <Form.Label>{label}</Form.Label>

      <Form.Control
        as={jakoObszarTekstu ? 'textarea' : 'input'}
        rows={jakoObszarTekstu ? wiersze : undefined}
        type={jakoObszarTekstu ? undefined : typ}
        name={id}
        value={wartosc}
        placeholder={placeholder}
        onChange={(e) => onChange(e.target.value)}
        autoComplete={autoComplete}
        required={wymagane}
        isInvalid={Boolean(blad)}
      />

      {/* Podpowiedz chowamy, gdy jest blad - dwa teksty pod polem tylko mylą */}
      {podpowiedz && !blad && <Form.Text muted>{podpowiedz}</Form.Text>}

      <Form.Control.Feedback type="invalid">{blad}</Form.Control.Feedback>
    </Form.Group>
  );
}

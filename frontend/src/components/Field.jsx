import Form from 'react-bootstrap/Form';

/**
 * Pojedyncze pole formularza: etykieta, input, suggestion-item i komunikat bledu.
 *
 * <p>Po przejsciu na Bootstrapa korzystamy z gotowych komponentow
 * {@code Form.Control} i {@code Form.Control.Feedback}. Wlasnosc
 * {@code isInvalid} sama dokleja czerwona ramke i pokazuje komunikat -
 * wczesniej trzeba bylo to obslugiwac recznie klasami CSS.</p>
 */
export default function Field({
  id,
  label,
  typ = 'text',
  value,
  onChange,
  error,
  suggestion,
  autoComplete,
  wymagane = true,
  asTextarea = false,
  rows = 4,
  placeholder,
}) {
  return (
    <Form.Group className="mb-3" controlId={id}>
      <Form.Label>{label}</Form.Label>

      <Form.Control
        as={asTextarea ? 'textarea' : 'input'}
        rows={asTextarea ? rows : undefined}
        type={asTextarea ? undefined : typ}
        name={id}
        value={value}
        placeholder={placeholder}
        onChange={(e) => onChange(e.target.value)}
        autoComplete={autoComplete}
        required={wymagane}
        isInvalid={Boolean(error)}
      />

      {/* Podpowiedz chowamy, gdy jest blad - dwa teksty pod polem tylko mylą */}
      {suggestion && !error && <Form.Text muted>{suggestion}</Form.Text>}

      <Form.Control.Feedback type="invalid">{error}</Form.Control.Feedback>
    </Form.Group>
  );
}

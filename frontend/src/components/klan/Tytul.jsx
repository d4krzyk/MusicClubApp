/**
 * Tytul przy czlonku klanu: kolorowa etykieta. Kolor idzie z serwera jako gotowy hex (paleta klanu -
 * wszystkie kolory sa dosc ciemne, zeby bialy napis byl czytelny).
 */
export default function Tytul({ tytul, onUsun, usunEtykieta }) {
  return (
    <span className="klan-tytul" style={{ '--tytul': tytul.colorHex }}>
      <span className="klan-tytul-nazwa">{tytul.name}</span>
      {onUsun && (
        <button type="button" className="klan-tytul-usun" aria-label={usunEtykieta} onClick={onUsun}>×</button>
      )}
    </span>
  );
}

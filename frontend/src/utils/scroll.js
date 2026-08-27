/**
 * Przewijanie okna na gore.
 *
 * <p><b>Dlaczego to nie jest jednolinijkowiec w komponencie.</b> Bo trzeba
 * uszanowac ustawienie "ogranicz animacje" z systemu. Regula CSS
 * {@code scroll-behavior: auto} dziala na przewijanie wywolane przez
 * przegladarke, ale <b>nie</b> na {@code window.scrollTo} z parametrem
 * {@code behavior: 'smooth'} - o tym decyduje wylacznie kod. Bez tego
 * sprawdzenia osoba, ktora wylaczyla animacje, i tak dostawalaby plynny
 * przeskok przez cala strone.</p>
 *
 * @param smooth czy przewijac plynnie (przy zmianie strony chcemy natychmiast)
 */
export function scrollToTop(smooth = false) {
  const reducedMotion = typeof window.matchMedia === 'function'
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  window.scrollTo({ top: 0, behavior: smooth && !reducedMotion ? 'smooth' : 'auto' });
}

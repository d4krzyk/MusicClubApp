/** Przewijanie okna na gore. */
export function scrollToTop(smooth = false) {
  const reducedMotion = typeof window.matchMedia === 'function'
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  window.scrollTo({ top: 0, behavior: smooth && !reducedMotion ? 'smooth' : 'auto' });
}

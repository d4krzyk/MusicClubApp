// Ikony w stylu kreskowym (24x24, currentColor) - jedna rodzina w calej aplikacji.
const I = ({ children, size = 20, ...r }) => (
  <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" strokeWidth="1.9"
    strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false" {...r}>{children}</svg>
);
export const IkKoncerty = (p) => <I {...p}><rect x="3.5" y="5" width="17" height="15" rx="3" /><path d="M8 3v4M16 3v4M3.5 10h17" /><path d="M12 13.2v3.6a1.4 1.4 0 1 1-1.4-1.4H12" /></I>;
export const IkGust = (p) => <I {...p}><path d="M9 18V6l11-2v12" /><circle cx="6.5" cy="18" r="2.5" /><circle cx="17.5" cy="16" r="2.5" /></I>;
export const IkUstawienia = (p) => <I {...p}><path d="M4 7h10M18 7h2M4 17h4M12 17h8" /><circle cx="16" cy="7" r="2" /><circle cx="10" cy="17" r="2" /></I>;
export const IkSzukaj = (p) => <I {...p}><circle cx="11" cy="11" r="6.5" /><path d="m20 20-4.2-4.2" /></I>;
export const IkX = (p) => <I {...p}><path d="M6 6l12 12M18 6 6 18" /></I>;
export const IkPinezka = (p) => <I {...p}><path d="M12 21s-6.5-5.6-6.5-11a6.5 6.5 0 0 1 13 0c0 5.4-6.5 11-6.5 11z" /><circle cx="12" cy="10" r="2.3" /></I>;
export const IkZasieg = (p) => <I {...p}><circle cx="12" cy="12" r="8" /><circle cx="12" cy="12" r="4" /><circle cx="12" cy="12" r=".6" fill="currentColor" /></I>;
export const IkStrzalka = (p) => <I {...p}><path d="m6 9 6 6 6-6" /></I>;
export const IkWstecz = (p) => <I {...p}><path d="M15 5 8 12l7 7" /></I>;
export const IkOdswiez = (p) => <I {...p}><path d="M20 11a8 8 0 0 0-14.3-4.6L4 8M4 4v4h4M4 13a8 8 0 0 0 14.3 4.6L20 16m0 4v-4h-4" /></I>;
export const IkPlus = (p) => <I {...p}><path d="M12 5v14M5 12h14" /></I>;
export const IkPtaszek = (p) => <I {...p}><path d="m5 12.5 4.5 4.5L19 7.5" /></I>;
export const IkBilet = (p) => <I {...p}><path d="M4 8a2 2 0 0 0 2-2h12a2 2 0 0 0 2 2v2a2 2 0 0 0 0 4v2a2 2 0 0 0-2 2H6a2 2 0 0 0-2-2v-2a2 2 0 0 0 0-4z" /><path d="M14 6v12" strokeDasharray="2 2.2" /></I>;
export const IkMapa = (p) => <I {...p}><path d="m9 4-5 2v14l5-2 6 2 5-2V4l-5 2z" /><path d="M9 4v14M15 6v14" /></I>;
export const IkTrasa = (p) => <I {...p}><circle cx="6" cy="18" r="2" /><circle cx="18" cy="6" r="2" /><path d="M8 18h7a3 3 0 0 0 0-6H9a3 3 0 0 1 0-6h7" /></I>;
export const IkKalendarzPlus = (p) => <I {...p}><rect x="3.5" y="5" width="17" height="15" rx="3" /><path d="M8 3v4M16 3v4M3.5 10h17M12 12.5v5M9.5 15h5" /></I>;
export const IkLink = (p) => <I {...p}><path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1" /><path d="M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1" /></I>;
export const IkPlik = (p) => <I {...p}><path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z" /><path d="M14 3v5h5M12 12v6M9.5 15.5 12 18l2.5-2.5" /></I>;
export const IkKosz = (p) => <I {...p}><path d="M4 7h16M10 11v6M14 11v6M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12M9 7V4h6v3" /></I>;
export const IkSlonce = (p) => <I {...p}><circle cx="12" cy="12" r="4" /><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" /></I>;
export const IkKsiezyc = (p) => <I {...p}><path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z" /></I>;
export const IkAuto = (p) => <I {...p}><rect x="3" y="4" width="18" height="12" rx="2" /><path d="M8 20h8M12 16v4" /></I>;
export const IkGwiazda = ({ wypelniona, ...p }) => (
  <I {...p}><path d="M12 3.2l2.7 5.5 6.1.9-4.4 4.3 1 6.1L12 17.1 6.6 20l1-6.1L3.2 9.6l6.1-.9z" fill={wypelniona ? 'currentColor' : 'none'} /></I>
);

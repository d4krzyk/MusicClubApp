import { createContext, useCallback, useContext, useRef, useState } from 'react';

// Krotkie potwierdzenia ("Zapisano", "Dodano artyste") - wyskakuja na dole i same znikaja.
const Ctx = createContext(() => {});
export const useToast = () => useContext(Ctx);

export function ToastProvider({ children }) {
  const [lista, setLista] = useState([]);
  const nr = useRef(0);
  const pokaz = useCallback((tekst, rodzaj = 'ok') => {
    const id = ++nr.current;
    setLista((l) => [...l.slice(-2), { id, tekst, rodzaj }]);
    setTimeout(() => setLista((l) => l.map((t) => (t.id === id ? { ...t, wychodzi: true } : t))), 2600);
    setTimeout(() => setLista((l) => l.filter((t) => t.id !== id)), 2900);
  }, []);
  return (
    <Ctx.Provider value={pokaz}>
      {children}
      <div className="mc-toasty" role="status" aria-live="polite">
        {lista.map((t) => <div key={t.id} className={`mc-toast ${t.rodzaj}${t.wychodzi ? ' wychodzi' : ''}`}>{t.tekst}</div>)}
      </div>
    </Ctx.Provider>
  );
}

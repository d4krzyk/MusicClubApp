import { useEffect, useId, useState } from 'react';
import Form from 'react-bootstrap/Form';
import { podpowiedziMiast } from '../api/lokalizacja';

/** Po tylu ms od ostatniej litery pytamy o podpowiedzi. */
const ZWLOKA = 200;

/**
 * Pole tekstowe z podpowiedziami miast (lista z serwera, po poczatku nazwy). Wpisac mozna cokolwiek,
 * takze miasto, ktorego nie ma na liscie - wtedy wazne jest tylko "to samo miasto".
 */
export default function MiastoInput({ id, wartosc, onChange, maxLength = 60, ...reszta }) {
  const listaId = useId();
  const [podpowiedzi, setPodpowiedzi] = useState([]);

  useEffect(() => {
    const tekst = wartosc.trim();
    if (tekst.length < 2) {
      setPodpowiedzi([]);
      return undefined;
    }
    let aktualne = true;
    const zegar = setTimeout(() => {
      podpowiedziMiast(tekst)
        .then((lista) => aktualne && setPodpowiedzi(lista.map((m) => m.name)))
        // podpowiedzi to dodatek - bez nich pole dziala jak zwykle
        .catch(() => aktualne && setPodpowiedzi([]));
    }, ZWLOKA);
    return () => {
      aktualne = false;
      clearTimeout(zegar);
    };
  }, [wartosc]);

  return (
    <>
      <Form.Control
        id={id}
        value={wartosc}
        maxLength={maxLength}
        list={listaId}
        autoComplete="off"
        onChange={(e) => onChange(e.target.value)}
        {...reszta}
      />
      <datalist id={listaId}>
        {podpowiedzi.map((nazwa) => <option key={nazwa} value={nazwa} />)}
      </datalist>
    </>
  );
}

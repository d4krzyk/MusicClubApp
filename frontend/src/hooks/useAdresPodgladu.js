import { useEffect, useState } from 'react';

/**
 * Lokalny adres (blob:) do podgladu pliku - zwalniany, gdy plik sie zmienia albo komponent znika.
 * URL.createObjectURL wolany wprost w renderze tworzy nowy adres przy kazdym odswiezeniu i zadnego
 * nie zwalnia: kazdy trzyma caly plik w pamieci az do zamkniecia karty.
 */
export default function useAdresPodgladu(plik) {
  const [adres, setAdres] = useState(null);
  useEffect(() => {
    if (!(plik instanceof Blob)) {
      setAdres(null);
      return undefined;
    }
    const nowy = URL.createObjectURL(plik);
    setAdres(nowy);
    return () => URL.revokeObjectURL(nowy);
  }, [plik]);
  return adres;
}

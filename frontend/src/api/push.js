import client from './client';

/** Powiadomienia push i przypomnienia o wydarzeniach. */

/** Czy serwer wysyla push, jego klucz, przypomnienia i liczba moich urzadzen. */
export async function ustawienia() {
  const { data } = await client.get('/push');
  return data;
}

export async function zmienPrzypomnienia(wlaczone) {
  const { data } = await client.put('/push/reminders', { eventReminders: wlaczone });
  return data;
}

/** Zapisuje subskrypcje przegladarki (PushSubscription) razem z jezykiem tresci. */
export async function zapiszUrzadzenie(subskrypcja, jezyk) {
  const { endpoint, keys } = subskrypcja.toJSON();
  await client.post('/push/subscriptions', {
    endpoint, p256dh: keys.p256dh, auth: keys.auth, lang: jezyk,
  });
}

export async function wypiszUrzadzenie(endpoint) {
  await client.delete('/push/subscriptions', { data: { endpoint } });
}

export async function probne() {
  await client.post('/push/test');
}

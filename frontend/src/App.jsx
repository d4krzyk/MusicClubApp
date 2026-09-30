import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import { ChatProvider } from './chat/ChatContext';
import { RequireAdmin, RequireAnonymous, RequireAuth } from './auth/RouteGuards';
import Layout from './components/Layout';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import FeedPage from './pages/FeedPage';
import PostPage from './pages/PostPage';
import FriendsPage from './pages/FriendsPage';
import ProfilePage from './pages/ProfilePage';
import SettingsPage from './pages/SettingsPage';
import UsersPage from './pages/UsersPage';
import ReportsPage from './pages/ReportsPage';
import MojeZgloszeniaPage from './pages/MojeZgloszeniaPage';
import EventsPage from './pages/EventsPage';
import EventPage from './pages/EventPage';
import ClanPage from './pages/ClanPage';
import PotwierdzEmailPage from './pages/PotwierdzEmailPage';
import ResetHaslaPage from './pages/ResetHaslaPage';
import NoweHasloPage from './pages/NoweHasloPage';
import ZmianaAdresuPage from './pages/ZmianaAdresuPage';

/** Mapa adresow aplikacji. */
export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        {/*
          Czat obejmuje CALA aplikacje, bo otwiera go kilka miejsc naraz: ikona w pasku, przycisk
          "Napisz" na profilu znajomego.
        */}
        <ChatProvider>
          <Layout>
            <Routes>
              {/* Strona glowna TO tablica. */}
              <Route
                path="/"
                element={
                  <RequireAuth>
                    <FeedPage />
                  </RequireAuth>
                }
              />

              {/*
                Pojedynczy post pod wlasnym adresem - tu prowadza powiadomienia o reakcjach i taki
                link da sie komus wyslac.
              */}
              <Route
                path="/post/:id"
                element={
                  <RequireAuth>
                    <PostPage />
                  </RequireAuth>
                }
              />

              {/* Stary adres tablicy zostaje jako przekierowanie. */}
              <Route path="/feed" element={<Navigate to="/" replace />} />
              {/*
                Dwie sciezki, jedna strona: /profil to skrot do wlasnego profilu (wygodny link z
                menu), /profil/:username to czyjs profil.
              */}
              <Route
                path="/profil"
                element={
                  <RequireAuth>
                    <ProfilePage />
                  </RequireAuth>
                }
              />
              <Route
                path="/profil/:username"
                element={
                  <RequireAuth>
                    <ProfilePage />
                  </RequireAuth>
                }
              />
              <Route
                path="/znajomi"
                element={
                  <RequireAuth>
                    <FriendsPage />
                  </RequireAuth>
                }
              />
              {/* Koncerty z Ticketmastera: lista i pojedyncze wydarzenie. */}
              <Route
                path="/wydarzenia"
                element={
                  <RequireAuth>
                    <EventsPage />
                  </RequireAuth>
                }
              />
              <Route
                path="/wydarzenia/:id"
                element={
                  <RequireAuth>
                    <EventPage />
                  </RequireAuth>
                }
              />
              {/* Klan: /klan to moj klan albo zaproszenia i zakladanie, /klany/:id - strona dowolnego klanu */}
              <Route
                path="/klan"
                element={
                  <RequireAuth>
                    <ClanPage />
                  </RequireAuth>
                }
              />
              <Route
                path="/klany/:id"
                element={
                  <RequireAuth>
                    <ClanPage />
                  </RequireAuth>
                }
              />
              {/* Wlasne zgloszenia - kazdy zalogowany widzi tylko swoje */}
              <Route
                path="/moje-zgloszenia"
                element={
                  <RequireAuth>
                    <MojeZgloszeniaPage />
                  </RequireAuth>
                }
              />
              <Route
                path="/settings"
                element={
                  <RequireAuth>
                    <SettingsPage />
                  </RequireAuth>
                }
              />

              {/* Lista wszystkich kont - tylko administrator */}
              <Route
                path="/users"
                element={
                  <RequireAdmin>
                    <UsersPage />
                  </RequireAdmin>
                }
              />

              {/* Panel zgloszen. */}
              <Route
                path="/zgloszenia"
                element={
                  <RequireAdmin>
                    <ReportsPage />
                  </RequireAdmin>
                }
              />
              <Route
                path="/login"
                element={
                  <RequireAnonymous>
                    <LoginPage />
                  </RequireAnonymous>
                }
              />
              <Route
                path="/register"
                element={
                  <RequireAnonymous>
                    <RegisterPage />
                  </RequireAnonymous>
                }
              />

              {/*
                Link z wiadomosci. Bez zadnego straznika: otwiera go i ktos niezalogowany
                (nowe konto), i zalogowany (zmiana adresu w ustawieniach).
              */}
              <Route path="/potwierdz-email" element={<PotwierdzEmailPage />} />
              <Route path="/potwierdz-zmiane-adresu" element={<ZmianaAdresuPage />} />

              {/* Reset hasla - takze dla zalogowanego (przycisk z powiadomienia "to nie ja") */}
              <Route path="/reset-hasla" element={<ResetHaslaPage />} />
              <Route path="/nowe-haslo" element={<NoweHasloPage />} />

              {/* Nieznany adres - zamiast pustej strony wracamy na glowna */}
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </Layout>
        </ChatProvider>
      </AuthProvider>
    </BrowserRouter>
  );
}

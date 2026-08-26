# =============================================================================
#  Obraz frontendu. KROK 6 planu - calosc na docker compose.
#
#  Dwa etapy, tak samo jak w Dockerfile backendu:
#   1. Node buduje aplikacje (npm run build) -> katalog dist/
#   2. nginx serwuje gotowe pliki
#
#  Node jest potrzebny TYLKO do budowania. Gotowy obraz zawiera sam nginx
#  i kilkaset kilobajtow plikow - zamiast calego srodowiska Node
#  z node_modules, ktore wazy setki megabajtow.
# =============================================================================

# --- ETAP 1: budowanie ------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /build

# Najpierw same pliki z lista zaleznosci. Docker cache'uje warstwy, wiec
# dopoki nie ruszysz package.json, npm ci nie bedzie sie wykonywac od nowa
# przy kazdej zmianie w kodzie.
COPY package.json package-lock.json ./

# `npm ci` zamiast `npm install` - instaluje DOKLADNIE wersje z package-lock.json.
# `npm install` moze po cichu podbic wersje, przez co obraz zbudowany dzis
# roznilby sie od zbudowanego wczoraj z tego samego kodu.
RUN npm ci

COPY . .
RUN npm run build

# --- ETAP 2: serwowanie -----------------------------------------------------
FROM nginx:alpine

# Nasza konfiguracja zastepuje domyslna - to ona przekazuje /api do backendu
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 80

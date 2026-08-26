# =============================================================================
#  Obraz backendu (KROK 6: calosc na docker compose).
#
#  Do pracy nad kodem nadal wygodniej odpalac backend z IntelliJ /
#  `mvn spring-boot:run` - tak da sie go debugowac. Ten obraz sluzy do tego,
#  zeby calosc wstawala jedna komenda `docker compose up`.
# =============================================================================

# --- ETAP 1: budowanie jara -------------------------------------------------
# Budujemy w kontenerze, wiec nie trzeba miec Mavena na swoim komputerze
# ani pamietac o `mvn package` przed `docker build`.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Najpierw sam pom.xml i pobranie zaleznosci. Docker cache'uje warstwy, wiec
# dopoki nie ruszysz pom.xml, zaleznosci nie beda pobierane od nowa przy
# kazdej zmianie w kodzie.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# --- ETAP 2: obraz uruchomieniowy -------------------------------------------
# Do samego uruchomienia wystarczy JRE (bez Mavena i kompilatora),
# dzieki czemu gotowy obraz jest duzo mniejszy.
FROM eclipse-temurin:17-jre
WORKDIR /app

# curl sluzy WYLACZNIE do healthchecku z docker-compose.yml. Instalujemy go
# jawnie, zamiast liczyc na to, ze akurat jest w obrazie bazowym - inaczej
# healthcheck zglaszalby kontener jako chory, mimo ze aplikacja dziala.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Katalog na wgrane zdjecia. W compose montujemy w to miejsce wolumen,
# zeby pliki przezyly restart kontenera.
RUN mkdir -p /app/uploads

COPY --from=build /build/target/musicclubapp-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

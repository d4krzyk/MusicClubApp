# =============================================================================
#  Obraz backendu. Uzywany dopiero w KROKU 6 (calosc na docker compose).
#  Na razie backend odpalasz z IntelliJ / `mvn spring-boot:run`, zeby moc
#  go debugowac - a na Dockerze stoi sama baza.
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

COPY --from=build /build/target/musicclubapp-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

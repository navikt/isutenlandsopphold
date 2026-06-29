# isutenlandsopphold

Applikasjon for team iSyfo for vedtak av søknader om å beholde sykepenger under utenlandsopphold.

## Technologies used

* Docker
* Gradle
* Kotlin
* Ktor
* Postgres
* Flyway
* HikariCP

##### Test Libraries:

* JUnit
* embedded-postgres

## Lokal utvikling

1. Start en lokal Postgres-database med Docker Compose:

   ```bash
   docker compose up -d
   ```

   Dette starter Postgres på `localhost:5432` med database `isutenlandsopphold_dev`
   (bruker `username` / passord `password`).
2. Start applikasjonen i lokal modus:

   ```bash
   ./gradlew run
   ```

   `KTOR_ENV` defaulter til `local`, som gjør at appen kobler til Postgres med
   lokale standardverdier i stedet for NAIS-miljøvariabler. Flyway kjører
   migrasjonene automatisk ved oppstart.

3. Verifiser at appen kjører:

   ```bash
   curl http://localhost:8080/internal/is_alive    # I'm alive! :)
   curl http://localhost:8080/internal/is_ready     # I'm ready! :)
   ```

## Build

```bash
./gradlew clean shadowJar
```

## Test

```bash
./gradlew test
```

## Lint (Ktlint)

Sjekk: `./gradlew --continue ktlintCheck`

Formater: `./gradlew ktlintFormat`

## Database

Migrasjoner ligger i `src/main/resources/db/migration` og kjøres med Flyway ved
oppstart. Lokalt brukes Postgres fra `docker-compose.yaml`; tester bruker
embedded-postgres.

## Contact

### For NAV employees

We are available at the Slack channel `#isyfo`.

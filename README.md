# Secret Santa Dropwizard API

An intentionally small Java-only Dropwizard example implementing the supplied Secret Santa OpenAPI contract. It demonstrates configuration, Jersey resources, request authentication, JWT access/refresh tokens, health-ready Dropwizard wiring, and a repository boundary.

The default repository is in-memory so the example runs without infrastructure. `src/main/resources/schema.sql` contains a PostgreSQL-compatible schema matching the supplied ER diagram; replace `InMemorySecretSantaRepository` with a database-backed implementation when persistence is needed.

## Run

Requirements: Java 17+ and Maven 3.9+.

```bash
mvn clean package
export SECRET_SANTA_SECRET='change-me'
java -jar target/secret-santa-dropwizard-1.0.0-SNAPSHOT.jar server config.yml
```

The API is available at `http://localhost:8080`; Dropwizard admin endpoints are at `http://localhost:8081`.

The authentication layer uses the `SECRET_SANTA_SECRET` environment variable by default. Set it before starting the server, or override it in `config.yml` under the `auth.secret` property.

Register first, sign in to obtain a bearer token, then use it for group and user endpoints:

```bash
curl -X POST http://localhost:8080/v1/user \
  -H 'Content-Type: application/json' \
  -d '{"user_name":"Alice","email":"alice@example.com","password":"secret123"}'
```

The OpenAPI file supplied for this example can be copied to `src/main/resources/openapi.yaml` if a documentation endpoint or generated client is added later.

## Data model

The schema models `users`, `groups`, `participants`, and `chat`. A draw assigns each participant the next participant in a circular permutation, which prevents self-assignment and demonstrates the core domain behavior.

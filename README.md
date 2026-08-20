# Panfu Game Server

Nowy silnik czasu rzeczywistego Panfu oparty na Java 21, Spring Boot i Reactor Netty.

## Uruchomienie testów

```bash
./gradlew clean check
```

## Porty

- `9596`: HTTP, WebSocket `/game` i endpointy wewnętrzne;
- `9595`: przejściowy, surowy protokół TCP dla klienta legacy.

Konfiguracja jest przekazywana przez zmienne środowiskowe opisane w `application.yml`.

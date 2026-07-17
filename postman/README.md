# PayFlow Postman Collection

This collection provides an executable local workflow for the PayFlow simulated digital-wallet API.

## Import

Import both files into Postman:

- `PayFlow.postman_collection.json`
- `PayFlow.local.postman_environment.json`

Select the **PayFlow Local** environment.

## Prerequisites

```bash
docker compose up -d postgres redis kafka
./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
docker compose up -d postgres redis kafka
.\mvnw.cmd spring-boot:run
```

## Recommended run order

1. System
2. Authentication
3. Users
4. Wallets
5. Transfers
6. Transactions

The registration requests generate unique source and target emails. Login requests save JWTs. Wallet requests save wallet IDs. `Create Transfer` generates an `Idempotency-Key`.

## Replay verification

Run `Create Transfer`, then run `Replay Last Transfer` without rerunning the first request. The replay must return the same transaction ID without changing balances again.

## Security

The repository contains no real JWTs, personal credentials, or production secrets. Do not commit a Postman environment exported after it contains live or sensitive values.

## API documentation

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

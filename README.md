# LumenBank

A full-stack banking demo: **Java 17** REST API (JDK `HttpServer` + **SQLite**) and a dark **React** (Vite) dashboard.

> Educational project. Not a real bank — do not use it with real money or real card data.

## Features
- Open an account: Luhn-valid 16-digit card number and a random 4-digit PIN (shown once)
- PIN stored as a salted **PBKDF2-SHA256** hash, never in clear text
- Deposit, transfer between cards, close account
- Transfers are **one atomic SQL transaction** (debit + credit + history, or nothing)
- Validation: positive amounts only, no self-transfers, no overdraft, Luhn check on recipient
- Bank-card style UI, balance chart, transaction history
- Console version with the same logic and the same database

## Structure
```
backend/   Java API  — Luhn.java · Bank.java (SQL + business logic) · Server.java (HTTP) · Console.java (CLI) · JUnit tests
frontend/  React UI  — Vite, plain CSS, no UI libraries
```

## Run
Requirements: JDK 17+, Maven, Node 18+.

```bash
# 1) API on http://localhost:8080
cd backend
mvn test
mvn compile exec:java

# 2) UI on http://localhost:5173 (new terminal)
cd frontend
npm install
npm run dev
```

### Console version
```bash
cd backend
mvn compile exec:java -Dexec.mainClass=bank.Console
```
Open the account in the console, then see it in the web UI (same `lumenbank.db`).

## API
| Method | Path | Auth | Body |
|---|---|---|---|
| POST | `/api/register` | – | – |
| POST | `/api/login` | – | `number`, `pin` |
| GET | `/api/me` | Bearer | – |
| POST | `/api/deposit` | Bearer | `amount` |
| POST | `/api/transfer` | Bearer | `to`, `amount` |
| DELETE | `/api/account` | Bearer | – |

## Author
Sima Kogan

Licensed under the MIT License.

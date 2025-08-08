# notif-test

A cool code challenge

## Prerequisites

- Java (JDK 11+) and Leiningen
- Node.js and npm
- Docker (for PostgreSQL) or local PostgreSQL 15+

## Usage

Quick start (in two different terminals)

### Terminal 1 – Database + Backend (Postgres default)

1. Start Postgres

```bash
docker compose up -d db
```

2. Prepare backend deps and database

```bash
lein deps
lein migratus migrate
lein db:seed
```

3. Start the web server (REPL-based dev)

```bash
lein repl
;; In the REPL, evaluate:
(require '[ring.adapter.jetty :as jetty] '[notif-test.web :as web])
(def server (jetty/run-jetty web/app {:port 3000 :join? false}))
;; When done: (.stop server)
```

### Terminal 2 – Frontend watcher

1. Install and start Shadow-CLJS watcher

```bash
npm install
npm run start
```

Open the app

- http://localhost:3000 (served by the backend; Shadow-CLJS watcher will output to `resources/public/js`)

Extras

- Run tests: `lein test`
- Production build (optimized JS): `npm run release`

Notes

- Default development uses Postgres-backed repositories for users and messages (logs remain in-memory).
- Default DATABASE_URL: jdbc:postgresql://localhost:5432/notif_test?user=notif&password=secret
- If you already have Postgres running locally, you can skip Docker; ensure DATABASE_URL matches.
- shadow-cljs.edn config includes :dev-http {3001 {:root "resources/public"}}.
- You can open http://localhost:3001/index.html for static hosting, but API calls to /api will target 3001. Prefer using the backend at 3000 for full functionality.

Key entry points

- Ring handler: notif-test.web/app
- Routes: defined via Reitit in src/notif_test/web.clj
- Service logic: submit-message! in src/notif_test/service/notification_service.clj

API usage

- POST /api/messages
  JSON body: {"category":"sports|finance|movies","messageBody":"your text"}
- GET /api/logs
  Returns notification logs (newest first).

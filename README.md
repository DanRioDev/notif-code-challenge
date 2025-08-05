# notif-test

A cool code challenge

## Usage

To run this project locally and execute tests, follow these steps.

Prerequisites

- Java (JDK 8+)
- Leiningen installed and available on PATH

Install dependencies

- lein deps

Start the web server
Option 1: From a REPL (recommended for dev)

1. lein repl
2. In the REPL, start Jetty with the Ring handler:
   (require 'ring.adapter.jetty)
   (require 'notif-test.web)
   (def server (ring.adapter.jetty/run-jetty notif-test.web/app {:port 3000 :join? false}))
3. Open http://localhost:3000 to access the form and log history.
4. To stop the server: (.stop server)

Option 2: Quick one-liner from REPL

- lein repl
  Then paste:
  (do (require 'ring.adapter.jetty 'notif-test.web)
  (ring.adapter.jetty/run-jetty notif-test.web/app {:port 3000}))

Run tests

- lein test

Key entry points

- Ring handler: notif-test.web/app
- Routes: defined via Reitit in src/notif_test/web.clj
- Service logic: submit-message! in src/notif_test/service/notification_service.clj

API usage

- POST /api/messages
  JSON body: {"category":"sports|finance|movies","messageBody":"your text"}
- GET /api/logs
  Returns notification logs (newest first).

## License

Copyright © 2025 FIXME

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.

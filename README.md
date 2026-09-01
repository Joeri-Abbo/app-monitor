# app-monitor

The same small website, written four times — in **PHP**, **Java**, **TypeScript**
and **C#** — each with a runtime-info page and a [Prometheus](https://prometheus.io/)
metrics exporter, so the four runtimes can be watched side by side in Grafana.
All runnable via Docker Compose.

## 🧩 The four apps

| App          | Stack                        | Port   | Info page     | Metric prefix  |
| ------------ | ---------------------------- | ------ | ------------- | -------------- |
| `php-app`    | PHP 8.3 + Illuminate router  | `8080` | `/phpinfo`    | `phpapp_`      |
| `java-app`   | Java 21 + Spring Boot        | `8081` | `/javainfo`   | `javaapp_`     |
| `node-app`   | TypeScript + Express         | `8082` | `/nodeinfo`   | `nodeapp_`     |
| `dotnet-app` | C# + ASP.NET Core (.NET 10)  | `8083` | `/dotnetinfo` | `dotnetapp_`   |

Every app exposes the same three routes:

| Route         | Description                                       |
| ------------- | ------------------------------------------------- |
| `/`           | Landing page with links and live readings         |
| `/<x>info`    | The full runtime report (the `phpinfo()` idea)     |
| `/metrics`    | Prometheus text-format metrics                    |

…and the same metric shape:

- `<prefix>http_requests_total{route,status}` — every handled request, 404s
  included, minus the `/metrics` scrape itself
- `<prefix>process_*` — memory, CPU, threads and uptime for that process
- `<prefix>system_*` — container RAM, CPU count and load average, all read from
  `/proc` so the four agree on what they are measuring

On top of that, each app contributes whatever its ecosystem's client library
collects for free: `jvm_*` (Micrometer), `nodejs_*` (prom-client) and `dotnet_*`
(prometheus-net). PHP has no equivalent, which is what the separate
`phpinfo-exporter` makes up for.

The info pages deliberately omit environment variables — unlike `phpinfo()`,
which prints them — but they still expose plenty about the runtime, so keep them
off the public internet.

## 🏗️ Project Structure

```
app-monitor/
├── php-app/                     # PHP 8.3 + Illuminate router
│   ├── public/index.php         # Front controller + routes
│   ├── src/Metrics.php          # Prometheus exporter (APCu-backed)
│   ├── composer.json
│   └── Dockerfile               # php:8.3-apache + apcu + composer
├── java-app/                    # Java 21 + Spring Boot
│   ├── src/main/java/com/example/javamonitor/
│   │   ├── HomeController.java  JavaInfoController.java  MetricsController.java
│   │   ├── AppMetrics.java      # javaapp_* counter + gauges (Micrometer)
│   │   ├── RequestMetricsFilter.java
│   │   └── SystemReadings.java  # JVM + /proc readings
│   ├── pom.xml
│   └── Dockerfile               # maven:3.9-eclipse-temurin-21 → eclipse-temurin:21-jre
├── node-app/                    # TypeScript + Express
│   ├── src/
│   │   ├── app.ts               # Express app, routes, request counting
│   │   ├── metrics.ts           # nodeapp_* counter + gauges (prom-client)
│   │   ├── nodeinfo.ts          # /nodeinfo report
│   │   └── readings.ts          # process + /proc readings
│   ├── package.json  tsconfig.json
│   └── Dockerfile               # node:22-slim, built then run with prod deps only
├── dotnet-app/                  # C# + ASP.NET Core
│   ├── Program.cs               # Minimal API, routes, request counting
│   ├── AppMetrics.cs            # dotnetapp_* counter + gauges (prometheus-net)
│   ├── DotnetInfo.cs            # /dotnetinfo report
│   ├── SystemReadings.cs        # runtime + /proc readings
│   ├── DotnetApp.csproj
│   └── Dockerfile               # sdk:10.0 → aspnet:10.0
├── phpinfo-exporter/            # Python exporter that scrapes /phpinfo
│   ├── exporter.py              # Parses OPcache used/free memory + PHP_VERSION
│   └── Dockerfile               # python:3.12-slim
├── monitoring/
│   ├── prometheus.yml           # One job per app + the phpinfo exporter
│   └── grafana/                 # Provisioned datasource + four dashboards
├── docker-compose.yml
└── start.sh                     # Build & run helper
```

## 📈 Monitoring stack

- **`phpinfo-exporter`** (Python, port `9308`) scrapes `php-app`'s `/phpinfo`
  HTML on every Prometheus poll and re-exports:
  - `php_opcache_used_memory_bytes` — Zend OPcache "Used memory"
  - `php_opcache_free_memory_bytes` — Zend OPcache "Free memory"
  - `php_info{version="..."}` — PHP_VERSION from the Environment section
  - `php_phpinfo_up` — `1` if the last scrape succeeded
- **Prometheus** (port `9090`) scrapes five jobs: one per app, plus the
  `phpinfo-exporter`.
- **Grafana** (port `3000`) auto-provisions the Prometheus datasource and four
  dashboards — **PHP OPcache**, **Java JVM**, **Node.js Runtime** and
  **.NET Runtime** — with anonymous access enabled for convenience.

### Notes per runtime

- **PHP** — counters live in APCu, because every request is a fresh process and
  an in-memory registry would reset to zero on each scrape.
- **Java** — Micrometer gauges take a supplier and are re-read per scrape. The
  same registry is also served at `/actuator/prometheus`.
- **Node** — prom-client's default metrics add V8 heap spaces, event-loop lag
  and GC timings; the dashboard graphs the lag.
- **.NET** — prometheus-net has no per-gauge callback, so one before-collect
  callback refreshes them all. `GCMemoryInfo` stays zeroed until the first
  collection, so the exporter reports allocated bytes instead of committed
  bytes, and `/dotnetinfo` says so explicitly.

## 🚀 Running with Docker

```bash
./start.sh
# or:
docker-compose up -d --build
```

Then open:

| What                     | URL                                              |
| ------------------------ | ------------------------------------------------ |
| PHP website              | <http://localhost:8080> (`/phpinfo`, `/metrics`)   |
| Java website             | <http://localhost:8081> (`/javainfo`, `/metrics`)  |
| Node website             | <http://localhost:8082> (`/nodeinfo`, `/metrics`)  |
| .NET website             | <http://localhost:8083> (`/dotnetinfo`, `/metrics`)|
| phpinfo exporter         | <http://localhost:9308/metrics>                    |
| Prometheus               | <http://localhost:9090>                            |
| Grafana                  | <http://localhost:3000>                            |

Stop everything with `docker-compose down`.

## 🧑‍💻 Local development

Each app runs standalone on port `8080`. The `<prefix>system_*` gauges are read
from `/proc`, so they are only registered on Linux; on macOS the homepages show
`n/a` for those rows.

### PHP

Requires PHP 8.2+ and Composer. The `apcu` extension is needed for `/metrics`
(the homepage and `/phpinfo` work without it).

```bash
cd php-app && composer install && php -S 127.0.0.1:8080 -t public
```

### Java

Requires JDK 21+ and Maven.

```bash
cd java-app && mvn verify && mvn spring-boot:run
```

### Node

Requires Node 20+.

```bash
cd node-app && npm install && npm test && npm start
```

### .NET

Requires the .NET 10 SDK.

```bash
cd dotnet-app && dotnet run
```

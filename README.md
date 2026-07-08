# php-monitor

A small PHP website served through an [Illuminate](https://packagist.org/packages/illuminate/routing)
router, with a `phpinfo()` page and a [Prometheus](https://prometheus.io/) metrics
exporter — all runnable via Docker Compose.

## 🏗️ Project Structure

```
php-monitor/
├── php-app/                     # The PHP website
│   ├── public/
│   │   ├── index.php            # Front controller + Illuminate router
│   │   └── .htaccess            # Rewrites all requests to index.php
│   ├── src/
│   │   └── Metrics.php          # Prometheus exporter (APCu-backed)
│   ├── composer.json            # Dependencies (illuminate/routing, prometheus_client_php)
│   └── Dockerfile               # php:8.3-apache + apcu + composer
├── phpinfo-exporter/            # Python exporter that scrapes /phpinfo
│   ├── exporter.py              # Parses OPcache used/free memory + PHP_VERSION
│   ├── requirements.txt
│   └── Dockerfile               # python:3.12-slim
├── monitoring/
│   ├── prometheus.yml           # Scrapes php-app AND phpinfo-exporter
│   └── grafana/                 # Provisioned datasource + "PHP OPcache" dashboard
├── docker-compose.yml           # php-app + phpinfo-exporter + prometheus + grafana
└── start.sh                     # Build & run helper
```

## 📈 Monitoring stack

- **`phpinfo-exporter`** (Python, port `9308`) scrapes `php-app`'s `/phpinfo`
  HTML on every Prometheus poll and re-exports:
  - `php_opcache_used_memory_bytes` — Zend OPcache "Used memory"
  - `php_opcache_free_memory_bytes` — Zend OPcache "Free memory"
  - `php_info{version="..."}` — PHP_VERSION from the Environment section
  - `php_phpinfo_up` — `1` if the last scrape succeeded
- **Prometheus** (port `9090`) scrapes two jobs: `php-app` (the app's own
  `/metrics`) and `phpinfo-exporter`.
- **Grafana** (port `3000`) auto-provisions the Prometheus datasource and a
  **PHP OPcache** dashboard (anonymous access is enabled for convenience).

## 🌐 Routes

| Route       | Description                                             |
| ----------- | ------------------------------------------------------- |
| `/`         | Landing page with links                                 |
| `/phpinfo`  | The full `phpinfo()` report                             |
| `/metrics`  | Prometheus text-format metrics (`phpapp_*`)             |

The app's own exporter records `phpapp_http_requests_total{route,status}` plus
live `phpapp_process_*` (per-request memory/CPU) and `phpapp_system_*`
(container RAM, CPU count, load average) gauges. Counters are stored in APCu so
they survive between requests (each PHP request is a fresh process). The
homepage also renders these live values in a small table.

## 🚀 Running with Docker

```bash
./start.sh
# or:
docker-compose up -d --build
```

Then open:

- PHP website: <http://localhost:8080>
- phpinfo: <http://localhost:8080/phpinfo>
- App metrics: <http://localhost:8080/metrics>
- phpinfo exporter: <http://localhost:9308/metrics>
- Prometheus: <http://localhost:9090>
- Grafana ("PHP OPcache" dashboard): <http://localhost:3000>

Stop everything with `docker-compose down`.

## 🧑‍💻 Local development

Requires PHP 8.2+ and Composer. The `apcu` extension is needed for the
`/metrics` endpoint (the homepage and `/phpinfo` work without it).

```bash
cd php-app
composer install
php -S 127.0.0.1:8080 -t public
```

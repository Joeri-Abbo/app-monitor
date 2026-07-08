"""Prometheus exporter that scrapes a PHP app's /phpinfo page.

It pulls three things out of the HTML on every scrape:
  * Zend OPcache "Used memory"  -> php_opcache_used_memory_bytes
  * Zend OPcache "Free memory"  -> php_opcache_free_memory_bytes
  * Environment PHP_VERSION     -> php_info{version="..."}

A php_phpinfo_up gauge reports whether the last scrape succeeded.
"""

import logging
import os
import time

import requests
from bs4 import BeautifulSoup
from prometheus_client import start_http_server
from prometheus_client.core import REGISTRY, GaugeMetricFamily

PHP_INFO_URL = os.environ.get("PHP_INFO_URL", "http://php-app:80/phpinfo")
LISTEN_PORT = int(os.environ.get("LISTEN_PORT", "9308"))
SCRAPE_TIMEOUT = float(os.environ.get("SCRAPE_TIMEOUT", "5"))

log = logging.getLogger("phpinfo-exporter")


def section_rows(soup, title):
    """Return {label: value} for every table row in the phpinfo section
    whose <h2> heading equals `title` (up to the next <h2>)."""
    heading = next(
        (h for h in soup.find_all("h2") if h.get_text(strip=True) == title),
        None,
    )
    if heading is None:
        return {}

    rows = {}
    for el in heading.find_all_next():
        if el.name == "h2":  # reached the next section
            break
        if el.name == "tr":
            cells = el.find_all("td")
            if len(cells) >= 2:
                rows[cells[0].get_text(strip=True)] = cells[1].get_text(strip=True)
    return rows


class PhpInfoCollector:
    """Scrapes /phpinfo on demand each time Prometheus pulls /metrics."""

    def collect(self):
        up = GaugeMetricFamily(
            "php_phpinfo_up", "1 if the last /phpinfo scrape succeeded, else 0."
        )
        used = GaugeMetricFamily(
            "php_opcache_used_memory_bytes", "Zend OPcache used memory in bytes."
        )
        free = GaugeMetricFamily(
            "php_opcache_free_memory_bytes", "Zend OPcache free memory in bytes."
        )
        info = GaugeMetricFamily(
            "php_info", "PHP build info; value is always 1.", labels=["version"]
        )

        try:
            resp = requests.get(PHP_INFO_URL, timeout=SCRAPE_TIMEOUT)
            resp.raise_for_status()
            soup = BeautifulSoup(resp.text, "html.parser")

            opcache = section_rows(soup, "Zend OPcache")
            environment = section_rows(soup, "Environment")

            used.add_metric([], float(opcache["Used memory"]))
            free.add_metric([], float(opcache["Free memory"]))
            info.add_metric([environment.get("PHP_VERSION", "unknown")], 1)
            up.add_metric([], 1)
        except Exception as exc:  # noqa: BLE001 - surface any failure as up=0
            log.warning("scrape of %s failed: %s", PHP_INFO_URL, exc)
            up.add_metric([], 0)

        yield up
        yield used
        yield free
        yield info


def main():
    logging.basicConfig(
        level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s"
    )
    REGISTRY.register(PhpInfoCollector())
    start_http_server(LISTEN_PORT)
    log.info("listening on :%d, scraping %s", LISTEN_PORT, PHP_INFO_URL)
    while True:
        time.sleep(3600)


if __name__ == "__main__":
    main()

#!/bin/bash

# php-monitor - Startup Script
# Builds and starts the PHP website and its Prometheus scraper.

set -e

echo "🐘 Starting php-monitor"
echo "======================="

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker first."
    exit 1
fi

# Check if Docker Compose is available
if ! command -v docker-compose > /dev/null 2>&1; then
    echo "❌ Docker Compose not found. Please install Docker Compose."
    exit 1
fi

echo "✅ Docker environment verified"

# Build and start all containers
echo "🏗️  Building and starting containers..."
docker-compose up -d --build

# Wait a moment for containers to start
echo "⏳ Waiting for containers to initialize..."
sleep 10

# Check container status
echo "📊 Container Status:"
docker-compose ps

echo ""
echo "🌐 Access URLs:"
echo "   PHP website:      http://localhost:8080"
echo "   phpinfo:          http://localhost:8080/phpinfo"
echo "   App metrics:      http://localhost:8080/metrics"
echo "   phpinfo exporter: http://localhost:9308/metrics"
echo "   Prometheus:       http://localhost:9090"
echo "   Grafana:          http://localhost:3000  (dashboard: 'PHP OPcache', anon access)"
echo ""
echo "🛑 To stop all containers:"
echo "   docker-compose down"

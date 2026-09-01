#!/bin/bash

# app-monitor - Startup Script
# Builds and starts the four example websites and their Prometheus scrapers.

set -e

echo "📊 Starting app-monitor"
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
echo "   PHP metrics:      http://localhost:8080/metrics"
echo "   Java website:     http://localhost:8081"
echo "   javainfo:         http://localhost:8081/javainfo"
echo "   Java metrics:     http://localhost:8081/metrics"
echo "   Node website:     http://localhost:8082"
echo "   nodeinfo:         http://localhost:8082/nodeinfo"
echo "   Node metrics:     http://localhost:8082/metrics"
echo "   .NET website:     http://localhost:8083"
echo "   dotnetinfo:       http://localhost:8083/dotnetinfo"
echo "   .NET metrics:     http://localhost:8083/metrics"
echo "   phpinfo exporter: http://localhost:9308/metrics"
echo "   Prometheus:       http://localhost:9090"
echo "   Grafana:          http://localhost:3000  (4 runtime dashboards, anon access)"
echo ""
echo "🛑 To stop all containers:"
echo "   docker-compose down"

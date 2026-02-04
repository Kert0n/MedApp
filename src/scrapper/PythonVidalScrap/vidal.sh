#!/bin/bash
# Vidal Scraper Management Script

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

COMPOSE_CMD="docker compose"
command -v docker-compose &>/dev/null && COMPOSE_CMD="docker-compose"

start() {
    log_info "Starting Vidal Scraper..."
    mkdir -p data
    $COMPOSE_CMD up -d
    log_info "Started! View logs: ./vidal.sh logs"
}

stop() {
    log_info "Stopping..."
    $COMPOSE_CMD down
}

status() {
    echo "=== Containers ==="
    $COMPOSE_CMD ps
    
    echo ""
    echo "=== Scraper Status ==="
    if docker exec vidal-scraper python scraper.py --stats 2>/dev/null; then
        :
    else
        log_warn "Scraper not running"
    fi
    
    echo ""
    echo "=== CSV File ==="
    if [ -f data/drugs.csv ]; then
        LINES=$(wc -l < data/drugs.csv)
        SIZE=$(du -h data/drugs.csv | cut -f1)
        log_info "drugs.csv: $((LINES-1)) records, $SIZE"
    else
        log_warn "No CSV file yet"
    fi
}

logs() {
    log_info "Following logs (Ctrl+C to exit)..."
    docker logs -f --tail 100 vidal-scraper
}

stats() {
    docker exec vidal-scraper python scraper.py --stats
}

export_dump() {
    DUMP="vidal_$(date +%Y%m%d_%H%M%S).sql"
    log_info "Creating dump: $DUMP"
    docker exec vidal-postgres pg_dump -U vidal -d vidal --no-owner > "$DUMP"
    gzip "$DUMP"
    log_info "Created: ${DUMP}.gz ($(du -h "${DUMP}.gz" | cut -f1))"
}

psql_cli() {
    docker exec -it vidal-postgres psql -U vidal -d vidal
}

shell() {
    docker exec -it vidal-scraper bash
}

reset() {
    log_warn "This will delete all progress!"
    read -p "Continue? (yes/no): " confirm
    if [ "$confirm" = "yes" ]; then
        stop 2>/dev/null || true
        rm -rf data/*
        docker volume rm vidal-postgres-data 2>/dev/null || true
        log_info "Reset complete. Run './vidal.sh start'"
    fi
}

case "${1:-help}" in
    start)      start ;;
    stop)       stop ;;
    status)     status ;;
    logs)       logs ;;
    stats)      stats ;;
    export-dump) export_dump ;;
    psql)       psql_cli ;;
    shell)      shell ;;
    reset)      reset ;;
    *)
        echo "Vidal.ru Scraper"
        echo ""
        echo "Usage: ./vidal.sh <command>"
        echo ""
        echo "Commands:"
        echo "  start        Start scraping"
        echo "  stop         Stop"
        echo "  logs         Follow logs"
        echo "  status       Show status"
        echo "  stats        DB statistics"
        echo "  export-dump  Create SQL dump"
        echo "  psql         PostgreSQL CLI"
        echo "  shell        Bash in container"
        echo "  reset        Delete all data"
        ;;
esac

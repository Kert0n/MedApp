#!/bin/bash
set -e

log_info() { echo "[INFO] $1"; }
log_warn() { echo "[WARN] $1"; }
log_error() { echo "[ERROR] $1"; }

# Ждём PostgreSQL
wait_for_postgres() {
    log_info "Waiting for PostgreSQL..."
    for i in $(seq 1 60); do
        if pg_isready -h postgres -p 5432 -U vidal > /dev/null 2>&1; then
            log_info "PostgreSQL ready!"
            return 0
        fi
        sleep 2
    done
    log_error "PostgreSQL not available"
    exit 1
}

case "${1:-scrape}" in
    scrape)
        wait_for_postgres
        shift
        exec python -u scraper.py --healthcheck "$@"
        ;;
    stats)
        wait_for_postgres
        python scraper.py --stats
        ;;
    bash|sh)
        exec /bin/bash
        ;;
    *)
        log_error "Unknown: $1"
        echo "Commands: scrape, stats, bash"
        exit 1
        ;;
esac

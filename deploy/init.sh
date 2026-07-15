#!/usr/bin/env bash
# =============================================================================
# init.sh — one-time provisioning for the One Verandah image backend on a fresh
#           Ubuntu Lightnode (22.04+).
#
# Installs a JDK, (optionally) ArangoDB, and prepares the application directory.
# Safe to re-run: every step is idempotent.
#
# Usage:
#   sudo ./deploy/init.sh                 # JDK + app dir only
#   INSTALL_ARANGO=1 sudo ./deploy/init.sh   # also install ArangoDB
# =============================================================================
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/oneverandah-images}"
APP_USER="${APP_USER:-$(logname 2>/dev/null || echo "$USER")}"
INSTALL_ARANGO="${INSTALL_ARANGO:-0}"

echo "==> Updating apt and installing base dependencies..."
sudo apt-get update -y
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y \
    openjdk-17-jdk-headless curl wget git unzip ca-certificates

if [ "$INSTALL_ARANGO" = "1" ]; then
    echo "==> Installing ArangoDB..."
    if ! command -v arangod >/dev/null 2>&1; then
        curl -fsSL https://download.arangodb.com/arangodb312/DEBIAN/Release.key \
            | sudo gpg --dearmor -o /usr/share/keyrings/arangodb.gpg
        echo "deb [signed-by=/usr/share/keyrings/arangodb.gpg] https://download.arangodb.com/arangodb312/DEBIAN/ /" \
            | sudo tee /etc/apt/sources.list.d/arangodb.list >/dev/null
        sudo apt-get update -y
        # Preseed a blank root password for a non-interactive install.
        echo "arangodb3 arangodb3/password password ${ARANGO_PASSWORD:-}" | sudo debconf-set-selections
        echo "arangodb3 arangodb3/password_again password ${ARANGO_PASSWORD:-}" | sudo debconf-set-selections
        echo "arangodb3 arangodb3/upgrade boolean true" | sudo debconf-set-selections
        echo "arangodb3 arangodb3/storage_engine select rocksdb" | sudo debconf-set-selections
        sudo DEBIAN_FRONTEND=noninteractive apt-get install -y arangodb3
    fi
    sudo systemctl enable arangodb3
    sudo systemctl start arangodb3
    echo "    ArangoDB is running on 127.0.0.1:8529. The application creates the"
    echo "    'image_generator' database, collection and indexes on first start."
else
    echo "==> Skipping ArangoDB install (set INSTALL_ARANGO=1 to enable)."
    echo "    Without ArangoDB the backend uses in-memory storage."
fi

echo "==> Preparing application directory at ${APP_DIR}..."
sudo mkdir -p "${APP_DIR}"
sudo mkdir -p "${APP_DIR}/data/images"
sudo chown -R "${APP_USER}:${APP_USER}" "${APP_DIR}"

if [ ! -f "${APP_DIR}/.env" ]; then
    if [ -f "$(dirname "$0")/../.env.example" ]; then
        sudo cp "$(dirname "$0")/../.env.example" "${APP_DIR}/.env"
        sudo chown "${APP_USER}:${APP_USER}" "${APP_DIR}/.env"
        echo "    Created ${APP_DIR}/.env from .env.example — edit it with your"
        echo "    Qwen API key, OSS bucket credentials and (optionally) ArangoDB."
    fi
fi

echo "==> init complete. Next: edit ${APP_DIR}/.env, then run deploy/deploy.sh"

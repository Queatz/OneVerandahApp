#!/usr/bin/env bash
# =============================================================================
# deploy.sh — build the image backend fat JAR and (re)start it under systemd.
#
# Run from the repository root (or anywhere; the script cd's to the repo root).
# Requires that deploy/init.sh has been run once first.
#
# Usage:
#   ./deploy/deploy.sh
# =============================================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP_DIR="${APP_DIR:-/opt/oneverandah-images}"
APP_USER="${APP_USER:-$(logname 2>/dev/null || echo "$USER")}"
SERVICE_NAME="${SERVICE_NAME:-oneverandah-images}"
JAR_NAME="server-all.jar"

cd "$REPO_ROOT"

echo "==> Building fat JAR (:server:buildFatJar)..."
./gradlew :server:buildFatJar --console=plain

BUILT_JAR="server/build/libs/${JAR_NAME}"
if [ ! -f "$BUILT_JAR" ]; then
    echo "ERROR: expected JAR not found at $BUILT_JAR" >&2
    exit 1
fi

echo "==> Installing JAR into ${APP_DIR}..."
sudo mkdir -p "${APP_DIR}"
sudo systemctl stop "${SERVICE_NAME}.service" 2>/dev/null || true
sudo cp "$BUILT_JAR" "${APP_DIR}/${JAR_NAME}"

# Keep an example env in place but never overwrite an existing configured .env.
if [ ! -f "${APP_DIR}/.env" ] && [ -f ".env.example" ]; then
    sudo cp ".env.example" "${APP_DIR}/.env"
fi
sudo chown -R "${APP_USER}:${APP_USER}" "${APP_DIR}"

echo "==> Writing systemd unit /etc/systemd/system/${SERVICE_NAME}.service..."
sudo tee "/etc/systemd/system/${SERVICE_NAME}.service" >/dev/null <<EOF
[Unit]
Description=One Verandah Dynamic Image Generation Backend
After=network.target arangodb3.service

[Service]
User=${APP_USER}
WorkingDirectory=${APP_DIR}
EnvironmentFile=${APP_DIR}/.env
ExecStart=/usr/bin/java -jar ${APP_DIR}/${JAR_NAME}
SuccessExitStatus=143
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

echo "==> Reloading and starting service..."
sudo systemctl daemon-reload
sudo systemctl enable "${SERVICE_NAME}.service"
sudo systemctl restart "${SERVICE_NAME}.service"

echo "==> Deployed. Recent status:"
sudo systemctl status "${SERVICE_NAME}.service" --no-pager || true
echo
echo "Tail logs with: sudo journalctl -u ${SERVICE_NAME}.service -f"
echo "Health check:   curl -s http://127.0.0.1:7071/health"

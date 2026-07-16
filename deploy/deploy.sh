#!/usr/bin/env bash
# =============================================================================
# deploy.sh — build the backend fat JAR LOCALLY, upload ("deploy") it to the
#             remote server over SSH, and (re)start it there under systemd,
#             serving on the default public ports 80/443 with SSL enabled
#             using the server's existing Let's Encrypt certificate.
#
# Run this from your LOCAL machine (the repo root, or anywhere — the script
# cd's to the repo root). Requires that deploy/init.sh has already been run
# once on the remote server, that you have SSH access to it, and that a
# Let's Encrypt certificate for SSL_DOMAIN already exists on the server at
# /etc/letsencrypt/live/<SSL_DOMAIN>/{fullchain,privkey}.pem (e.g. via
# `certbot certonly`).
#
# Usage:
#   ./deploy/deploy.sh
#   SERVER_HOST=38.54.15.84 SERVER_USER=root SSL_DOMAIN=api.oneverandah.app ./deploy/deploy.sh
# =============================================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR_NAME="server-all.jar"
APP_DIR="${APP_DIR:-/opt/oneverandah-images}"
SERVICE_NAME="${SERVICE_NAME:-oneverandah-images}"
SERVER_HOST="${SERVER_HOST:-38.54.15.84}"
SERVER_USER="${SERVER_USER:-root}"
SSL_DOMAIN="${SSL_DOMAIN:-api.oneverandah.app}"
SSH_HOST="${SERVER_USER}@${SERVER_HOST}"
SSH_OPTS=(-o StrictHostKeyChecking=accept-new)

cd "$REPO_ROOT"

# 1. Build the backend fat JAR locally.
echo "==> Building fat JAR (:server:buildFatJar)..."
./gradlew :server:buildFatJar --console=plain

BUILT_JAR="server/build/libs/${JAR_NAME}"
if [ ! -f "$BUILT_JAR" ]; then
    echo "ERROR: expected JAR not found at $BUILT_JAR" >&2
    exit 1
fi

# 2. Deploy the JAR to the remote server.
echo "==> Deploying JAR to ${SSH_HOST}:${APP_DIR}..."
ssh "${SSH_OPTS[@]}" "$SSH_HOST" "sudo mkdir -p '${APP_DIR}'"
scp "${SSH_OPTS[@]}" "$BUILT_JAR" "${SSH_HOST}:/tmp/${JAR_NAME}"
ssh "${SSH_OPTS[@]}" "$SSH_HOST" "sudo mv -f '/tmp/${JAR_NAME}' '${APP_DIR}/${JAR_NAME}'"

# Deploy the real .env (with your production APP_ENV/ARANGO/OSS/QWEN config)
# to the server, so it actually runs with the same settings as your local
# production .env instead of silently falling back to the development
# placeholders baked into .env.example. This always overwrites the remote
# .env — that's safe because APP_PORT/SSL_* are re-applied on top of it below
# regardless of what this uploads.
if [ -f ".env" ]; then
    echo "==> Deploying local .env to ${SSH_HOST}:${APP_DIR}/.env..."
    scp "${SSH_OPTS[@]}" ".env" "${SSH_HOST}:/tmp/.env"
    ssh "${SSH_OPTS[@]}" "$SSH_HOST" "sudo mv -f '/tmp/.env' '${APP_DIR}/.env'"
elif [ -f ".env.example" ]; then
    # No local .env at all: seed the example as a last resort, and never
    # overwrite an already-configured remote .env in that case.
    scp "${SSH_OPTS[@]}" ".env.example" "${SSH_HOST}:/tmp/.env.example"
    ssh "${SSH_OPTS[@]}" "$SSH_HOST" \
        "[ -f '${APP_DIR}/.env' ] || sudo cp '/tmp/.env.example' '${APP_DIR}/.env'"
fi

# 3. Run the JAR on the remote server: build the SSL keystore from the
#    existing Let's Encrypt certificate, wire it into .env (serving on the
#    public 80/443 ports with SSL enabled), then write/refresh the systemd
#    unit and restart it.
echo "==> Starting ${SERVICE_NAME}.service on ${SERVER_HOST}..."
ssh "${SSH_OPTS[@]}" "$SSH_HOST" bash -s -- "$APP_DIR" "$SERVICE_NAME" "$JAR_NAME" "$SSL_DOMAIN" <<'REMOTE_SCRIPT'
set -euo pipefail
APP_DIR="$1"
SERVICE_NAME="$2"
JAR_NAME="$3"
SSL_DOMAIN="$4"
APP_USER="$(logname 2>/dev/null || echo "$USER")"
ENV_FILE="${APP_DIR}/.env"
KEYSTORE_PATH="${APP_DIR}/keystore.p12"
LE_DIR="/etc/letsencrypt/live/${SSL_DOMAIN}"

# --- Silence "sudo: unable to resolve host ..." noise --------------------
# Fresh cloud VMs frequently boot with a hostname that isn't in /etc/hosts,
# which makes every single `sudo` call below print a harmless but noisy
# "unable to resolve host" warning. Fix it once, up front.
CURRENT_HOSTNAME="$(hostname)"
if ! getent hosts "${CURRENT_HOSTNAME}" >/dev/null 2>&1; then
    echo "127.0.1.1 ${CURRENT_HOSTNAME}" | sudo tee -a /etc/hosts >/dev/null
fi

sudo chown -R "${APP_USER}:${APP_USER}" "${APP_DIR}"

# --- Make sure a compatible JDK is installed -------------------------------
# The fat JAR is built targeting Java 21 bytecode (see server/build.gradle.kts
# jvmToolchain). Running it on an older JDK fails immediately with
# "UnsupportedClassVersionError" (crash-loop, systemd auto-restart). Install
# JDK 21 automatically if it's missing, and make sure it's the active `java`.
REQUIRED_JAVA_MAJOR=21
CURRENT_JAVA_MAJOR="$(java -version 2>&1 | grep -oP '"\K[0-9]+' | head -1 || echo 0)"
if [ "${CURRENT_JAVA_MAJOR:-0}" -lt "$REQUIRED_JAVA_MAJOR" ]; then
    echo "==> Installed JDK (major ${CURRENT_JAVA_MAJOR:-0}) is older than required ${REQUIRED_JAVA_MAJOR}; installing OpenJDK ${REQUIRED_JAVA_MAJOR}..."
    # Tolerate unrelated broken third-party apt sources (e.g. a stale/typo'd
    # repo file) so they don't block installing the JDK from the main Ubuntu
    # archives, which apt-get update still refreshes successfully.
    sudo apt-get update -y >/dev/null 2>&1 || true
    sudo DEBIAN_FRONTEND=noninteractive apt-get install -y "openjdk-${REQUIRED_JAVA_MAJOR}-jdk-headless" >/dev/null
fi
NEW_JAVA_ALT="$(update-alternatives --list java 2>/dev/null | grep -m1 "java-${REQUIRED_JAVA_MAJOR}" || true)"
if [ -n "$NEW_JAVA_ALT" ]; then
    sudo update-alternatives --set java "$NEW_JAVA_ALT" >/dev/null
fi
echo "==> Using: $(java -version 2>&1 | head -1)"

# --- Build the SSL keystore from the server's Let's Encrypt certificate ----
if [ ! -f "${LE_DIR}/fullchain.pem" ] || [ ! -f "${LE_DIR}/privkey.pem" ]; then
    echo "ERROR: no Let's Encrypt certificate found at ${LE_DIR}." >&2
    echo "       Issue one first, e.g.: sudo certbot certonly --standalone -d ${SSL_DOMAIN}" >&2
    exit 1
fi

echo "==> Building SSL keystore from ${LE_DIR}..."
KEYSTORE_PASSWORD="$(openssl rand -hex 16)"

# Some OpenSSL 3.x builds default to PBES2/AES-256 encryption for PKCS12
# files, which some JDK builds fail to parse (the app then crashes on
# startup with an uncaught KeyStore exception). Build the keystore, verify
# the JVM itself can actually read it back with `keytool`, and automatically
# retry with widely-compatible legacy algorithms if not.
build_keystore() {
    sudo openssl pkcs12 -export \
        -in "${LE_DIR}/fullchain.pem" \
        -inkey "${LE_DIR}/privkey.pem" \
        -out "${KEYSTORE_PATH}" \
        -name oneverandah \
        -passout "pass:${KEYSTORE_PASSWORD}" \
        "$@"
}
keystore_readable() {
    sudo keytool -list -keystore "${KEYSTORE_PATH}" -storepass "${KEYSTORE_PASSWORD}" >/dev/null 2>&1
}

build_keystore
if ! keystore_readable; then
    echo "    Default keystore encoding isn't readable by this JDK; retrying with -legacy..."
    if build_keystore -legacy 2>/dev/null && keystore_readable; then
        :
    else
        echo "    Retrying with explicit legacy-compatible cipher/MAC..."
        build_keystore -certpbe PBE-SHA1-3DES -keypbe PBE-SHA1-3DES -macalg SHA1
        if ! keystore_readable; then
            echo "ERROR: the JVM still cannot read the generated keystore. Inspect with:" >&2
            echo "       sudo keytool -list -keystore ${KEYSTORE_PATH} -storepass '<hidden>'" >&2
            exit 1
        fi
    fi
fi
sudo chown "${APP_USER}:${APP_USER}" "${KEYSTORE_PATH}"
sudo chmod 600 "${KEYSTORE_PATH}"

# --- Wire .env to serve on the public 80/443 ports with SSL enabled --------
set_env_var() {
    local key="$1" value="$2"
    if sudo grep -q "^${key}=" "${ENV_FILE}" 2>/dev/null; then
        sudo sed -i "s|^${key}=.*|${key}=${value}|" "${ENV_FILE}"
    else
        echo "${key}=${value}" | sudo tee -a "${ENV_FILE}" >/dev/null
    fi
}

set_env_var_if_blank() {
    local key="$1" value="$2"
    if sudo grep -q "^${key}=" "${ENV_FILE}" 2>/dev/null; then
        if sudo grep -q "^${key}=$" "${ENV_FILE}" 2>/dev/null; then
            sudo sed -i "s|^${key}=.*|${key}=${value}|" "${ENV_FILE}"
        fi
    else
        echo "${key}=${value}" | sudo tee -a "${ENV_FILE}" >/dev/null
    fi
}

set_env_var "APP_ENV" "production"
set_env_var "APP_PORT" "80"
set_env_var "SSL_ENABLED" "true"
set_env_var "SSL_PORT" "443"
# The public domain the server is actually reached at. Without this the app
# would derive its public base URL from the 0.0.0.0 bind address and emit
# broken image links like https://localhost/images/... ; SSL_DOMAIN makes it
# build https://<SSL_DOMAIN>/images/... instead.
set_env_var "SSL_DOMAIN" "${SSL_DOMAIN}"
set_env_var "SSL_KEYSTORE_PATH" "${KEYSTORE_PATH}"
set_env_var "SSL_KEYSTORE_PASSWORD" "${KEYSTORE_PASSWORD}"
set_env_var "SSL_KEY_ALIAS" "oneverandah"
# The keystore is built with a single `-passout` password for BOTH the store
# and the private key (openssl pkcs12 -export encrypts them the same way), so
# any leftover/different SSL_PRIVATE_KEY_PASSWORD in an uploaded .env (e.g.
# from an old self-signed-cert setup) must be overwritten too, or the app
# fails to unlock the key with BadPaddingException at startup.
set_env_var "SSL_PRIVATE_KEY_PASSWORD" "${KEYSTORE_PASSWORD}"

# If init.sh installed ArangoDB locally, keep a freshly-uploaded .env with blank
# ARANGO_* values from silently disabling persistence and falling back to the
# in-memory repository in production.
if systemctl list-unit-files arangodb3.service >/dev/null 2>&1; then
    sudo systemctl enable --now arangodb3 >/dev/null 2>&1 || true
    if curl -sS --max-time 2 "http://127.0.0.1:8529/_api/version" >/dev/null 2>&1; then
        set_env_var_if_blank "ARANGO_HOST" "127.0.0.1"
        set_env_var_if_blank "ARANGO_PORT" "8529"
        set_env_var_if_blank "ARANGO_DB" "image_generator"
        set_env_var_if_blank "ARANGO_USER" "root"
        set_env_var_if_blank "ARANGO_SSL" "false"
    else
        echo "ERROR: arangodb3.service exists but 127.0.0.1:8529 did not answer." >&2
        echo "       Check: sudo systemctl status arangodb3 --no-pager" >&2
        exit 1
    fi
fi
sudo chown "${APP_USER}:${APP_USER}" "${ENV_FILE}"

# --- Make sure the OS firewall lets the public 80/443 ports through --------
if command -v ufw >/dev/null 2>&1 && sudo ufw status | grep -q "^Status: active"; then
    sudo ufw allow 80/tcp >/dev/null
    sudo ufw allow 443/tcp >/dev/null
elif command -v firewall-cmd >/dev/null 2>&1 && sudo firewall-cmd --state 2>/dev/null | grep -q running; then
    sudo firewall-cmd --permanent --add-port=80/tcp >/dev/null
    sudo firewall-cmd --permanent --add-port=443/tcp >/dev/null
    sudo firewall-cmd --reload >/dev/null
fi

# --- Keep the keystore in sync with certbot renewals ------------------------
sudo mkdir -p /etc/letsencrypt/renewal-hooks/deploy
sudo tee "/etc/letsencrypt/renewal-hooks/deploy/${SERVICE_NAME}.sh" >/dev/null <<HOOK
#!/usr/bin/env bash
set -euo pipefail
KEYSTORE_PASSWORD="${KEYSTORE_PASSWORD}"
build() {
    openssl pkcs12 -export \\
        -in "${LE_DIR}/fullchain.pem" \\
        -inkey "${LE_DIR}/privkey.pem" \\
        -out "${KEYSTORE_PATH}" \\
        -name oneverandah \\
        -passout "pass:\${KEYSTORE_PASSWORD}" \\
        "\$@"
}
readable() {
    keytool -list -keystore "${KEYSTORE_PATH}" -storepass "\${KEYSTORE_PASSWORD}" >/dev/null 2>&1
}
build
if ! readable; then
    build -legacy 2>/dev/null && readable || build -certpbe PBE-SHA1-3DES -keypbe PBE-SHA1-3DES -macalg SHA1
fi
chown "${APP_USER}:${APP_USER}" "${KEYSTORE_PATH}"
chmod 600 "${KEYSTORE_PATH}"
systemctl restart "${SERVICE_NAME}.service"
HOOK
sudo chmod +x "/etc/letsencrypt/renewal-hooks/deploy/${SERVICE_NAME}.sh"

# --- Write the systemd unit and (re)start the service -----------------------
sudo tee "/etc/systemd/system/${SERVICE_NAME}.service" >/dev/null <<EOF
[Unit]
Description=One Verandah Dynamic Image Generation Backend
After=network.target arangodb3.service

[Service]
User=${APP_USER}
WorkingDirectory=${APP_DIR}
EnvironmentFile=${APP_DIR}/.env
# The app serves on the public ports 80/443 (SSL enabled), which are
# privileged; grant just enough capability to bind them without running the
# whole process as root.
AmbientCapabilities=CAP_NET_BIND_SERVICE
CapabilityBoundingSet=CAP_NET_BIND_SERVICE
ExecStart=/usr/bin/java -jar ${APP_DIR}/${JAR_NAME}
SuccessExitStatus=143
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable "${SERVICE_NAME}.service"
sudo systemctl restart "${SERVICE_NAME}.service"

# Give the JVM a moment to boot, then confirm locally (via loopback) that it
# actually bound the HTTPS port, independent of DNS/external firewalls.
echo "==> Waiting for the app to come up..."
UP=0
for _ in $(seq 1 15); do
    if sudo curl -sk --max-time 2 "https://127.0.0.1/health" >/dev/null 2>&1; then
        UP=1
        break
    fi
    sleep 1
done
if [ "$UP" = "1" ]; then
    echo "    Local HTTPS health check OK (https://127.0.0.1/health)"
else
    echo "ERROR: the app did not answer on https://127.0.0.1/health after restart." >&2
    echo "    ---- Last 40 log lines (sudo journalctl -u ${SERVICE_NAME}.service -n 40 --no-pager) ----"
    sudo journalctl -u "${SERVICE_NAME}.service" -n 40 --no-pager || true
    echo "    ------------------------------------------------------------------------------"
    echo "       Deployment stopped because the backend did not become healthy." >&2
    exit 1
fi

echo "==> Deployed. Recent status:"
sudo systemctl status "${SERVICE_NAME}.service" --no-pager || true
REMOTE_SCRIPT

# 4. Verify from the LOCAL machine that the backend is actually reachable on
#    the public internet. --resolve pins the connection to $SERVER_HOST while
#    still using $SSL_DOMAIN for SNI/Host/cert validation, so this works even
#    if DNS for $SSL_DOMAIN hasn't been (re)pointed at $SERVER_HOST yet.
echo
echo "==> Verifying external health check..."
if curl -s --max-time 10 --resolve "${SSL_DOMAIN}:443:${SERVER_HOST}" "https://${SSL_DOMAIN}/health" >/dev/null; then
    echo "    OK — https://${SSL_DOMAIN}/health is reachable on ${SERVER_HOST}."
else
    echo "    WARNING: could not reach https://${SSL_DOMAIN}/health on ${SERVER_HOST}."
    echo "    - Confirm DNS for ${SSL_DOMAIN} points to ${SERVER_HOST}."
    echo "    - Confirm ports 80/443 are open in the OS firewall AND any cloud"
    echo "      provider security group / firewall rules (this script only"
    echo "      manages the OS-level firewall, e.g. ufw/firewalld)."
    echo "    - Check the service logs: ssh ${SSH_HOST} sudo journalctl -u ${SERVICE_NAME}.service -f"
fi

echo
echo "Tail logs with: ssh ${SSH_HOST} sudo journalctl -u ${SERVICE_NAME}.service -f"
echo "Health check:   curl -s https://${SSL_DOMAIN}/health"

# Dynamic Image Generation & Display System

This document explains how the automatic cover-image system works and how to set
it up in **development** and **production**. It implements the PRD in
[`AIImages.md`](./AIImages.md).

The web frontend automatically shows an AI-generated cover photo for every
category and service. Covers are generated once, cached, and reused. Everything
is designed to **degrade gracefully**: with no external services configured at
all, the backend still runs end-to-end using in-memory storage, local file
storage and a built-in offline placeholder image generator.

---

## 1. Architecture at a glance

```
Web (Compose HTML)  ──GET /api/v1/images──▶  Ktor server ──▶ Repository (ArangoDB | in-memory)
   cover photos                                   │
   + 10s polling                                  ├──▶ Queue (in-memory, deduped, rate-limited)
                                                   │        │
                                                   │        ▼
                                                   │   Qwen / DashScope  (or offline placeholder)
                                                   │        │
                                                   │        ▼
                                                   └──▶ Storage: Alibaba OSS (or local /images)
```

| Concern     | Configured backend            | Fallback (nothing configured)      |
|-------------|-------------------------------|------------------------------------|
| Persistence | ArangoDB (HTTP API)           | In-memory (re-seeded on startup)   |
| Storage     | Alibaba OSS (signed upload)   | Local disk, served at `/images/`   |
| Generation  | Qwen / DashScope text-to-image| Offline placeholder PNG (JDK 2D)   |

Server code lives in [`server/`](./server/src/main/kotlin/apap/oneverandah),
shared models in [`core/`](./core/src/commonMain/kotlin/apap/oneverandah/images),
and the frontend integration in
[`app/web/`](./app/web/src/jsMain/kotlin/apap/oneverandah).

---

## 2. Quick start (development)

No API keys required — the offline defaults produce real, distinct covers.

```shell
# 1. Backend (Ktor). Serves the API on http://localhost:7071
./gradlew :server:run

# 2. Web app (Compose HTML). Opens http://localhost:8080
./gradlew :app:web:jsBrowserDevelopmentRun
```

Then browse `http://localhost:8080`. On launch the frontend calls
`http://localhost:7071/api/v1/images` (the dev base URL is auto-detected for
`localhost`) and cover photos appear as they finish generating.

Verify the backend directly:

```shell
curl -s http://localhost:7071/health
curl -s http://localhost:7071/api/v1/images | head
```

Generated files (local storage mode) are written under `data/images/` and served
at `http://localhost:7071/images/...`.

---

## 3. Configuration

All configuration comes from environment variables, optionally seeded from a
`.env` file. Copy the template and edit it:

```shell
cp .env.example .env
```

Precedence: **real environment variables override `.env`**. The full, commented
list is in [`.env.example`](./.env.example). Key variables:

| Variable | Purpose | Default |
|----------|---------|---------|
| `APP_ENV` | `development` or `production` | `development` |
| `APP_HOST` / `APP_PORT` | HTTP bind address | `0.0.0.0` / `7071` |
| `PUBLIC_BASE_URL` | Base URL for local image links | auto |
| `CORS_ALLOWED_HOSTS` | Comma-separated origins (blank = any) | any |
| `QWEN_API_KEY` | Enables real Qwen generation | *(offline placeholder)* |
| `OSS_*` | Enables Alibaba OSS storage | *(local disk)* |
| `ARANGO_HOST` | Enables ArangoDB persistence | *(in-memory)* |
| `SSL_*` | Enables the in-app HTTPS connector | disabled |

---

## 4. Qwen (DashScope) API key

The backend calls Alibaba Cloud **Model Studio / DashScope** text-to-image
(Qwen `wanx` models).

1. Create/log in to an Alibaba Cloud account and open **Model Studio (DashScope)**.
2. Enable the image synthesis (Tongyi Wanxiang / `wanx`) service.
3. Create an **API key** in the DashScope console.
4. Put it in `.env`:

   ```bash
   QWEN_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxx
   # Optional overrides (quality defaults shown):
   # Use qwen-image-3.0 for text-only and reference-grounded stills.
   # Text-only calls are auto-routed to multimodal-generation.
   QWEN_MODEL=qwen-image-3.0
   QWEN_R2I_MODEL=qwen-image-3.0
   # wan2.x-image pixel sizes bottom out around 768*768 (512 is rejected).
   QWEN_MULTIMODAL_SIZE=768*768
   # Only used if you switch QWEN_MODEL back to a classic *-t2i* model.
   QWEN_T2I_SIZE=512*512
   QWEN_R2I_SIZE=512*512
   # Prompt rewrite adds ~3–4s per image; keep off when prompts are already detailed.
   QWEN_PROMPT_EXTEND=false
   # Classic synthesis URL remains valid for wanx / *-t2i* models. For wan2.x-image
   # the client auto-routes text-only generation to multimodal-generation
   # (otherwise DashScope returns InvalidParameter "url error").
   QWEN_API_ENDPOINT=https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis
   QWEN_R2I_ENDPOINT=https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation
   # For the international endpoint use dashscope-intl.aliyuncs.com
   ```

The client picks the right DashScope contract per model: classic async
`text2image/image-synthesis` for `wanx` / `*-t2i*` if configured, and
multimodal-generation (messages payload, sync or async) for `wan2.x-image`
text-only and reference-grounded stills (default: `qwen-image-3.0`). Reference images re-hosted into
DashScope temp storage are cached in-process so study story panels reuse the
same character portraits without re-uploading each time. Failures are retried
with exponential backoff; after `GENERATION_MAX_RETRIES` the image is marked
`FAILED` and re-queued later by the scheduler. If `QWEN_API_KEY` is empty the
offline placeholder generator is used instead, so the pipeline always works.

---

## 5. Alibaba OSS bucket

Generated images are uploaded to OSS and served from its public URL.

1. In the OSS console, **create a bucket** (e.g. `oneverandah-covers`) in your
   region (e.g. `cn-hangzhou`). Note its **endpoint**
   (`oss-cn-hangzhou.aliyuncs.com`).
2. Make objects publicly readable. The uploader sets
   `x-oss-object-acl: public-read` per object, but you can also set the bucket
   ACL to *public-read*. (Optionally front it with a CDN / custom domain.)
3. Create a RAM user with OSS write access and generate an **AccessKey ID/Secret**.
4. Configure `.env`:

   ```bash
   OSS_ENDPOINT=oss-cn-hangzhou.aliyuncs.com
   OSS_ACCESS_KEY_ID=LTAI....
   OSS_ACCESS_KEY_SECRET=....
   OSS_BUCKET_NAME=oneverandah-covers
   OSS_REGION=cn-hangzhou
   # Optional CDN/custom-domain base for the public URL:
   OSS_PUBLIC_BASE_URL=https://cdn.example.com
   ```

All four of `OSS_ENDPOINT`, `OSS_ACCESS_KEY_ID`, `OSS_ACCESS_KEY_SECRET`,
`OSS_BUCKET_NAME` must be present to enable OSS; otherwise local disk storage is
used and images are served by the app at `/images/`.

### Mainland-China buckets: public endpoint is forbidden

Since **2025-03-20** Alibaba Cloud **forbids using the default public OSS
endpoints** (`*.aliyuncs.com`, e.g. `oss-cn-beijing.aliyuncs.com`) for data
operations (upload/download) on buckets in **mainland-China regions**. Such
requests are rejected with `400 PublicEndpointForbidden` ("please use CNAME
instead"). Choose one of the following (all supported by the client):

- **Custom domain / CNAME (recommended).** Bind a domain to the bucket in the
  OSS console and add a DNS `CNAME` record pointing it at
  `<bucket>.oss-<region>.aliyuncs.com`, then set:

  ```bash
  OSS_ENDPOINT=cdn.example.com
  OSS_CNAME=true
  OSS_PUBLIC_BASE_URL=https://cdn.example.com
  ```

  With `OSS_CNAME=true` the endpoint is used verbatim as the request host
  instead of the virtual-hosted `bucket.endpoint` form.
- **Internal endpoint** (only when the server runs on an ECS in the bucket's
  region): `OSS_ENDPOINT=oss-<region>-internal.aliyuncs.com`, `OSS_CNAME=false`.
  Public serving still needs a CNAME/CDN in `OSS_PUBLIC_BASE_URL`.
- **Transfer acceleration** (enable it on the bucket first):
  `OSS_ENDPOINT=oss-accelerate.aliyuncs.com`, `OSS_CNAME=false`.

If none of these is configured for a China-region bucket, leave the OSS values
blank and the app falls back to local disk storage served at `/images/`.

---

## 6. ArangoDB (optional persistence)

Without ArangoDB the backend keeps image state in memory and re-seeds the
catalog on every start (fine for a single node). To persist across restarts:

1. Install ArangoDB (the `deploy/init.sh` script can do this with
   `INSTALL_ARANGO=1`).
2. Configure `.env`:

   ```bash
   ARANGO_HOST=127.0.0.1
   ARANGO_PORT=8529
   ARANGO_DB=image_generator
   ARANGO_USER=root
   ARANGO_PASSWORD=your_arango_password
   ```

On startup the app **auto-provisions** the database, the `image_entities`
collection, a unique index on `(targetId, targetType)` and an index on `status`.
If ArangoDB is unreachable it logs a warning and falls back to in-memory.

---

## 7. HTTP API

| Method | Path | Description |
|--------|------|-------------|
| `GET`  | `/health` | Liveness + which backends are active |
| `GET`  | `/api/v1/images` | All image records (id, targetId, targetType, imageUrl, status) |
| `POST` | `/api/v1/images/generate` | Body `{"imageId":"..."}` — (re)queue an image |
| `GET`  | `/api/v1/images/status/{targetId}` | Status for one category/service |
| `GET`  | `/images/{...}` | Serves locally-stored images (local mode) |

---

## 8. Development vs. production (SSL)

The **same build** runs in both environments; only configuration differs.

### Development
- `APP_ENV=development`, plain HTTP on `APP_PORT` (default `7071`).
- Permissive CORS (any origin) so the webpack dev server on `:8080` can call it.

### Production
The backend serves directly on the **default public ports 80 (HTTP) / 443
(HTTPS) with SSL enabled** — no nginx reverse proxy required in front of it.
`deploy/deploy.sh` sets this up automatically on every deploy:

1. The fat JAR is built targeting **Java 21** bytecode (`jvmToolchain(21)` in
   `server/build.gradle.kts` / `core/build.gradle.kts`). Before restarting the
   service, `deploy.sh` checks the remote JDK's major version and
   automatically installs `openjdk-21-jdk-headless` (and switches
   `update-alternatives` to it) if an older JDK is active — this avoids the
   app crash-looping with `UnsupportedClassVersionError` right after startup.
2. It converts the server's existing **Let's Encrypt** certificate for
   `SSL_DOMAIN` (default `api.oneverandah.app`, at
   `/etc/letsencrypt/live/<SSL_DOMAIN>/{fullchain,privkey}.pem`) into a PKCS12
   keystore at `/opt/oneverandah-images/keystore.p12`.
3. It uploads your **local `.env`** (with `APP_ENV=production` and your real
   `ARANGO`/`OSS`/`QWEN` settings) to the server first, then overwrites just
   `APP_PORT=80`, `SSL_ENABLED=true`, `SSL_PORT=443` and the keystore
   path/password/`SSL_PRIVATE_KEY_PASSWORD` on top of it — so the deployed
   server always runs with the same production config as your local `.env`,
   not the development placeholders from `.env.example`.
4. It grants the systemd service `AmbientCapabilities=CAP_NET_BIND_SERVICE` so
   the app can bind ports 80/443 without running as root.
5. It installs a certbot **renewal-hook** that regenerates the keystore and
   restarts the service whenever the certificate is renewed.
6. Right after generating the keystore, it verifies with `keytool -list` that
   the JVM can actually read it back, and automatically retries with
   `-legacy` / explicit `PBE-SHA1-3DES`+`SHA1` encryption if the default
   OpenSSL 3.x PBES2/AES-256 encoding isn't supported by the installed JDK
   (a common cause of the app crashing immediately on startup).
7. It opens ports `80`/`443` in the OS firewall (`ufw`/`firewalld`) if one is
   active, then waits for the app to answer on `https://127.0.0.1/health`
   (loopback) — printing the last 40 log lines automatically if it doesn't —
   and finally checks `https://<SSL_DOMAIN>/health` from **your** machine
   (pinning the connection to the target IP with `curl --resolve`, so it
   works even before DNS is fully (re)pointed).

The certificate itself must already exist on the server (issue it once with
`certbot`, e.g. `sudo certbot certonly --standalone -d api.oneverandah.app`) —
`deploy.sh` only consumes it, it never issues or renews certificates itself.

If you'd rather terminate TLS at nginx instead (e.g. to keep the backend
behind the same reverse proxy as the UI, see [`DEPLOY.md`](./DEPLOY.md)), set
`APP_PORT` back to an internal port such as `7071`, leave `SSL_ENABLED=false`,
and proxy `/api/` and `/images/` to it from nginx.

---

## 9. Deploying to the production server

Provisioning (`init.sh`) and issuing the SSL certificate happen **once, on the
server**. Day-to-day, the actual build and deploy (`deploy.sh`) runs **from
your local machine**: it builds the JAR, pushes it over SSH, wires up SSL from
the existing certificate and (re)starts the service — nothing needs to be
built or cloned on the server itself.

```shell
# One-time, on the server (SSH in first), optionally with ArangoDB:
ssh root@38.54.15.84
git clone <your-repo> && cd <repo>
INSTALL_ARANGO=1 sudo ./deploy/init.sh
nano /opt/oneverandah-images/.env               # add Qwen key / OSS creds
sudo certbot certonly --standalone -d api.oneverandah.app   # one-time cert
exit

# From your LOCAL machine — builds, deploys and runs the service:
./deploy/deploy.sh
# or target a different host/user/domain:
SERVER_HOST=38.54.15.84 SERVER_USER=root SSL_DOMAIN=api.oneverandah.app ./deploy/deploy.sh

# Observe:
ssh root@38.54.15.84 sudo journalctl -u oneverandah-images.service -f
curl -s https://api.oneverandah.app/health
```

`deploy.sh` builds the fat JAR locally (`:server:buildFatJar` →
`server/build/libs/server-all.jar`), uploads it over SSH/SCP to
`/opt/oneverandah-images` on `38.54.15.84` (default; override with
`SERVER_HOST`/`SERVER_USER`), **uploads your local `.env`** (overwriting the
remote one so production settings actually make it to the server — only
falls back to seeding `.env.example` when no local `.env` exists and the
remote one is also missing), converts the server's Let's Encrypt certificate
for `SSL_DOMAIN` into a PKCS12 keystore, wires `.env` to serve on ports 80/443
with SSL enabled, installs a certbot renewal-hook to keep the keystore in sync,
and remotely (re)writes and restarts the `oneverandah-images.service` systemd
unit that loads `.env` via `EnvironmentFile`.

---

## 10. Troubleshooting

- **Covers never appear** — check `GET /health` shows the expected backends, and
  `GET /api/v1/images` returns `COMPLETED` items. In `production` behind nginx,
  make sure `/api` and `/images` are proxied.
- **All images `FAILED`** — usually a bad `QWEN_API_KEY`/endpoint or OSS
  credentials; the scheduler re-queues failures every `RESCHEDULE_INTERVAL_MS`.
  You can force a retry with `POST /api/v1/images/generate`.
- **Want to force placeholders** — leave `QWEN_API_KEY` empty.
- **Deployed server shows `"environment":"DEVELOPMENT"` / `"storage":"local"` /
  `"generator":"placeholder"` even though your local `.env` is set up for
  production** — `deploy.sh` now always uploads your local `.env` to the
  server (overwriting the remote one); previously it only ever seeded
  `.env.example` on the very first deploy and never touched `.env` again, so
  the real production values (`APP_ENV`, `QWEN_API_KEY`, `OSS_*`, ...) never
  reached the server. Just re-run `./deploy/deploy.sh`. If the app then
  crashes with a `BadPaddingException` unlocking the SSL key, it means your
  `.env` had a stale `SSL_PRIVATE_KEY_PASSWORD` from an older setup — this is
  now also overwritten automatically to match the freshly generated keystore
  password.
- **`sudo: unable to resolve host ...` printed during deploy** — harmless; it
  happens once on a server whose hostname isn't yet in `/etc/hosts`.
  `deploy.sh` now adds that entry automatically on the first run, so it should
  not repeat on subsequent deploys.
- **Health check can't connect at all** — `deploy.sh` now checks
  `https://127.0.0.1/health` on the server (proves the app itself bound
  80/443) and `https://<SSL_DOMAIN>/health` from your machine (proves it's
  reachable from outside), printing a clear warning if either fails. If the
  external check fails while the local one succeeds, it's a network problem,
  not the app — most commonly: DNS for `SSL_DOMAIN` not (yet) pointing at
  `SERVER_HOST`, or ports `80`/`443` blocked by your **cloud provider's**
  security group/firewall (`deploy.sh` only opens the OS-level firewall, e.g.
  `ufw`/`firewalld`; it can't change provider-side rules).
- **Service keeps crashing right after start (`status=1/FAILURE`,
  `activating (auto-restart)`)** — two known causes, both handled
  automatically by `deploy.sh` now:
  - `UnsupportedClassVersionError: ... class file version 65.0, this version
    of the Java Runtime only recognizes class file versions up to 61.0` — the
    JAR is built for Java 21 but an older JDK (e.g. 17) is active on the
    server. `deploy.sh` detects this and installs/activates OpenJDK 21
    automatically before restarting the service.
  - The JVM couldn't parse the PKCS12 keystore built from the Let's Encrypt
    certificate (OpenSSL 3.x's default encryption isn't always readable by
    every JDK). `deploy.sh` validates the keystore with `keytool` right after
    building it and automatically rebuilds it with compatible algorithms if
    needed.
  If it still fails after a deploy, `deploy.sh` prints the last 40 log lines
  automatically — inspect them with
  `ssh <host> sudo journalctl -u oneverandah-images.service -n 100 --no-pager`
  for the exact exception.
- **`apt-get update` fails during deploy because of an unrelated broken
  third-party repo** (e.g. a stale/incorrect entry under
  `/etc/apt/sources.list.d/`) — `deploy.sh`'s JDK-install step tolerates this
  (`apt-get update ... || true`) so it doesn't block installing the JDK from
  the main Ubuntu archives, but a broken repo file left on the server should
  still be fixed/removed if it's not actually needed.

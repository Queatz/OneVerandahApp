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
   # Optional overrides:
   QWEN_MODEL=wanx2.1-t2i-turbo
   QWEN_API_ENDPOINT=https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis
   # For the international endpoint use dashscope-intl.aliyuncs.com
   ```

The client uses the documented **asynchronous** flow: submit a task, poll it to
completion, then download the produced image. Failures are retried with
exponential backoff; after `GENERATION_MAX_RETRIES` the image is marked `FAILED`
and re-queued later by the scheduler. If `QWEN_API_KEY` is empty the offline
placeholder generator is used instead, so the pipeline always works.

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
Two supported TLS strategies:

**A) Terminate TLS at nginx (recommended, matches [`DEPLOY.md`](./DEPLOY.md)).**
Keep the backend on plain HTTP (`127.0.0.1:7071`) and let nginx serve HTTPS and
reverse-proxy the API and images to it:

```nginx
# add inside the existing server { } block in DEPLOY.md
location /api/  { proxy_pass http://127.0.0.1:7071; }
location /images/ { proxy_pass http://127.0.0.1:7071; }
```

Because the frontend uses a same-origin base URL in production, `/api` and
`/images` are served from your domain over HTTPS with no extra config.

**B) Terminate TLS in the app (built-in HTTPS connector).**
Provide a Java keystore and enable SSL:

```bash
APP_ENV=production
SSL_ENABLED=true
SSL_KEYSTORE_PATH=/opt/oneverandah-images/keystore.p12
SSL_KEYSTORE_PASSWORD=changeit
SSL_KEY_ALIAS=oneverandah
SSL_PORT=8443
CORS_ALLOWED_HOSTS=oneverandah.app
```

Create a PKCS12 keystore from Let's Encrypt certificates, e.g.:

```shell
openssl pkcs12 -export \
  -in  /etc/letsencrypt/live/oneverandah.app/fullchain.pem \
  -inkey /etc/letsencrypt/live/oneverandah.app/privkey.pem \
  -out /opt/oneverandah-images/keystore.p12 \
  -name oneverandah -passout pass:changeit
```

When `SSL_ENABLED=true`, the server listens on both the HTTP `APP_PORT` and the
HTTPS `SSL_PORT`.

---

## 9. Deploying to an Ubuntu Lightnode

```shell
# On the server, one-time provisioning (optionally with ArangoDB):
git clone <your-repo> && cd <repo>
INSTALL_ARANGO=1 sudo ./deploy/init.sh
nano /opt/oneverandah-images/.env      # add Qwen key / OSS creds / SSL

# Build + (re)deploy the systemd service:
./deploy/deploy.sh

# Observe:
sudo journalctl -u oneverandah-images.service -f
curl -s http://127.0.0.1:7071/health
```

`deploy.sh` builds the fat JAR (`:server:buildFatJar` →
`server/build/libs/server-all.jar`), installs it to `/opt/oneverandah-images`,
and manages a `oneverandah-images.service` unit that loads `.env` via
`EnvironmentFile`.

---

## 10. Troubleshooting

- **Covers never appear** — check `GET /health` shows the expected backends, and
  `GET /api/v1/images` returns `COMPLETED` items. In `production` behind nginx,
  make sure `/api` and `/images` are proxied.
- **All images `FAILED`** — usually a bad `QWEN_API_KEY`/endpoint or OSS
  credentials; the scheduler re-queues failures every `RESCHEDULE_INTERVAL_MS`.
  You can force a retry with `POST /api/v1/images/generate`.
- **Want to force placeholders** — leave `QWEN_API_KEY` empty.

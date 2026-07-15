# PRD: Dynamic Image Generation & Display System

## 1. Overview

### 1.1 Purpose
Transform the current static web-only frontend into a dynamic system that auto-generates and displays images for categories and services using AI (Qwen), with persistent storage on Alibaba OSS.

### 1.2 Goals
- Automatically generate descriptive images for all categories and services
- Cache generated images permanently on OSS
- Display generated images as cover photos in the UI with graceful fallbacks
- Eliminate duplicate generation requests
- Provide a scalable backend architecture using Ktor/KMP on a self‑hosted Ubuntu Lightnode server

---

## 2. Architecture

### 2.1 Technology Stack

**Backend:**
- **Framework**: Ktor (Kotlin) with KMP (Kotlin Multiplatform) shared module
- **Database**: ArangoDB (graph + document store) using the official ArangoDB Java driver
- **Queue**: In‑memory coroutine‑based channel (single‑node; no external broker)
- **HTTP Client**: Ktor client for calling Qwen API
- **Storage SDK**: Aliyun OSS SDK for Kotlin/Java

**Frontend:**
- Existing web frontend (to be updated)
- Display generated images via OSS URLs

**Infrastructure:**
- Self‑hosted Ubuntu Lightnode server (single VM)
- Environment variables via `.env` file
- Alibaba OSS for image storage
- Qwen API for image generation

### 2.2 Data Flow

```
┌─────────────┐
│   Frontend  │
│   (Web UI)  │
└──────┬──────┘
       │ 1. Load images on launch
       ▼
┌─────────────┐    2. Check cache    ┌─────────────┐
│   Backend   │─────────────────────▶│  ArangoDB   │
│   (Ktor)    │                      │             │
└──────┬──────┘                      └─────────────┘
       │ 3. If missing, queue generation
       ▼
┌─────────────┐    4. Generate        ┌─────────────┐
│    Queue    │──────────────────────▶│  Qwen API   │
│  (In-memory)│                      │             │
└──────┬──────┘                      └─────────────┘
       │ 5. Upload
       ▼
┌─────────────┐
│  Alibaba    │
│  OSS        │
└─────────────┘
       │ 6. Return URL
       ▼
┌─────────────┐
│  ArangoDB   │
│  (Update)   │
└─────────────┘
```

---

## 3. Backend Design

### 3.1 Project Structure (KMP Shared Module)

```
project/
├── shared/                     # KMP shared module
│   ├── src/commonMain/kotlin/
│   │   ├── models/             # Shared data models
│   │   │   ├── Category.kt
│   │   │   ├── Service.kt
│   │   │   └── ImageEntity.kt
│   │   └── utils/              # Shared utilities
│   └── src/jvmMain/kotlin/     # JVM-specific (backend)
│       ├── Application.kt      # Ktor server entry
│       ├── plugins/            # Ktor plugins
│       ├── services/           # Business logic
│       │   ├── ImageGenerationService.kt
│       │   ├── OSSStorageService.kt
│       │   └── QueueService.kt
│       ├── repositories/       # Database layer (ArangoDB)
│       │   └── ImageRepository.kt
│       └── routes/             # API endpoints
│           └── ImageRoutes.kt
├── deploy/                     # Deployment scripts
│   ├── init.sh                 # Initial server setup
│   └── deploy.sh               # Build & deploy
├── .env                        # Environment variables
└── build.gradle.kts
```

### 3.2 Data Models

```kotlin
// shared/src/commonMain/kotlin/models/Category.kt
data class Category(
    val id: String,
    val name: String,
    val description: String? = null
)

// shared/src/commonMain/kotlin/models/Service.kt
data class Service(
    val id: String,
    val categoryId: String,
    val name: String,
    val description: String? = null
)

// shared/src/commonMain/kotlin/models/ImageEntity.kt
enum class ImageTargetType {
    CATEGORY,
    SERVICE
}

data class ImageEntity(
    val id: String,                // ArangoDB _key
    val targetId: String,          // categoryId or serviceId
    val targetType: ImageTargetType,
    val imageUrl: String? = null,  // OSS URL, null if not generated yet
    val prompt: String,            // The prompt used for generation
    val status: ImageStatus,
    val createdAt: Long,
    val updatedAt: Long
)

enum class ImageStatus {
    PENDING,    // Queued for generation
    GENERATING, // Currently being generated
    COMPLETED,  // Successfully generated and stored
    FAILED      // Generation failed
}
```

### 3.3 Database Schema (ArangoDB)

We use a single **document collection** `image_entities`. The `_key` is the image ID. Indexes are created on `targetId` and `targetType` for fast lookups.

```javascript
// ArangoDB collection: image_entities
// Document structure:
{
  "_key": "img_001",
  "targetId": "cat_001",
  "targetType": "CATEGORY",       // or "SERVICE"
  "imageUrl": "https://...",      // optional
  "prompt": "A professional...",
  "status": "PENDING",
  "createdAt": 1623456789,
  "updatedAt": 1623456789
}

// Indexes (created on startup):
// - Hash index on ["targetId", "targetType"] with unique constraint
// - Hash index on "status"
```

**Repository implementation:**

```kotlin
class ImageRepository(private val arangoDB: ArangoDB) {
    private val collection = arangoDB.db().collection("image_entities")
    
    init {
        // Ensure indexes exist
        collection.ensureHashIndex(listOf("targetId", "targetType"), true, false)
        collection.ensureHashIndex(listOf("status"), false, false)
    }
    
    suspend fun findByTarget(targetId: String, targetType: ImageTargetType): ImageEntity? {
        val query = """
            FOR doc IN image_entities
            FILTER doc.targetId == @targetId AND doc.targetType == @targetType
            RETURN doc
        """
        val cursor = arangoDB.db().query(query, mapOf("targetId" to targetId, "targetType" to targetType.name), null, ImageEntity::class.java)
        return cursor.nextOrNull()
    }
    
    suspend fun findPending(): List<ImageEntity> {
        val query = "FOR doc IN image_entities FILTER doc.status == 'PENDING' RETURN doc"
        val cursor = arangoDB.db().query(query, null, null, ImageEntity::class.java)
        return cursor.asList()
    }
    
    suspend fun save(entity: ImageEntity) {
        collection.insertDocument(entity)
    }
    
    suspend fun updateStatus(id: String, status: ImageStatus) {
        collection.updateDocument(id, mapOf("status" to status.name, "updatedAt" to System.currentTimeMillis()))
    }
    
    suspend fun updateImage(id: String, url: String, status: ImageStatus) {
        collection.updateDocument(id, mapOf("imageUrl" to url, "status" to status.name, "updatedAt" to System.currentTimeMillis()))
    }
    
    suspend fun findAll(): List<ImageEntity> {
        val query = "FOR doc IN image_entities RETURN doc"
        val cursor = arangoDB.db().query(query, null, null, ImageEntity::class.java)
        return cursor.asList()
    }
}
```

---

## 4. API Endpoints

### 4.1 Get All Images

**Endpoint:** `GET /api/v1/images`

**Response:**
```json
{
  "images": [
    {
      "id": "img_001",
      "targetId": "cat_001",
      "targetType": "CATEGORY",
      "imageUrl": "https://oss.example.com/images/cat_001.png",
      "status": "COMPLETED"
    },
    {
      "id": "img_002",
      "targetId": "svc_001",
      "targetType": "SERVICE",
      "imageUrl": null,
      "status": "PENDING"
    }
  ]
}
```

### 4.2 Trigger Generation (Internal)

**Endpoint:** `POST /api/v1/images/generate`

**Request:**
```json
{
  "imageId": "img_002"
}
```

### 4.3 Get Image Status

**Endpoint:** `GET /api/v1/images/status/{targetId}`

**Response:**
```json
{
  "targetId": "cat_001",
  "targetType": "CATEGORY",
  "status": "COMPLETED",
  "imageUrl": "https://oss.example.com/images/cat_001.png"
}
```

---

## 5. Image Generation Logic

### 5.1 Prompt Engineering

```kotlin
fun generatePrompt(entity: ImageEntity): String {
    return when (entity.targetType) {
        ImageTargetType.CATEGORY -> 
            "A professional and modern illustration representing the category '${entity.name}' in a business context, clean design, high quality, 1024x1024"
        ImageTargetType.SERVICE -> 
            "A professional and modern illustration representing the service '${entity.name}' in the category '${categoryName}', clean design, high quality, 1024x1024"
    }
}
```

### 5.2 Generation Flow

```kotlin
class ImageGenerationService(
    private val qwenApiClient: HttpClient,
    private val ossService: OSSStorageService,
    private val repository: ImageRepository
) {
    
    suspend fun generateImage(imageId: String) {
        val entity = repository.findByImageId(imageId) ?: throw ImageNotFoundException()
        if (entity.status == ImageStatus.COMPLETED) return
        
        repository.updateStatus(imageId, ImageStatus.GENERATING)
        
        try {
            val imageData = qwenApiClient.generateImage(entity.prompt)
            val fileName = "${entity.targetType.name.lowercase()}/${entity.targetId}.png"
            val imageUrl = ossService.uploadImage(imageData, fileName)
            repository.updateImage(imageId, imageUrl, ImageStatus.COMPLETED)
        } catch (e: Exception) {
            repository.updateStatus(imageId, ImageStatus.FAILED)
            // Log and retry later via separate scheduled job
        }
    }
}
```

### 5.3 Queue Management

```kotlin
class QueueService(
    private val generationService: ImageGenerationService,
    private val repository: ImageRepository
) {
    private val queue = Channel<ImageEntity>(Channel.UNLIMITED)
    private var isProcessing = false
    
    suspend fun enqueue(entity: ImageEntity) {
        // Only enqueue if not already in a terminal state
        val existing = repository.findByTarget(entity.targetId, entity.targetType)
        if (existing != null && existing.status != ImageStatus.FAILED) {
            return // duplicate
        }
        repository.save(entity)
        queue.send(entity)
        processQueue()
    }
    
    private suspend fun processQueue() {
        if (isProcessing) return
        isProcessing = true
        try {
            for (image in queue) {
                generationService.generateImage(image.id)
                // Wait between generations to avoid rate limits
                delay(1000)
            }
        } finally {
            isProcessing = false
        }
    }
    
    suspend fun hasPendingOrCompleted(targetId: String, targetType: ImageTargetType): Boolean {
        return repository.findByTarget(targetId, targetType) != null
    }
}
```

---

## 6. Frontend Updates

### 6.1 Launch Flow

```javascript
async function initializeApp() {
    try {
        const response = await fetch('/api/v1/images');
        const data = await response.json();
        window.imageStore = data.images.reduce((acc, img) => {
            acc[img.targetId] = img;
            return acc;
        }, {});
        renderCategories();
        renderServices();
    } catch (error) {
        console.error('Failed to load images:', error);
        renderCategories(); // fallback
        renderServices();
    }
}

function getImageForEntity(targetId, targetType) {
    const img = window.imageStore[targetId];
    if (img && img.status === 'COMPLETED' && img.imageUrl) {
        return img.imageUrl;
    }
    return '/assets/default-cover.png';
}

function renderCategory(category) {
    const url = getImageForEntity(category.id, 'CATEGORY');
    return `<div class="category-card">
        <img src="${url}" alt="${category.name}" onerror="this.src='/assets/default-cover.png'" />
        <h3>${category.name}</h3>
    </div>`;
}
```

### 6.2 Polling

```javascript
setInterval(async () => {
    const response = await fetch('/api/v1/images');
    const data = await response.json();
    let changed = false;
    data.images.forEach(img => {
        if (window.imageStore[img.targetId]?.status !== img.status) {
            window.imageStore[img.targetId] = img;
            changed = true;
        }
    });
    if (changed) refreshUI();
}, 10000);
```

---

## 7. Deployment: Ubuntu Lightnode Server

We run a single Ubuntu Lightnode (VM). The backend is built as a fat JAR and executed with systemd or a simple shell script.

### 7.1 `.env` File (on the server)

```bash
# Server
APP_PORT=8080
APP_HOST=0.0.0.0

# ArangoDB
ARANGO_HOST=127.0.0.1
ARANGO_PORT=8529
ARANGO_DB=image_generator
ARANGO_USER=root
ARANGO_PASSWORD=your_arango_password

# Alibaba OSS
OSS_ENDPOINT=oss-cn-hangzhou.aliyuncs.com
OSS_ACCESS_KEY_ID=your_access_key
OSS_ACCESS_KEY_SECRET=your_secret_key
OSS_BUCKET_NAME=your-bucket-name
OSS_REGION=cn-hangzhou

# Qwen API
QWEN_API_ENDPOINT=https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis
QWEN_API_KEY=your_qwen_api_key

# Application
MAX_CONCURRENT_GENERATIONS=2
IMAGE_QUEUE_BATCH_SIZE=10
```

### 7.2 Initial Server Setup Script (`deploy/init.sh`)

Run once when provisioning the Lightnode.

```bash
#!/bin/bash
# init.sh - Initial server setup for Ubuntu Lightnode

set -e

echo "Updating system..."
sudo apt update && sudo apt upgrade -y

echo "Installing dependencies..."
sudo apt install -y openjdk-17-jdk curl wget git unzip

# Install ArangoDB
echo "Installing ArangoDB..."
curl -OL https://download.arangodb.com/arangodb3/DEBIAN/Release.key
sudo apt-key add Release.key
echo 'deb https://download.arangodb.com/arangodb3/DEBIAN/ /' | sudo tee /etc/apt/sources.list.d/arangodb.list
sudo apt update
sudo apt install -y arangodb3

# Configure ArangoDB (non-interactive)
sudo arangodb3 --install --no-start || true
sudo systemctl enable arangodb3
sudo systemctl start arangodb3

# Wait for ArangoDB to be ready
sleep 5

# Create database and user (adjust credentials as needed)
echo "Creating database and user..."
arangoosh --server.username root --server.password "" --javascript.execute-string "db._createDatabase('image_generator');"
arangoosh --server.username root --server.password "" --javascript.execute-string "db._useDatabase('image_generator');"

# Alternatively, set a password for root (recommended):
# arangoosh --server.username root --server.password "" --javascript.execute-string "db._changeUser('root', 'your_arango_password');"

# Create the collection (will be done by the app on startup, but we can pre-create)
arangoosh --server.username root --server.password "" --javascript.execute-string "db._useDatabase('image_generator'); db._create('image_entities');"

echo "ArangoDB setup completed."

# Create application directory
sudo mkdir -p /opt/image-generator
sudo chown $USER:$USER /opt/image-generator

# Create systemd service file (will be done by deploy.sh)
echo "Init complete. Now run deploy.sh to build and start the app."
```

### 7.3 Deployment Script (`deploy/deploy.sh`)

Run after each code update to build and restart the service.

```bash
#!/bin/bash
# deploy.sh - Build and deploy the Ktor app

set -e

APP_DIR="/opt/image-generator"
BUILD_DIR="$APP_DIR/build"
JAR_NAME="server-all.jar"
ENV_FILE="$APP_DIR/.env"

echo "Building application..."
cd "$(dirname "$0")/.."  # go to project root
./gradlew :shared:jvm:shadowJar  # or whichever task produces the fat jar

# Stop the service if running
sudo systemctl stop image-generator.service || true

# Copy new JAR and env file
sudo cp shared/build/libs/$JAR_NAME $APP_DIR/
sudo cp .env $APP_DIR/

# Create/update systemd service
sudo tee /etc/systemd/system/image-generator.service > /dev/null <<EOF
[Unit]
Description=Image Generator Backend
After=network.target arangodb3.service

[Service]
User=$USER
WorkingDirectory=$APP_DIR
EnvironmentFile=$APP_DIR/.env
ExecStart=/usr/bin/java -jar $APP_DIR/$JAR_NAME
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable image-generator.service
sudo systemctl start image-generator.service

echo "Deployment complete. Service status:"
sudo systemctl status image-generator.service --no-pager
```

### 7.4 Manual Run (for testing)

Alternatively, you can run the JAR directly:

```bash
java -jar server-all.jar --port=8080
```

But systemd is recommended for production.

---

## 8. Monitoring & Logging

### 8.1 Logging

Use SLF4J with Logback. Logs go to `/var/log/image-generator.log` (configurable in `logback.xml`). View with:

```bash
sudo journalctl -u image-generator.service -f
```

### 8.2 Health Check Endpoint

Add a simple `GET /health` to verify service status.

---

## 9. Error Handling & Retries

### 9.1 Retry Strategy

- Qwen API failures: retry up to 3 times with exponential backoff.
- OSS upload failures: retry up to 3 times.
- If all retries fail, mark as `FAILED`. A separate scheduled job (e.g., every hour) can re-queue all `FAILED` entries.

```kotlin
// Scheduled job (using Ktor's launch or a cron-like scheduler)
fun rescheduleFailed() {
    val failed = repository.findByStatus(ImageStatus.FAILED)
    failed.forEach { enqueue(it) }
}
```

### 9.2 Frontend Fallback

Always use default image if `imageUrl` is null or image fails to load.

---

## 10. Performance Considerations

- ArangoDB indexes: ensure `(targetId, targetType)` is unique and indexed.
- Use OSS CDN for faster global delivery.
- Process one image at a time to respect Qwen rate limits.
- Use coroutines for non-blocking I/O.

---

## 11. Testing Strategy

- **Unit tests**: business logic, repository mocking.
- **Integration tests**: test with a real ArangoDB container and mock Qwen/OSS.
- **Manual test**: verify end‑to‑end flow on the Lightnode.

---

## 12. Timeline

| Phase | Duration | Deliverables |
|-------|----------|--------------|
| **Setup** | Week 1 | Project structure, ArangoDB integration, Ktor server |
| **Core Logic** | Week 2 | Qwen & OSS integration, repository |
| **Queue System** | Week 3 | Queue, duplicate prevention, error handling |
| **Frontend** | Week 4 | API endpoints, frontend updates, fallback |
| **Deployment** | Week 5 | Scripts, deployment on Lightnode, monitoring |

---

## 13. Success Criteria

- All categories/services have images (or are queued).
- Frontend loads images on launch.
- No duplicates generated.
- Graceful fallback to default images.
- Backend runs on Ubuntu Lightnode with ArangoDB.
- `.env` based configuration.

---

## 14. Appendix: Script Usage

### 14.1 Initial Setup

On a fresh Lightnode (Ubuntu 22.04), run:

```bash
git clone <your-repo>
cd <repo>/deploy
chmod +x init.sh deploy.sh
./init.sh
```

### 14.2 Deploy Updates

After pushing new code, on the server:

```bash
cd <repo>
git pull
./deploy/deploy.sh
```

### 14.3 View Logs

```bash
sudo journalctl -u image-generator.service -f
```

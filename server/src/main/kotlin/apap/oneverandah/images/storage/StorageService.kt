package apap.oneverandah.images.storage

/** Generated image bytes, storage metadata, and optionally the provider's source URL. */
data class GeneratedImage(
    val bytes: ByteArray,
    val contentType: String = "image/png",
    val extension: String = "png",
    val sourceUrl: String? = null,
) {
    // ByteArray needs custom equals/hashCode to behave as a value type.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GeneratedImage) return false
        return bytes.contentEquals(other.bytes) &&
            contentType == other.contentType &&
            extension == other.extension
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + extension.hashCode()
        return result
    }

    override fun toString(): String = "GeneratedImage(contentType=$contentType, extension=$extension)"
}

/**
 * Persists a generated image somewhere publicly reachable and returns its URL.
 *
 * Implementations: [LocalStorageService] (disk, served by the app) and
 * [AliyunOssStorageService] (Alibaba OSS). The generation pipeline is agnostic
 * to which one is active.
 */
interface StorageService {
    /** A short label used in logs (e.g. "local", "oss"). */
    val kind: String

    /**
     * Uploads [image] under [objectKey] (e.g. `category/massages.png`) and
     * returns a public URL pointing at the stored object.
     */
    suspend fun upload(objectKey: String, image: GeneratedImage): String
}

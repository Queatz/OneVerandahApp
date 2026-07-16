package apap.oneverandah.images.storage

import apap.oneverandah.config.OssConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Uploads images to Alibaba Cloud OSS using the documented V1 ("OSS") request
 * signature (HMAC-SHA1). Implemented directly on top of the Ktor HTTP client so
 * the server does not need to bundle the heavier official OSS SDK.
 *
 * Each object is written with `x-oss-object-acl: public-read` so the resulting
 * URL is directly reachable by the frontend even when the bucket default ACL is
 * private.
 */
class AliyunOssStorageService(
    private val config: OssConfig,
    private val client: HttpClient,
) : StorageService {

    override val kind: String = "oss"

    // Host used for data-plane requests. With a CNAME custom domain this is the
    // domain itself (required for mainland-China buckets, which reject the
    // default public `*.aliyuncs.com` endpoint); otherwise it is the standard
    // virtual-hosted `bucket.endpoint`.
    private val host: String = config.requestHost

    override suspend fun upload(objectKey: String, image: GeneratedImage): String =
        withContext(Dispatchers.IO) {
            val date = rfc1123Now()
            val contentMd5 = base64Md5(image.bytes)
            val acl = "public-read"
            val canonicalizedOssHeaders = "x-oss-object-acl:$acl\n"
            val canonicalizedResource = "/${config.bucket}/$objectKey"

            val stringToSign = buildString {
                append("PUT\n")
                append(contentMd5).append("\n")
                append(image.contentType).append("\n")
                append(date).append("\n")
                append(canonicalizedOssHeaders)
                append(canonicalizedResource)
            }
            val signature = sign(stringToSign, config.accessKeySecret)
            val authorization = "OSS ${config.accessKeyId}:$signature"

            val url = "https://$host/$objectKey"
            val response: HttpResponse = client.put(url) {
                header("Host", host)
                header("Date", date)
                header("Content-MD5", contentMd5)
                header("Content-Type", image.contentType)
                header("x-oss-object-acl", acl)
                header("Authorization", authorization)
                setBody(image.bytes)
            }

            if (response.status != HttpStatusCode.OK) {
                val body = runCatching { response.bodyAsText() }.getOrDefault("")
                throw StorageException("OSS upload failed (${response.status}): $body")
            }
            config.urlFor(objectKey)
        }

    private fun rfc1123Now(): String {
        val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("GMT")
        return format.format(Date())
    }

    private fun base64Md5(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5").digest(bytes)
        return Base64.getEncoder().encodeToString(digest)
    }

    private fun sign(data: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA1"))
        val raw = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(raw)
    }
}

/** Raised when a storage backend fails to persist an image. */
class StorageException(message: String) : RuntimeException(message)

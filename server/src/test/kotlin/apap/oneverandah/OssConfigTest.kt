package apap.oneverandah

import apap.oneverandah.config.OssConfig
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies how [OssConfig] resolves the data-plane request host and the public
 * URL, in particular the CNAME (custom-domain) mode required for mainland-China
 * buckets that reject the default public `*.aliyuncs.com` endpoint.
 */
class OssConfigTest {

    private fun config(
        endpoint: String,
        cname: Boolean,
        publicBaseUrl: String? = null,
    ) = OssConfig(
        endpoint = endpoint,
        accessKeyId = "id",
        accessKeySecret = "secret",
        bucket = "oss-ov-d6a4us96v00k",
        region = "cn-beijing",
        publicBaseUrl = publicBaseUrl,
        cname = cname,
    )

    @Test
    fun virtualHostedPrefixesBucketToEndpoint() {
        val cfg = config("oss-cn-beijing.aliyuncs.com", cname = false)
        assertEquals("oss-ov-d6a4us96v00k.oss-cn-beijing.aliyuncs.com", cfg.requestHost)
        assertEquals(
            "https://oss-ov-d6a4us96v00k.oss-cn-beijing.aliyuncs.com/category/yoga.png",
            cfg.urlFor("category/yoga.png"),
        )
    }

    @Test
    fun cnameUsesEndpointVerbatimAsHost() {
        val cfg = config("cdn.oneverandah.app", cname = true)
        // The custom domain is the request host; the bucket is NOT prefixed.
        assertEquals("cdn.oneverandah.app", cfg.requestHost)
        assertEquals("https://cdn.oneverandah.app/category/yoga.png", cfg.urlFor("category/yoga.png"))
    }

    @Test
    fun cnameStripsSchemeAndTrailingSlash() {
        val cfg = config("https://cdn.oneverandah.app/", cname = true)
        assertEquals("cdn.oneverandah.app", cfg.requestHost)
    }

    @Test
    fun publicBaseUrlOverridesUrlButNotRequestHost() {
        val cfg = config(
            endpoint = "oss-cn-beijing-internal.aliyuncs.com",
            cname = false,
            publicBaseUrl = "https://cdn.oneverandah.app",
        )
        assertEquals("oss-ov-d6a4us96v00k.oss-cn-beijing-internal.aliyuncs.com", cfg.requestHost)
        assertEquals("https://cdn.oneverandah.app/category/yoga.png", cfg.urlFor("category/yoga.png"))
    }
}

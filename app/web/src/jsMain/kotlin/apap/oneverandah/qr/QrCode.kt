package apap.oneverandah.qr

import kotlin.js.Promise

/**
 * Minimal bindings for the `qrcode` npm package.
 *
 * We generate a PNG data URL at error-correction level H so a center logo
 * overlay can cover ~20% of the code without breaking scans.
 */
@JsModule("qrcode")
@JsNonModule
external object QrCode {
    fun toDataURL(text: String, options: dynamic = definedExternally): Promise<String>
}

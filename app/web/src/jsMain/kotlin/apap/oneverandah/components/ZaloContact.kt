package apap.oneverandah.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import apap.oneverandah.Styles
import apap.oneverandah.i18n.Localization
import apap.oneverandah.qr.QrCode
import kotlinx.coroutines.await
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

private const val ZALO_BRAND = "Zalo"
private const val APP_ICON_SRC = "/one.png"

fun zaloQrPayload(phone: String): String {
    val digits = phone.filter { it.isDigit() }
    return if (digits.isEmpty()) "" else "https://zalo.me/$digits"
}

@Composable
fun ZaloContactButton(
    phone: String,
    onClick: () -> Unit,
) {
    if (phone.isBlank()) return
    val strings = Localization.strings
    SectionTitle(strings.locationsContact)
    Button(attrs = {
        classes(Styles.zaloContactBtn)
        attr("type", "button")
        onClick { onClick() }
    }) {
        MaterialSymbol("qr_code_2", extraClass = Styles.zaloContactIcon)
        Div(attrs = { classes(Styles.zaloContactCopy) }) {
            Span(attrs = { classes(Styles.zaloContactBrand) }) { Text(ZALO_BRAND) }
            Span(attrs = { classes(Styles.zaloContactNumber) }) { Text(phone) }
        }
    }
}

@Composable
fun ZaloQrDialog(
    visible: Boolean,
    phone: String,
    onDismiss: () -> Unit,
) {
    val strings = Localization.strings
    var dataUrl by remember { mutableStateOf("") }

    LaunchedEffect(visible, phone) {
        if (!visible) return@LaunchedEffect
        val payload = zaloQrPayload(phone)
        if (payload.isEmpty()) {
            dataUrl = ""
            return@LaunchedEffect
        }
        val options = js("{ errorCorrectionLevel: 'H', margin: 2, width: 512 }")
        dataUrl = runCatching { QrCode.toDataURL(payload, options).await() }.getOrDefault("")
    }

    BottomDialog(
        visible = visible && phone.isNotBlank(),
        closeLabel = strings.close,
        onDismiss = onDismiss,
    ) {
        Div(attrs = { classes(Styles.zaloQrDialog) }) {
            Div(attrs = { classes(Styles.zaloQrFrame) }) {
                if (dataUrl.isNotBlank()) {
                    Img(src = dataUrl, alt = ZALO_BRAND, attrs = { classes(Styles.zaloQrImage) })
                }
            }
            P(attrs = { classes(Styles.zaloQrNumber) }) { Text(phone) }
            Div(attrs = { classes(Styles.dialogActions) }) {
                PrimaryButton(
                    label = strings.close,
                    icon = "close",
                    onClick = onDismiss,
                )
            }
        }
    }
}

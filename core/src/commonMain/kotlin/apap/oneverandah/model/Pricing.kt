package apap.oneverandah.model

/**
 * A monetary amount expressed in Vietnamese Dong (VND).
 *
 * VND has no commonly used minor unit, so the amount is stored as a whole
 * number of dong. Keeping a dedicated value type (rather than a bare [Long])
 * makes the pricing code self-documenting and lets us centralise formatting and
 * arithmetic so the same rules apply everywhere (client today, API later).
 */
data class Money(val amountVnd: Long) {
    operator fun plus(other: Money): Money = Money(amountVnd + other.amountVnd)
    operator fun times(factor: Int): Money = Money(amountVnd * factor)

    val isZero: Boolean get() = amountVnd == 0L

    /**
     * Formats the amount with locale-appropriate thousands separators and the
     * `₫` symbol, e.g. `150.000 ₫` (Vietnamese) or `150,000 ₫` (English).
     */
    fun format(language: Language): String {
        val separator = if (language == Language.VIETNAMESE) "." else ","
        val negative = amountVnd < 0
        val digits = kotlin.math.abs(amountVnd).toString()
        val grouped = digits
            .reversed()
            .chunked(3)
            .joinToString(separator)
            .reversed()
        val sign = if (negative) "-" else ""
        return "$sign$grouped ₫"
    }

    companion object {
        val ZERO: Money = Money(0)

        /** Convenience for thousands of dong, e.g. `vnd(150)` == `150.000 ₫`. */
        fun thousands(value: Long): Money = Money(value * 1_000)
    }
}

/** A single labelled line in a [PriceBreakdown] (e.g. "60 minutes — 450.000 ₫"). */
data class PriceLine(
    val label: LocalizedText,
    val amount: Money,
)

/** An itemised price breakdown with a computed [total]. */
data class PriceBreakdown(
    val lines: List<PriceLine>,
    val total: Money,
)

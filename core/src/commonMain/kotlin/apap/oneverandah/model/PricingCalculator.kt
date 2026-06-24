package apap.oneverandah.model

/**
 * Turns a [Service] + the user's [BookingSelection] into an itemised
 * [PriceBreakdown]. This is the single source of truth for how a booking total
 * is computed, shared by the live form summary today and re-usable by a server
 * when one exists.
 *
 * Pricing rules (all amounts in VND):
 *  - The chosen duration provides the base price.
 *  - The booking type may add or subtract a [BookingTypeOption.priceDelta].
 *  - Each selected [Choice] adds its [Choice.priceDelta].
 *  - Each [CounterOption] adds quantity x unit price.
 *  - Each selected [BodyPart] adds its [BodyPart.priceDelta].
 */
object PricingCalculator {

    fun breakdown(service: Service, selection: BookingSelection): PriceBreakdown {
        val lines = mutableListOf<PriceLine>()

        val duration = service.durations.firstOrNull { it.id == selection.durationId }
            ?: service.durations.first()
        lines += PriceLine(
            label = LocalizedText(
                en = "${duration.minutes} minutes",
                vi = "${duration.minutes} phút",
            ),
            amount = duration.price,
        )

        service.bookingTypes.firstOrNull { it.id == selection.bookingTypeId }
            ?.takeIf { !it.priceDelta.isZero }
            ?.let { lines += PriceLine(it.label, it.priceDelta) }

        service.options.forEach { option ->
            when (option) {
                is SingleSelectOption -> {
                    val choiceId = selection.singleSelects[option.id]
                    option.choices.firstOrNull { it.id == choiceId }
                        ?.takeIf { !it.priceDelta.isZero }
                        ?.let { lines += PriceLine(it.label, it.priceDelta) }
                }

                is MultiSelectOption -> {
                    val selected = selection.multiSelects[option.id].orEmpty()
                    option.choices
                        .filter { it.id in selected && !it.priceDelta.isZero }
                        .forEach { lines += PriceLine(it.label, it.priceDelta) }
                }

                is CounterOption -> {
                    val count = selection.counters[option.id] ?: option.default
                    if (count > 0 && !option.unitPrice.isZero) {
                        lines += PriceLine(
                            label = LocalizedText(
                                en = "${option.unitLabel.en} x $count",
                                vi = "${option.unitLabel.vi} x $count",
                            ),
                            amount = option.unitPrice * count,
                        )
                    }
                }

                is BodyMapOption -> {
                    val selected = selection.bodyParts[option.id].orEmpty()
                    option.parts
                        .filter { it.id in selected && !it.priceDelta.isZero }
                        .forEach { lines += PriceLine(it.label, it.priceDelta) }
                }
            }
        }

        val total = lines.fold(Money.ZERO) { acc, line -> acc + line.amount }
        return PriceBreakdown(lines = lines, total = total)
    }
}

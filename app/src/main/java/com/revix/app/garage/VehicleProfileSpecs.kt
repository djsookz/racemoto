package com.revix.app.garage

object VehicleProfileSpecs {
    private const val MIN_CC = 1
    private const val MAX_CC = 20_000
    private const val MIN_HP = 1
    private const val MAX_HP = 3_000

    fun parseCc(raw: String?): Int? = parsePositiveInt(raw, MIN_CC, MAX_CC)

    fun parseHp(raw: String?): Int? = parsePositiveInt(raw, MIN_HP, MAX_HP)

    fun formatValue(value: Int?): String {
        return value?.takeIf { it > 0 }?.toString().orEmpty()
    }

    fun displayLine(cc: Int?, hp: Int?, ccUnit: String, hpUnit: String): String {
        val parts = buildList {
            cc?.takeIf { it > 0 }?.let { add("$it $ccUnit") }
            hp?.takeIf { it > 0 }?.let { add("$it $hpUnit") }
        }
        return parts.joinToString(" • ")
    }

    private fun parsePositiveInt(raw: String?, min: Int, max: Int): Int? {
        val digits = raw.orEmpty().filter { it.isDigit() }
        if (digits.isEmpty()) return null
        val value = digits.toIntOrNull() ?: return null
        if (value < min || value > max) return null
        return value
    }
}

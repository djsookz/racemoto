package com.example.clinometer.main.navigation

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import androidx.core.content.ContextCompat
import com.example.clinometer.R

object ManeuverFormatter {
    fun formatManeuverInstruction(context: Context, instruction: String): SpannableString {
        val spannable = SpannableString(instruction)
        val orangeColor = ContextCompat.getColor(context, R.color.accent_orange)
        val lowerInstruction = instruction.lowercase()

        val directionPatterns = listOf(
            Regex("""\b(left|right|straight)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(ляво|дясно|направо)\b""", RegexOption.IGNORE_CASE)
        )
        directionPatterns.forEach { pattern ->
            pattern.findAll(instruction).forEach { matchResult ->
                spannable.setSpan(
                    ForegroundColorSpan(orangeColor),
                    matchResult.range.first,
                    matchResult.range.last + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        val isRoundabout = lowerInstruction.contains("roundabout") ||
            lowerInstruction.contains("кръгово") ||
            (lowerInstruction.contains("take") && lowerInstruction.contains("exit"))

        if (isRoundabout) {
            val exitPattern = Regex("""(\d+)(st|nd|rd|th)\s+exit""", RegexOption.IGNORE_CASE)
            exitPattern.find(instruction)?.let { matchResult ->
                val start = matchResult.range.first
                val end = matchResult.range.last + 1
                spannable.setSpan(
                    ForegroundColorSpan(orangeColor),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        val roadNamePatterns = listOf(
            Regex("""(onto|to|toward)\s+(.+?)(?:\s*\.\s*$|$)""", RegexOption.IGNORE_CASE),
            Regex("""(на|към)\s+(.+?)(?:\s*\.\s*$|$)""", RegexOption.IGNORE_CASE)
        )
        roadNamePatterns.forEach { pattern ->
            pattern.findAll(instruction).forEach { matchResult ->
                val roadNameGroup = matchResult.groups[2]
                if (roadNameGroup != null) {
                    var roadNameStart = roadNameGroup.range.first
                    var roadNameEnd = roadNameGroup.range.last + 1

                    while (roadNameStart < roadNameEnd && instruction[roadNameStart].isWhitespace()) {
                        roadNameStart++
                    }

                    while (roadNameEnd > roadNameStart) {
                        val lastChar = instruction[roadNameEnd - 1]
                        if (lastChar.isWhitespace()) {
                            roadNameEnd--
                        } else if (lastChar == '.' && roadNameEnd >= instruction.length) {
                            val textBeforeDot = instruction.substring(roadNameStart, roadNameEnd - 1).trimEnd()
                            val lastWordBeforeDot = textBeforeDot.takeLastWhile { !it.isWhitespace() && it != '.' }
                            if (lastWordBeforeDot.lowercase() !in listOf("бул", "ул", "пл", "ул", "str", "blvd", "st", "ave")) {
                                roadNameEnd--
                            }
                            break
                        } else {
                            break
                        }
                    }

                    if (roadNameStart < roadNameEnd) {
                        spannable.setSpan(
                            ForegroundColorSpan(orangeColor),
                            roadNameStart,
                            roadNameEnd,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
            }
        }

        return spannable
    }

    fun parseManeuverFromInstruction(instruction: String): Pair<String?, String?> {
        val lowerInstruction = instruction.lowercase().trim()

        return when {
            lowerInstruction.contains("roundabout") || lowerInstruction.contains("кръгово") ||
                lowerInstruction.startsWith("take") && lowerInstruction.contains("exit") -> Pair("roundabout", null)

            lowerInstruction.contains("u-turn") || lowerInstruction.contains("u turn") ||
                lowerInstruction.contains("обратна посока") -> Pair("turn", "uturn")

            lowerInstruction.contains("sharp right") || lowerInstruction.contains("рязко надясно") -> Pair("turn", "sharp right")
            lowerInstruction.contains("sharp left") || lowerInstruction.contains("рязко наляво") -> Pair("turn", "sharp left")

            lowerInstruction.contains("slight right") || lowerInstruction.contains("леко надясно") -> Pair("turn", "slight right")
            lowerInstruction.contains("slight left") || lowerInstruction.contains("леко наляво") -> Pair("turn", "slight left")

            lowerInstruction.startsWith("turn right") || lowerInstruction.contains("turn right") ||
                lowerInstruction.contains("завий надясно") || lowerInstruction.contains("поемете надясно") ||
                (lowerInstruction.contains("right") && !lowerInstruction.contains("roundabout")) -> Pair("turn", "right")
            lowerInstruction.startsWith("turn left") || lowerInstruction.contains("turn left") ||
                lowerInstruction.contains("завий наляво") || lowerInstruction.contains("поемете наляво") ||
                (lowerInstruction.contains("left") && !lowerInstruction.contains("roundabout")) -> Pair("turn", "left")

            lowerInstruction.contains("merge") || lowerInstruction.contains("сливане") -> Pair("merge", null)
            lowerInstruction.contains("arrive") || lowerInstruction.contains("пристигнахте") -> Pair("arrive", null)
            lowerInstruction.contains("continue") || lowerInstruction.contains("продължете") -> Pair("continue", null)
            else -> Pair(null, null)
        }
    }

    fun getManeuverIcon(type: String?, modifier: String?): Int {
        val normalizedType = type?.lowercase()?.trim()
        val normalizedModifier = modifier?.lowercase()?.trim()

        return when (normalizedType) {
            "turn" -> when (normalizedModifier) {
                "left" -> R.drawable.ic_turn_left
                "right" -> R.drawable.ic_turn_right
                "slight left" -> R.drawable.ic_turn_slight_left
                "slight right" -> R.drawable.ic_turn_slight_right
                "sharp left" -> R.drawable.ic_turn_sharp_left
                "sharp right" -> R.drawable.ic_turn_sharp_right
                "uturn" -> R.drawable.ic_uturn
                else -> R.drawable.ic_turn_straight
            }
            "merge" -> R.drawable.ic_merge
            "roundabout", "rotary", "roundabout turn" -> R.drawable.ic_roundabout
            "arrive" -> R.drawable.ic_arrive
            "fork" -> when (normalizedModifier) {
                "left", "slight left" -> R.drawable.ic_turn_slight_left
                "right", "slight right" -> R.drawable.ic_turn_slight_right
                else -> R.drawable.ic_turn_straight
            }
            "off ramp", "on ramp" -> when (normalizedModifier) {
                "left", "slight left" -> R.drawable.ic_turn_slight_left
                "right", "slight right" -> R.drawable.ic_turn_slight_right
                else -> R.drawable.ic_turn_straight
            }
            "depart", "continue", "new name" -> R.drawable.ic_turn_straight
            else -> R.drawable.ic_turn_straight
        }
    }
}

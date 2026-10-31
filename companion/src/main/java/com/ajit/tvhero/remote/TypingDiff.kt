package com.ajit.tvhero.remote

/**
 * Live typing on the TV (2.10): what to send when the phone's text box changes from [before] to [after].
 * The TV keyboard deletes [backspaces] characters (code points, as the TV counts them) and then types
 * [insert]. Only the changed end is sent, so typing a letter sends one letter.
 */
data class TypingDiff(val backspaces: Int, val insert: String) {
    val empty get() = backspaces == 0 && insert.isEmpty()

    companion object {
        fun of(before: String, after: String): TypingDiff {
            var i = 0
            val n = minOf(before.length, after.length)
            while (i < n && before[i] == after[i]) i++
            // Never split a surrogate pair (an emoji): step back to its start.
            if (i > 0 && i < before.length && Character.isLowSurrogate(before[i]) && Character.isHighSurrogate(before[i - 1])) i--
            return TypingDiff(before.codePointCount(i, before.length), after.substring(i))
        }
    }
}

/** The size a photo is sent at: fits [maxW]x[maxH] (the TV's 1280x720), keeps its shape, never enlarged. */
fun fitWithin(w: Int, h: Int, maxW: Int = 1280, maxH: Int = 720): Pair<Int, Int> {
    if (w <= 0 || h <= 0) return 0 to 0
    val scale = minOf(1.0, maxW.toDouble() / w, maxH.toDouble() / h)
    return maxOf(1, (w * scale).toInt()) to maxOf(1, (h * scale).toInt())
}

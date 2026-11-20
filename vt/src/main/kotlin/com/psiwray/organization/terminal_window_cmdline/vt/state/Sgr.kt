package com.psiwray.organization.terminal_window_cmdline.vt.state

import com.psiwray.organization.terminal_window_cmdline.vt.attr.Attributes
import com.psiwray.organization.terminal_window_cmdline.vt.attr.Color
import com.psiwray.organization.terminal_window_cmdline.vt.parser.ParserEvent

/** SGR (`CSI Pm m`) parsing and DECRQSS serialization. */
internal object Sgr {
    fun apply(
        event: ParserEvent.Csi,
        state: TerminalState,
    ) {
        val parameters = event.parameters
        if (parameters.isEmpty()) {
            state.resetPen()
            return
        }
        var index = 0
        while (index < parameters.size) {
            val code = parameters[index]
            when {
                code == ParserEvent.MISSING_PARAMETER || code == 0 -> {
                    state.resetPen()
                    index++
                }

                code == 1 -> {
                    state.penAttributes = state.penAttributes or Attributes.BOLD
                    index++
                }

                code == 2 -> {
                    state.penAttributes = state.penAttributes or Attributes.FAINT
                    index++
                }

                code == 3 -> {
                    state.penAttributes = state.penAttributes or Attributes.ITALIC
                    index++
                }

                code == 4 -> {
                    index =
                        if (index + 1 < parameters.size && event.parameterMore[index]) {
                            val end = groupEnd(parameters, event.parameterMore, index + 1)
                            val style = parameters[index + 1]
                            if (style in 0..5) {
                                state.penAttributes = Attributes.withUnderlineStyle(state.penAttributes, style)
                            }
                            end
                        } else {
                            state.penAttributes = Attributes.withUnderlineStyle(state.penAttributes, 1)
                            index + 1
                        }
                }

                code == 5 || code == 6 -> {
                    state.penAttributes = state.penAttributes or Attributes.BLINK
                    index++
                }

                code == 7 -> {
                    state.penAttributes = state.penAttributes or Attributes.REVERSE
                    index++
                }

                code == 8 -> {
                    state.penAttributes = state.penAttributes or Attributes.INVISIBLE
                    index++
                }

                code == 9 -> {
                    state.penAttributes = state.penAttributes or Attributes.STRIKE
                    index++
                }

                code == 21 -> {
                    state.penAttributes = Attributes.withUnderlineStyle(state.penAttributes, 2)
                    index++
                }

                code == 22 -> {
                    state.penAttributes = state.penAttributes and (Attributes.BOLD or Attributes.FAINT).inv()
                    index++
                }

                code == 23 -> {
                    state.penAttributes = state.penAttributes and Attributes.ITALIC.inv()
                    index++
                }

                code == 24 -> {
                    state.penAttributes = Attributes.withUnderlineStyle(state.penAttributes, 0)
                    index++
                }

                code == 25 -> {
                    state.penAttributes = state.penAttributes and Attributes.BLINK.inv()
                    index++
                }

                code == 27 -> {
                    state.penAttributes = state.penAttributes and Attributes.REVERSE.inv()
                    index++
                }

                code == 28 -> {
                    state.penAttributes = state.penAttributes and Attributes.INVISIBLE.inv()
                    index++
                }

                code == 29 -> {
                    state.penAttributes = state.penAttributes and Attributes.STRIKE.inv()
                    index++
                }

                code in 30..37 -> {
                    state.penForeground = Color.encode(Color.Indexed(code - 30))
                    index++
                }

                code == 39 -> {
                    state.penForeground = 0
                    index++
                }

                code in 40..47 -> {
                    state.penBackground = Color.encode(Color.Indexed(code - 40))
                    index++
                }

                code == 49 -> {
                    state.penBackground = 0
                    index++
                }

                code in 90..97 -> {
                    state.penForeground = Color.encode(Color.Indexed(code - 90 + 8))
                    index++
                }

                code in 100..107 -> {
                    state.penBackground = Color.encode(Color.Indexed(code - 100 + 8))
                    index++
                }

                code == 38 || code == 48 || code == 58 -> {
                    index = applyExtendedColor(state, code, event, index)
                }

                code == 59 -> {
                    state.penUnderlineColor = 0
                    index++
                }

                else -> {
                    index++
                }
            }
        }
    }

    fun serialize(state: TerminalState): String {
        val parts = ArrayList<String>(10)
        val attributes = state.penAttributes
        if (attributes and Attributes.BOLD != 0) parts += "1"
        if (attributes and Attributes.FAINT != 0) parts += "2"
        if (attributes and Attributes.ITALIC != 0) parts += "3"
        val underlineStyle = Attributes.underlineStyle(attributes)
        if (underlineStyle == 1) {
            parts += "4"
        } else if (underlineStyle > 1) {
            parts += "4:$underlineStyle"
        }
        if (attributes and Attributes.BLINK != 0) parts += "5"
        if (attributes and Attributes.REVERSE != 0) parts += "7"
        if (attributes and Attributes.INVISIBLE != 0) parts += "8"
        if (attributes and Attributes.STRIKE != 0) parts += "9"
        appendColor(parts, state.penForeground, foreground = true)
        appendColor(parts, state.penBackground, foreground = false)
        appendUnderlineColor(parts, state.penUnderlineColor)
        return if (parts.isEmpty()) "0" else parts.joinToString(";")
    }

    private fun applyExtendedColor(
        state: TerminalState,
        code: Int,
        event: ParserEvent.Csi,
        baseIndex: Int,
    ): Int {
        val parameters = event.parameters
        val more = event.parameterMore
        if (baseIndex + 1 >= parameters.size) {
            return parameters.size
        }
        val colonForm = more[baseIndex]
        val selectorIndex = baseIndex + 1
        val selector = parameters[selectorIndex]
        if (colonForm) {
            val end = groupEnd(parameters, more, selectorIndex)
            when (selector) {
                5 -> {
                    if (selectorIndex + 1 < end) {
                        setColor(state, code, parameters[selectorIndex + 1], indexed = true)
                    }
                }

                2 -> {
                    val start = selectorIndex + 1
                    val count = end - start
                    val rgbStart = if (count >= 4) start + 1 else start
                    if (end - rgbStart >= 3) {
                        setColor(
                            state,
                            code,
                            parameters[rgbStart],
                            indexed = false,
                            green = parameters[rgbStart + 1],
                            blue =
                                parameters[
                                    rgbStart +
                                        2,
                                ],
                        )
                    }
                }
            }
            return end
        }

        when (selector) {
            5 -> {
                if (selectorIndex + 1 >= parameters.size) {
                    return parameters.size
                }
                setColor(state, code, parameters[selectorIndex + 1], indexed = true)
                return selectorIndex + 2
            }

            2 -> {
                if (selectorIndex + 3 >= parameters.size) {
                    return parameters.size
                }
                setColor(
                    state,
                    code,
                    parameters[selectorIndex + 1],
                    indexed = false,
                    green = parameters[selectorIndex + 2],
                    blue = parameters[selectorIndex + 3],
                )
                return selectorIndex + 4
            }
        }
        return parameters.size
    }

    private fun setColor(
        state: TerminalState,
        code: Int,
        value: Int,
        indexed: Boolean,
        green: Int = 0,
        blue: Int = 0,
    ) {
        val color =
            if (indexed) {
                if (value !in 0..255) return
                Color.Indexed(value)
            } else {
                if (value < 0 || green < 0 || blue < 0) return
                Color.Rgb(value.coerceIn(0, 255), green.coerceIn(0, 255), blue.coerceIn(0, 255))
            }
        val encoded = Color.encode(color)
        when (code) {
            38 -> state.penForeground = encoded
            48 -> state.penBackground = encoded
            58 -> state.penUnderlineColor = encoded
        }
    }

    private fun groupEnd(
        parameters: IntArray,
        more: BooleanArray,
        start: Int,
    ): Int {
        var index = start
        while (index < parameters.size && more[index]) {
            index++
        }
        return minOf(parameters.size, index + 1)
    }

    private fun appendColor(
        parts: MutableList<String>,
        tagged: Int,
        foreground: Boolean,
    ) {
        when (val color = Color.decode(tagged)) {
            Color.Default -> {
                return
            }

            is Color.Indexed -> {
                val index = color.index
                when {
                    index < 8 -> parts += ((if (foreground) 30 else 40) + index).toString()
                    index < 16 -> parts += ((if (foreground) 90 else 100) + index - 8).toString()
                    else -> parts += "${if (foreground) 38 else 48};5;$index"
                }
            }

            is Color.Rgb -> {
                parts += "${if (foreground) 38 else 48};2;${color.red};${color.green};${color.blue}"
            }
        }
    }

    private fun appendUnderlineColor(
        parts: MutableList<String>,
        tagged: Int,
    ) {
        when (val color = Color.decode(tagged)) {
            Color.Default -> return
            is Color.Indexed -> parts += "58;5;${color.index}"
            is Color.Rgb -> parts += "58;2;${color.red};${color.green};${color.blue}"
        }
    }
}

package io.github.chenjin.androidsshclient.core.terminal

data class TerminalStyle(
    val foreground: Int,
    val background: Int,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
)

data class TerminalCell(val text: String, val width: Int, val style: TerminalStyle, val continuation: Boolean = false)
data class TerminalGrid(val lines: List<List<TerminalCell?>>, val cursorRow: Int, val cursorColumn: Int)

class AnsiTerminalParser(
    private val columns: Int,
    private val scheme: TerminalColorScheme,
    private val screenRows: Int = 24,
) {
    private val rows = mutableListOf<MutableList<TerminalCell?>>(mutableListOf())
    private var screenTop = 0
    private var row = 0
    private var column = 0
    private var wrapPending = false
    private var joinNextCodePoint = false
    private var regionalIndicatorPending = false
    private var savedRow = 0
    private var savedColumn = 0
    private var foreground = scheme.foreground
    private var background = scheme.background
    private var bold = false
    private var italic = false
    private var underline = false
    private var inverse = false

    fun parse(input: String): TerminalGrid {
        var index = 0
        while (index < input.length) {
            val codePoint = input.codePointAt(index)
            when {
                codePoint == 0x1b -> index = consumeEscape(input, index)
                codePoint == '\n'.code -> indexLine(resetColumn = false)
                codePoint == '\r'.code -> { column = 0; wrapPending = false }
                codePoint == '\b'.code -> { column = (column - 1).coerceAtLeast(0); wrapPending = false }
                codePoint == '\t'.code -> { column = ((column / 8) + 1) * 8; wrapPending = false }
                codePoint >= 0x20 && codePoint != 0x7f -> writeCodePoint(codePoint)
            }
            index += Character.charCount(codePoint)
        }
        val offset = (rows.size - MAX_ROWS).coerceAtLeast(0)
        return TerminalGrid(rows.drop(offset).map { it.toList() }, (row - offset).coerceAtLeast(0), column)
    }

    private fun consumeEscape(input: String, start: Int): Int {
        if (start + 1 >= input.length) return start
        return when (input[start + 1]) {
            '[' -> consumeCsi(input, start + 2)
            ']' -> consumeOsc(input, start + 2)
            '7' -> { savedRow = row; savedColumn = column; start + 1 }
            '8' -> { row = savedRow; column = savedColumn; ensureRow(); start + 1 }
            'D' -> { indexLine(resetColumn = false); start + 1 }
            'E' -> { indexLine(resetColumn = true); start + 1 }
            'c' -> { clearAll(); resetStyle(); start + 1 }
            else -> start + 1
        }
    }

    private fun consumeOsc(input: String, from: Int): Int {
        var index = from
        while (index < input.length) {
            if (input[index] == '\u0007') return index
            if (input[index] == '\u001b' && index + 1 < input.length && input[index + 1] == '\\') return index + 1
            index++
        }
        return input.lastIndex
    }

    private fun consumeCsi(input: String, from: Int): Int {
        var end = from
        while (end < input.length && input[end].code !in 0x40..0x7e) end++
        if (end >= input.length) return input.lastIndex
        val raw = input.substring(from, end).trimStart('?', '>', '!')
        val params = raw.replace(':', ';').split(';').map { it.toIntOrNull() ?: 0 }
        val first = params.firstOrNull()?.takeIf { it > 0 } ?: 1
        wrapPending = false
        when (input[end]) {
            'm' -> applySgr(raw)
            'A' -> row = (row - first).coerceAtLeast(screenTop)
            'B' -> { row = (row + first).coerceAtMost(screenTop + screenRows - 1); ensureRow() }
            'C' -> column = (column + first).coerceAtMost(columns - 1)
            'D' -> column = (column - first).coerceAtLeast(0)
            'E' -> { row = (row + first).coerceAtMost(screenTop + screenRows - 1); column = 0; ensureRow() }
            'F' -> { row = (row - first).coerceAtLeast(screenTop); column = 0 }
            'G' -> column = (first - 1).coerceIn(0, columns - 1)
            'H', 'f' -> {
                val targetRow = ((params.getOrNull(0) ?: 1).takeIf { it > 0 } ?: 1) - 1
                row = screenTop + targetRow.coerceIn(0, screenRows - 1)
                column = (((params.getOrNull(1) ?: 1).takeIf { it > 0 } ?: 1) - 1).coerceIn(0, columns - 1)
                ensureRow()
            }
            'J' -> when (params.firstOrNull() ?: 0) {
                2 -> clearActiveScreen()
                3 -> clearAll()
                0 -> eraseDisplayBelow()
            }
            'K' -> eraseLine(params.firstOrNull() ?: 0)
            'X' -> eraseCharacters(first)
            'P' -> deleteCharacters(first)
            '@' -> insertCharacters(first)
            's' -> { savedRow = row; savedColumn = column }
            'u' -> { row = savedRow.coerceAtLeast(screenTop); column = savedColumn; ensureRow() }
        }
        return end
    }

    private fun applySgr(raw: String) {
        val params = if (raw.isBlank()) listOf("0") else raw.split(';', ':')
        var index = 0
        while (index < params.size) {
            when (val code = params[index].toIntOrNull() ?: 0) {
                0 -> resetStyle()
                1 -> bold = true
                3 -> italic = true
                4 -> underline = true
                7 -> inverse = true
                22 -> bold = false
                23 -> italic = false
                24 -> underline = false
                27 -> inverse = false
                39 -> foreground = scheme.foreground
                49 -> background = scheme.background
                in 30..37 -> foreground = scheme.ansi[code - 30]
                in 40..47 -> background = scheme.ansi[code - 40]
                in 90..97 -> foreground = scheme.ansi[code - 90 + 8]
                in 100..107 -> background = scheme.ansi[code - 100 + 8]
                38, 48 -> {
                    val resolved = extendedColor(params, index + 1)
                    if (resolved != null) {
                        if (code == 38) foreground = resolved.first else background = resolved.first
                        index = resolved.second
                    }
                }
            }
            index++
        }
    }

    private fun extendedColor(params: List<String>, start: Int): Pair<Int, Int>? = when (params.getOrNull(start)?.toIntOrNull()) {
        5 -> params.getOrNull(start + 1)?.toIntOrNull()?.let { color256(it.coerceIn(0, 255)) to (start + 1) }
        2 -> {
            var offset = start + 1
            if (params.getOrNull(offset).isNullOrEmpty()) offset++
            val red = params.getOrNull(offset)?.toIntOrNull() ?: return null
            val green = params.getOrNull(offset + 1)?.toIntOrNull() ?: return null
            val blue = params.getOrNull(offset + 2)?.toIntOrNull() ?: return null
            ((0xff shl 24) or (red.coerceIn(0, 255) shl 16) or (green.coerceIn(0, 255) shl 8) or blue.coerceIn(0, 255)) to (offset + 2)
        }
        else -> null
    }

    private fun color256(index: Int): Int = when {
        index < 16 -> scheme.ansi[index]
        index < 232 -> {
            val value = index - 16
            val red = value / 36
            val green = value / 6 % 6
            val blue = value % 6
            fun component(value: Int) = if (value == 0) 0 else 55 + value * 40
            (0xff shl 24) or (component(red) shl 16) or (component(green) shl 8) or component(blue)
        }
        else -> {
            val gray = 8 + (index - 232) * 10
            (0xff shl 24) or (gray shl 16) or (gray shl 8) or gray
        }
    }

    private fun writeCodePoint(codePoint: Int) {
        if (joinNextCodePoint) {
            appendToPrevious(codePoint)
            joinNextCodePoint = false
            return
        }
        if (codePoint == ZERO_WIDTH_JOINER) {
            appendToPrevious(codePoint)
            joinNextCodePoint = true
            return
        }
        if (isZeroWidth(codePoint)) {
            appendToPrevious(codePoint)
            return
        }
        if (isRegionalIndicator(codePoint) && regionalIndicatorPending) {
            appendToPrevious(codePoint)
            regionalIndicatorPending = false
            return
        }
        val width = cellWidth(codePoint)
        if (wrapPending) indexLine(resetColumn = true)
        if (column + width > columns) indexLine(resetColumn = true)
        ensureRow()
        val line = rows[row]
        ensureColumn(line, column + width - 1)
        clearWideCellAt(line, column)
        val style = currentStyle()
        line[column] = TerminalCell(String(Character.toChars(codePoint)), width, style)
        if (width == 2) line[column + 1] = TerminalCell("", 0, style, continuation = true)
        column += width
        if (column >= columns) {
            column = columns - 1
            wrapPending = true
        }
        regionalIndicatorPending = isRegionalIndicator(codePoint)
    }

    private fun appendToPrevious(codePoint: Int) {
        val line = rows.getOrNull(row) ?: return
        var target = (if (wrapPending) column else column - 1).coerceAtMost(line.lastIndex)
        while (target >= 0 && line[target]?.continuation == true) target--
        val cell = line.getOrNull(target) ?: return
        line[target] = cell.copy(text = cell.text + String(Character.toChars(codePoint)))
    }

    private fun clearWideCellAt(line: MutableList<TerminalCell?>, target: Int) {
        if (target > 0 && line.getOrNull(target)?.continuation == true) line[target - 1] = null
        val existing = line.getOrNull(target)
        if (existing != null && existing.width == 2 && target + 1 < line.size) line[target + 1] = null
    }

    private fun eraseLine(mode: Int) {
        ensureRow()
        val line = rows[row]
        ensureColumn(line, columns - 1)
        when (mode) {
            0 -> for (index in column until columns) line[index] = blankCell()
            1 -> for (index in 0..column.coerceAtMost(columns - 1)) line[index] = blankCell()
            2 -> for (index in 0 until columns) line[index] = blankCell()
        }
    }

    private fun eraseCharacters(count: Int) {
        ensureRow(); val line = rows[row]; ensureColumn(line, columns - 1)
        for (index in column until (column + count).coerceAtMost(columns)) line[index] = blankCell()
    }

    private fun deleteCharacters(count: Int) {
        ensureRow(); val line = rows[row]; ensureColumn(line, columns - 1)
        repeat(count.coerceAtMost(columns - column)) { line.removeAt(column); line.add(blankCell()) }
    }

    private fun insertCharacters(count: Int) {
        ensureRow(); val line = rows[row]; ensureColumn(line, columns - 1)
        repeat(count.coerceAtMost(columns - column)) { line.add(column, blankCell()); line.removeAt(line.lastIndex) }
    }

    private fun eraseDisplayBelow() {
        eraseLine(0)
        val activeBottom = (screenTop + screenRows - 1).coerceAtMost(rows.lastIndex)
        for (targetRow in row + 1..activeBottom) {
            val line = rows[targetRow]
            ensureColumn(line, columns - 1)
            for (index in 0 until columns) line[index] = blankCell()
        }
    }

    private fun clearActiveScreen() {
        while (rows.size > screenTop) rows.removeAt(rows.lastIndex)
        rows.add(mutableListOf())
        row = screenTop
        column = 0
        wrapPending = false
    }

    private fun clearAll() {
        rows.clear(); rows.add(mutableListOf()); screenTop = 0; row = 0; column = 0; wrapPending = false
    }

    private fun indexLine(resetColumn: Boolean) {
        row++
        if (resetColumn) column = 0
        wrapPending = false
        if (row >= screenTop + screenRows) screenTop = row - screenRows + 1
        ensureRow()
    }

    private fun ensureRow() { while (rows.size <= row) rows.add(mutableListOf()) }
    private fun ensureColumn(line: MutableList<TerminalCell?>, target: Int) { while (line.size <= target) line += null }
    private fun blankCell() = TerminalCell(" ", 1, currentStyle())
    private fun resetStyle() { foreground = scheme.foreground; background = scheme.background; bold = false; italic = false; underline = false; inverse = false }
    private fun currentStyle() = if (inverse) TerminalStyle(background, foreground, bold, italic, underline) else TerminalStyle(foreground, background, bold, italic, underline)

    companion object {
        private const val MAX_ROWS = 4000
        private const val ZERO_WIDTH_JOINER = 0x200d

        fun cellWidth(codePoint: Int): Int {
            if (isZeroWidth(codePoint)) return 0
            return if (
                codePoint in 0x1100..0x115f || codePoint in 0x2329..0x232a ||
                codePoint in 0x2e80..0xa4cf || codePoint in 0xac00..0xd7a3 ||
                codePoint in 0xf900..0xfaff || codePoint in 0xfe10..0xfe6f ||
                codePoint in 0xff01..0xff60 || codePoint in 0xffe0..0xffe6 ||
                codePoint in 0x1f1e6..0x1f1ff || codePoint in 0x1f300..0x1faff || codePoint in 0x20000..0x3fffd
            ) 2 else 1
        }

        private fun isZeroWidth(codePoint: Int): Boolean {
            val type = Character.getType(codePoint)
            return codePoint == ZERO_WIDTH_JOINER || codePoint in 0xfe00..0xfe0f || codePoint in 0xe0100..0xe01ef ||
                codePoint in 0x1f3fb..0x1f3ff || type in setOf(
                    Character.NON_SPACING_MARK.toInt(),
                    Character.COMBINING_SPACING_MARK.toInt(),
                    Character.ENCLOSING_MARK.toInt(),
                )
        }

        private fun isRegionalIndicator(codePoint: Int) = codePoint in 0x1f1e6..0x1f1ff
    }
}

package io.github.chenjin.androidsshclient.core.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.SpannableStringBuilder
import android.util.TypedValue
import android.view.ActionMode
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import io.github.chenjin.androidsshclient.core.model.TerminalFont
import io.github.chenjin.androidsshclient.core.ui.font.AppFonts
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

enum class TerminalKey { ESC, TAB, HOME, UP, END, PAGE_UP, LEFT, DOWN, RIGHT, PAGE_DOWN }

class TerminalView(context: Context) : View(context) {
    private data class CellPosition(val row: Int, val column: Int)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private var rawText = ""
    private var bracketedPasteMode = false
    private var applicationCursorMode = false
    private var forceReparseOnNextText = false
    private var grid = TerminalGrid(listOf(emptyList()), 0, 0)
    private var scheme = TerminalColorSchemes.byName("One Dark")
    private var terminalFont = TerminalFont.FIRA_CODE
    private var customFontRevision: Long = 0
    private var regularTypeface: Typeface = AppFonts.terminal(context)
    private var boldTypeface: Typeface = AppFonts.terminal(context, isBold = true)
    private var baseFontSizeSp = 14f
    private var zoomFactor = 1f
    private var fontSizeSp = 14f
    private var lineHeightScale = 1.2f
    private var ligatures = true
    private var cellWidth = 8f
    private var columns = 120
    private var scrollLine = 0f
    private var lastY = 0f
    private var inputListener: ((String) -> Unit)? = null
    private var pasteConfirmationListener: ((String) -> Unit)? = null
    private var sizeListener: ((columns: Int, rows: Int, width: Int, height: Int) -> Unit)? = null
    private var sizeListenerToken: String? = null
    private var lastReportedSize: List<Int> = emptyList()
    private var selectionStart: CellPosition? = null
    private var selectionEnd: CellPosition? = null
    private var selecting = false
    private var selectionActionMode: ActionMode? = null
    private val reflowRunnable = Runnable {
        if (selecting) cancelSelection()
        reparse()
        scrollLine = maxScroll().toFloat()
        invalidate()
    }

    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoomFactor = (zoomFactor * detector.scaleFactor).coerceIn(9f / baseFontSizeSp, 32f / baseFontSizeSp)
            fontSizeSp = baseFontSizeSp * zoomFactor
            updateMetrics(reparse = true)
            return true
        }
    })
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent): Boolean = true
        override fun onSingleTapUp(event: MotionEvent): Boolean {
            clearSelection()
            showKeyboard()
            return true
        }
        override fun onLongPress(event: MotionEvent) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            selectWordAt(positionAt(event.x, event.y))
            startSelectionActionMode()
        }
    })

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        updateMetrics(reparse = false)
    }

    fun setTerminalText(value: String) {
        if (rawText == value && !forceReparseOnNextText) return
        forceReparseOnNextText = false
        if (selecting) cancelSelection()
        rawText = value
        reparse()
        if (!selecting) scrollLine = maxScroll().toFloat()
        invalidate()
    }

    fun setInputListener(listener: (String) -> Unit) {
        inputListener = listener
    }

    fun setPasteConfirmationListener(listener: (String) -> Unit) {
        pasteConfirmationListener = listener
    }

    fun paste(text: String) {
        if (text.isEmpty()) return
        if (!grid.bracketedPaste && ('\n' in text || '\r' in text)) {
            pasteConfirmationListener?.invoke(text)
            return
        }
        pasteConfirmed(text)
    }

    fun pasteConfirmed(text: String) {
        if (text.isEmpty()) return
        val payload = if (grid.bracketedPaste) "\u001b[200~$text\u001b[201~" else text
        inputListener?.invoke(payload)
    }

    fun keySequence(key: TerminalKey): String = when (key) {
        TerminalKey.ESC -> "\u001b"
        TerminalKey.TAB -> "\t"
        TerminalKey.HOME -> if (grid.applicationCursor) "\u001bOH" else "\u001b[H"
        TerminalKey.UP -> if (grid.applicationCursor) "\u001bOA" else "\u001b[A"
        TerminalKey.END -> if (grid.applicationCursor) "\u001bOF" else "\u001b[F"
        TerminalKey.PAGE_UP -> "\u001b[5~"
        TerminalKey.LEFT -> if (grid.applicationCursor) "\u001bOD" else "\u001b[D"
        TerminalKey.DOWN -> if (grid.applicationCursor) "\u001bOB" else "\u001b[B"
        TerminalKey.RIGHT -> if (grid.applicationCursor) "\u001bOC" else "\u001b[C"
        TerminalKey.PAGE_DOWN -> "\u001b[6~"
    }

    fun focusInput() = showKeyboard()

    fun setTerminalSizeListener(token: String, listener: (columns: Int, rows: Int, width: Int, height: Int) -> Unit) {
        sizeListener = listener
        if (sizeListenerToken != token) {
            cancelSelection()
            bracketedPasteMode = false
            applicationCursorMode = false
            forceReparseOnNextText = true
            sizeListenerToken = token
            lastReportedSize = emptyList()
        }
        reportTerminalSize()
    }

    fun configure(
        fontSize: Float,
        lineHeight: Float,
        ligatures: Boolean,
        font: TerminalFont = TerminalFont.FIRA_CODE,
        schemeName: String = "One Dark",
        customFontRevision: Long = 0,
    ) {
        val nextScheme = TerminalColorSchemes.byName(schemeName)
        val changed = baseFontSizeSp != fontSize || lineHeightScale != lineHeight || this.ligatures != ligatures ||
            terminalFont != font || scheme != nextScheme || this.customFontRevision != customFontRevision
        baseFontSizeSp = fontSize.coerceIn(9f, 32f)
        fontSizeSp = (baseFontSizeSp * zoomFactor).coerceIn(9f, 32f)
        lineHeightScale = lineHeight.coerceIn(1f, 1.8f)
        this.ligatures = ligatures
        terminalFont = font
        this.customFontRevision = customFontRevision
        scheme = nextScheme
        if (changed) updateMetrics(reparse = true)
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_NONE
        return object : BaseInputConnection(this, true) {
            private val buffer = SpannableStringBuilder()

            override fun getEditable(): Editable = buffer

            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                super.commitText(text, newCursorPosition)
                flushBuffer()
                return true
            }

            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean =
                super.setComposingText(text, newCursorPosition)

            override fun finishComposingText(): Boolean {
                super.finishComposingText()
                flushBuffer()
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (buffer.isNotEmpty()) return super.deleteSurroundingText(beforeLength, afterLength)
                repeat(beforeLength.coerceAtLeast(0)) { inputListener?.invoke("\u007f") }
                repeat(afterLength.coerceAtLeast(0)) { inputListener?.invoke("\u001b[3~") }
                return true
            }

            override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean =
                deleteSurroundingText(beforeLength, afterLength)

            override fun performEditorAction(actionCode: Int): Boolean {
                finishComposingText()
                inputListener?.invoke("\r")
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action != KeyEvent.ACTION_DOWN) return true
                flushBuffer()
                return handleKeyEvent(event) || super.sendKeyEvent(event)
            }

            private fun flushBuffer() {
                if (buffer.isNotEmpty()) inputListener?.invoke(buffer.toString())
                buffer.clear()
                buffer.clearSpans()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = handleKeyEvent(event) || super.onKeyDown(keyCode, event)

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        val nextColumns = floor((width - paddingLeft - paddingRight).coerceAtLeast(1) / cellWidth).toInt().coerceAtLeast(2)
        val columnsChanged = nextColumns != columns
        if (columnsChanged) {
            removeCallbacks(reflowRunnable)
            if (selecting) cancelSelection()
            columns = nextColumns
            reparse()
            scrollLine = maxScroll().toFloat()
        } else if (height != oldHeight) {
            removeCallbacks(reflowRunnable)
            postDelayed(reflowRunnable, REFLOW_DEBOUNCE_MS)
        }
        reportTerminalSize()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(reflowRunnable)
        selectionActionMode?.finish()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(scheme.background)
        paint.typeface = regularTypeface
        paint.textSkewX = 0f
        val lineHeight = lineHeightPx()
        val visible = ceil(height / lineHeight).toInt() + 1
        val start = scrollLine.toInt().coerceIn(0, maxScroll())
        val end = min(grid.lines.size, start + visible)
        var lineTop = 0f
        var baseline = lineHeight - paint.fontMetrics.descent
        for (lineIndex in start until end) {
            drawLine(canvas, grid.lines[lineIndex], baseline, lineTop, lineHeight)
            drawSelection(canvas, lineIndex, lineTop, lineHeight)
            if (lineIndex == grid.cursorRow && !selecting) drawCursor(canvas, grid.cursorColumn, baseline, lineHeight)
            lineTop += lineHeight
            baseline += lineHeight
        }
    }

    private fun drawLine(canvas: Canvas, cells: List<TerminalCell?>, baseline: Float, lineTop: Float, lineHeight: Float) {
        var column = 0
        while (column < cells.size && column < columns) {
            val cell = cells[column]
            if (cell == null || cell.continuation) {
                column++
                continue
            }
            val style = cell.style
            val text = StringBuilder()
            val startColumn = column
            var occupied = 0
            val mayShapeRun = terminalFont != TerminalFont.CUSTOM && cell.isAsciiWidthOne()
            while (column < cells.size && column < columns) {
                val next = cells[column] ?: break
                if (next.continuation) { column++; continue }
                if (next.style != style || (!mayShapeRun && occupied > 0) || (mayShapeRun && !next.isAsciiWidthOne())) break
                text.append(next.text)
                occupied += next.width
                column += next.width
            }
            val left = paddingLeft + startColumn * cellWidth
            val right = left + occupied * cellWidth
            paint.typeface = if (style.bold) boldTypeface else regularTypeface
            if (style.background != scheme.background) {
                paint.color = style.background
                paint.style = Paint.Style.FILL
                canvas.drawRect(floor(left), floor(lineTop), ceil(right) + 1f, ceil(lineTop + lineHeight), paint)
            }
            paint.textSkewX = if (style.italic) -0.2f else 0f
            paint.fontFeatureSettings = if (ligatures) "liga,clig,calt" else "-liga,-clig,-calt"
            paint.color = style.foreground
            val value = text.toString()
            val codePoint = value.codePointAt(0)
            if (occupied == 1 && codePoint in POWERLINE_GLYPHS) {
                val naturalWidth = paint.measureText(value).coerceAtLeast(1f)
                paint.textScaleX = (cellWidth / naturalWidth) * 1.03f
                canvas.drawText(value, left - 0.5f, baseline, paint)
                paint.textScaleX = 1f
            } else {
                canvas.drawText(value, left, baseline, paint)
            }
            if (style.underline) {
                paint.strokeWidth = max(1f, paint.textSize / 16f)
                canvas.drawLine(left, baseline + paint.strokeWidth * 2, right, baseline + paint.strokeWidth * 2, paint)
            }
            paint.textSkewX = 0f
        }
    }

    private fun drawSelection(canvas: Canvas, lineIndex: Int, lineTop: Float, lineHeight: Float) {
        val range = normalizedSelection() ?: return
        if (lineIndex !in range.first.row..range.second.row) return
        val startColumn = if (lineIndex == range.first.row) range.first.column else 0
        val endColumn = if (lineIndex == range.second.row) range.second.column else columns - 1
        if (endColumn < startColumn) return
        paint.color = scheme.cursor
        paint.alpha = 90
        canvas.drawRect(
            floor(paddingLeft + startColumn.coerceIn(0, columns - 1) * cellWidth),
            floor(lineTop),
            ceil(paddingLeft + (endColumn.coerceIn(0, columns - 1) + 1) * cellWidth),
            ceil(lineTop + lineHeight),
            paint,
        )
        paint.alpha = 255
    }

    private fun drawCursor(canvas: Canvas, cursorColumn: Int, baseline: Float, lineHeight: Float) {
        paint.color = scheme.cursor
        paint.alpha = 180
        val left = paddingLeft + cursorColumn.coerceIn(0, columns - 1) * cellWidth
        canvas.drawRect(left, baseline + paint.fontMetrics.descent - max(2f, lineHeight / 12f), left + cellWidth, baseline + paint.fontMetrics.descent, paint)
        paint.alpha = 255
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaler.onTouchEvent(event)
        gestures.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> lastY = event.y
            MotionEvent.ACTION_MOVE -> if (!scaler.isInProgress) {
                if (selecting) {
                    selectionEnd = positionAt(event.x, event.y)
                } else {
                    val lineHeight = lineHeightPx()
                    scrollLine = (scrollLine + (lastY - event.y) / lineHeight).coerceIn(0f, maxScroll().toFloat())
                    lastY = event.y
                }
                invalidate()
            }
            MotionEvent.ACTION_UP -> performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun handleKeyEvent(event: KeyEvent): Boolean {
        val sequence = when (event.keyCode) {
            KeyEvent.KEYCODE_DEL -> "\u007f"
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> "\r"
            KeyEvent.KEYCODE_TAB -> keySequence(TerminalKey.TAB)
            KeyEvent.KEYCODE_DPAD_UP -> keySequence(TerminalKey.UP)
            KeyEvent.KEYCODE_DPAD_DOWN -> keySequence(TerminalKey.DOWN)
            KeyEvent.KEYCODE_DPAD_RIGHT -> keySequence(TerminalKey.RIGHT)
            KeyEvent.KEYCODE_DPAD_LEFT -> keySequence(TerminalKey.LEFT)
            KeyEvent.KEYCODE_ESCAPE -> keySequence(TerminalKey.ESC)
            else -> {
                val unicode = event.unicodeChar
                if (unicode == 0) return false
                val character = unicode.toChar()
                if (event.isCtrlPressed) ((character.uppercaseChar().code and 0x1f).toChar()).toString()
                else (if (event.isAltPressed) "\u001b" else "") + character
            }
        }
        inputListener?.invoke(sequence)
        return true
    }

    private fun showKeyboard() {
        requestFocus()
        post {
            val manager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            manager.restartInput(this)
            manager.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun positionAt(x: Float, y: Float): CellPosition {
        val row = (scrollLine.toInt() + floor(y / lineHeightPx()).toInt()).coerceIn(0, grid.lines.lastIndex.coerceAtLeast(0))
        val rawColumn = floor((x - paddingLeft).coerceAtLeast(0f) / cellWidth).toInt().coerceIn(0, columns - 1)
        return normalizePosition(CellPosition(row, rawColumn))
    }

    private fun normalizePosition(position: CellPosition): CellPosition {
        val line = grid.lines.getOrNull(position.row).orEmpty()
        var column = position.column
        while (column > 0 && line.getOrNull(column)?.continuation == true) column--
        return CellPosition(position.row, column)
    }

    private fun selectWordAt(position: CellPosition) {
        val line = grid.lines.getOrNull(position.row).orEmpty()
        var column = position.column.coerceAtMost(line.lastIndex.coerceAtLeast(0))
        while (column > 0 && line.getOrNull(column)?.continuation == true) column--
        fun selectable(index: Int): Boolean = line.getOrNull(index)?.let { !it.continuation && it.text.isNotBlank() } == true
        var start = column
        var end = column
        while (start > 0 && selectable(start - 1)) start--
        while (end + 1 < line.size && (selectable(end + 1) || line[end + 1]?.continuation == true)) end++
        selectionStart = CellPosition(position.row, start)
        selectionEnd = CellPosition(position.row, end)
        selecting = true
        invalidate()
    }

    private fun startSelectionActionMode() {
        selectionActionMode?.finish()
        selectionActionMode = startActionMode(object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.add(0, ACTION_COPY, 0, "复制").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                menu.add(0, ACTION_PASTE, 1, "粘贴").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                menu.add(0, ACTION_SELECT_ALL, 2, "全选").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                return true
            }
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                when (item.itemId) {
                    ACTION_COPY -> copySelection()
                    ACTION_PASTE -> pasteFromClipboard()
                    ACTION_SELECT_ALL -> {
                        selectionStart = CellPosition(0, 0)
                        selectionEnd = CellPosition(grid.lines.lastIndex.coerceAtLeast(0), columns - 1)
                        invalidate()
                        return true
                    }
                    else -> return false
                }
                mode.finish()
                return true
            }
            override fun onDestroyActionMode(mode: ActionMode) {
                selectionActionMode = null
                clearSelection()
            }
        }, ActionMode.TYPE_FLOATING)
    }

    private fun copySelection() {
        val text = selectedText()
        if (text.isEmpty()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("terminal", text))
    }

    private fun pasteFromClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.let(::paste)
    }

    private fun selectedText(): String {
        val range = normalizedSelection() ?: return ""
        return buildString {
            for (rowIndex in range.first.row..range.second.row) {
                val line = grid.lines.getOrNull(rowIndex).orEmpty()
                val start = if (rowIndex == range.first.row) range.first.column else 0
                val end = if (rowIndex == range.second.row) range.second.column else columns - 1
                val lineText = StringBuilder()
                for (column in start..end) {
                    val cell = line.getOrNull(column)
                    when {
                        cell == null -> lineText.append(' ')
                        !cell.continuation -> lineText.append(cell.text)
                    }
                }
                val hardBreak = rowIndex in grid.hardBreakRows
                val selectedLine = lineText.toString()
                append(if (hardBreak || rowIndex == range.second.row) selectedLine.trimEnd() else selectedLine)
                if (rowIndex != range.second.row && hardBreak) append('\n')
            }
        }
    }

    private fun normalizedSelection(): Pair<CellPosition, CellPosition>? {
        val start = selectionStart ?: return null
        val end = selectionEnd ?: return null
        return if (start.row < end.row || start.row == end.row && start.column <= end.column) start to end else end to start
    }

    private fun cancelSelection() {
        selectionActionMode?.finish()
        if (selectionActionMode == null) clearSelection()
    }

    private fun clearSelection() {
        selecting = false
        selectionStart = null
        selectionEnd = null
        invalidate()
    }

    private fun maxScroll(): Int {
        val visible = (height / lineHeightPx()).toInt().coerceAtLeast(1)
        return (grid.lines.size - visible).coerceAtLeast(0)
    }

    private fun lineHeightPx() = paint.textSize * lineHeightScale

    private fun updateMetrics(reparse: Boolean) {
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, fontSizeSp, resources.displayMetrics)
        regularTypeface = AppFonts.terminal(context, terminalFont)
        boldTypeface = AppFonts.terminal(context, terminalFont, isBold = true)
        paint.typeface = regularTypeface
        paint.fontFeatureSettings = if (ligatures) "liga,clig,calt" else "-liga,-clig,-calt"
        cellWidth = paint.measureText("M").coerceAtLeast(1f)
        val nextColumns = floor((width - paddingLeft - paddingRight).coerceAtLeast(1) / cellWidth).toInt().coerceAtLeast(2)
        val columnsChanged = nextColumns != columns
        columns = nextColumns
        setBackgroundColor(scheme.background)
        if (reparse || columnsChanged) reparse()
        scrollLine = scrollLine.coerceIn(0f, maxScroll().toFloat())
        lastReportedSize = emptyList()
        reportTerminalSize()
        invalidate()
    }

    private fun reportTerminalSize() {
        if (width <= 0 || height <= 0) return
        val rows = floor(height / lineHeightPx()).toInt().coerceAtLeast(1)
        val size = listOf(columns, rows, width, height)
        if (size != lastReportedSize) {
            lastReportedSize = size
            sizeListener?.invoke(columns, rows, width, height)
        }
    }

    private fun reparse() {
        val rows = floor(height.coerceAtLeast(1) / lineHeightPx()).toInt().coerceAtLeast(1)
        grid = AnsiTerminalParser(
            columns = columns,
            scheme = scheme,
            screenRows = rows,
            initialBracketedPaste = bracketedPasteMode,
            initialApplicationCursor = applicationCursorMode,
        ).parse(rawText)
        bracketedPasteMode = grid.bracketedPaste
        applicationCursorMode = grid.applicationCursor
    }

    private fun TerminalCell.isAsciiWidthOne(): Boolean = width == 1 && text.all { it.code in 0x20..0x7e }

    private companion object {
        const val ACTION_COPY = 1
        const val ACTION_PASTE = 2
        const val ACTION_SELECT_ALL = 3
        const val REFLOW_DEBOUNCE_MS = 120L
        val POWERLINE_GLYPHS = 0xe0b0..0xe0d4
    }
}

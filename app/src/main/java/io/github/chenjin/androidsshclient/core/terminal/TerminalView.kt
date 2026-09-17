package io.github.chenjin.androidsshclient.core.terminal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import io.github.chenjin.androidsshclient.core.model.TerminalFont
import io.github.chenjin.androidsshclient.core.ui.font.AppFonts
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class TerminalView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private var rawText = ""
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
    private var sizeListener: ((columns: Int, rows: Int, width: Int, height: Int) -> Unit)? = null
    private var sizeListenerToken: String? = null
    private var lastReportedSize: List<Int> = emptyList()
    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoomFactor = (zoomFactor * detector.scaleFactor).coerceIn(9f / baseFontSizeSp, 32f / baseFontSizeSp)
            fontSizeSp = baseFontSizeSp * zoomFactor
            updateMetrics(reparse = true)
            return true
        }
    })

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        updateMetrics(reparse = false)
    }

    fun setTerminalText(value: String) {
        if (rawText == value) return
        rawText = value
        reparse()
        scrollLine = maxScroll().toFloat()
        invalidate()
    }

    fun setTerminalSizeListener(token: String, listener: (columns: Int, rows: Int, width: Int, height: Int) -> Unit) {
        sizeListener = listener
        if (sizeListenerToken != token) {
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

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        val nextColumns = floor((width - paddingLeft - paddingRight).coerceAtLeast(1) / cellWidth).toInt().coerceAtLeast(2)
        if (nextColumns != columns) {
            columns = nextColumns
            reparse()
            scrollLine = maxScroll().toFloat()
        }
        reportTerminalSize()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(scheme.background)
        val lineHeight = paint.textSize * lineHeightScale
        val visible = ceil(height / lineHeight).toInt() + 1
        val start = scrollLine.toInt().coerceIn(0, maxScroll())
        val end = min(grid.lines.size, start + visible)
        var baseline = lineHeight - paint.fontMetrics.descent
        for (lineIndex in start until end) {
            drawLine(canvas, grid.lines[lineIndex], baseline, lineHeight)
            if (lineIndex == grid.cursorRow) drawCursor(canvas, grid.cursorColumn, baseline, lineHeight)
            baseline += lineHeight
        }
    }

    private fun drawLine(canvas: Canvas, cells: List<TerminalCell?>, baseline: Float, lineHeight: Float) {
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
            if (style.background != scheme.background) {
                paint.color = style.background
                paint.style = Paint.Style.FILL
                canvas.drawRect(left, baseline - lineHeight + paint.fontMetrics.descent, right, baseline + paint.fontMetrics.descent, paint)
            }
            paint.typeface = if (style.bold) boldTypeface else regularTypeface
            paint.textSkewX = if (style.italic) -0.2f else 0f
            paint.fontFeatureSettings = if (ligatures) "liga,clig,calt" else "-liga,-clig,-calt"
            paint.color = style.foreground
            canvas.drawText(text.toString(), left, baseline, paint)
            if (style.underline) {
                paint.strokeWidth = max(1f, paint.textSize / 16f)
                canvas.drawLine(left, baseline + paint.strokeWidth * 2, right, baseline + paint.strokeWidth * 2, paint)
            }
            paint.textSkewX = 0f
        }
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
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> lastY = event.y
            MotionEvent.ACTION_MOVE -> if (!scaler.isInProgress) {
                val lineHeight = paint.textSize * lineHeightScale
                scrollLine = (scrollLine + (lastY - event.y) / lineHeight).coerceIn(0f, maxScroll().toFloat())
                lastY = event.y
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

    private fun maxScroll(): Int {
        val visible = (height / (paint.textSize * lineHeightScale)).toInt().coerceAtLeast(1)
        return (grid.lines.size - visible).coerceAtLeast(0)
    }

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
        reportTerminalSize()
        invalidate()
    }

    private fun reportTerminalSize() {
        if (width <= 0 || height <= 0) return
        val rows = floor(height / (paint.textSize * lineHeightScale)).toInt().coerceAtLeast(1)
        val size = listOf(columns, rows, width, height)
        if (size != lastReportedSize) {
            lastReportedSize = size
            sizeListener?.invoke(columns, rows, width, height)
        }
    }

    private fun reparse() {
        val rows = floor(height.coerceAtLeast(1) / (paint.textSize * lineHeightScale)).toInt().coerceAtLeast(1)
        grid = AnsiTerminalParser(columns, scheme, rows).parse(rawText)
    }

    private fun TerminalCell.isAsciiWidthOne(): Boolean =
        width == 1 && text.all { it.code in 0x20..0x7e }
}

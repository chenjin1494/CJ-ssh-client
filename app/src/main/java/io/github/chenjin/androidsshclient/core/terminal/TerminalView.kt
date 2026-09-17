package io.github.chenjin.androidsshclient.core.terminal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import io.github.chenjin.androidsshclient.core.model.TerminalFont
import io.github.chenjin.androidsshclient.core.ui.font.AppFonts
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class TerminalView(context: Context) : View(context) {
    private data class Run(val text: String, val color: Int, val bold: Boolean)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = AppFonts.terminal() }
    private val palette = intArrayOf(
        0xff282c34.toInt(), 0xffe06c75.toInt(), 0xff98c379.toInt(), 0xffe5c07b.toInt(),
        0xff61afef.toInt(), 0xffc678dd.toInt(), 0xff56b6c2.toInt(), 0xffabb2bf.toInt(),
        0xff5c6370.toInt(), 0xfff07178.toInt(), 0xffc3e88d.toInt(), 0xffffcb6b.toInt(),
        0xff82aaff.toInt(), 0xffc792ea.toInt(), 0xff89ddff.toInt(), 0xffffffff.toInt(),
    )
    private var lines: List<List<Run>> = listOf(emptyList())
    private var terminalFont = TerminalFont.FIRA_CODE
    private var fontSizeSp = 14f
    private var lineHeightScale = 1.2f
    private var scrollLine = 0f
    private var lastY = 0f
    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            fontSizeSp = (fontSizeSp * detector.scaleFactor).coerceIn(9f, 32f)
            updatePaint(); invalidate(); return true
        }
    })

    init {
        isFocusable = true
        setBackgroundColor(0xff101315.toInt())
        updatePaint()
    }

    fun setTerminalText(value: String) {
        lines = parse(value)
        scrollLine = maxScroll().toFloat()
        invalidate()
    }

    fun configure(fontSize: Float, lineHeight: Float, ligatures: Boolean, font: TerminalFont = TerminalFont.FIRA_CODE) {
        terminalFont = font
        fontSizeSp = fontSize.coerceIn(9f, 32f)
        lineHeightScale = lineHeight.coerceIn(1f, 1.8f)
        paint.fontFeatureSettings = if (ligatures) "liga" else "-liga"
        paint.typeface = AppFonts.terminal()
        updatePaint(); invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val lineHeight = paint.textSize * lineHeightScale
        val visible = ceil(height / lineHeight).toInt() + 1
        val start = scrollLine.toInt().coerceIn(0, maxScroll())
        val end = min(lines.size, start + visible)
        var y = lineHeight - paint.fontMetrics.descent
        for (lineIndex in start until end) {
            var x = paddingLeft.toFloat()
            lines[lineIndex].forEach { run ->
                paint.color = run.color
                paint.typeface = AppFonts.terminal(terminalFont, run.bold)
                canvas.drawText(run.text, x, y, paint)
                x += paint.measureText(run.text)
            }
            y += lineHeight
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaler.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> lastY = event.y
            MotionEvent.ACTION_MOVE -> if (!scaler.isInProgress) {
                val lineHeight = paint.textSize * lineHeightScale
                scrollLine = (scrollLine + (lastY - event.y) / lineHeight).coerceIn(0f, maxScroll().toFloat())
                lastY = event.y; invalidate()
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
        return (lines.size - visible).coerceAtLeast(0)
    }

    private fun updatePaint() {
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, fontSizeSp, resources.displayMetrics)
    }

    private fun parse(raw: String): List<List<Run>> {
        val result = mutableListOf<MutableList<Run>>(mutableListOf())
        val text = StringBuilder()
        var color = palette[7]
        var bold = false
        fun flush() {
            if (text.isNotEmpty()) {
                result.last() += Run(text.toString(), color, bold)
                text.clear()
            }
        }
        var i = 0
        while (i < raw.length) {
            when {
                raw[i] == '\u001B' && i + 1 < raw.length && raw[i + 1] == '[' -> {
                    flush()
                    var end = i + 2
                    while (end < raw.length && raw[end].code !in 0x40..0x7e) end++
                    if (end >= raw.length) break
                    if (raw[end] == 'm') {
                        val codes = raw.substring(i + 2, end).split(';').mapNotNull(String::toIntOrNull).ifEmpty { listOf(0) }
                        codes.forEach { code ->
                            when (code) {
                                0 -> { color = palette[7]; bold = false }
                                1 -> bold = true
                                in 30..37 -> color = palette[code - 30]
                                in 90..97 -> color = palette[code - 90 + 8]
                                39 -> color = palette[7]
                            }
                        }
                    }
                    i = end
                }
                raw[i] == '\n' -> { flush(); result.add(mutableListOf()) }
                raw[i] == '\r' -> Unit
                raw[i] == '\b' -> if (text.isNotEmpty()) text.deleteCharAt(text.lastIndex)
                raw[i].code >= 0x20 || raw[i] == '\t' -> text.append(raw[i])
            }
            i++
        }
        flush()
        return result.takeLast(4000)
    }
}

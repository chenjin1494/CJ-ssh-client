package io.github.chenjin.androidsshclient.core.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class AnsiTerminalParserTest {
    private val scheme = TerminalColorSchemes.byName("One Dark")

    @Test
    fun carriageReturnOverwritesCurrentLine() {
        val grid = AnsiTerminalParser(80, scheme).parse("old prompt\rnew")

        assertEquals("n", grid.lines[0][0]?.text)
        assertEquals("e", grid.lines[0][1]?.text)
        assertEquals("w", grid.lines[0][2]?.text)
        assertEquals(" ", grid.lines[0][3]?.text)
    }

    @Test
    fun parsesZeroRedTrueColorAndFollowingXtermBackground() {
        val grid = AnsiTerminalParser(80, scheme).parse("\u001b[38;2;0;128;255;48;5;196mX")
        val cell = grid.lines[0][0]!!

        assertEquals(0xff0080ff.toInt(), cell.style.foreground)
        assertEquals(0xffff0000.toInt(), cell.style.background)
    }

    @Test
    fun parsesColonTrueColorWithEmptyColorSpace() {
        val grid = AnsiTerminalParser(80, scheme).parse("\u001b[38:2::12:34:56mX")
        assertEquals(0xff0c2238.toInt(), grid.lines[0][0]?.style?.foreground)
    }

    @Test
    fun assignsCjkTwoCellsAndNerdGlyphOneCell() {
        val grid = AnsiTerminalParser(80, scheme).parse("A中\ue0b0B")

        assertEquals(1, grid.lines[0][0]?.width)
        assertEquals(2, grid.lines[0][1]?.width)
        assertEquals(true, grid.lines[0][2]?.continuation)
        assertEquals(1, grid.lines[0][3]?.width)
        assertEquals("B", grid.lines[0][4]?.text)
    }

    @Test
    fun keepsEmojiZwjSequenceInOneDoubleWidthCell() {
        val grid = AnsiTerminalParser(80, scheme).parse("👨‍💻X")

        assertEquals("👨‍💻", grid.lines[0][0]?.text)
        assertEquals(2, grid.lines[0][0]?.width)
        assertEquals(true, grid.lines[0][1]?.continuation)
        assertEquals("X", grid.lines[0][2]?.text)
    }

    @Test
    fun eraseLineUsesCurrentBackground() {
        val grid = AnsiTerminalParser(5, scheme).parse("\u001b[48;5;196mabc\rZ\u001b[K")

        assertEquals("Z", grid.lines[0][0]?.text)
        assertEquals(" ", grid.lines[0][1]?.text)
        assertEquals(0xffff0000.toInt(), grid.lines[0][1]?.style?.background)
    }

    @Test
    fun exactWidthThenCrLfDoesNotCreateBlankRow() {
        val grid = AnsiTerminalParser(3, scheme).parse("abc\r\nX")

        assertEquals(2, grid.lines.size)
        assertEquals("X", grid.lines[1][0]?.text)
    }

    @Test
    fun absoluteCursorAddressingIsRelativeToActiveScreen() {
        val grid = AnsiTerminalParser(10, scheme, screenRows = 2).parse("a\r\nb\r\nc\u001b[1;1HX")

        assertEquals("a", grid.lines[0][0]?.text)
        assertEquals("X", grid.lines[1][0]?.text)
        assertEquals("c", grid.lines[2][0]?.text)
    }

    @Test
    fun tracksPrivateInputModes() {
        val enabled = AnsiTerminalParser(80, scheme).parse("\u001b[?1h\u001b[?2004h")
        val disabled = AnsiTerminalParser(80, scheme).parse("\u001b[?1h\u001b[?1l\u001b[?2004h\u001b[?2004l")

        assertEquals(true, enabled.applicationCursor)
        assertEquals(true, enabled.bracketedPaste)
        assertEquals(false, disabled.applicationCursor)
        assertEquals(false, disabled.bracketedPaste)
    }

    @Test
    fun preservesPrivateModesWhenRetainedTranscriptHasNoToggle() {
        val grid = AnsiTerminalParser(
            columns = 80,
            scheme = scheme,
            initialBracketedPaste = true,
            initialApplicationCursor = true,
        ).parse("retained tail")

        assertEquals(true, grid.applicationCursor)
        assertEquals(true, grid.bracketedPaste)
    }

    @Test
    fun distinguishesSoftWrapFromHardLineBreak() {
        val grid = AnsiTerminalParser(3, scheme).parse("abcX\r\nY")

        assertEquals(false, 0 in grid.hardBreakRows)
        assertEquals(true, 1 in grid.hardBreakRows)
    }

    @Test
    fun ignoresOscWindowTitle() {
        val grid = AnsiTerminalParser(80, scheme).parse("A\u001b]0;title\u0007B")
        assertEquals("A", grid.lines[0][0]?.text)
        assertEquals("B", grid.lines[0][1]?.text)
    }
}

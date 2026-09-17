package io.github.chenjin.androidsshclient.feature.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class ExtraKeysTest {
    @Test
    fun ctrlArrowUsesXtermModifierSequence() {
        assertEquals("\u001b[1;5A", applyKeyModifiers("\u001b[A", ctrl = true, alt = false))
    }

    @Test
    fun ctrlAltArrowCombinesModifiers() {
        assertEquals("\u001b[1;7D", applyKeyModifiers("\u001bOD", ctrl = true, alt = true))
    }

    @Test
    fun pageKeysUseXtermModifierSequence() {
        assertEquals("\u001b[5;5~", applyKeyModifiers("\u001b[5~", ctrl = true, alt = false))
        assertEquals("\u001b[6;3~", applyKeyModifiers("\u001b[6~", ctrl = false, alt = true))
        assertEquals("\u001b[5;7~", applyKeyModifiers("\u001b[5~", ctrl = true, alt = true))
    }

    @Test
    fun ctrlLetterProducesControlCharacter() {
        assertEquals("\u0003", applyKeyModifiers("c", ctrl = true, alt = false))
    }
}

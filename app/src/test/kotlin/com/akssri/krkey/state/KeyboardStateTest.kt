package com.akssri.krkey.state

import com.akssri.krkey.BrahmiScript
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for KeyboardState.
 * Tests state transitions, immutability, and mode queries.
 */
class KeyboardStateTest {

    @Test
    fun `initial state is IndicNormal with NAGARI script`() {
        val state = KeyboardState()
        assertTrue(state.mode is InputMode.IndicNormal)
        assertEquals(BrahmiScript.NAGARI, state.script)
        assertEquals("", state.currentBaseChar)
        assertFalse(state.isShiftLocked)
    }

    @Test
    fun `toggleShift from IndicNormal goes to LatinNormal`() {
        val state = KeyboardState(mode = InputMode.IndicNormal)
        val newState = state.toggleShift()
        assertTrue(newState.mode is InputMode.LatinNormal)
    }

    @Test
    fun `toggleShift from LatinNormal goes back to IndicNormal`() {
        val state = KeyboardState(mode = InputMode.LatinNormal)
        val newState = state.toggleShift()
        assertTrue(newState.mode is InputMode.IndicNormal)
    }

    @Test
    fun `toggleShift from IndicSymbol goes to IndicSymbolShifted`() {
        val state = KeyboardState(mode = InputMode.IndicSymbol)
        val newState = state.toggleShift()
        assertTrue(newState.mode is InputMode.IndicSymbolShifted)
    }

    @Test
    fun `toggleShift from IndicSymbolShifted goes back to IndicSymbol`() {
        val state = KeyboardState(mode = InputMode.IndicSymbolShifted)
        val newState = state.toggleShift()
        assertTrue(newState.mode is InputMode.IndicSymbol)
    }

    @Test
    fun `toggleSymbol from IndicNormal goes to IndicSymbol`() {
        val state = KeyboardState(mode = InputMode.IndicNormal)
        val newState = state.toggleSymbol(wasLatinMode = false)
        assertTrue(newState.mode is InputMode.IndicSymbol)
    }

    @Test
    fun `toggleSymbol from LatinNormal goes to LatinSymbol`() {
        val state = KeyboardState(mode = InputMode.LatinNormal)
        val newState = state.toggleSymbol(wasLatinMode = true)
        assertTrue(newState.mode is InputMode.LatinSymbol)
    }

    @Test
    fun `toggleSymbol from IndicSymbol goes back to IndicNormal`() {
        val state = KeyboardState(mode = InputMode.IndicSymbol)
        val newState = state.toggleSymbol(wasLatinMode = false)
        assertTrue(newState.mode is InputMode.IndicNormal)
    }

    @Test
    fun `toggleSymbol from LatinSymbol goes back to LatinNormal`() {
        val state = KeyboardState(mode = InputMode.LatinSymbol)
        val newState = state.toggleSymbol(wasLatinMode = true)
        assertTrue(newState.mode is InputMode.LatinNormal)
    }

    @Test
    fun `withBaseChar updates base character`() {
        val state = KeyboardState()
        val newState = state.withBaseChar("क")
        assertEquals("क", newState.currentBaseChar)
    }

    @Test
    fun `withScript updates script`() {
        val state = KeyboardState(script = BrahmiScript.NAGARI)
        val newState = state.withScript(BrahmiScript.KANNADA)
        assertEquals(BrahmiScript.KANNADA, newState.script)
    }

    @Test
    fun `clearBaseChar clears base character`() {
        val state = KeyboardState(currentBaseChar = "क")
        val newState = state.clearBaseChar()
        assertEquals("", newState.currentBaseChar)
    }

    @Test
    fun `state is immutable - original unchanged after transformation`() {
        val originalState = KeyboardState(
            mode = InputMode.IndicNormal,
            script = BrahmiScript.NAGARI,
            currentBaseChar = "क"
        )
        val newState = originalState.toggleShift()

        // Original unchanged
        assertTrue(originalState.mode is InputMode.IndicNormal)
        assertEquals(BrahmiScript.NAGARI, originalState.script)
        assertEquals("क", originalState.currentBaseChar)

        // New state changed
        assertTrue(newState.mode is InputMode.LatinNormal)
    }

    @Test
    fun `toOldFlags converts state to legacy flags`() {
        val state = KeyboardState(
            mode = InputMode.LatinShifted,
            script = BrahmiScript.KANNADA,
            currentBaseChar = "ಕ",
            isShiftLocked = true
        )
        val flags = state.toOldFlags()

        assertTrue(flags.isLatinMode)
        assertFalse(flags.isSymbolMode)
        assertTrue(flags.isShifted)
        assertFalse(flags.isLatinSymbolMode)
        assertTrue(flags.isShiftLocked)
        assertEquals(BrahmiScript.KANNADA, flags.currentScript)
        assertEquals("ಕ", flags.currentBaseChar)
    }

    @Test
    fun `InputMode isLatin returns true for Latin modes`() {
        assertTrue(InputMode.LatinNormal.isLatin())
        assertTrue(InputMode.LatinShifted.isLatin())
        assertTrue(InputMode.LatinSymbol.isLatin())
        assertFalse(InputMode.IndicNormal.isLatin())
        assertFalse(InputMode.IndicSymbol.isLatin())
    }

    @Test
    fun `InputMode isSymbol returns true for symbol modes`() {
        assertTrue(InputMode.IndicSymbol.isSymbol())
        assertTrue(InputMode.IndicSymbolShifted.isSymbol())
        assertTrue(InputMode.LatinSymbol.isSymbol())
        assertFalse(InputMode.IndicNormal.isSymbol())
        assertFalse(InputMode.LatinNormal.isSymbol())
    }

    @Test
    fun `InputMode isShifted returns true for shifted modes`() {
        assertTrue(InputMode.IndicSymbolShifted.isShifted())
        assertTrue(InputMode.LatinShifted.isShifted())
        assertFalse(InputMode.IndicNormal.isShifted())
        assertFalse(InputMode.LatinNormal.isShifted())
        assertFalse(InputMode.IndicSymbol.isShifted())
    }
}

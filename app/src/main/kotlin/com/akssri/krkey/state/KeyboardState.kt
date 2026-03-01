package com.akssri.krkey.state

import com.akssri.krkey.BrahmiScript

/**
 * Immutable keyboard state.
 * Replaces scattered boolean flags with a cohesive state object.
 * All transformations return new instances.
 */
data class KeyboardState(
    val mode: InputMode = InputMode.IndicNormal,
    val script: BrahmiScript = BrahmiScript.NAGARI,
    val currentBaseChar: String = "",
    val isShiftLocked: Boolean = false
) {
    /**
     * Toggles between Indic and Latin normal modes.
     */
    fun toggleLanguage(): KeyboardState {
        val newMode = when (mode) {
            is InputMode.IndicNormal, is InputMode.IndicSymbol, is InputMode.IndicSymbolShifted -> InputMode.LatinNormal
            is InputMode.LatinNormal, is InputMode.LatinShifted, is InputMode.LatinSymbol -> InputMode.IndicNormal
        }
        return copy(mode = newMode, isShiftLocked = false)
    }

    /**
     * Toggles shift behavior (capitalization in Latin, secondary symbols in Symbol mode).
     */
    fun toggleShift(): KeyboardState {
        val newMode = when (mode) {
            is InputMode.IndicSymbol -> InputMode.IndicSymbolShifted
            is InputMode.IndicSymbolShifted -> InputMode.IndicSymbol
            is InputMode.LatinNormal -> InputMode.LatinShifted
            is InputMode.LatinShifted -> InputMode.LatinNormal
            is InputMode.LatinSymbol -> InputMode.LatinSymbol // Usually no shift in Latin symbols, but can add if needed
            is InputMode.IndicNormal -> InputMode.IndicNormal // No shift in Indic base layout
        }
        return copy(mode = newMode, isShiftLocked = false)
    }

    /**
     * Toggle symbol mode.
     * Remembers whether we came from Latin or Indic mode.
     */
    fun toggleSymbol(wasLatinMode: Boolean = mode.isLatin()): KeyboardState {
        val newMode = when (mode) {
            is InputMode.IndicNormal, is InputMode.LatinNormal, is InputMode.LatinShifted ->
                if (wasLatinMode) InputMode.LatinSymbol else InputMode.IndicSymbol
            is InputMode.IndicSymbol, is InputMode.IndicSymbolShifted ->
                InputMode.IndicNormal
            is InputMode.LatinSymbol ->
                if (wasLatinMode) InputMode.LatinNormal else InputMode.IndicNormal
        }
        return copy(mode = newMode, isShiftLocked = false)
    }

    /**
     * Update the base character for composition (e.g., consonant for matra attachment).
     */
    fun withBaseChar(baseChar: String): KeyboardState {
        return copy(currentBaseChar = baseChar)
    }

    /**
     * Switch to a different Brahmi script.
     */
    fun withScript(script: BrahmiScript): KeyboardState {
        return copy(script = script)
    }

    /**
     * Clear the base character (typically after committing text).
     */
    fun clearBaseChar(): KeyboardState {
        return copy(currentBaseChar = "")
    }

    // Legacy compatibility helpers - convert to old boolean flags
    fun toOldFlags() = OldFlags(
        isLatinMode = mode.isLatin(),
        isSymbolMode = mode.isSymbol(),
        isLatinSymbolMode = mode is InputMode.LatinSymbol,
        isShifted = mode.isShifted(),
        isShiftLocked = isShiftLocked,
        currentScript = script,
        currentBaseChar = currentBaseChar
    )

    data class OldFlags(
        val isLatinMode: Boolean,
        val isSymbolMode: Boolean,
        val isLatinSymbolMode: Boolean,
        val isShifted: Boolean,
        val isShiftLocked: Boolean,
        val currentScript: BrahmiScript,
        val currentBaseChar: String
    )
}

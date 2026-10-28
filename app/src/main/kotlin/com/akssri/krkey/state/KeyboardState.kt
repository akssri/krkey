/*
 *  Copyright (c) 2026 Akshay Srinivasan <akssri@vakra.xyz>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.akssri.krkey.state

import com.akssri.krkey.BrahmiScript

/**
 * Immutable keyboard state.
 */
data class KeyboardState(
    val mode: InputMode = InputMode.IndicNormal,
    val script: BrahmiScript = BrahmiScript.DEVANAGARI,
    val currentBaseChar: String = "",
    val isShiftLocked: Boolean = false,
) {
    fun toggleLanguage(): KeyboardState {
        val newMode =
            when (mode) {
                is InputMode.IndicNormal -> InputMode.LatinNormal
                is InputMode.LatinNormal -> InputMode.IndicNormal
                is InputMode.Symbol -> if (mode.fromLatin) InputMode.IndicNormal else InputMode.LatinNormal
                is InputMode.SymbolShifted -> if (mode.fromLatin) InputMode.IndicNormal else InputMode.LatinNormal
                is InputMode.Numeric -> mode
            }
        return copy(mode = newMode, isShiftLocked = false)
    }

    fun toggleShift(): KeyboardState {
        val newMode =
            when (mode) {
                is InputMode.Symbol -> InputMode.SymbolShifted(mode.fromLatin)
                is InputMode.SymbolShifted -> InputMode.Symbol(mode.fromLatin)
                else -> mode
            }
        return copy(mode = newMode, isShiftLocked = false)
    }

    fun toggleSymbol(wasLatinMode: Boolean = mode.isLatin()): KeyboardState {
        val newMode =
            when (mode) {
                is InputMode.IndicNormal, is InputMode.LatinNormal -> InputMode.Symbol(wasLatinMode)

                is InputMode.Symbol -> if (mode.fromLatin) InputMode.LatinNormal else InputMode.IndicNormal

                is InputMode.SymbolShifted -> if (mode.fromLatin) InputMode.LatinNormal else InputMode.IndicNormal

                // The keypad has no symbol key; it is left by leaving the field
                is InputMode.Numeric -> mode
            }

        return copy(mode = newMode, isShiftLocked = false)
    }

    /** Switches to the numeric keypad, remembering which full keyboard to return to. */
    fun withNumericKeypad(phone: Boolean): KeyboardState {
        val fromLatin = if (mode is InputMode.Numeric) mode.fromLatin else mode.isLatin()
        return copy(mode = InputMode.Numeric(phone, fromLatin), isShiftLocked = false)
    }

    /** Leaves the numeric keypad (e.g. on moving to a text field); other modes are kept. */
    fun withoutNumericKeypad(): KeyboardState {
        if (mode !is InputMode.Numeric) return this
        return copy(mode = if (mode.fromLatin) InputMode.LatinNormal else InputMode.IndicNormal)
    }

    fun withBaseChar(baseChar: String) = copy(currentBaseChar = baseChar)

    fun withScript(script: BrahmiScript) = copy(script = script)

    fun clearBaseChar() = copy(currentBaseChar = "")
}

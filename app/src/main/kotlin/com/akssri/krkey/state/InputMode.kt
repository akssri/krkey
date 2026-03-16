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

/**
 * Layer indices.
 */
object KeyboardLayer {
    const val INDIC = 0
    const val LATIN = 1
    const val SYM = 2
    const val SYM_SHIFT = 3
}

/**
 * Represents the active keyboard layer and return-path metadata.
 */
sealed class InputMode(val layer: Int) {
    data object IndicNormal : InputMode(KeyboardLayer.INDIC)

    data object LatinNormal : InputMode(KeyboardLayer.LATIN)

    data class Symbol(val fromLatin: Boolean) : InputMode(KeyboardLayer.SYM)

    data class SymbolShifted(val fromLatin: Boolean) : InputMode(KeyboardLayer.SYM_SHIFT)

    fun isLatin() = this is LatinNormal || (this is Symbol && fromLatin) || (this is SymbolShifted && fromLatin)

    fun isSymbol() = this is Symbol || this is SymbolShifted

    fun isShifted() = this is SymbolShifted
}

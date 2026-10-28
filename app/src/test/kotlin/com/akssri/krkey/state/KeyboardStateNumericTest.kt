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

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardStateNumericTest {
    @Test
    fun keypadReturnsToTheKeyboardItCameFrom() {
        val indic = KeyboardState(mode = InputMode.IndicNormal).withNumericKeypad(phone = false)
        assertEquals(InputMode.Numeric(phone = false, fromLatin = false), indic.mode)
        assertEquals(indic.mode, indic.toggleSymbol().mode) // no way off the keypad but leaving the field
        assertEquals(InputMode.IndicNormal, indic.withoutNumericKeypad().mode)

        val latin = KeyboardState(mode = InputMode.LatinNormal).withNumericKeypad(phone = true)
        assertEquals(InputMode.Numeric(phone = true, fromLatin = true), latin.mode)
        assertEquals(InputMode.LatinNormal, latin.withoutNumericKeypad().mode)
    }

    @Test
    fun keypadFromSymbolLayerRemembersLatin() {
        val state = KeyboardState(mode = InputMode.Symbol(fromLatin = true)).withNumericKeypad(phone = false)
        assertEquals(InputMode.LatinNormal, state.withoutNumericKeypad().mode)
    }

    @Test
    fun switchingKeypadKindKeepsTheReturnKeyboard() {
        val state =
            KeyboardState(mode = InputMode.LatinNormal)
                .withNumericKeypad(phone = false)
                .withNumericKeypad(phone = true)
        assertEquals(InputMode.Numeric(phone = true, fromLatin = true), state.mode)
    }

    @Test
    fun leavingIsANoOpOutsideTheKeypad() {
        val state = KeyboardState(mode = InputMode.Symbol(fromLatin = false))
        assertEquals(state, state.withoutNumericKeypad())
        assertEquals(InputMode.IndicNormal.layer, KeyboardLayer.INDIC)
        assertEquals(KeyboardLayer.NUMBER, InputMode.Numeric(false, false).layer)
        assertEquals(KeyboardLayer.PHONE, InputMode.Numeric(true, false).layer)
    }
}

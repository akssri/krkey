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

package com.akssri.krkey

import com.akssri.krkey.state.KeyboardLayer
import org.junit.Assert.assertEquals
import org.junit.Test

class FlickKeyLayoutTest {
    private fun bottomRowFlickKeys(script: BrahmiScript): List<FlickKey> =
        ScriptManager.getScriptData(script).layers.getValue(KeyboardLayer.INDIC).last().filterIsInstance<FlickKey>()

    @Test
    fun devanagariKeepsDanda() {
        val (comma, danda) = bottomRowFlickKeys(BrahmiScript.DEVANAGARI)
        assertEquals(COMMA_KEY, comma)
        assertEquals("\u0964", danda.base)
        assertEquals("-", danda.right)
    }

    @Test
    fun scriptsWithoutDandaUseAPeriod() {
        // Kannada writes the danda as "."
        val (_, danda) = bottomRowFlickKeys(BrahmiScript.KANNADA)
        assertEquals(".", danda.base)
        assertEquals("-", danda.right)
        assertEquals("?", danda.up)
        assertEquals("!", danda.left)
        assertEquals("", danda.down) // no down flicks on the bottom row
    }

    @Test
    fun latinAndTamilUsePeriodKey() {
        val latin = ScriptManager.getScriptData(BrahmiScript.DEVANAGARI).layers.getValue(KeyboardLayer.LATIN).last().filterIsInstance<FlickKey>()
        assertEquals(listOf(COMMA_KEY, PERIOD_KEY), latin)
        assertEquals(listOf(COMMA_KEY, PERIOD_KEY), bottomRowFlickKeys(BrahmiScript.TAMIL))
    }
}

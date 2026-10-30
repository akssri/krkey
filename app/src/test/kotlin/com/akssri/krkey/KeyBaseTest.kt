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

import org.junit.Assert.assertEquals
import org.junit.Test

// Escapes: KA 0915, SSA 0937, DDA 0921, TA 0924, RA 0930, YA 092F, SA 0938, VIRAMA 094D,
// NUKTA 093C, AA-sign 093E, A 0905, ZWNJ 200C; Kannada KA 0C95, SSA 0CB7, VIRAMA 0CCD
class KeyBaseTest {
    private val deva = ScriptManager.getScriptData(BrahmiScript.DEVANAGARI)

    @Test
    fun conjunctIsTheBase() {
        assertEquals("क्ष", deva.keyBase("x क्ष"))
        assertEquals("क्षा", formatKeyText("आ", "क्ष", deva))
    }

    @Test
    fun singleConsonantAndNukta() {
        assertEquals("क", deva.keyBase("abc क"))
        assertEquals("ड़", deva.keyBase("ड़"))
        assertEquals("ड़ा", formatKeyText("आ", "ड़", deva))
    }

    @Test
    fun noBaseAfterViramaOrNothing() {
        assertEquals("", deva.keyBase("क्"))
        assertEquals("", deva.keyBase(""))
        assertEquals("", deva.keyBase("abc "))
    }

    @Test
    fun nuktaOnAnyConsonant() {
        // f-r: nukta on the first consonant (PHA 092B, RA 0930)
        assertEquals("\u092B\u093C\u094D\u0930", deva.keyBase("\u092B\u093C\u094D\u0930"))
        // izzat: nukta on both consonants of the doubled z (JA 091C)
        assertEquals("\u091C\u093C\u094D\u091C\u093C", deva.keyBase("\u0907\u091C\u093C\u094D\u091C\u093C"))
        // a nukta on a later consonant only
        assertEquals("\u092B\u094D\u0930\u093C", deva.keyBase("\u092B\u094D\u0930\u093C"))
    }

    @Test
    fun zwnjBreaksTheCluster() {
        assertEquals("ष", deva.keyBase("क्‌ष"))
    }

    @Test
    fun clusterIsCappedAtFourConsonants() {
        val five = "स्त्र्य्क"
        assertEquals("त्र्य्क", deva.keyBase(five))
    }

    @Test
    fun trailingVowelIsABaseForMarks() {
        assertEquals("अ", deva.keyBase("अ"))
    }

    @Test
    fun otherScriptsUseTheirOwnVirama() {
        val kannada = ScriptManager.getScriptData(BrahmiScript.KANNADA)
        assertEquals("ಕ್ಷ", kannada.keyBase("ಕ್ಷ"))
    }
}

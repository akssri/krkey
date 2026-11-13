/*
 *  Copyright (c) 2026 Akshay Srinivasan <akssri@vakra.xyz>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.akssri.krkey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IndicClusterUtilsTest {
    private val deva = ScriptManager.getScriptData(BrahmiScript.DEVANAGARI)
    private val kannada = ScriptManager.getScriptData(BrahmiScript.KANNADA)
    private val sharada = ScriptManager.getScriptData(BrahmiScript.SHARADA)

    private fun extractAksharas(
        text: String,
        scriptData: ScriptData,
    ): List<String> {
        return IndicClusterUtils.findAksharas(text, scriptData).map { range ->
            text.substring(range)
        }
    }

    @Test
    fun testDevanagariSegmentation() {
        assertEquals(listOf("न", "म", "स्ते"), extractAksharas("नमस्ते", deva))
        assertEquals(listOf("कृ", "ष्णा"), extractAksharas("कृष्णा", deva))
        assertEquals(listOf("स्त्री"), extractAksharas("स्त्री", deva))
        assertEquals(listOf("स्थि", "ति"), extractAksharas("स्थिति", deva))
        assertEquals(listOf("वि", "द्वा", "न्"), extractAksharas("विद्वान्", deva))
        assertEquals(listOf("ल", "ड़", "की"), extractAksharas("लड़की", deva))
        assertEquals(listOf("दुः", "ख"), extractAksharas("दुःख", deva))
    }

    @Test
    fun testKannadaSegmentation() {
        assertEquals(listOf("ಕ", "ನ್ನ", "ಡ"), extractAksharas("ಕನ್ನಡ", kannada))
        assertEquals(listOf("ಸ್ತ್ರೀ"), extractAksharas("ಸ್ತ್ರೀ", kannada))
    }

    @Test
    fun testNonBmpSharadaSegmentation() {
        assertEquals(listOf("𑆯𑆳", "𑆫", "𑆢𑆳"), extractAksharas("𑆯𑆳𑆫𑆢𑆳", sharada))
        assertEquals(listOf("𑆑𑆳", "𑆯𑆴", "𑆫𑆵"), extractAksharas("𑆑𑆳𑆯𑆴𑆫𑆵", sharada))
    }

    @Test
    fun testTrailingAkshara() {
        assertEquals("स्ते", IndicClusterUtils.getTrailingAkshara("नमस्ते", deva))
        assertEquals("म", IndicClusterUtils.getTrailingAkshara("नम", deva))
        assertEquals("न", IndicClusterUtils.getTrailingAkshara("न", deva))
        assertEquals("", IndicClusterUtils.getTrailingAkshara("", deva))
    }

    @Test
    fun testLeadingAkshara() {
        assertEquals("न", IndicClusterUtils.getLeadingAkshara("नमस्ते", deva))
        assertEquals("म", IndicClusterUtils.getLeadingAkshara("मस्ते", deva))
        assertEquals("स्ते", IndicClusterUtils.getLeadingAkshara("स्ते", deva))
        assertEquals("", IndicClusterUtils.getLeadingAkshara("", deva))
        // When cursor is inside an akshara (leading combining marks)
        assertEquals("ो", IndicClusterUtils.getLeadingAkshara("ो", deva))
        assertEquals("्", IndicClusterUtils.getLeadingAkshara("्ते", deva))
    }

    @Test
    fun testConjunctClusterDetection() {
        assertTrue(IndicClusterUtils.isConjunctCluster("स्ते", deva))
        assertTrue(IndicClusterUtils.isConjunctCluster("स्त्री", deva))
        assertFalse(IndicClusterUtils.isConjunctCluster("न्", deva)) // lone trailing halant
        assertFalse(IndicClusterUtils.isConjunctCluster("क", deva))
        assertFalse(IndicClusterUtils.isConjunctCluster("का", deva))
        assertFalse(IndicClusterUtils.isConjunctCluster("स्\u200C", deva)) // explicit halant with ZWNJ
    }

    @Test
    fun testClusterExpansionAndCollapse() {
        val expanded = IndicClusterUtils.expandCluster("स्ते", deva)
        assertEquals("स्\u200Cते", expanded)
        assertEquals("स्ते", IndicClusterUtils.collapseCluster(expanded))

        val multiExpanded = IndicClusterUtils.expandCluster("स्त्री", deva)
        assertEquals("स्\u200Cत्\u200Cरी", multiExpanded)
        assertEquals("स्त्री", IndicClusterUtils.collapseCluster(multiExpanded))

        // Expanded cluster segments into visible sub-components
        assertEquals(listOf("स्\u200C", "ते"), extractAksharas(expanded, deva))
        assertEquals(listOf("स्\u200C", "त्\u200C", "री"), extractAksharas(multiExpanded, deva))
    }

    @Test
    fun testLeadingCombiningMarksLength() {
        assertEquals(0, IndicClusterUtils.getLeadingCombiningMarksLength("स्ते", deva))
        assertEquals(1, IndicClusterUtils.getLeadingCombiningMarksLength("ा", deva))
        assertEquals(1, IndicClusterUtils.getLeadingCombiningMarksLength("्", deva))
        // In "्ते", virama (1) + ta (consonant) -> length of leading combining marks is 1 (virama only)
        assertEquals(1, IndicClusterUtils.getLeadingCombiningMarksLength("्ते", deva))
    }

    @Test
    fun testBackspaceLength() {
        // Marks come off first, one at a time
        assertEquals(1, IndicClusterUtils.getBackspaceLength("का", deva))
        assertEquals(1, IndicClusterUtils.getBackspaceLength("किं", deva))
        assertEquals(1, IndicClusterUtils.getBackspaceLength("क्षि", deva))
        // Then the whole consonant or conjunct
        assertEquals(1, IndicClusterUtils.getBackspaceLength("क", deva))
        assertEquals(3, IndicClusterUtils.getBackspaceLength("नक्ष", deva))
        assertEquals(5, IndicClusterUtils.getBackspaceLength("स्त्र", deva))
        assertEquals(2, IndicClusterUtils.getBackspaceLength("ज़", deva))
        // A half-consonant, with its ZWNJ when expanded
        assertEquals(2, IndicClusterUtils.getBackspaceLength("नमस्", deva))
        assertEquals(3, IndicClusterUtils.getBackspaceLength("नमस्\u200C", deva))
        // A ZWNJ not after a virama, other text, and an empty text
        assertEquals(1, IndicClusterUtils.getBackspaceLength("abc\u200C", deva))
        assertEquals(1, IndicClusterUtils.getBackspaceLength("abc", deva))
        assertEquals(0, IndicClusterUtils.getBackspaceLength("", deva))
    }
}

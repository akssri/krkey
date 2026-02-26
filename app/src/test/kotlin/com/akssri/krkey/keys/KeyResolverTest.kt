package com.akssri.krkey.keys

import com.akssri.krkey.BrahmiScript
import com.akssri.krkey.R
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for KeyResolver implementations.
 * Tests VowelKeyResolver, ModifierKeyResolver, SimpleKeyResolver.
 */
class KeyResolverTest {

    @Test
    fun `SimpleKeyResolver returns base text in Indic mode`() {
        val resolver = SimpleKeyResolver()
        val data = KeyData(R.id.r4c2, "/", "'")
        val (base, flick) = resolver.resolve(
            data, isLatin = false, isSymbol = false,
            isShifted = false, isLatinSymbol = false,
            currentBaseChar = "", script = BrahmiScript.NAGARI
        )
        assertEquals("/", base)
        assertEquals("'", flick)
    }

    @Test
    fun `SimpleKeyResolver returns Latin text in Latin mode`() {
        val resolver = SimpleKeyResolver()
        val data = KeyData(
            R.id.r1c2, "क", "ख",
            KeyVariants(latinBase = "w", latinFlick = "W")
        )
        val (base, flick) = resolver.resolve(
            data, isLatin = true, isSymbol = false,
            isShifted = false, isLatinSymbol = false,
            currentBaseChar = "", script = BrahmiScript.NAGARI
        )
        assertEquals("w", base)
        assertEquals("W", flick)
    }

    @Test
    fun `SimpleKeyResolver handles Latin shift mode`() {
        val resolver = SimpleKeyResolver()
        val data = KeyData(
            R.id.r1c2, "क", "ख",
            KeyVariants(latinBase = "w", latinFlick = "W")
        )
        val (base, flick) = resolver.resolve(
            data, isLatin = true, isSymbol = false,
            isShifted = true, isLatinSymbol = false,
            currentBaseChar = "", script = BrahmiScript.NAGARI
        )
        assertEquals("W", base)
        assertEquals("w", flick)
    }

    @Test
    fun `SimpleKeyResolver returns symbol text in symbol mode`() {
        val resolver = SimpleKeyResolver()
        val data = KeyData(
            R.id.r1c2, "क", "ख",
            KeyVariants(symBase = "२", symFlick = "2")
        )
        val (base, flick) = resolver.resolve(
            data, isLatin = false, isSymbol = true,
            isShifted = false, isLatinSymbol = false,
            currentBaseChar = "", script = BrahmiScript.NAGARI
        )
        assertEquals("२", base)
        assertEquals("2", flick)
    }

    @Test
    fun `VowelKeyResolver shows standalone vowel without base`() {
        val resolver = VowelKeyResolver()
        val data = KeyData(
            R.id.r2c1, "उ", "ऊ",
            KeyVariants(matraBase = "ु", matraFlick = "ू")
        )
        val (base, flick) = resolver.resolve(
            data, isLatin = false, isSymbol = false,
            isShifted = false, isLatinSymbol = false,
            currentBaseChar = "", script = BrahmiScript.NAGARI
        )
        assertEquals("उ", base)
        assertEquals("ऊ", flick)
    }

    @Test
    fun `VowelKeyResolver shows matra with consonant base`() {
        val resolver = VowelKeyResolver()
        val data = KeyData(
            R.id.r2c1, "उ", "ऊ",
            KeyVariants(matraBase = "ु", matraFlick = "ू")
        )
        val (base, flick) = resolver.resolve(
            data, isLatin = false, isSymbol = false,
            isShifted = false, isLatinSymbol = false,
            currentBaseChar = "क", script = BrahmiScript.NAGARI
        )
        assertEquals("कु", base)
        assertEquals("कू", flick)
    }

    @Test
    fun `VowelKeyResolver shows standalone vowel with non-consonant base`() {
        val resolver = VowelKeyResolver()
        val data = KeyData(
            R.id.r2c1, "उ", "ऊ",
            KeyVariants(matraBase = "ु", matraFlick = "ू")
        )
        // "अ" is a vowel, not consonant
        val (base, flick) = resolver.resolve(
            data, isLatin = false, isSymbol = false,
            isShifted = false, isLatinSymbol = false,
            currentBaseChar = "अ", script = BrahmiScript.NAGARI
        )
        assertEquals("उ", base)
        assertEquals("ऊ", flick)
    }

    @Test
    fun `VowelKeyResolver works in symbol mode`() {
        val resolver = VowelKeyResolver()
        val data = KeyData(
            R.id.r2c1, "उ", "ऊ",
            KeyVariants(matraBase = "ु", matraFlick = "ू", symBase = "*", symFlick = "`")
        )
        val (base, flick) = resolver.resolve(
            data, isLatin = false, isSymbol = true,
            isShifted = false, isLatinSymbol = false,
            currentBaseChar = "क", script = BrahmiScript.NAGARI
        )
        assertEquals("*", base)
        assertEquals("`", flick)
    }

    @Test
    fun `VowelKeyResolver works in Latin mode`() {
        val resolver = VowelKeyResolver()
        val data = KeyData(
            R.id.r2c1, "उ", "ऊ",
            KeyVariants(
                matraBase = "ु", matraFlick = "ू",
                latinBase = "a", latinFlick = "A"
            )
        )
        val (base, flick) = resolver.resolve(
            data, isLatin = true, isSymbol = false,
            isShifted = false, isLatinSymbol = false,
            currentBaseChar = "", script = BrahmiScript.NAGARI
        )
        assertEquals("a", base)
        assertEquals("A", flick)
    }

    @Test
    fun `ModifierKeyResolver prefixes with base char`() {
        val resolver = ModifierKeyResolver()
        val data = KeyData(R.id.r3c3, "ं", "ँ")
        val (base, flick) = resolver.resolve(
            data, isLatin = false, isSymbol = false,
            isShifted = false, isLatinSymbol = false,
            currentBaseChar = "क", script = BrahmiScript.NAGARI
        )
        assertEquals("कं", base)
        assertEquals("कँ", flick)
    }

    @Test
    fun `ModifierKeyResolver prefixes with dotted circle without base`() {
        val resolver = ModifierKeyResolver()
        val data = KeyData(R.id.r3c3, "ं", "ँ")
        val (base, flick) = resolver.resolve(
            data, isLatin = false, isSymbol = false,
            isShifted = false, isLatinSymbol = false,
            currentBaseChar = "", script = BrahmiScript.NAGARI
        )
        assertEquals("◌ं", base)
        assertEquals("◌ँ", flick)
    }

    @Test
    fun `ModifierKeyResolver works in symbol mode`() {
        val resolver = ModifierKeyResolver()
        val data = KeyData(
            R.id.r3c3, "ं", "ँ",
            KeyVariants(symBase = "़", symFlick = "ॐ")
        )
        val (base, flick) = resolver.resolve(
            data, isLatin = false, isSymbol = true,
            isShifted = false, isLatinSymbol = false,
            currentBaseChar = "क", script = BrahmiScript.NAGARI
        )
        assertEquals("़", base)
        assertEquals("ॐ", flick)
    }

    @Test
    fun `ModifierKeyResolver works in Latin mode`() {
        val resolver = ModifierKeyResolver()
        val data = KeyData(
            R.id.r3c3, "ं", "ँ",
            KeyVariants(latinBase = "x", latinFlick = "X")
        )
        val (base, flick) = resolver.resolve(
            data, isLatin = true, isSymbol = false,
            isShifted = false, isLatinSymbol = false,
            currentBaseChar = "", script = BrahmiScript.NAGARI
        )
        assertEquals("x", base)
        assertEquals("X", flick)
    }

    @Test
    fun `ConsonantKeyResolver returns base text`() {
        val resolver = ConsonantKeyResolver()
        val data = KeyData(R.id.r1c2, "क", "ख")
        val (base, flick) = resolver.resolve(
            data, isLatin = false, isSymbol = false,
            isShifted = false, isLatinSymbol = false,
            currentBaseChar = "", script = BrahmiScript.NAGARI
        )
        assertEquals("क", base)
        assertEquals("ख", flick)
    }

    @Test
    fun `Key factory methods create correct resolver types`() {
        val simpleKey = Key.simple(R.id.r4c2, "/", "'")
        val vowelKey = Key.vowel(R.id.r2c1, "उ", "ऊ")
        val modifierKey = Key.modifier(R.id.r3c3, "ं", "ँ")
        val consonantKey = Key.consonant(R.id.r1c2, "क", "ख")

        // Can't directly test resolver types (private), but can test behavior
        val (base1, _) = simpleKey.resolve(
            false, false, false, false, "", BrahmiScript.NAGARI
        )
        assertEquals("/", base1)

        val (base2, _) = vowelKey.resolve(
            false, false, false, false, "", BrahmiScript.NAGARI
        )
        assertEquals("उ", base2)

        val (base3, _) = modifierKey.resolve(
            false, false, false, false, "", BrahmiScript.NAGARI
        )
        assertEquals("◌ं", base3)

        val (base4, _) = consonantKey.resolve(
            false, false, false, false, "", BrahmiScript.NAGARI
        )
        assertEquals("क", base4)
    }
}

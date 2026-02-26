package com.akssri.krkey.keys

import com.akssri.krkey.BrahmiScript
import com.akssri.krkey.KeyType

/**
 * Key wrapper combining data and resolution logic.
 * Factory methods provide clean API for key creation.
 */
data class Key(
    val data: KeyData,
    val type: KeyType,
    private val resolver: KeyResolver
) {
    /**
     * Resolve key to display/commit text based on current state.
     */
    fun resolve(
        isLatin: Boolean,
        isSymbol: Boolean,
        isShifted: Boolean,
        isLatinSymbol: Boolean,
        currentBaseChar: String,
        script: BrahmiScript
    ): Pair<String, String> {
        return resolver.resolve(data, isLatin, isSymbol, isShifted, isLatinSymbol, currentBaseChar, script)
    }

    companion object {
        /**
         * Simple key - punctuation, symbols, etc.
         */
        fun simple(
            id: Int,
            base: String,
            flick: String,
            variants: KeyVariants = KeyVariants()
        ): Key {
            return Key(
                data = KeyData(id, base, flick, variants),
                type = KeyType.SIMPLE,
                resolver = SimpleKeyResolver()
            )
        }

        /**
         * Vowel key - shows matra form after consonant.
         */
        fun vowel(
            id: Int,
            base: String,
            flick: String,
            variants: KeyVariants = KeyVariants()
        ): Key {
            return Key(
                data = KeyData(id, base, flick, variants),
                type = KeyType.VOWEL,
                resolver = VowelKeyResolver()
            )
        }

        /**
         * Modifier key - prefixes with base char or dotted circle.
         */
        fun modifier(
            id: Int,
            base: String,
            flick: String,
            variants: KeyVariants = KeyVariants()
        ): Key {
            return Key(
                data = KeyData(id, base, flick, variants),
                type = KeyType.MODIFIER,
                resolver = ModifierKeyResolver()
            )
        }

        /**
         * Consonant key - standard Brahmi consonant.
         */
        fun consonant(
            id: Int,
            base: String,
            flick: String,
            variants: KeyVariants = KeyVariants()
        ): Key {
            return Key(
                data = KeyData(id, base, flick, variants),
                type = KeyType.CONSONANT,
                resolver = ConsonantKeyResolver()
            )
        }
    }
}

# KrKey Android - Project Context & History

This file serves as a memory and context document for AI assistants (like Gemini) working on this project. It outlines the overall goal, strict architectural constraints, key domain knowledge, and a history of major decisions.

## Overall Goal
Create an extensible Devanagari/Brahmi-derived IME for Android using Japanese-style flick gestures (up-direction only) and integrated Latin swipe typing. The UI must utilize a declarative QMK-style layered architecture (inspired by a Sailfish OS QML prototype).

## Active Constraints & Architectural Mandates
- **Direct Commit Model:** Use `commitText` only (no `setComposingText` spans) for pecked text to bypass buggy third-party webview interactions (e.g., Perplexity AI text doubling). Replacements from the `CandidateView` MUST use `beginBatchEdit()` and `endBatchEdit()` to ensure atomic deletion and insertion without leaving duplicate partial words.
- **Multi-Touch Architecture:** Touch events are tracked using a dictionary of `PointerState` objects keyed by `pointerId`. No single-pointer state variables are allowed, ensuring fast two-thumb pecking doesn't drop keystrokes.
- **Exclusive Swipe Priority:** If a gesture (swipe) begins in Latin mode, it takes absolute priority. Existing concurrent touch-downs are marked as `canceled`, and new touch-downs are ignored until the swipe is complete to prevent accidental garbage characters during gesture typing.
- **QMK-Style Layers:** Layers are independent 2D lists. All character keys, including punctuation (comma/period), are pure `Pair(Base, Flick)` tuples defined directly in the layer grids in `KeyMap.kt`.
- **Latin Layer Casing:** When dynamically applying casing in Latin mode (base=lower, flick=upper), the logic MUST verify `base.first().isLetter()`. Otherwise, punctuation tuples (e.g., `,` to `'`) are erroneously overwritten and appear blank.
- **Smart Auto-Spacing:** Use a "prepending" model `needsPrecedingSpace(skipCount)`. The keyboard checks text before the cursor (ignoring pending deletions) and prepends a space to predicted words unless it's the start of a field, right after a newline, or right after sentence-ending punctuation (`.?!।॥`). Gesture backspacing cleanly undoes this via `lastGestureHadSpace`.

## Key Domain Knowledge
- **Build Command:** `cd krkey-android && ./gradlew assembleDebug`
- **Linting & Formatting:** `cd krkey-android && ./gradlew ktlintFormat`. An `.editorconfig` file is present to disable `max_line_length`, `property-naming`, and `no-wildcard-imports` so the declarative layout grids remain easily readable.
- **Unicode Prediction:** `WordPredictor.kt` operates entirely on Unicode Code Points (`Int`) rather than UTF-16 `Char`s. This is strictly mandatory for supporting non-BMP Brahmi scripts via surrogate pairs.
- **Spacebar Cursor:** Horizontal dragging on the spacebar calculates step increments (`SPACE_DRAG_STEP_DP` = 20f) and sends synthetic `DPAD_LEFT` or `DPAD_RIGHT` `KeyEvent`s to the `InputConnection`.
- **Lifecycle Safety:** `currentLayoutGrid` must be reset to `null` in `onCreateInputView` to force a view-tag rebuild. Otherwise, Android's memory caching of the Service skips population, resulting in blank keyfaces.
- **Language-Specific Hacks:**
  - **Dravidian Vowels:** In Kannada, Telugu, and Malayalam, the short "E/O" and long "E/O" tuples are explicitly swapped post-transliteration because short vowels are vastly more frequent in Dravidian languages.
  - **Tamil Layout:** Tamil requires a heavily customized, non-Devanagari 1:1 grid (`TAMIL_INDIC_GRID`). It is ergonomically designed based on EMILLE corpus consonant frequencies to maximize left/right thumb alternation (vowels clustered left, high-freq consonants like க, த, ர clustered right).

## Major Artifact History & Milestones
- **Multi-Touch Migration:** Transitioned from single `x/y` tracking to `activePointers` map to solve fast-typing dropped key bugs. Added gesture exclusion to prevent pecking while swiping.
- **Cursor Control:** Built the spacebar swipe-to-move-cursor mechanism.
- **Auto-Space Overhaul:** Ripped out trailing space auto-inserts in favor of smart prepending spaces for candidate/gesture commits, fixing punctuation clash bugs.
- **Shift Toggle:** Converted standard shift into a dynamic script-switch toggle that reads the native `firstSyllable` of the active script to act as a UI indicator.
- **Declarative Punctuation:** Removed hardcoded `SpecialKey` enums for punctuation, moving them into the `KeyMap.kt` pure tuples.
- **Tamil Ergonomics:** Wrote a Python script to parse the csil-emille dataset for Tamil consonant frequencies, proving the efficacy of the right-heavy consonant clustering in `TAMIL_INDIC_GRID`.

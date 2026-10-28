# krkey-android — review findings (2026-10-21)

These come from reading the code only, not from a build or a device test. Bugs 1 and 5 need checking on a real device.

## Bugs

- [x] **1. Touches on special keys are mishandled during multi-touch.** `KrKeyIME.kt` `handleGlobalTouch` (~L584) and the space/backspace listeners (~L267–348).
  - `FlickKeyView` doesn't claim touches, so the frame's listener takes them. Once the first finger lands on a letter key, Android sends every later finger to the frame too.
  - If one of those fingers lands on space or backspace, `findSpecialKeyAt` returns `false` and the tap is dropped.
  - It goes wrong the other way too. If space is held first, a second finger on a letter key gets routed to the space button. Space's listener only handles `ACTION_UP`, not `ACTION_POINTER_UP`, so whichever finger lifts last fires the space.
  - Result: fast two-thumb typing loses spaces.
  - Fix: handle special-key hits inside `handleGlobalTouch`, so no touch ever goes to the buttons.
  - Done 2026-10-22: special keys are tracked per pointer in `handleGlobalTouch` (the buttons no longer take touches), with AOSP-style rollover: a new finger commits older taps first. Reported as lost spaces in split landscape.

- [ ] **2. Hindi: you can't add a vowel sign (maatraa) after a nukta.** `KeyMap.kt` `DEVA_CONSONANTS` (~L223), `KrKeyIME.updateBase` (~L743).
  - `DEVA_CONSONANTS` lists `"ढ"` (U+0922) twice. The second one was probably meant to be ढ़ (U+095D), which is currently missing.
  - Typing ड then the ़ key leaves ़ (U+093C) as the last character, and ़ isn't in `prefixableChars`. The vowel keys then commit standalone vowels, so लड़की comes out as लड़ई.
  - Fix: add ढ़. Count a trailing nukta after a consonant as a consonant base, as the Sailfish version does.

- [ ] **3. Latin flick: the preview and the committed letter can differ.** `KrKeyIME.kt` ~L656 vs `isFlickGesture` ~L922.
  - The popup switches to uppercase after 10dp of upward movement (`FLICK_VISUAL_THRESHOLD_DP`).
  - The commit uses `isFlickGesture`, which needs 15dp and a verticality ratio.
  - Between those two points the user sees "A" and gets "a".
  - Fix: use the same check for both.

- [ ] **4. Word tracking breaks in long text.** `KrKeyIME.kt` ~L413, ~L809, ~L755.
  - `expectedSelStart` is taken from `getTextBeforeCursor(10000)`, which stops at 10,000 characters, but `newSelStart` is an absolute position.
  - Past 10k characters, `cursorJumped` is always true, so the typed word resets on every key and predictions disappear.
  - It also copies up to 10k characters across processes on every keystroke.
  - Fix: track the position from `newSelStart` in `onUpdateSelection` and from `EditorInfo.initialSelStart`.

- [ ] **5. The bottom row is probably clipped.** `KrKeyIME.kt` ~L154, ~L495.
  - Each row takes the key height plus 6dp of margin (3dp above, 3dp below).
  - Landscape: four rows need about 0.91·h + 24dp. That is more than the frame height h for every slider value from 120 to 200dp. At 160dp, roughly 9dp of the space row is cut off.
  - Portrait: four rows need 224dp against a fixed 220dp frame (`keyboard_view.xml`).
  - Check this against the screenshots.

## Performance

- [x] **Predictor rebuilt on every focus change.** `onStartInputView` calls `predictionManager.clearCache()` each time a text field gains focus. The first two-letter peck then transliterates the whole dictionary and rebuilds the trie and index on the UI thread. Fix: invalidate only when a dictionary or script setting changes, and build in the background.
  - Done 2026-10-22: the cache is kept across fields and cleared only when a `dict_`/`script_` setting changes (about 33 ms per build on the desktop). Predictors built before layout (0×0 keys) are rebuilt (`WordPredictor.hasGeometry`).
- [x] **Slow decoding of long swipes.** Exact trie search visited nearly the whole trie (about 51k nodes) on long swipes. Fixed 2026-10-22: beam search (BEAM=200, BEAM_ALPHA=0.5; `tools/swipe-sim/beam_eval.py`, same simulated accuracy as exact search) plus per-swipe distance caches. The longest logged swipe went from 36 ms to 1.3 ms on the desktop.
- [ ] **Learned words re-indexed on every key press.** `PredictionManager.ensurePredictor` calls `setLearnedWords` each time. That re-sorts up to 1,000 words and rebuilds the user index.
- [ ] **Dictionary parsed on every save.** `UserDictionaryManager.learnWord` writes the whole JSON to prefs. Its own pref listener then calls `load()` and parses it again.

## Design and consistency

- [ ] **Wrong gesture words get learned.** The top gesture result is learned automatically when the next input arrives (`commitCurrentInput`). Unless the user backspaces, mispredictions reinforce themselves. Consider learning only words chosen from the candidate bar or pecked out letter by letter.
- [ ] **E/O swap only applies to Kannada.** `CLAUDE.md` says the short/long E/O swap also applies to Telugu and Malayalam, but `KeyMap.buildScriptData` only does it for Kannada. Fix either the doc or the code.
- [ ] **Telugu label in Kannada script.** The Telugu dictionary is labelled "ತೆಲುಗು" (`KeyMap.kt` ~L91). It should be "తెలుగు".
- [ ] **Shift-lock is never set.** `isShiftLocked` is never true, so the ⇪ label can't appear.
- [ ] **Redundant score check.** In `performGestureTyping`, `res[0].second <= 8.0` is always true because `scoreAndAdd` already drops scores of 8 or more. The function's return value is also never used.

## Cleanup

- [ ] **Unused code:**
  - Delete the dead `gesture/` package; git already keeps the history.
  - `PredictionManager` takes a `keyLocator` it never uses.
  - `PredictionManager` also gets the initial key pool rather than the active keys. That only works because the exception on detached views is caught.
- [ ] **Add tests:** `formatKeyText`, `getCleanOutput`, `toBrahmiScript` and `KeyboardState` are pure functions and easy to cover with plain JVM tests. Bug 2 would have been caught that way.
- [ ] **Unused assets:** `ks_dict.txt` and `ks_dict_devanagari_filtered.txt` aren't referenced but still ship in the APK (about 285KB).
- [ ] **Leftover files:** add the editor `*~` and `.*.~undo-tree~` files to `.gitignore`. Decide whether to track or ignore `README.md`, `relicense.py`, `brahmi.el` and `.envrc`.

## Checked and fine

- The generated transliteration maps (`GeneratedMaps.kt`): source and target alphabets line up one-to-one, with no duplicate keys.
- Touch coordinates and key bounds use the same coordinate space: `keyboard_rows` sits at (0,0) inside `keyboard_frame`.

---

# Swipe typing: review and plan (2026-10-21)

Compared against FUTO Keyboard (`../android-keyboard`).

## How krkey decodes a swipe today (`WordPredictor.predict`)

1. **Pick candidates by endpoints.** Only words whose first and last letters are within 1.5 key-widths of where the swipe starts and ends are considered. Any word whose end is more than 2 key-widths away is thrown out entirely.
2. **Compare shapes.** The swipe and the word's ideal path (a straight line through each letter's key centre) are both resampled to 40 evenly spaced points. The score is the average distance between matching points.
3. **Check letter order.** It walks through the letters, moving forward to the first swipe point within 1.2 keys of each one.
4. **Penalise length**, using the log of the ratio between the swipe length and the ideal length.
5. **Weight by word frequency** with `×(1 + rank/N·0.25)`. The most common word gets ×1.0 and the rarest ×1.25, so in practice frequency hardly counts.

The swipe itself is recorded as one point per `ACTION_MOVE` event (`KrKeyIME.kt` ~L627). It doesn't use previous words as context, the timing of the swipe, or Android's batched historical touch points.

## How FUTO does it

FUTO has two engines.

**Neural decoder (the default):** `SwipeDecoderDictionary.kt`
- **Input:** a learned encoder that takes normalised (x, y, t) points.
- **Search:** a beam search over the dictionary (up to 300 candidates kept at once while swiping, then a final pass).
- **Language model:** a neural model that scores words against the last 10 words typed.
- **Two thumbs:** it handles multi-segment swipes.
- **Limits:** it's trained for English QWERTY only. Its model files aren't in our checkout (`java/assets/futo-swipe/` is empty).

**Legacy engine** (`native/jni/src/suggest/policyimpl/gesture/swipe_weighting.h`): a beam search over the dictionary in which letters are matched to the swipe one at a time.
- **Letter matching:** each next letter is matched to the point where the swipe comes closest to its key, searching forward from the previous letter's match (`getMatchedCost`). This alignment is elastic: corner cutting, uneven speed and hooks don't throw off the matching for the rest of the word.
- **Off-line penalty:** it penalises how far the swipe strays from the straight line between consecutive letters. It adds an extra penalty when the swipe moves against that line's direction (`calcLineDeviationPunishment`).
- **Doubled letters** ("ll") cost nothing extra.
- **Language model:** word frequency and the previous word are added as a cost (`getTerminalLanguageCost`, `getNewWordBigramLanguageCost`).
- **Input sampling** (inherited from AOSP's `PointerTracker` and `GestureStrokeRecognitionPoints`):
  - It uses all of Android's batched historical touch points (`getHistoricalX`).
  - It keeps a new point every ⅙ key-width.
  - It detects a gesture from speed and time, not just distance.

## Comparison

| | krkey | FUTO (legacy) | FUTO (neural) |
|---|---|---|---|
| Swipe points | one per `MOVE` event | all historical points, every ⅙ key | same, plus timing |
| Matching letters to the swipe | fixed 40-point resampling | elastic, one letter at a time | learned |
| Candidate search | endpoint letter pairs, score every word | beam search over the dictionary | beam search over the dictionary |
| Frequency prior | ×1.0–1.25 (almost none) | additive cost from frequency | neural, from previous words |
| Previous-word context | none | previous word | last 10 words |
| Ending past the last key | hard cutoff at 2 keys | handled by the off-line penalty | learned |

## Simulation results

`WordPredictor` scoring ported to Python (`tools/swipe-sim/sim.py`, `sim2.py`), on the exact portrait Latin layout. 300 synthetic swipes, with words drawn by how often they're used:

| Distortion | Top-1 correct |
|---|---|
| Clean, densely sampled | 93% |
| Sparse points (no historical samples) | 88% |
| Cutting corners | 77% |
| Overshooting the last key by 0.6 key | **69%** |

Typical errors:
- the→tyre, to→top, to→tp
- of→od, for→fir, this→thus
- long→lg, news→nes

Two things stand out:
- **Junk entries in the dictionary.** The top 20k list contains "lg", "nes", "pst", "tp" and "od". It has no "I" and no apostrophes ("dont", "im").
- **The ×1.25 frequency factor doesn't help.** A stronger, additive frequency prior raised the overshoot case from 69% to 81%. In the clean case it went from 93% down to 84%, which only means the weight tried was too high and needs tuning.

Simulated swipes are much kinder than real thumbs, so real-device accuracy will be lower than these numbers.

## Plan

- [x] **1. Collect points from historical touch samples.** Add `event.getHistoricalX/Y` for each pointer in `ACTION_MOVE`, and keep a point every ⅙ key-width or so. This is a few lines of code.
- [x] **2. Clean the dictionary.** Drop junk 2–3 letter tokens and add "I" and apostrophe forms. Commit the apostrophe form while still matching on letters only.
- [x] **3. Use a real frequency prior,** e.g. `+ λ·log(rank)` instead of the multiplier, with λ tuned on usage-weighted data. Add the previous word as context once that's in place.
  - Done 2026-10-21:
    - Points from batched historical touch samples are now kept, one every ⅙ key-width.
    - `en_dict.txt` is regenerated from the AOSP wordlist (`tools/dicts/gen_en_dict.py`, see `NOTICE`). Matching uses letters only, so "dont" gives "don't".
    - The frequency prior is now additive, `+0.12·ln(rank+2)`, tuned with `tools/swipe-sim/sweep.py` (simulated top-1 rose from 83.5% to 88–89% for λ between 0.1 and 0.15; 0.12 itself was not run).
    - Previous-word context is still to do.
- [x] **4. Handle overshoot gently.** Make the end distance a soft cost, or measure it against the last segment rather than the key centre. Widen the 1.5-key range used to pick end letters.
- [x] **5. Replace the fixed 40-point shape comparison with elastic alignment.** This is the main structural fix and the core idea of FUTO's legacy engine: match each letter to its closest-approach point in order, and penalise straying from the letter-to-letter line. An optimal alignment (dynamic time warping) between the swipe and the ideal path also works. Run it as a beam search over the existing trie, not over endpoint buckets.
- [x] **6. Build a replay harness before tuning.** In debug builds, log real swipes as JSON (as FUTO does) and replay them in JVM tests or the Python sim (`tools/swipe-sim/`). Every constant in `WordPredictor` is currently hand-picked with nothing measuring it.
  - Done 2026-10-21 (4–6):
    - `WordPredictor` now uses elastic alignment as a dynamic program over the dictionary trie (prototype: `tools/swipe-sim/elastic.py`).
    - Swipe before the first letter and after the last is a soft cost, which handles overshoot.
    - Weights were tuned with `tune.py` and checked on held-out words with `refine.py`: simulated top-1 rose from 88.2% to 94.0% (overshoot 87% → 96%).
    - `WordPredictorParityTest` checks the Kotlin port against the prototype (100/100 top-1).
    - Debug builds log swipes to `files/swipes.jsonl` (`SwipeLogger`); `tools/swipe-sim/replay.py` replays them through both decoders.
    - Next: tune on real logged swipes.

## Licensing

FUTO is under the "FUTO Source First License", which only allows non-commercial use and isn't GPL-compatible. `swipe_weighting.h` and `SwipeDecoderDictionary.kt` carry FUTO's license, so reimplement the ideas rather than copying code.

Many of the AOSP-derived Java files (`PointerTracker`, `GestureStrokeRecognitionPoints`) may still carry Apache-2.0 headers. Check each file's header before borrowing; Apache-2.0 files are compatible with GPLv3.

---

# TODO (after the swipe work)

- [ ] **Numeric keypad for number fields.** Number, phone and date/time fields (`TYPE_CLASS_NUMBER`, `TYPE_CLASS_PHONE`, `TYPE_CLASS_DATETIME`) currently get the full Indic/Latin keyboard. Add a dedicated numeric layer and select it in `onStartInputView` from `EditorInfo.inputType`. It needs:
  - a phone-style 3×4 grid with digits, and `+ * #` for phone fields;
  - `. , -` for decimal or signed numbers (`TYPE_NUMBER_FLAG_DECIMAL`, `TYPE_NUMBER_FLAG_SIGNED`);
  - backspace and enter;
  - a key to switch back to the full keyboard;
  - a choice between ASCII and native-script digits (e.g. ०–९), since some fields reject non-ASCII digits.

  Numeric password fields (`TYPE_NUMBER_VARIATION_PASSWORD`, e.g. PINs) should use the same keypad.

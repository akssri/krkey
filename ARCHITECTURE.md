# krkey-android Architecture

## Overview

krkey-android is a multi-script Brahmic IME (Input Method Editor) with Japanese-style flick gestures and Latin gesture typing. It uses a pure commit-based input model (no composing text) and multi-pointer touch tracking.

## Architecture Layers

```
┌──────────────────────────────────────────┐
│         UI Layer (Views)                 │
│  FlickKeyView, CandidateView            │
└──────────────────────────────────────────┘
              ↓
┌──────────────────────────────────────────┐
│      State Management Layer              │
│  KeyboardState, InputMode                │
└──────────────────────────────────────────┘
              ↓
┌──────────────────────────────────────────┐
│    Input Handling Layer                  │
│  KrKeyIME (handleGlobalTouch),           │
│  KeyLocator                              │
└──────────────────────────────────────────┘
              ↓
┌──────────────────────────────────────────┐
│     Key Resolution Layer                 │
│  KeyMap, KeyConfig, ScriptData           │
└──────────────────────────────────────────┘
              ↓
┌──────────────────────────────────────────┐
│      Prediction Layer                    │
│  PredictionManager, WordPredictor        │
└──────────────────────────────────────────┘
```

---

## Layer Details

### 1. State Management Layer (`state/`)

**Purpose**: Manages keyboard state with immutable transformations

**Key Classes**:
- **`InputMode`** (sealed class)
  - `IndicNormal`, `LatinNormal`, `LatinShifted`
  - `Symbol(fromLatin: Boolean)`, `SymbolShifted(fromLatin: Boolean)`
  - Helper methods: `isLatin()`, `isSymbol()`, `isShifted()`

- **`KeyboardState`** (data class)
  - Fields: `mode: InputMode`, `script: BrahmiScript`, `currentBaseChar: String`, `isShiftLocked: Boolean`
  - Transformation methods (return new instances):
    - `toggleShift()` - Smart shift based on mode
    - `toggleSymbol()` - Remembers Latin/Indic context via `fromLatin` flag
    - `toggleLanguage()` - Switch between Indic and Latin
    - `withBaseChar(char)` - Update composition base
    - `withScript(script)` - Switch scripts
    - `clearBaseChar()` - Clear after commit

---

### 2. Input Handling Layer (`KrKeyIME.kt`, `location/`)

**Purpose**: Multi-pointer touch handling with inline gesture detection

**Key Components**:

#### `KrKeyIME.handleGlobalTouch()`
- **Purpose**: Unified touch handler for all character keys
- **Tracking**: `activePointers` map of `PointerState` keyed by `pointerId`
- **Gestures**: Inline flick detection (vertical threshold) and swipe-to-gesture-type detection (horizontal distance)
- **Exclusive swipe**: When gesture typing activates, all other active touches are canceled

#### `KeyLocator`
- **Purpose**: Fast key lookup with cached bounds
- **Methods**:
  - `initialize(container, keys)` - Build cache, accepts filtered key list for dynamic layouts
  - `findKeyAt(x, y)` - O(1) lookup
  - `rebuildCache()` - On layout changes

---

### 3. Key Resolution Layer (`KeyMap.kt`)

**Purpose**: Defines key data, script configurations, and layout grids

**Key Classes**:

#### `KeyConfig`
- **Purpose**: Stores all text variants for a key across layers
- **Fields**: `base`, `flick`, `symBase`, `symFlick`, `sym2Base`, `sym2Flick`, `latinBase`, `latinFlick`, `latinSymBase`, `latinSymFlick`, `latinSym2Base`, `latinSym2Flick`
- **Method**: `getResolvedStrings(mode, currentBaseChar, scriptData)` - Resolves display/commit text with fallback chains

#### `ScriptData`
- **Purpose**: Per-script configuration (layout grids, prefixable chars, vowel maps)
- **Method**: `layoutFor(mode)` - Returns the appropriate layout grid for the current input mode

#### `BrahmiScript` (enum)
- **Purpose**: Enumerates supported scripts with transliteration offsets
- **Scripts**: Devanagari, Kannada, Telugu, Tamil, Malayalam, Gujarati, Gurmukhi, Bengali, Odia, Siddham, Grantha, Sharada, Brahmi

---

### 4. Prediction Layer (`prediction/`)

**Purpose**: Manages word prediction lifecycle

#### `PredictionManager`
- **Purpose**: Centralized predictor lifecycle and caching
- **Caching**: Per (isLatin, script) mode
- **Methods**:
  - `ensurePredictor(isLatin, script, learnedWords)` - Lazy init + cache
  - `getPrefixMatches(prefix)` - Prefix completions for pecked input
  - `predictGesture(path)` - Gesture typing prediction

#### `WordPredictor`
- **Purpose**: Core prediction logic using Unicode code points (not UTF-16 chars)
- **Features**: Prefix matching with trie, gesture path scoring, learned words integration

---

### 5. UI Layer (`ui/`, `FlickKeyView.kt`)

#### `CandidateView`
- **Purpose**: RecyclerView-based candidate display with view recycling
- **Callback**: `setOnCandidateClickListener { displayWord, originalWord -> ... }`

#### `FlickKeyView`
- **Purpose**: Individual keyboard key with popup display and visual state

---

## Data Flow

### 1. User Taps Key (Indic Mode)

```
User touch → handleGlobalTouch(ACTION_DOWN)
    ↓
KeyLocator.findKeyAt(x, y)
    ↓
FlickKeyView.showPopup(base text)
    ↓
handleGlobalTouch(ACTION_UP)
    ↓
handleFlickOrTap() → isFlickGesture(path)?
    ↓
onKeyInput(text) → ic.commitText(text, 1)
    ↓
updateUI() → updateBase() + refresh key labels
```

### 2. Gesture Typing (Latin Mode)

```
User drags horizontally → ACTION_MOVE
    ↓
pathDist > SWIPE_START_DISTANCE? → state.isGestureTyping = true
    ↓
Cancel other active pointers, show gesture trail
    ↓
ACTION_UP → performGestureTyping(path)
    ↓
PredictionManager.predictGesture(path) → candidates
    ↓
needsPrecedingSpace()? → prepend " "
    ↓
ic.commitText(space + word, 1)
    ↓
CandidateView.showCandidates(words)
```

### 3. Candidate Selection

```
User taps candidate → setupCandidateClickListener
    ↓
needsPrecedingSpace(deleteLen) ← called BEFORE batch edit
    ↓
ic.beginBatchEdit()
ic.deleteSurroundingText(deleteLen, 0)
ic.commitText(space + displayWord, 1)
ic.endBatchEdit()
    ↓
resetInputState() + updateUI()
```

---

## Key Design Decisions

### Pure Commit Model
No `setComposingText` — all text goes through `commitText` to avoid doubling bugs in third-party webviews (e.g., Perplexity AI).

### Smart Auto-Spacing
`needsPrecedingSpace(skipCount)` checks the character before the cursor (skipping `skipCount` chars for pre-deletion queries). Prepends space unless at field start, after whitespace, or after sentence-ending punctuation (`.?!।॥`).

### Unified Symbol Modes
`Symbol(fromLatin: Boolean)` replaces separate `IndicSymbol`/`LatinSymbol` modes. The `fromLatin` flag preserves context for returning to the correct base layer and selecting the right numeral style.

### Dynamic Layouts
`rebuildKeyboardGrid()` shuffles `FlickKeyView` instances from a pool into rows based on the current layout grid. `allKeys` is filtered to only active keys, preventing crashes from orphaned views in `KeyLocator`.

---

## Build

- **Kotlin**: 2.1.0
- **AGP**: 8.7.3
- **Gradle**: 8.14.4
- **compileSdk/targetSdk**: 36, **minSdk**: 26
- **JVM target**: 17
- **Dev environment**: Nix flake (`nix develop`)

---

## File Structure

```
app/src/main/kotlin/com/akssri/krkey/
├── KrKeyIME.kt                    # Main IME service (~800 lines)
├── state/
│   ├── InputMode.kt               # Sealed class for modes
│   └── KeyboardState.kt           # Immutable state
├── gesture/
│   ├── GestureDetector.kt         # Gesture classification (unused, kept for reference)
│   └── TouchHandler.kt            # Touch lifecycle (unused, kept for reference)
├── location/
│   └── KeyLocator.kt              # Cached key lookup
├── prediction/
│   └── PredictionManager.kt       # Predictor lifecycle
├── ui/
│   └── CandidateView.kt           # RecyclerView candidates
├── KeyMap.kt                      # Key configuration, layouts, script data
├── FlickKeyView.kt                # Individual key view
├── WordPredictor.kt               # Prediction logic
├── UserDictionaryManager.kt       # Learned words
├── GestureTrailView.kt            # Trail rendering
├── GeneratedMaps.kt               # Generated transliteration maps
└── SettingsActivity.kt            # App settings
```

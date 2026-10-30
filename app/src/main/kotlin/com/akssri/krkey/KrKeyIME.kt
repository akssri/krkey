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

import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.ContextThemeWrapper
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Space
import androidx.core.content.res.ResourcesCompat
import com.akssri.krkey.location.KeyLocator
import com.akssri.krkey.prediction.PredictionManager
import com.akssri.krkey.state.InputMode
import com.akssri.krkey.state.KeyboardState
import com.akssri.krkey.ui.CandidateView

class KrKeyIME : InputMethodService() {
    companion object {
        // Punctuation that is usually followed by a space (and moves in front of an automatic one)
        private val FOLLOWED_BY_SPACE = ".,!?;:)]}।॥".codePoints().toArray().toSet()
        private val OPENING_BRACKETS = "([{".codePoints().toArray().toSet()
        private const val SPACE_STATE_SETTLE_MS = 200L
        private const val TYPED_TAIL_MAX = 64
        private const val ZWNJ = "\u200C" // zero-width non-joiner

        // "e. g" / "i. e" left by auto-spacing, about to be closed with "."
        private val ABBREVIATION_SPLIT = Regex("(?:^|\\W)(?:[Ee]\\. [Gg]|[Ii]\\. [Ee])$")

        private val PASSWORD_TEXT_VARIATIONS =
            setOf(
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            )

        private val NO_AUTO_SPACE_VARIATIONS =
            setOf(
                InputType.TYPE_TEXT_VARIATION_URI,
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_FILTER,
            )
    }

    // State
    private var keyboardState = KeyboardState()
    private val currentPeckedWord = StringBuilder()
    private var lastGestureWord: String? = null
    private var lastGestureHadSpace: Boolean = false
    private var expectedSelStart: Int = -1

    // Automatic spacing around swiped/picked words and punctuation:
    //   PHANTOM: a space not yet inserted; the next letter/digit/opening bracket gets it first.
    //   ANTIPHANTOM: an inserted automatic space; a space tap is swallowed, sentence punctuation
    //   moves in front of it, enter strips it.
    // Any other input, or the cursor leaving spaceStateAnchor, resets to NONE.
    private enum class SpaceState { NONE, PHANTOM, ANTIPHANTOM }

    private var spaceState = SpaceState.NONE
    private var spaceStateAnchor = -1
    private var spaceStateSetAt = 0L
    private var autoSpaceAllowed = true

    // TYPE_NULL editors (remote input, terminals) handle raw key events rather than text edits
    private var isTypeNullField = false

    // What we typed most recently, for editors that do not report the text before the cursor
    // (remote input such as KDE Connect): needed e.g. to choose vowel signs after consonants.
    // Decided from what the editor declares (TYPE_NULL, or no cursor position), never from empty
    // read-backs: asynchronous editors (e.g. Firefox) lag on those but do track the cursor
    private val typedTail = StringBuilder()
    private var editorHidesText = false

    // Clipboard chip in the suggestion bar (when it has no candidates); hidden once pasted, until
    // the clipboard changes. The clip is read on field start and on change, not on every edit.
    private var clipboard: ClipboardManager? = null
    private var currentClip: String? = null
    private var lastPastedClip: String? = null
    private val clipListener =
        ClipboardManager.OnPrimaryClipChangedListener {
            lastPastedClip = null
            currentClip = clipboardText()
            refreshClipboardChip()
        }

    // Password fields: no predictions, swipe typing or learning; incognito fields: no learning
    private var isPasswordField = false
    private var learningAllowed = true

    // Components
    private lateinit var keyLocator: KeyLocator
    private lateinit var predictionManager: PredictionManager
    private lateinit var userDict: UserDictionaryManager
    private lateinit var swipeLogger: SwipeLogger
    private var candidateView: CandidateView? = null

    // View Pool
    private var allKeys: List<FlickKeyView> = emptyList() // All character keys
    private var flickKeyPool: List<FlickKeyView> = emptyList() // Same as allKeys but used for layout allocation
    private val specialKeyMap = mutableMapOf<SpecialKey, View>()

    private var shiftBtn: Button? = null
    private var symBtn: Button? = null
    private var spaceBtn: Button? = null
    private var gestureTrailView: GestureTrailView? = null
    private var candidateBar: View? = null
    private var keyboardRowsContainer: LinearLayout? = null

    private var currentLayoutGrid: List<List<Any>>? = null
    private var density = 1f
    private var keyHeightPx = 0
    private var splitGapWeight = 0f
    private var landscapeMode = false
    private var screenWidthDp = 0f

    // Gesture State
    private class PointerState {
        var activeKey: FlickKeyView? = null
        var isGestureTyping = false
        var isCanceled = false
        var lastPopupText: String? = null
        val gesturePath = mutableListOf<PointF>()
        val gestureTimes = mutableListOf<Long>() // event time of each gesturePath point

        // True when the last gesturePath point only tracks the finger and is not a kept sample
        var hasProvisionalTail = false

        // Special keys (space, backspace, ...) are tracked per pointer like character keys
        var specialKey: SpecialKey? = null
        var specialView: View? = null
        var isSpaceDragging = false
        var lastSpaceDragX = 0f
        var repeat: Runnable? = null

        // Committed early because a newer pointer went down (rollover); the rest is ignored
        var released = false
    }

    private val activePointers = mutableMapOf<Int, PointerState>()

    // Fonts
    private var siddhamTypeface: Typeface? = null
    private var granthaTypeface: Typeface? = null
    private var sharadaTypeface: Typeface? = null
    private var brahmiTypeface: Typeface? = null

    // Gesture Tuning
    private val FLICK_VERTICAL_THRESHOLD_DP = 15f
    private val FLICK_VISUAL_THRESHOLD_DP = 10f
    private val FLICK_MIN_DISTANCE_DP = 10f
    private val FLICK_ROLLOVER_SLOP_DP = 6f
    private val FLICK_VERTICALITY_RATIO = 1.2f
    private val SWIPE_START_DISTANCE_DP = 50f
    private val SWIPE_FORCE_DISTANCE_DP = 100f
    private val SWIPE_NEW_KEY_RATIO = 0.6f
    private val GESTURE_SAMPLING_KEY_RATIO = 1f / 6f

    private val SPACE_DRAG_THRESHOLD_DP = 10f
    private val SPACE_DRAG_STEP_DP = 20f
    private val BACKSPACE_REPEAT_DELAY_MS = 400L
    private val BACKSPACE_REPEAT_INTERVAL_MS = 50L
    private val repeatHandler = Handler(Looper.getMainLooper())

    private val prefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            // Predictors depend on the enabled dictionaries; rebuild them only when those change
            if (key != null && (key.startsWith("dict_") || key.startsWith("script_")) && ::predictionManager.isInitialized) {
                predictionManager.clearCache()
            }
            if (key in listOf("keyboard_height_landscape", "key_width_dp", "keyboard_opacity_landscape", "landscape_overlay", "landscape_split")) {
                if (keyboardRowsContainer != null) {
                    setInputView(onCreateInputView())
                }
            }
        }

    override fun onCreate() {
        super.onCreate()
        density = resources.displayMetrics.density
        landscapeMode = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        screenWidthDp = resources.configuration.screenWidthDp.toFloat()
        DebugLog.init(this)
        userDict = UserDictionaryManager(this)
        clipboard = getSystemService(ClipboardManager::class.java)
        clipboard?.addPrimaryClipChangedListener(clipListener)
        swipeLogger = SwipeLogger(this)
        val prefs = getSharedPreferences("krkey_prefs", MODE_PRIVATE)
        val lastScriptStr = prefs.getString("last_script", BrahmiScript.DEVANAGARI.name)
        val script =
            try {
                BrahmiScript.valueOf(lastScriptStr!!)
            } catch (e: Exception) {
                BrahmiScript.DEVANAGARI
            }
        keyboardState = KeyboardState(script = script)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onDestroy() {
        clipboard?.removePrimaryClipChangedListener(clipListener)
        getSharedPreferences("krkey_prefs", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        cancelAllPointers()
        loadFonts()
        val themedContext = ContextThemeWrapper(this, R.style.Theme_KrKey)
        val layout = LayoutInflater.from(themedContext).inflate(R.layout.keyboard_view, null) as LinearLayout

        val prefs = getSharedPreferences("krkey_prefs", MODE_PRIVATE)
        if (landscapeMode) {
            val heightDp = prefs.getInt("keyboard_height_landscape", 160)
            val containerHeightPx = (heightDp * density).toInt()
            keyHeightPx = (heightDp / 4.4f * density).toInt()

            val keyboardFrame = layout.findViewById<FrameLayout>(R.id.keyboard_frame)
            keyboardFrame.layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, containerHeightPx,
                )

            // Compute split gap weight based on target key width
            if (prefs.getBoolean("landscape_split", true)) {
                val targetKeyWidthDp = prefs.getInt("key_width_dp", 42).toFloat()
                val maxKeysInRow = 10 // widest row (row 1)
                splitGapWeight = ((screenWidthDp - maxKeysInRow * targetKeyWidthDp) / targetKeyWidthDp).coerceAtLeast(0f)
            } else {
                splitGapWeight = 0f
            }
        } else {
            keyHeightPx = (50f * density).toInt()
            splitGapWeight = 0f
        }

        candidateBar = layout.findViewById(R.id.candidate_bar)
        gestureTrailView = layout.findViewById(R.id.gesture_trail)
        candidateView = layout.findViewById(R.id.candidate_view)
        keyboardRowsContainer = layout.findViewById(R.id.keyboard_rows)

        flickKeyPool = findAllFlickKeys(layout)
        allKeys = flickKeyPool

        val specialIds =
            mapOf(
                SpecialKey.SHIFT to R.id.key_shift,
                SpecialKey.BACKSPACE to R.id.key_backspace,
                SpecialKey.SYMBOL to R.id.key_sym,
                SpecialKey.GLOBE to R.id.key_globe,
                SpecialKey.SPACE to R.id.key_space,
                SpecialKey.ENTER to R.id.key_enter,
            )
        specialIds.forEach { (key, id) ->
            layout.findViewById<View>(id)?.let { specialKeyMap[key] = it }
        }

        shiftBtn = specialKeyMap[SpecialKey.SHIFT] as? Button
        symBtn = specialKeyMap[SpecialKey.SYMBOL] as? Button
        spaceBtn = specialKeyMap[SpecialKey.SPACE] as? Button

        keyLocator = KeyLocator(allKeys)
        keyLocator.initialize(layout.findViewById(R.id.keyboard_rows))

        predictionManager = PredictionManager(this, assets, keyLocator, allKeys, layout.findViewById(R.id.keyboard_rows))
        setupCandidateClickListener()
        setupSpecialKeyListeners()

        val rowsContainer = layout.findViewById<View>(R.id.keyboard_rows).parent as View
        rowsContainer.setOnTouchListener { _, event -> handleGlobalTouch(event) }

        if (landscapeMode && prefs.getBoolean("landscape_overlay", true)) {
            layout.alpha = prefs.getInt("keyboard_opacity_landscape", 80) / 100f
        }

        currentLayoutGrid = null // Force a rebuild of the grid to populate tags
        updateUI()

        // Key previews are views over the keyboard, not PopupWindows (see KeyPreviewLayer)
        val previewLayer = KeyPreviewLayer(themedContext)
        flickKeyPool.forEach { it.previewLayer = previewLayer }
        return FrameLayout(themedContext).apply {
            addView(layout, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(previewLayer, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        val overlayEnabled =
            getSharedPreferences("krkey_prefs", MODE_PRIVATE)
                .getBoolean("landscape_overlay", true)
        if (landscapeMode && overlayEnabled) {
            val decorView = window?.window?.decorView ?: return
            // Push content inset to the bottom so the app is not resized
            outInsets.contentTopInsets = decorView.height
            outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
            outInsets.touchableRegion.set(
                0,
                outInsets.visibleTopInsets,
                decorView.width,
                decorView.height,
            )
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        cancelAllPointers() // e.g. keyboard hidden while backspace is held
        if (finishingInput) {
            requestHideSelf(0)
        }
    }

    private fun loadFonts() {
        try {
            siddhamTypeface = ResourcesCompat.getFont(this, R.font.noto_sans_siddham)
            granthaTypeface = ResourcesCompat.getFont(this, R.font.noto_sans_grantha)
            sharadaTypeface = ResourcesCompat.getFont(this, R.font.noto_sans_sharada)
            brahmiTypeface = ResourcesCompat.getFont(this, R.font.noto_sans_brahmi)
        } catch (e: Exception) {
        }
    }

    private fun findAllFlickKeys(view: View): List<FlickKeyView> {
        val keys = mutableListOf<FlickKeyView>()
        if (view is FlickKeyView) {
            keys.add(view)
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) keys.addAll(findAllFlickKeys(view.getChildAt(i)))
        }
        return keys
    }

    // All touches are handled by handleGlobalTouch, so that a press on a special key while another
    // finger is down (two-thumb typing) is not routed to the wrong view or dropped
    private fun setupSpecialKeyListeners() {
        specialKeyMap.values.forEach { v ->
            v.setOnTouchListener(null)
            v.setOnClickListener(null)
            v.isClickable = false
            v.isLongClickable = false
            v.isFocusable = false
        }
    }

    private fun onSpaceTap() {
        // A space right after an automatic one is swallowed
        val swallow = hasAntiphantomSpace()
        DebugLog.d { if (swallow) "space: swallowed (automatic space already there)" else "space: inserted (state=$spaceState)" }
        commitCurrentInput()
        if (!swallow) commit(" ")
        updateUI()
    }

    private fun onSpaceDrag(
        state: PointerState,
        x: Float,
    ) {
        val start = state.gesturePath[0]
        val dx = Math.abs(x - start.x)
        val dy = Math.abs(state.gesturePath.last().y - start.y)
        // A drag is sideways; mostly-vertical movement is a flick (ZWNJ), not a cursor move
        if (!state.isSpaceDragging && dx > SPACE_DRAG_THRESHOLD_DP * density && dx > dy) {
            state.isSpaceDragging = true
            resetSpaceState()
            state.lastSpaceDragX = x
        }
        if (!state.isSpaceDragging) return
        val step = SPACE_DRAG_STEP_DP * density
        val steps = ((x - state.lastSpaceDragX) / step).toInt()
        if (steps == 0) return
        val ic = currentInputConnection ?: return
        val code = if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        repeat(Math.abs(steps)) {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
        state.lastSpaceDragX += steps * step
    }

    private fun onGlobe() {
        val prefs = getSharedPreferences("krkey_prefs", Context.MODE_PRIVATE)
        val enabled = BrahmiScript.values().filter { !it.isExperimental && prefs.getBoolean("script_${it.name}", it == BrahmiScript.DEVANAGARI) }

        commitCurrentInputKeepingSpaceState()
        if (keyboardState.mode.isLatin()) {
            keyboardState = keyboardState.withScript(keyboardState.script).copy(mode = InputMode.IndicNormal)
        } else if (enabled.size > 1) {
            val next = enabled[(enabled.indexOf(keyboardState.script) + 1) % enabled.size]
            keyboardState = keyboardState.withScript(next)
            prefs.edit().putString("last_script", next.name).apply()
        }
        updateUI()
    }

    private fun onEnter() {
        if (hasAntiphantomSpace()) removeTrailingSpace()
        commitCurrentInput()
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        noteTyped("\n")
        updateUI()
    }

    private fun onShift() {
        if (keyboardState.mode.isSymbol()) {
            keyboardState = keyboardState.toggleShift() // Shift symbol layer (Layer 2)
        } else {
            commitCurrentInputKeepingSpaceState()
            keyboardState = keyboardState.toggleLanguage()
        }
        updateUI()
    }

    private fun onSymbol() {
        keyboardState = keyboardState.toggleSymbol()
        updateUI()
    }

    private fun onSpecialDown(
        state: PointerState,
        key: SpecialKey,
        view: View,
    ) {
        state.specialKey = key
        state.specialView = view
        view.isPressed = true
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (key == SpecialKey.BACKSPACE) {
            deleteLastChar()
            val repeat =
                object : Runnable {
                    override fun run() {
                        deleteLastChar()
                        repeatHandler.postDelayed(this, BACKSPACE_REPEAT_INTERVAL_MS)
                    }
                }
            state.repeat = repeat
            repeatHandler.postDelayed(repeat, BACKSPACE_REPEAT_DELAY_MS)
        }
    }

    private fun onSpecialUp(
        state: PointerState,
        perform: Boolean,
    ) {
        stopPointer(state)
        if (!perform) return
        when (state.specialKey) {
            SpecialKey.SPACE ->
                if (state.isSpaceDragging) {
                    Unit
                } else if (isZwnjFlick(state)) {
                    // Zero-width non-joiner: stops a conjunct after a virama in Indic text
                    DebugLog.d { "space flick: ZWNJ" }
                    onKeyInput(ZWNJ)
                } else {
                    onSpaceTap()
                }
            SpecialKey.ENTER -> onEnter()
            SpecialKey.SHIFT -> onShift()
            SpecialKey.SYMBOL -> onSymbol()
            SpecialKey.GLOBE -> onGlobe()
            SpecialKey.BACKSPACE, null -> {}
        }
    }

    // Releases a pointer's visual state and any key repeat, without performing its action
    private fun stopPointer(state: PointerState) {
        state.repeat?.let { repeatHandler.removeCallbacks(it) }
        state.repeat = null
        state.specialView?.isPressed = false
        state.activeKey?.let {
            it.isPressed = false
            it.dismissPopup()
        }
    }

    private fun cancelAllPointers() {
        activePointers.values.forEach { stopPointer(it) }
        activePointers.clear()
        gestureTrailView?.clear()
        gestureTrailView?.visibility = View.GONE
    }

    // Rollover: when a new finger goes down, commit older taps now so that characters come out in
    // the order the keys were pressed ("d" then space), not the order the fingers lifted
    private fun releaseOlderPointers() {
        if (learningAllowed && activePointers.isNotEmpty()) {
            DebugLog.d { "down with ${activePointers.size} active: " + activePointers.entries.joinToString { (id, st) -> "#$id key=${(st.activeKey?.tag as? Pair<*, *>)?.first} special=${st.specialKey} released=${st.released} canceled=${st.isCanceled}" } }
        }
        for (state in activePointers.values.toList()) {
            if (state.released || state.isCanceled || state.isGestureTyping) continue
            when {
                state.specialKey == SpecialKey.SPACE && !state.isSpaceDragging && !isFlickInProgress(state.gesturePath) -> {
                    stopPointer(state)
                    state.released = true
                    onSpaceTap()
                }
                // A finger already moving up may be mid-flick: let it finish and commit on lift
                state.specialKey == null && state.activeKey != null && !isKeyFlickInProgress(state) -> {
                    stopPointer(state)
                    state.released = true
                    if (learningAllowed) DebugLog.d { "rollover commits the older tap" }
                    handleFlickOrTap(state.gesturePath, state.activeKey)
                }
            }
        }
    }

    private fun getCleanOutput(
        raw: String,
        scriptData: ScriptData,
    ): String {
        val base = keyboardState.currentBaseChar
        val resolved = formatKeyText(raw, base, scriptData)
        return if (base.isNotEmpty() && resolved.startsWith(base) && resolved.length > base.length) {
            resolved.substring(base.length)
        } else if (resolved.startsWith("◌")) {
            resolved.substring(1)
        } else {
            resolved
        }
    }

    private fun onKeyInput(text: String) {
        val prevSpaceState = if (spaceState == SpaceState.ANTIPHANTOM && !hasAntiphantomSpace()) SpaceState.NONE else spaceState
        resetSpaceState()
        if (lastGestureWord != null) commitCurrentInput()

        val cp = if (text.isEmpty()) -1 else text.codePointAt(0)
        if (text.length == Character.charCount(cp) && cp in FOLLOWED_BY_SPACE) {
            if (currentPeckedWord.isNotEmpty()) commitCurrentInput()
            commitPunctuation(text, cp, prevSpaceState)
            updateUI()
            return
        }
        // A pending phantom space goes in front of the next word
        val space =
            if (prevSpaceState == SpaceState.PHANTOM && (Character.isLetterOrDigit(cp) || cp in OPENING_BRACKETS) &&
                !looksLikeUrl(textBefore(64) ?: "")
            ) {
                " "
            } else {
                ""
            }

        val isWordChar = !listOf("।", "॥", ".", ",", "!", "?", "/", "'", "\"", "\\", " ").contains(text)
        if (!keyboardState.mode.isSymbol() && !keyboardState.mode.isNumeric() && isWordChar && text.isNotBlank()) {
            val ic = currentInputConnection ?: return
            if (currentPeckedWord.isEmpty() && expectedSelStart == -1) {
                expectedSelStart = (ic.getTextBeforeCursor(10000, 0)?.length ?: 0) + space.length
            }
            currentPeckedWord.append(text)
            commit(space + text)
            expectedSelStart += text.length
            updatePeckedCandidates()
        } else {
            if (currentPeckedWord.isNotEmpty()) commitCurrentInput()
            commit(space + text)
        }
        updateUI()
    }

    private fun updateUI() {
        updateBase()
        refreshClipboardChip()
        val mode = keyboardState.mode
        val script = keyboardState.script
        candidateBar?.visibility = if (!mode.isSymbol() && !mode.isNumeric() && !isPasswordField) View.VISIBLE else View.GONE

        val tf =
            if (!mode.isLatin()) {
                when (script) {
                    BrahmiScript.SIDDHAM -> siddhamTypeface
                    BrahmiScript.GRANTHA -> granthaTypeface
                    BrahmiScript.SHARADA -> sharadaTypeface
                    BrahmiScript.BRAHMI -> brahmiTypeface
                    else -> Typeface.DEFAULT
                }
            } else {
                Typeface.DEFAULT
            }

        symBtn?.setTextIfChanged(
            if (mode.isSymbol()) {
                if (mode.isLatin()) "abc" else "अल्".toBrahmiScript(script)
            } else {
                "१२३".toBrahmiScript(script)
            },
        )

        shiftBtn?.setTextIfChanged(
            if (mode.isSymbol()) {
                if (keyboardState.isShiftLocked) "⇪" else "⇧"
            } else if (mode.isLatin()) {
                script.firstSyllable
            } else {
                "EN"
            },
        )

        spaceBtn?.setTextIfChanged(
            when {
                mode.isNumeric() -> "␣"
                mode.isLatin() && !mode.isSymbol() -> "English"
                else -> script.nativeName
            },
        )
        spaceBtn?.typeface = tf

        val scriptData = ScriptManager.getScriptData(script)
        val targetLayout = scriptData.layoutFor(mode)
        if (currentLayoutGrid != targetLayout) rebuildKeyboardGrid(targetLayout)

        allKeys.forEach { k ->
            val tuple = k.tag as? Pair<*, *> ?: return@forEach
            val base = tuple.first as String
            val flick = tuple.second as String

            k.setTypeface(tf)

            val (displayBase, displayFlick) =
                if (mode is InputMode.LatinNormal) {
                    val b = if (base.length == 1 && base.first().isLetter()) base.lowercase() else base
                    val f = if (base.length == 1 && base.first().isLetter()) base.uppercase() else flick
                    b to f
                } else {
                    formatKeyText(base, keyboardState.currentBaseChar, scriptData) to
                        formatKeyText(flick, keyboardState.currentBaseChar, scriptData)
                }

            k.setVisualState(displayBase, displayFlick, false)
            val fk = k.flickKey
            if (fk == null) {
                k.setDirectionalHints(null, null, null)
            } else {
                val hint = { s: String -> formatKeyText(s, keyboardState.currentBaseChar, scriptData) }
                k.setDirectionalHints(hint(fk.left), hint(fk.right), hint(fk.down))
                k.setTypefaceForHints(tf)
            }
        }
    }

    private fun rebuildKeyboardGrid(layout: List<List<Any>>) {
        val container = keyboardRowsContainer ?: return

        container.removeAllViews()
        var poolIdx = 0
        val effectiveKeyHeight = if (keyHeightPx > 0) keyHeightPx else (50f * density).toInt()
        val margin = (3f * density).toInt()

        val activeKeys = mutableListOf<FlickKeyView>()
        for (rowItems in layout) {
            val rowLayout =
                LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                }

            for (item in rowItems) {
                val view =
                    when (item) {
                        is Pair<*, *>, is FlickKey -> {
                            if (poolIdx < flickKeyPool.size) {
                                flickKeyPool[poolIdx++].apply {
                                    // Four-direction keys keep `base to up` as the tag, so code that
                                    // only knows pairs keeps working; the rest is on the view
                                    flickKey = item as? FlickKey
                                    tag = if (item is FlickKey) item.base to item.up else item
                                    activeKeys.add(this)
                                }
                            } else {
                                android.util.Log.e("KrKeyIME", "Flick key pool exhausted at index $poolIdx")
                                null
                            }
                        }
                        is SpecialKey -> {
                            specialKeyMap[item]
                        }
                        else -> null
                    } ?: continue

                (view.parent as? ViewGroup)?.removeView(view)

                val weight =
                    if (keyboardState.mode.isNumeric()) {
                        1f // keypad: an even grid
                    } else if (view.id == R.id.key_space) {
                        3.5f
                    } else if (listOf(R.id.key_shift, R.id.key_backspace, R.id.key_sym, R.id.key_enter).contains(view.id)) {
                        1.2f
                    } else {
                        1f
                    }

                view.layoutParams =
                    LinearLayout.LayoutParams(0, effectiveKeyHeight, weight).apply {
                        setMargins(margin, margin, margin, margin)
                    }
                rowLayout.addView(view)
            }

            // Auto-split: insert a proportional gap at the midpoint of every row
            if (splitGapWeight > 0f) {
                val midpoint = rowLayout.childCount / 2
                val spacer =
                    Space(this).apply {
                        layoutParams = LinearLayout.LayoutParams(0, effectiveKeyHeight, splitGapWeight)
                    }
                rowLayout.addView(spacer, midpoint)
            }

            container.addView(rowLayout)
        }

        allKeys = activeKeys
        keyLocator.initialize(container, allKeys)
        container.post { keyLocator.rebuildCache() }
        currentLayoutGrid = layout
    }

    private fun findSpecialKeyAt(
        x: Float,
        y: Float,
    ): Pair<SpecialKey, View>? {
        val container = keyboardRowsContainer ?: return null
        val r = Rect()
        for ((key, v) in specialKeyMap) {
            if (v.parent == null) continue
            v.getDrawingRect(r)
            try {
                container.offsetDescendantRectToMyCoords(v, r)
                if (r.contains(x.toInt(), y.toInt())) return key to v
            } catch (e: IllegalArgumentException) {
                // Not in the current layout
            }
        }
        return null
    }

    private fun handleGlobalTouch(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (action == MotionEvent.ACTION_DOWN) cancelAllPointers() // stale state from a lost gesture
                // Ignore new touches if we are already swiping
                if (activePointers.values.any { it.isGestureTyping }) return true

                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)

                releaseOlderPointers()

                val state = PointerState()
                activePointers[pointerId] = state
                state.gesturePath.add(PointF(x, y))
                state.gestureTimes.add(event.eventTime)

                val special = findSpecialKeyAt(x, y)
                if (special != null) {
                    onSpecialDown(state, special.first, special.second)
                    return true
                }

                state.activeKey = keyLocator.findKeyAt(x, y)
                state.activeKey?.let { k ->
                    k.isPressed = true
                    k.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    val tuple = k.tag as? Pair<*, *>

                    val base =
                        if (!keyboardState.mode.isLatin() || keyboardState.mode.isSymbol()) {
                            val scriptData = ScriptManager.getScriptData(keyboardState.script)
                            formatKeyText(tuple?.first as? String ?: "", keyboardState.currentBaseChar, scriptData)
                        } else {
                            val rawBase = tuple?.first as? String ?: ""
                            if (keyboardState.mode is InputMode.LatinNormal && rawBase.length == 1 && rawBase.first().isLetter()) rawBase.lowercase() else rawBase
                        }

                    state.lastPopupText = base
                    k.showPopup(base)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val minSampleDist = (allKeys.firstOrNull()?.width ?: 0) * GESTURE_SAMPLING_KEY_RATIO
                for (i in 0 until event.pointerCount) {
                    val pId = event.getPointerId(i)
                    val state = activePointers[pId] ?: continue
                    if (state.released) continue
                    val px = event.getX(i)
                    val py = event.getY(i)
                    if (state.specialKey != null) {
                        if (state.specialKey == SpecialKey.SPACE) {
                            state.gesturePath.add(PointF(px, py))
                            state.gestureTimes.add(event.eventTime)
                            onSpaceDrag(state, px)
                        }
                        continue
                    }
                    // MOVE events batch several touch samples; keep them so fast swipes don't cut corners
                    for (h in 0 until event.historySize) {
                        addGesturePoint(state, event.getHistoricalX(i, h), event.getHistoricalY(i, h), event.getHistoricalEventTime(h), minSampleDist)
                    }
                    addGesturePoint(state, px, py, event.eventTime, minSampleDist, isLatest = true)

                    if (!keyboardState.mode.isLatin() || keyboardState.mode.isSymbol()) {
                        // Non-gesture mode
                        state.activeKey?.let { k ->
                            val tuple = k.tag as? Pair<*, *>
                            val scriptData = ScriptManager.getScriptData(keyboardState.script)
                            val base = formatKeyText(tuple?.first as? String ?: "", keyboardState.currentBaseChar, scriptData)
                            val flick = formatKeyText(tuple?.second as? String ?: "", keyboardState.currentBaseChar, scriptData)
                            k.showPopup(
                                if (k.flickKey != null) {
                                    formatKeyText(rawKeyOutput(k, state.gesturePath), keyboardState.currentBaseChar, scriptData)
                                } else if (isFlickGesture(state.gesturePath)) {
                                    flick
                                } else {
                                    base
                                },
                            )
                        }
                    } else {
                        // Latin/gesture mode
                        if (!state.isGestureTyping) {
                            val currentKey = keyLocator.findKeyAt(px, py)
                            val startPos = state.gesturePath[0]
                            val verticalDist = startPos.y - py
                            val horizontalDist = Math.abs(px - startPos.x)
                            val movedToNewKey = currentKey != null && currentKey != state.activeKey && horizontalDist > (state.activeKey?.width ?: 0) * SWIPE_NEW_KEY_RATIO
                            val isLikelyFlick = verticalDist > 0 && horizontalDist < verticalDist * FLICK_VERTICALITY_RATIO

                            state.activeKey?.let { k ->
                                val tuple = k.tag as? Pair<*, *>
                                val rawBase = tuple?.first as? String ?: ""
                                val rawFlick = tuple?.second as? String ?: ""

                                val base = if (rawBase.length == 1 && rawBase.first().isLetter()) rawBase.lowercase() else rawBase
                                val flick = if (rawBase.length == 1 && rawBase.first().isLetter()) rawBase.uppercase() else rawFlick

                                val text =
                                    if (k.flickKey != null) {
                                        rawKeyOutput(k, state.gesturePath)
                                    } else if (verticalDist > FLICK_VISUAL_THRESHOLD_DP * density) {
                                        flick
                                    } else {
                                        base
                                    }
                                if (text != state.lastPopupText) {
                                    state.lastPopupText = text
                                    k.showPopup(text)
                                }
                            }

                            // Four-direction (punctuation) keys never start a swipe: their sideways flicks would
                            if (splitGapWeight == 0f && !isPasswordField && state.activeKey?.flickKey == null &&
                                (
                                    (pathDist(state.gesturePath) > SWIPE_START_DISTANCE_DP * density && !isLikelyFlick) ||
                                        (movedToNewKey && !isLikelyFlick) ||
                                        pathDist(state.gesturePath) > SWIPE_FORCE_DISTANCE_DP * density
                                )
                            ) {
                                state.isGestureTyping = true
                                // Cancel all other active touches to prevent accidental pecks while swiping
                                activePointers.forEach { (id, pState) ->
                                    if (id != pId) {
                                        pState.isCanceled = true
                                        stopPointer(pState)
                                    }
                                }
                                commitCurrentInput()
                                state.activeKey?.let {
                                    it.isPressed = false
                                    it.dismissPopup()
                                }
                                gestureTrailView?.visibility = View.VISIBLE
                                gestureTrailView?.setPoints(state.gesturePath)
                                candidateView?.showCandidates(emptyList())
                            }
                        }
                        if (state.isGestureTyping) {
                            for (h in 0 until event.historySize) gestureTrailView?.addPoint(event.getHistoricalX(i, h), event.getHistoricalY(i, h))
                            gestureTrailView?.addPoint(px, py)
                        }
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> cancelAllPointers()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val state = activePointers.remove(pointerId) ?: return true
                if (state.released) return true
                if (state.specialKey != null) {
                    // Like a button: sliding off the key before lifting cancels it
                    val liftedOnKey = findSpecialKeyAt(event.getX(pointerIndex), event.getY(pointerIndex))?.first == state.specialKey
                    // A drag or a flick off the space bar still counts; other slide-offs cancel
                    onSpecialUp(state, perform = !state.isCanceled && (liftedOnKey || state.isSpaceDragging || isZwnjFlick(state)))
                    return true
                }
                val finalKey = state.activeKey
                stopPointer(state)

                if (state.isGestureTyping) {
                    gestureTrailView?.visibility = View.GONE
                    performGestureTyping(state.gesturePath, state.gestureTimes)
                } else if (!state.isCanceled) {
                    // Double check no other pointer is currently swiping
                    if (activePointers.values.none { it.isGestureTyping }) {
                        handleFlickOrTap(state.gesturePath, finalKey)
                    }
                }

                if (activePointers.values.none { it.isGestureTyping }) {
                    gestureTrailView?.clear()
                    gestureTrailView?.visibility = View.GONE
                }
            }
        }
        return true
    }

    private fun addGesturePoint(
        state: PointerState,
        x: Float,
        y: Float,
        time: Long,
        minDist: Float,
        isLatest: Boolean = false,
    ) {
        val path = state.gesturePath
        val times = state.gestureTimes
        val anchor = if (state.hasProvisionalTail) path[path.size - 2] else path.last()
        val farEnough = Math.hypot((x - anchor.x).toDouble(), (y - anchor.y).toDouble()) >= minDist
        when {
            farEnough && state.hasProvisionalTail -> {
                path.last().set(x, y)
                times[times.size - 1] = time
                state.hasProvisionalTail = false
            }
            farEnough -> {
                path.add(PointF(x, y))
                times.add(time)
            }
            // Too close to keep as a sample, but the path must still end at the finger
            isLatest && state.hasProvisionalTail -> {
                path.last().set(x, y)
                times[times.size - 1] = time
            }
            isLatest -> {
                path.add(PointF(x, y))
                times.add(time)
                state.hasProvisionalTail = true
            }
        }
    }

    private fun handleFlickOrTap(
        path: List<PointF>,
        key: FlickKeyView?,
    ) {
        if (path.isEmpty() || key == null) return
        val tuple = key.tag as? Pair<*, *> ?: return
        val scriptData = ScriptManager.getScriptData(keyboardState.script)
        val isFlick = isFlickGesture(path)
        val raw = rawKeyOutput(key, path)
        // Debug builds only; never in password/incognito fields (it records typed characters)
        if (learningAllowed) {
            DebugLog.d { "tap at (${path.first().x.toInt()},${path.first().y.toInt()})->(${path.last().x.toInt()},${path.last().y.toInt()}) key=$tuple flick=$isFlick raw='$raw' base='${keyboardState.currentBaseChar}'" }
        }
        onKeyInput(getCleanOutput(raw, scriptData))
    }

    private fun updateBase() {
        val ic = currentInputConnection ?: return
        val text = textBefore(2)
        val last =
            if (!text.isNullOrEmpty()) {
                if (text.length >= 2 && Character.isSurrogatePair(text[text.length - 2], text[text.length - 1])) {
                    text.substring(text.length - 2)
                } else {
                    text.substring(text.length - 1)
                }
            } else {
                ""
            }

        val scriptData = ScriptManager.getScriptData(keyboardState.script)
        keyboardState = keyboardState.withBaseChar(if (scriptData.prefixableChars.contains(last)) last else "")
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (spaceState != SpaceState.NONE) {
            // Updates for our own edits can arrive late, so shortly after the automatic-space
            // edit they only move the anchor; after that, any cursor movement cancels the state
            val settling = SystemClock.uptimeMillis() - spaceStateSetAt < SPACE_STATE_SETTLE_MS
            if (newSelStart != newSelEnd) {
                resetSpaceState()
            } else if (spaceStateAnchor == -1 || settling) {
                spaceStateAnchor = newSelStart
            } else if (newSelStart != spaceStateAnchor) {
                resetSpaceState()
            }
        }
        val cursorJumped = expectedSelStart != -1 && Math.abs(newSelStart - expectedSelStart) > 2
        if (newSelStart != newSelEnd || cursorJumped) {
            resetInputState()
        }
        updateUI()
    }

    override fun onStartInputView(
        info: EditorInfo?,
        restarting: Boolean,
    ) {
        super.onStartInputView(info, restarting)
        userDict.load()
        autoSpaceAllowed = allowsAutoSpace(info)
        isTypeNullField = (info?.inputType ?: InputType.TYPE_CLASS_TEXT) == InputType.TYPE_NULL
        editorHidesText = isTypeNullField || (info?.initialSelStart ?: 0) < 0
        if (!restarting) typedTail.setLength(0)
        isPasswordField = isPasswordInput(info)
        learningAllowed = !isPasswordField && (info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING == 0
        val prefs = getSharedPreferences("krkey_prefs", Context.MODE_PRIVATE)
        val oldScript = keyboardState.script
        val isCurrentEnabled = !keyboardState.script.isExperimental && prefs.getBoolean("script_${keyboardState.script.name}", keyboardState.script == BrahmiScript.DEVANAGARI)
        if (!isCurrentEnabled) {
            val enabled = BrahmiScript.values().filter { !it.isExperimental && prefs.getBoolean("script_${it.name}", it == BrahmiScript.DEVANAGARI) }
            val next = if (enabled.isNotEmpty()) enabled.first() else BrahmiScript.DEVANAGARI
            keyboardState = keyboardState.withScript(next)
            prefs.edit().putString("last_script", next.name).apply()
        }
        // Number/phone/date fields get the keypad; other fields get the full keyboard back
        val keypad = numericKeypadFor(info)
        keyboardState =
            if (keypad == null) {
                keyboardState.withoutNumericKeypad()
            } else {
                keyboardState.withNumericKeypad(phone = keypad == KeypadKind.PHONE)
            }
        if (!restarting || keyboardState.script != oldScript) {
            resetInputState()
        }
        currentClip = clipboardText()
        updateUI()
    }

    // Shows the clipboard's text as a chip in the suggestion bar; the bar shows it whenever there
    // are no candidates, so it can be pasted anywhere in the field. Called from updateUI.
    private fun refreshClipboardChip() {
        val view = candidateView ?: return
        val clip = if (isPasswordField) null else currentClip
        if (clip == null || clip == lastPastedClip) {
            view.setClipboardChip(null, null)
            return
        }
        // Just the icon: the clip's text is never shown (it may be sensitive anyway)
        view.setClipboardChip("📋") { pasteClip(clip) }
    }

    /** Plain text of the primary clip, if any. */
    private fun clipboardText(): String? {
        val clip =
            try {
                clipboard?.primaryClip
            } catch (e: SecurityException) {
                null
            } ?: return null
        if (clip.itemCount == 0) return null
        // Only real text: coerceToText() can do slow content-provider lookups for URIs
        val text = clip.getItemAt(0).text?.toString()
        return if (text.isNullOrBlank()) null else text
    }

    private fun pasteClip(text: String) {
        // After a swipe the pasted text starts a new word, like any typed word
        val space = if (spaceState == SpaceState.PHANTOM && Character.isLetterOrDigit(text.codePointAt(0))) " " else ""
        commitCurrentInput()
        commit(space + text)
        DebugLog.d { "paste from clipboard chip (${text.length} chars)" }
        lastPastedClip = text
        updateUI()
    }

    private enum class KeypadKind { NUMBER, PHONE }

    private fun numericKeypadFor(info: EditorInfo?): KeypadKind? =
        when ((info?.inputType ?: 0) and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_DATETIME -> KeypadKind.NUMBER
            InputType.TYPE_CLASS_PHONE -> KeypadKind.PHONE
            else -> null
        }

    private fun allowsAutoSpace(info: EditorInfo?): Boolean {
        val inputType = info?.inputType ?: return true
        if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        return inputType and InputType.TYPE_MASK_VARIATION !in NO_AUTO_SPACE_VARIATIONS
    }

    private fun isPasswordInput(info: EditorInfo?): Boolean {
        val inputType = info?.inputType ?: return false
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT -> variation in PASSWORD_TEXT_VARIATIONS
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    private fun updatePeckedCandidates() {
        if (keyboardState.mode.isSymbol() || keyboardState.mode.isNumeric() || currentPeckedWord.length < 2 || isPasswordField) {
            candidateView?.showCandidates(emptyList())
            return
        }
        predictionManager.ensurePredictor(keyboardState.mode.isLatin(), keyboardState.script, userDict.getLearnedWords())
        val matches = predictionManager.getPrefixMatches(currentPeckedWord.toString())
        val firstChar = currentPeckedWord.firstOrNull()
        candidateView?.showCandidates(matches, (firstChar != null && firstChar.isUpperCase()) || isSentenceStart())
    }

    private fun performGestureTyping(
        path: List<PointF>,
        times: List<Long>,
    ): Boolean {
        predictionManager.ensurePredictor(keyboardState.mode.isLatin(), keyboardState.script, userDict.getLearnedWords())
        val res = predictionManager.predictGesture(path)
        if (res.isNotEmpty()) {
            val word = res[0].first
            val shouldCaps = isSentenceStart()
            lastGestureWord = word
            val out = if (shouldCaps) word.replaceFirstChar { it.uppercase() } else word

            val ic = currentInputConnection ?: return true
            val space = if (needsPrecedingSpace()) " " else ""
            lastGestureHadSpace = space.isNotEmpty()
            commit(space + out)

            expectedSelStart = (ic.getTextBeforeCursor(10000, 0)?.length ?: 0)
            setSpaceState(SpaceState.PHANTOM)
            candidateView?.showCandidates(res.map { it.first }, shouldCaps)
            if (learningAllowed) {
                val keys = keyLocator.getKeyBounds().mapNotNull { (k, r) -> ((k.tag as? Pair<*, *>)?.first as? String)?.let { it.lowercase() to r } }
                swipeLogger.begin(keys, path, times, res)
            }
            return true
        }
        return false
    }

    private fun deleteLastChar() {
        val ic = currentInputConnection ?: return
        resetSpaceState()
        if (lastGestureWord != null) {
            // Only the word: the space before it stays, so a retyped word does not stick to the
            // previous one (as AOSP/FUTO); a second backspace removes the space
            DebugLog.d { "backspace: swiped word (space before it kept: $lastGestureHadSpace)" }
            deleteBefore(lastGestureWord!!)
            swipeLogger.finish("rejected", null)
            resetInputState()
            updateUI()
            return
        }
        if (!keyboardState.mode.isSymbol() && currentPeckedWord.isNotEmpty()) {
            val len = if (currentPeckedWord.length >= 2 && Character.isSurrogatePair(currentPeckedWord[currentPeckedWord.length - 2], currentPeckedWord[currentPeckedWord.length - 1])) 2 else 1
            DebugLog.d { "backspace: pecked character" }
            deleteBefore(currentPeckedWord.substring(currentPeckedWord.length - len))
            currentPeckedWord.delete(currentPeckedWord.length - len, currentPeckedWord.length)
            if (currentPeckedWord.isEmpty()) {
                candidateView?.showCandidates(emptyList())
                expectedSelStart = -1
            } else {
                if (expectedSelStart != -1) expectedSelStart -= len
                updatePeckedCandidates()
            }
            updateUI()
            return
        }
        // A selection is deleted as a whole, not the character before it
        if (!ic.getSelectedText(0).isNullOrEmpty()) {
            DebugLog.d { "backspace: selection" }
            ic.commitText("", 1)
            updateUI()
            return
        }
        val text = textBefore(2)
        val len = if (text != null && text.length == 2 && Character.isSurrogatePair(text[0], text[1])) 2 else 1
        if (isTypeNullField || editorHidesText || text.isNullOrEmpty()) {
            DebugLog.d { "backspace: key event (typeNull=$isTypeNullField hidesText=$editorHidesText empty=${text.isNullOrEmpty()})" }
            // Raw-key fields (remote input, terminals) and apps that react to backspace in an
            // empty field expect a key event, as other keyboards send
            sendDelKey()
            forgetTyped(len)
            updateUI()
            return
        }
        DebugLog.d { "backspace: character before cursor${if (isSpace(text.lastOrNull())) " (a space)" else ""}" }
        ic.deleteSurroundingText(len, 0)
        forgetTyped(len)
        updateUI()
    }

    private fun sendDelKey() {
        val ic = currentInputConnection ?: return
        DebugLog.d { "send KEYCODE_DEL" }
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
    }

    /** Text before the cursor: from the editor, or what we typed if the editor hides it. */
    private fun textBefore(n: Int): String? {
        if (editorHidesText) return typedTail.takeLast(n).toString()
        return currentInputConnection?.getTextBeforeCursor(n, 0)?.toString()
    }

    /** Commits text and remembers it (for editors that hide their text). */
    private fun commit(text: String) {
        val ic = currentInputConnection ?: return
        if (learningAllowed) DebugLog.d { "commit '$text' ${text.codePoints().toArray().joinToString(" ") { "U+%04X".format(it) }} hidesText=$editorHidesText typeNull=$isTypeNullField" }
        ic.commitText(text, 1)
        noteTyped(text)
    }

    private fun noteTyped(text: String) {
        typedTail.append(text)
        if (typedTail.length > TYPED_TAIL_MAX) typedTail.delete(0, typedTail.length - TYPED_TAIL_MAX)
    }

    private fun forgetTyped(chars: Int) {
        typedTail.setLength(maxOf(0, typedTail.length - chars))
    }

    // Deletes text we know is just before the cursor: by key events (one per code point) where
    // the editor handles raw keys or hides its text, otherwise directly
    private fun deleteBefore(text: String) {
        val ic = currentInputConnection ?: return
        if (isTypeNullField || editorHidesText) {
            repeat(text.codePointCount(0, text.length)) { sendDelKey() }
        } else {
            ic.deleteSurroundingText(text.length, 0)
        }
        forgetTyped(text.length)
    }

    private fun resetInputState() {
        currentPeckedWord.setLength(0)
        lastGestureWord = null
        lastGestureHadSpace = false
        expectedSelStart = -1
        resetSpaceState()
        swipeLogger.discard() // outcome unknown (e.g. cursor moved away)
        candidateView?.showCandidates(emptyList())
    }

    // Switching layout should not cancel automatic spacing
    private fun commitCurrentInputKeepingSpaceState() {
        val state = spaceState
        val anchor = spaceStateAnchor
        commitCurrentInput()
        spaceState = state
        spaceStateAnchor = anchor
    }

    private fun setSpaceState(state: SpaceState) {
        DebugLog.d { "spaceState $spaceState -> $state" }
        // Inserted spaces depend on the field type; a phantom space only mirrors what the
        // swipe itself does (needsPrecedingSpace), so it applies in any field
        spaceState = if (state == SpaceState.ANTIPHANTOM && !autoSpaceAllowed) SpaceState.NONE else state
        spaceStateAnchor = -1
        spaceStateSetAt = SystemClock.uptimeMillis()
    }

    // The automatic space is only acted on if it is still right before the cursor
    private fun hasAntiphantomSpace(): Boolean = spaceState == SpaceState.ANTIPHANTOM && isSpace(textBefore(1)?.lastOrNull())

    // A space as editors report it: web editors (Firefox, WebViews) store a trailing space as a
    // no-break space U+00A0, which Java's Character.isWhitespace does not count. Not a newline.
    private fun isSpace(c: Char?): Boolean = c != null && c != '\n' && (c == ' ' || Character.isSpaceChar(c))

    private fun resetSpaceState() {
        if (spaceState != SpaceState.NONE) DebugLog.d { "spaceState $spaceState -> NONE" }
        spaceState = SpaceState.NONE
        spaceStateAnchor = -1
    }

    private fun removeTrailingSpace() {
        val last = textBefore(1)
        if (isSpace(last?.lastOrNull())) deleteBefore(last!!.takeLast(1))
    }

    // Commits sentence punctuation, moving it in front of an automatic space and following it
    // with one: "word |" + "." -> "word. |"
    private fun commitPunctuation(
        text: String,
        cp: Int,
        prevState: SpaceState,
    ) {
        val ic = currentInputConnection ?: return
        val isOptionallySpaced = cp == ':'.code || cp == ';'.code
        var before = textBefore(64) ?: ""

        // Sentence punctuation goes in front of the space after a word, whether we inserted it or the
        // user did ("word |" + "." -> "word. |"), as FUTO does; never for ";" / ":"
        val endsWithSpace = isSpace(before.lastOrNull())
        val spaceAfterWord = before.length >= 2 && endsWithSpace && !before[before.length - 2].isWhitespace()
        val swapSpace =
            !isOptionallySpaced && endsWithSpace &&
                (prevState == SpaceState.ANTIPHANTOM || (autoSpaceAllowed && spaceAfterWord))
        ic.beginBatchEdit()
        if (swapSpace) {
            DebugLog.d { "punctuation: moved in front of the space (state=$prevState)" }
            deleteBefore(before.takeLast(1))
            before = before.dropLast(1)
        }
        // Keep "e.g." and "i.e." together instead of "e. g."
        if (cp == '.'.code && ABBREVIATION_SPLIT.containsMatchIn(before)) {
            val joined = before.takeLast(4).replace(" ", "")
            deleteBefore(before.takeLast(4))
            commit(joined)
            before = before.dropLast(4) + joined
        }
        commit(text)

        val last = before.lastOrNull()
        val next = ic.getTextAfterCursor(1, 0)?.firstOrNull()
        val followWithSpace =
            autoSpaceAllowed && last != null && !last.isWhitespace() && !last.isDigit() &&
                !looksLikeUrl(before + text) &&
                (next == null || isSpace(next) || next.code !in FOLLOWED_BY_SPACE)
        if (followWithSpace) {
            // An existing space after the cursor is re-inserted so the cursor ends up after it
            if (isSpace(next)) ic.deleteSurroundingText(0, 1)
            commit(" ")
            setSpaceState(SpaceState.ANTIPHANTOM)
        }
        ic.endBatchEdit()
    }

    private fun looksLikeUrl(text: String): Boolean {
        val token = text.substringAfterLast(' ').substringAfterLast('\n')
        return '@' in token || "://" in token || token.startsWith("www.") || ('.' in token && '/' in token)
    }

    private fun commitCurrentInput() {
        val wordToLearn = if (currentPeckedWord.isNotEmpty()) currentPeckedWord.toString() else lastGestureWord
        if (learningAllowed && wordToLearn != null && wordToLearn.length > 1) userDict.learnWord(wordToLearn)
        if (lastGestureWord != null) swipeLogger.finish("accepted", lastGestureWord)
        resetInputState()
    }

    private fun isSentenceStart(): Boolean {
        if (keyboardState.mode.isShifted()) return true
        var text = textBefore(50) ?: return true
        val currentWordLen = if (currentPeckedWord.isNotEmpty()) currentPeckedWord.length else 0
        if (currentWordLen > 0 && text.length >= currentWordLen) text = text.substring(0, text.length - currentWordLen)
        val trimmed = text.trimEnd()
        if (trimmed.isEmpty()) return true
        val lastChar = trimmed.last()
        return lastChar == '.' || lastChar == '?' || lastChar == '!' || lastChar == '।' || lastChar == '॥' || lastChar == '\n'
    }

    private fun pathDist(p: List<PointF>): Double {
        if (p.size < 2) return 0.0
        val dx = (p.last().x - p.first().x).toDouble()
        val dy = (p.last().y - p.first().y).toDouble()
        return Math.sqrt(dx * dx + dy * dy)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        landscapeMode = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        screenWidthDp = newConfig.screenWidthDp.toFloat()
        setInputView(onCreateInputView())
    }

    override fun onFinishInput() {
        super.onFinishInput()
        candidateView?.showCandidates(emptyList())
        keyboardState = keyboardState.clearBaseChar()
    }

    private fun setupCandidateClickListener() {
        candidateView?.setOnCandidateClickListener { displayWord, originalWord ->
            val ic = currentInputConnection ?: return@setOnCandidateClickListener

            val deleted =
                if (currentPeckedWord.isNotEmpty()) {
                    currentPeckedWord.toString()
                } else {
                    (if (lastGestureHadSpace) " " else "") + (lastGestureWord ?: "")
                }
            val deleteLen = deleted.length

            val space = if (needsPrecedingSpace(deleteLen)) " " else ""
            val before = textBefore(64) ?: ""
            val next = ic.getTextAfterCursor(1, 0)?.firstOrNull()
            // A picked word is followed by a space right away, unless punctuation already follows
            val trailingSpace = autoSpaceAllowed && !looksLikeUrl(before) && (next == null || isSpace(next) || next.code !in FOLLOWED_BY_SPACE)

            ic.beginBatchEdit()
            if (deleteLen > 0) deleteBefore(deleted)
            if (trailingSpace && isSpace(next)) ic.deleteSurroundingText(0, 1)
            commit(space + displayWord + (if (trailingSpace) " " else ""))
            ic.endBatchEdit()

            if (learningAllowed) userDict.learnWord(originalWord)
            if (lastGestureWord != null) swipeLogger.finish("picked", originalWord)
            resetInputState()
            setSpaceState(if (trailingSpace) SpaceState.ANTIPHANTOM else SpaceState.PHANTOM)
            updateUI()
        }
    }

    private fun needsPrecedingSpace(skipCount: Int = 0): Boolean {
        val text = textBefore(50 + skipCount) ?: ""
        if (text.length <= skipCount) return false
        val last = text[text.length - 1 - skipCount]
        if (learningAllowed) DebugLog.d { "needsPrecedingSpace: char before is U+%04X".format(last.code) }
        if (last.isWhitespace()) return false // Kotlin's isWhitespace includes U+00A0
        return last != '.' && last != '?' && last != '!' && last != '।' && last != '॥'
    }

    // Flick up on the space bar in an Indic layer (in Latin it is just a space)
    private fun isZwnjFlick(state: PointerState): Boolean = state.specialKey == SpecialKey.SPACE && !keyboardState.mode.isLatin() && isFlickGesture(state.gesturePath)

    private enum class FlickDirection { UP, LEFT, RIGHT, DOWN }

    // Direction of a flick on a four-direction key: the dominant axis, past the flick threshold
    private fun flickDirection(path: List<PointF>): FlickDirection? {
        if (path.size < 2) return null
        val dx = path.last().x - path.first().x
        val dy = path.last().y - path.first().y
        if (Math.hypot(dx.toDouble(), dy.toDouble()) < FLICK_VERTICAL_THRESHOLD_DP * density) return null
        return if (Math.abs(dx) > Math.abs(dy)) {
            if (dx < 0) FlickDirection.LEFT else FlickDirection.RIGHT
        } else {
            if (dy < 0) FlickDirection.UP else FlickDirection.DOWN
        }
    }

    /** Raw text a key gives for this touch path: base, up flick, or (four-direction keys) any direction. */
    private fun rawKeyOutput(
        key: FlickKeyView,
        path: List<PointF>,
    ): String {
        val fk = key.flickKey
        if (fk == null) {
            val tuple = key.tag as? Pair<*, *> ?: return ""
            return (if (isFlickGesture(path)) tuple.second else tuple.first) as String
        }
        val text =
            when (flickDirection(path)) {
                FlickDirection.UP -> fk.up
                FlickDirection.LEFT -> fk.left
                FlickDirection.RIGHT -> fk.right
                FlickDirection.DOWN -> fk.down
                null -> fk.base
            }
        return text.ifEmpty { fk.base }
    }

    // Rollover leaves a key mid-flick alone: any direction for four-direction keys, else upward
    private fun isKeyFlickInProgress(state: PointerState): Boolean =
        if (state.activeKey?.flickKey != null) {
            pathDist(state.gesturePath) > FLICK_ROLLOVER_SLOP_DP * density
        } else {
            isFlickInProgress(state.gesturePath)
        }

    private fun isFlickInProgress(path: List<PointF>): Boolean = path.size >= 2 && path.first().y - path.last().y > FLICK_ROLLOVER_SLOP_DP * density

    private fun isFlickGesture(path: List<PointF>): Boolean {
        if (path.size < 2) return false
        val start = path.first()
        val end = path.last()
        val dist = pathDist(path)
        val verticalDist = start.y - end.y
        val horizontalDist = Math.abs(end.x - start.x)
        return verticalDist > FLICK_VERTICAL_THRESHOLD_DP * density &&
            horizontalDist < verticalDist * FLICK_VERTICALITY_RATIO &&
            dist > FLICK_MIN_DISTANCE_DP * density
    }
}

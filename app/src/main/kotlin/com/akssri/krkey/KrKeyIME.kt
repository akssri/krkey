package com.akssri.krkey

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
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
    // State
    private var keyboardState = KeyboardState()
    private val currentPeckedWord = StringBuilder()
    private var lastGestureWord: String? = null
    private var lastGestureHadSpace: Boolean = false
    private var expectedSelStart: Int = -1

    // Components
    private lateinit var keyLocator: KeyLocator
    private lateinit var predictionManager: PredictionManager
    private lateinit var userDict: UserDictionaryManager
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
    private val FLICK_VERTICALITY_RATIO = 1.2f
    private val SWIPE_START_DISTANCE_DP = 50f
    private val SWIPE_FORCE_DISTANCE_DP = 100f
    private val SWIPE_NEW_KEY_RATIO = 0.6f

    private val SPACE_DRAG_THRESHOLD_DP = 10f
    private val SPACE_DRAG_STEP_DP = 20f
    private var spaceDragStartX = 0f
    private var lastSpaceDragX = 0f
    private var isSpaceDragging = false

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
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
        userDict = UserDictionaryManager(this)
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
        getSharedPreferences("krkey_prefs", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        loadFonts()
        val themedContext = ContextThemeWrapper(this, R.style.Theme_KrKey)
        val layout = LayoutInflater.from(themedContext).inflate(R.layout.keyboard_view, null) as LinearLayout

        val prefs = getSharedPreferences("krkey_prefs", MODE_PRIVATE)
        if (landscapeMode) {
            val heightDp = prefs.getInt("keyboard_height_landscape", 160)
            val containerHeightPx = (heightDp * density).toInt()
            keyHeightPx = (heightDp / 4.4f * density).toInt()

            val keyboardFrame = layout.findViewById<FrameLayout>(R.id.keyboard_frame)
            keyboardFrame.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, containerHeightPx
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
        return layout
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        val overlayEnabled = getSharedPreferences("krkey_prefs", MODE_PRIVATE)
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
                decorView.height
            )
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

    private fun setupSpecialKeyListeners() {
        specialKeyMap[SpecialKey.BACKSPACE]?.let { btn ->
            val handler = Handler(Looper.getMainLooper())
            val repeat =
                object : Runnable {
                    override fun run() {
                        deleteLastChar()
                        handler.postDelayed(this, 50)
                    }
                }
            btn.setOnTouchListener { _, ev ->
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
                        btn.isPressed = true
                        btn.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        deleteLastChar()
                        handler.postDelayed(repeat, 400)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        btn.isPressed = false
                        handler.removeCallbacks(repeat)
                        true
                    }
                    else -> false
                }
            }
        }

        spaceBtn?.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    spaceDragStartX = event.x
                    lastSpaceDragX = event.x
                    isSpaceDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - spaceDragStartX
                    if (!isSpaceDragging && Math.abs(dx) > SPACE_DRAG_THRESHOLD_DP * density) {
                        isSpaceDragging = true
                        lastSpaceDragX = event.x
                    }
                    if (isSpaceDragging) {
                        val step = SPACE_DRAG_STEP_DP * density
                        val diff = event.x - lastSpaceDragX
                        if (Math.abs(diff) > step) {
                            val ic = currentInputConnection
                            if (ic != null) {
                                val steps = (diff / step).toInt()
                                if (steps > 0) {
                                    for (i in 0 until steps) {
                                        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
                                        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
                                    }
                                } else if (steps < 0) {
                                    for (i in 0 until -steps) {
                                        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
                                        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT))
                                    }
                                }
                                lastSpaceDragX += steps * step
                            }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    if (!isSpaceDragging && event.actionMasked == MotionEvent.ACTION_UP) {
                        commitCurrentInput()
                        currentInputConnection?.commitText(" ", 1)
                        updateUI()
                    }
                    isSpaceDragging = false
                    true
                }
                else -> false
            }
        }

        specialKeyMap[SpecialKey.GLOBE]?.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            val prefs = getSharedPreferences("krkey_prefs", Context.MODE_PRIVATE)
            val enabled = BrahmiScript.values().filter { !it.isExperimental && prefs.getBoolean("script_${it.name}", it == BrahmiScript.DEVANAGARI) }

            commitCurrentInput()
            if (keyboardState.mode.isLatin()) {
                keyboardState = keyboardState.withScript(keyboardState.script).copy(mode = InputMode.IndicNormal)
            } else if (enabled.size > 1) {
                val next = enabled[(enabled.indexOf(keyboardState.script) + 1) % enabled.size]
                keyboardState = keyboardState.withScript(next)
                prefs.edit().putString("last_script", next.name).apply()
            }
            updateUI()
        }

        specialKeyMap[SpecialKey.ENTER]?.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            commitCurrentInput()
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            updateUI()
        }

        shiftBtn?.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            if (keyboardState.mode.isSymbol()) {
                keyboardState = keyboardState.toggleShift() // Shift symbol layer (Layer 2)
            } else {
                commitCurrentInput()
                keyboardState = keyboardState.toggleLanguage()
            }
            updateUI()
        }
        symBtn?.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            keyboardState = keyboardState.toggleSymbol()
            updateUI()
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
        if (lastGestureWord != null) commitCurrentInput()

        val isWordChar = !listOf("।", "॥", ".", ",", "!", "?", "/", "'", "\"", "\\", " ").contains(text)
        if (!keyboardState.mode.isSymbol() && isWordChar && text.isNotBlank()) {
            val ic = currentInputConnection ?: return
            if (currentPeckedWord.isEmpty() && expectedSelStart == -1) {
                expectedSelStart = ic.getTextBeforeCursor(10000, 0)?.length ?: 0
            }
            currentPeckedWord.append(text)
            ic.commitText(text, 1)
            expectedSelStart += text.length
            updatePeckedCandidates()
        } else {
            if (currentPeckedWord.isNotEmpty()) commitCurrentInput()
            currentInputConnection?.commitText(text, 1)
        }
        updateUI()
    }

    private fun updateUI() {
        updateBase()
        val mode = keyboardState.mode
        val script = keyboardState.script
        candidateBar?.visibility = if (!mode.isSymbol()) View.VISIBLE else View.GONE

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

        symBtn?.text =
            if (mode.isSymbol()) {
                if (mode.isLatin()) "abc" else "अल्".toBrahmiScript(script)
            } else {
                "१२३".toBrahmiScript(script)
            }

        shiftBtn?.text =
            if (mode.isSymbol()) {
                if (keyboardState.isShiftLocked) "⇪" else "⇧"
            } else if (mode.isLatin()) {
                script.firstSyllable
            } else {
                "EN"
            }

        spaceBtn?.text = if (mode.isLatin() && !mode.isSymbol()) "English" else script.nativeName
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
                        is Pair<*, *> -> {
                            if (poolIdx < flickKeyPool.size) {
                                flickKeyPool[poolIdx++].apply {
                                    tag = item
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
                    if (view.id == R.id.key_space) {
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
                val spacer = Space(this).apply {
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
    ): View? {
        val container = gestureTrailView?.rootView?.findViewById<ViewGroup>(R.id.keyboard_rows) ?: return null
        val r = Rect()
        for (v in specialKeyMap.values) {
            v.getDrawingRect(r)
            try {
                container.offsetDescendantRectToMyCoords(v, r)
                if (r.contains(x.toInt(), y.toInt())) return v
            } catch (e: Exception) {
            }
        }
        return null
    }

    private fun handleGlobalTouch(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)

        if ((action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) &&
            findSpecialKeyAt(event.getX(pointerIndex), event.getY(pointerIndex)) != null
        ) {
            return false
        }

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                // Ignore new touches if we are already swiping
                if (activePointers.values.any { it.isGestureTyping }) return true

                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)

                val state = PointerState()
                activePointers[pointerId] = state
                state.gesturePath.add(PointF(x, y))

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
                for (i in 0 until event.pointerCount) {
                    val pId = event.getPointerId(i)
                    val state = activePointers[pId] ?: continue
                    val px = event.getX(i)
                    val py = event.getY(i)
                    state.gesturePath.add(PointF(px, py))

                    if (!keyboardState.mode.isLatin() || keyboardState.mode.isSymbol()) {
                        // Non-gesture mode
                        state.activeKey?.let { k ->
                            val tuple = k.tag as? Pair<*, *>
                            val scriptData = ScriptManager.getScriptData(keyboardState.script)
                            val base = formatKeyText(tuple?.first as? String ?: "", keyboardState.currentBaseChar, scriptData)
                            val flick = formatKeyText(tuple?.second as? String ?: "", keyboardState.currentBaseChar, scriptData)
                            k.showPopup(if (isFlickGesture(state.gesturePath)) flick else base)
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

                                val text = if (verticalDist > FLICK_VISUAL_THRESHOLD_DP * density) flick else base
                                if (text != state.lastPopupText) {
                                    state.lastPopupText = text
                                    k.showPopup(text)
                                }
                            }

                            if (splitGapWeight == 0f &&
                                ((pathDist(state.gesturePath) > SWIPE_START_DISTANCE_DP * density && !isLikelyFlick) ||
                                (movedToNewKey && !isLikelyFlick) ||
                                pathDist(state.gesturePath) > SWIPE_FORCE_DISTANCE_DP * density)
                            ) {
                                state.isGestureTyping = true
                                // Cancel all other active touches to prevent accidental pecks while swiping
                                activePointers.forEach { (id, pState) ->
                                    if (id != pId) pState.isCanceled = true
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
                        if (state.isGestureTyping) gestureTrailView?.addPoint(px, py)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val state = activePointers.remove(pointerId) ?: return true
                val finalKey = state.activeKey
                finalKey?.let {
                    it.isPressed = false
                    it.dismissPopup()
                }

                if (state.isGestureTyping) {
                    gestureTrailView?.visibility = View.GONE
                    performGestureTyping(state.gesturePath)
                } else if (!state.isCanceled && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP)) {
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

    private fun handleFlickOrTap(
        path: List<PointF>,
        key: FlickKeyView?,
    ) {
        if (path.isEmpty() || key == null) return
        val tuple = key.tag as? Pair<*, *> ?: return
        val scriptData = ScriptManager.getScriptData(keyboardState.script)
        val isFlick = isFlickGesture(path)
        val raw = if (isFlick) tuple.second as String else tuple.first as String
        onKeyInput(getCleanOutput(raw, scriptData))
    }

    private fun updateBase() {
        val ic = currentInputConnection ?: return
        val text = ic.getTextBeforeCursor(2, 0)
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
        val prefs = getSharedPreferences("krkey_prefs", Context.MODE_PRIVATE)
        val oldScript = keyboardState.script
        val isCurrentEnabled = !keyboardState.script.isExperimental && prefs.getBoolean("script_${keyboardState.script.name}", keyboardState.script == BrahmiScript.DEVANAGARI)
        if (!isCurrentEnabled) {
            val enabled = BrahmiScript.values().filter { !it.isExperimental && prefs.getBoolean("script_${it.name}", it == BrahmiScript.DEVANAGARI) }
            val next = if (enabled.isNotEmpty()) enabled.first() else BrahmiScript.DEVANAGARI
            keyboardState = keyboardState.withScript(next)
            prefs.edit().putString("last_script", next.name).apply()
        }
        predictionManager.clearCache()
        if (!restarting || keyboardState.script != oldScript) {
            resetInputState()
        }
        updateUI()
    }

    private fun updatePeckedCandidates() {
        if (keyboardState.mode.isSymbol() || currentPeckedWord.length < 2) {
            candidateView?.showCandidates(emptyList())
            return
        }
        predictionManager.ensurePredictor(keyboardState.mode.isLatin(), keyboardState.script, userDict.getLearnedWords())
        val matches = predictionManager.getPrefixMatches(currentPeckedWord.toString())
        val firstChar = currentPeckedWord.firstOrNull()
        candidateView?.showCandidates(matches, (firstChar != null && firstChar.isUpperCase()) || isSentenceStart())
    }

    private fun performGestureTyping(path: List<PointF>): Boolean {
        predictionManager.ensurePredictor(keyboardState.mode.isLatin(), keyboardState.script, userDict.getLearnedWords())
        val res = predictionManager.predictGesture(path)
        if (res.isNotEmpty() && res[0].second <= 8.0) {
            val word = res[0].first
            val shouldCaps = isSentenceStart()
            lastGestureWord = word
            val out = if (shouldCaps) word.replaceFirstChar { it.uppercase() } else word

            val ic = currentInputConnection ?: return true
            val space = if (needsPrecedingSpace()) " " else ""
            lastGestureHadSpace = space.isNotEmpty()
            ic.commitText(space + out, 1)

            expectedSelStart = (ic.getTextBeforeCursor(10000, 0)?.length ?: 0)
            candidateView?.showCandidates(res.map { it.first }, shouldCaps)
            return true
        }
        return false
    }

    private fun deleteLastChar() {
        val ic = currentInputConnection ?: return
        if (lastGestureWord != null) {
            ic.deleteSurroundingText(lastGestureWord!!.length + (if (lastGestureHadSpace) 1 else 0), 0)
            resetInputState()
            updateUI()
            return
        }
        if (!keyboardState.mode.isSymbol() && currentPeckedWord.isNotEmpty()) {
            val len = if (currentPeckedWord.length >= 2 && Character.isSurrogatePair(currentPeckedWord[currentPeckedWord.length - 2], currentPeckedWord[currentPeckedWord.length - 1])) 2 else 1
            currentPeckedWord.delete(currentPeckedWord.length - len, currentPeckedWord.length)
            ic.deleteSurroundingText(len, 0)
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
        val text = ic.getTextBeforeCursor(2, 0) ?: return
        val len = if (text.length == 2 && Character.isSurrogatePair(text[0], text[1])) 2 else 1
        ic.deleteSurroundingText(len, 0)
        updateUI()
    }

    private fun resetInputState() {
        currentPeckedWord.setLength(0)
        lastGestureWord = null
        lastGestureHadSpace = false
        expectedSelStart = -1
        candidateView?.showCandidates(emptyList())
    }

    private fun commitCurrentInput() {
        val wordToLearn = if (currentPeckedWord.isNotEmpty()) currentPeckedWord.toString() else lastGestureWord
        if (wordToLearn != null && wordToLearn.length > 1) userDict.learnWord(wordToLearn)
        resetInputState()
    }

    private fun isSentenceStart(): Boolean {
        if (keyboardState.mode.isShifted()) return true
        var text = currentInputConnection?.getTextBeforeCursor(50, 0)?.toString() ?: return true
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

            val deleteLen =
                if (currentPeckedWord.isNotEmpty()) {
                    currentPeckedWord.length
                } else {
                    (lastGestureWord?.length ?: 0) + (if (lastGestureHadSpace) 1 else 0)
                }

            val space = if (needsPrecedingSpace(deleteLen)) " " else ""

            ic.beginBatchEdit()
            if (deleteLen > 0) ic.deleteSurroundingText(deleteLen, 0)
            ic.commitText(space + displayWord, 1)
            ic.endBatchEdit()

            userDict.learnWord(originalWord)
            resetInputState()
            updateUI()
        }
    }

    private fun needsPrecedingSpace(skipCount: Int = 0): Boolean {
        val ic = currentInputConnection ?: return false
        val text = ic.getTextBeforeCursor(50 + skipCount, 0)?.toString() ?: ""
        if (text.length <= skipCount) return false
        val last = text[text.length - 1 - skipCount]
        if (Character.isWhitespace(last)) return false
        return last != '.' && last != '?' && last != '!' && last != '।' && last != '॥'
    }

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

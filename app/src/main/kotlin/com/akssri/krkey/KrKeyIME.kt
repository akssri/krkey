package com.akssri.krkey

import android.content.Context
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
import android.widget.LinearLayout
import androidx.core.content.res.ResourcesCompat
import com.akssri.krkey.gesture.GestureDetector
import com.akssri.krkey.gesture.TouchHandler
import com.akssri.krkey.location.KeyLocator
import com.akssri.krkey.prediction.PredictionManager
import com.akssri.krkey.state.InputMode
import com.akssri.krkey.state.KeyboardState
import com.akssri.krkey.ui.CandidateView

class KrKeyIME : InputMethodService(), FlickKeyView.OnKeyListener {

    // State
    private var keyboardState = KeyboardState()
    private val currentPeckedWord = StringBuilder()
    private var lastGestureWord: String? = null  // Last word inserted by gesture typing (for candidate replacement)
    private var expectedSelStart: Int = -1

    // Components
    private lateinit var gestureDetector: GestureDetector
    private lateinit var keyLocator: KeyLocator
    private lateinit var touchHandler: TouchHandler
    private lateinit var predictionManager: PredictionManager
    private lateinit var userDict: UserDictionaryManager
    private var candidateView: CandidateView? = null

    // Views
    private var allKeys: List<FlickKeyView> = emptyList()
    private var shiftBtn: Button? = null
    private var symBtn: Button? = null
    private var spaceBtn: Button? = null
    private var gestureTrailView: GestureTrailView? = null
    private var candidateBar: View? = null

    // Layout engine
    private val viewPool = mutableMapOf<Int, View>()
    private var currentLayoutGrid: List<List<Int>>? = null

    // Typefaces for non-standard scripts
    private var siddhamTypeface: Typeface? = null
    private var granthaTypeface: Typeface? = null
    private var sharadaTypeface: Typeface? = null
    private var brahmiTypeface: Typeface? = null

    // Gesture state (Indic flick mode)
    private var capturedKey: FlickKeyView? = null
    private val gesturePath = mutableListOf<PointF>()

    // Gesture state (Latin swipe mode)
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var activeKey: FlickKeyView? = null
    private var isGestureTyping = false
    private var lastPopupText: String? = null
    private var density = 1f

    // Gesture tuning
    private val FLICK_VERTICAL_THRESHOLD_DP = 15f   // Minimum upward travel to register a flick
    private val FLICK_VISUAL_THRESHOLD_DP = 10f     // Upward travel to change popup from base to flick
    private val FLICK_MIN_DISTANCE_DP = 10f         // Minimum total path distance for a flick
    private val FLICK_VERTICALITY_RATIO = 1.2f      // Allowed horizontal drift (higher = lazier diagonal flicks)
    private val SWIPE_START_DISTANCE_DP = 50f       // Horizontal distance to trigger gesture typing
    private val SWIPE_FORCE_DISTANCE_DP = 100f      // Path distance that forces gesture typing
    private val SWIPE_NEW_KEY_RATIO = 0.6f          // Key width ratio to trigger gesture typing

    private val prefixable = "़कखगघङचछजझञटठडढणतथदधनपफबभमयरलवशषसहळअआइईउऊऋॠऌॡएऐओऔ"

    private fun setupCandidateClickListener() {
        candidateView?.setOnCandidateClickListener { displayWord, originalWord ->
            val ic = currentInputConnection ?: return@setOnCandidateClickListener
            if (currentPeckedWord.isNotEmpty() && displayWord.startsWith(currentPeckedWord)) {
                // Prefix match: just append the suffix (avoids delete+rewrite blink)
                val suffix = displayWord.substring(currentPeckedWord.length)
                if (suffix.isNotEmpty()) ic.commitText(suffix, 1)
            } else {
                // Gesture word or mismatch: full replace
                val deleteLen = if (currentPeckedWord.isNotEmpty()) currentPeckedWord.length
                                else (lastGestureWord?.length ?: 0)
                ic.beginBatchEdit()
                if (deleteLen > 0) ic.deleteSurroundingText(deleteLen, 0)
                ic.commitText(displayWord, 1)
                ic.endBatchEdit()
            }
            userDict.learnWord(originalWord)
            currentPeckedWord.setLength(0)
            lastGestureWord = null
            expectedSelStart = -1
            candidateView?.showCandidates(emptyList())
            updateBase()
            updateUI()
        }
    }

    private fun setupTouchHandlerCallbacks() {
        touchHandler.setCallbacks(object : TouchHandler.Callbacks {
            override fun onKeyPress(key: FlickKeyView, displayText: String) {
                key.showPopup(displayText)
            }

            override fun onKeyRelease(key: FlickKeyView, text: String, isFlick: Boolean) {
                onKeyInput(key, text, isFlick)
            }

            override fun onGestureUpdate(path: List<PointF>, isActive: Boolean) {
                if (isActive) {
                    gestureTrailView?.visibility = View.VISIBLE
                    gestureTrailView?.setPoints(path)
                    candidateView?.showCandidates(emptyList())
                } else {
                    gestureTrailView?.visibility = View.GONE
                    gestureTrailView?.clear()
                }
            }

            override fun onGestureComplete(path: List<PointF>) {
                gestureTrailView?.visibility = View.GONE
                performGestureTyping()
                gestureTrailView?.clear()
            }

            override fun getKeyDisplayText(key: FlickKeyView, isFlickActive: Boolean): Pair<String, String> {
                val scriptData = ScriptManager.getScriptData(keyboardState.script)
                val cfg = scriptData.keyConfigs[key.id] ?: return Pair("", "")
                val (base, flick) = cfg.getResolvedStrings(
                    keyboardState.mode, keyboardState.currentBaseChar, scriptData
                )
                return Pair(base, flick)
            }

            override fun getKeyCommitText(key: FlickKeyView, isFlick: Boolean): String {
                val scriptData = ScriptManager.getScriptData(keyboardState.script)
                val cfg = scriptData.keyConfigs[key.id] ?: return ""
                return getCleanOutput(cfg, isFlick)
            }

            override fun isGestureTypingEnabled(): Boolean {
                return keyboardState.mode.isLatin() && !keyboardState.mode.isSymbol()
            }
        })
    }

    override fun onCreate() {
        super.onCreate()

        density = resources.displayMetrics.density
        userDict = UserDictionaryManager(this)

        val lastScript = getSharedPreferences("krkey_prefs", MODE_PRIVATE)
            .getString("last_script", BrahmiScript.DEVANAGARI.name)
        val script = try {
            BrahmiScript.valueOf(lastScript!!)
        } catch (e: Exception) {
            BrahmiScript.DEVANAGARI
        }

        // Initialize state
        keyboardState = KeyboardState(script = script)
    }

    override fun onCreateInputView(): View {
        loadFonts()
        val themedContext = ContextThemeWrapper(this, R.style.Theme_KrKey)
        val layout = LayoutInflater.from(themedContext).inflate(R.layout.keyboard_view, null) as LinearLayout
        candidateBar = layout.findViewById(R.id.candidate_bar)
        gestureTrailView = layout.findViewById(R.id.gesture_trail)
        allKeys = findAllFlickKeys(layout)
        
        // Cache all keys for the dynamic layout engine
        allKeys.forEach { viewPool[it.id] = it }
        listOf(R.id.key_shift, R.id.key_backspace, R.id.key_sym, R.id.key_globe, R.id.key_space, R.id.key_enter).forEach { id ->
            layout.findViewById<View>(id)?.let { viewPool[id] = it }
        }

        candidateView = layout.findViewById(R.id.candidate_view)
        val container = layout.findViewById<ViewGroup>(R.id.keyboard_rows)

        gestureDetector = GestureDetector(density)
        keyLocator = KeyLocator(allKeys)
        keyLocator.initialize(container)

        touchHandler = TouchHandler(keyLocator, gestureDetector)
        setupTouchHandlerCallbacks()

        predictionManager = PredictionManager(this, assets, keyLocator, allKeys, container)
        setupCandidateClickListener()

        setupSpecialKeys(layout)

        val rowsContainer = layout.findViewById<View>(R.id.keyboard_rows).parent as View
        rowsContainer.setOnTouchListener { _, event -> handleGlobalTouch(event) }
        updateUI()
        return layout
    }

    private fun loadFonts() {
        try {
            siddhamTypeface = ResourcesCompat.getFont(this, R.font.noto_sans_siddham)
            granthaTypeface = ResourcesCompat.getFont(this, R.font.noto_sans_grantha)
            sharadaTypeface = ResourcesCompat.getFont(this, R.font.noto_sans_sharada)
            brahmiTypeface = ResourcesCompat.getFont(this, R.font.noto_sans_brahmi)
        } catch (e: Exception) {}
    }

    private fun findAllFlickKeys(view: View): List<FlickKeyView> {
        val keys = mutableListOf<FlickKeyView>()
        if (view is FlickKeyView) keys.add(view)
        else if (view is ViewGroup) {
            for (i in 0 until view.childCount) keys.addAll(findAllFlickKeys(view.getChildAt(i)))
        }
        return keys
    }

    private fun moveCursor(count: Int) {
        val ic = currentInputConnection ?: return

        // Commit current word before moving cursor
        if (currentPeckedWord.isNotEmpty() || lastGestureWord != null) {
            commitCurrentInput()
        }

        val absCount = Math.abs(count)

        for (i in 0 until absCount) {
            if (count > 0) {
                val after = ic.getTextAfterCursor(1, 0)
                if (!after.isNullOrEmpty()) {
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
                }
            } else {
                val before = ic.getTextBeforeCursor(1, 0)
                if (!before.isNullOrEmpty()) {
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT))
                }
            }
        }
    }

    private fun setupSpecialKeys(layout: View) {
        layout.findViewById<Button>(R.id.key_backspace)?.let { btn ->
            val handler = Handler(Looper.getMainLooper())
            val repeat = object : Runnable {
                override fun run() { deleteLastChar(); handler.postDelayed(this, 50) }
            }
            btn.setOnTouchListener { _, ev ->
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
                        btn.isPressed = true; btn.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        deleteLastChar(); handler.postDelayed(repeat, 400); true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        btn.isPressed = false; handler.removeCallbacks(repeat); true
                    }
                    else -> false
                }
            }
        }
        
        spaceBtn = layout.findViewById(R.id.key_space)
        var spaceInitialX = 0f
        var totalMoveX = 0f
        var isMovingCursor = false
        val moveThreshold = 30f // pixels per cursor move

        spaceBtn?.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    spaceInitialX = event.x
                    totalMoveX = 0f
                    isMovingCursor = false
                    v.isPressed = true
                    false // allow long press / click
                }
                MotionEvent.ACTION_MOVE -> {
                    val diffX = event.x - spaceInitialX
                    totalMoveX += diffX
                    spaceInitialX = event.x

                    if (Math.abs(totalMoveX) > moveThreshold) {
                        isMovingCursor = true
                        val count = (totalMoveX / moveThreshold).toInt()
                        moveCursor(count)
                        totalMoveX -= count * moveThreshold
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    if (event.action == MotionEvent.ACTION_UP && !isMovingCursor) {
                        v.performClick()
                    }
                    true
                }
                else -> false
            }
        }

        spaceBtn?.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            commitCurrentInput(); currentInputConnection?.commitText(" ", 1)
            updateBase(); updateUI()
        }
        layout.findViewById<Button>(R.id.key_globe)?.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            val prefs = getSharedPreferences("krkey_prefs", Context.MODE_PRIVATE)
            val enabled = BrahmiScript.values().filter {
                !it.isExperimental && prefs.getBoolean("script_${it.name}", it == BrahmiScript.DEVANAGARI)
            }
            
            commitCurrentInput()
            if (keyboardState.mode.isLatin()) {
                // Return to Indic mode
                keyboardState = keyboardState.copy(mode = InputMode.IndicNormal)
                updateBase()
                updateUI()
            } else if (enabled.size > 1) {
                // Cycle through Indic scripts
                val currentScript = keyboardState.script
                val newScript = enabled[(enabled.indexOf(currentScript) + 1) % enabled.size]
                
                keyboardState = keyboardState.withScript(newScript)
                prefs.edit().putString("last_script", newScript.name).apply()
                updateBase()
                updateUI()
            }
        }
        layout.findViewById<Button>(R.id.key_enter)?.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            commitCurrentInput(); currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            updateBase(); updateUI()
        }
        shiftBtn = layout.findViewById(R.id.key_shift)
        shiftBtn?.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            if (keyboardState.mode.isSymbol()) {
                keyboardState = keyboardState.toggleShift() // Shift symbol layer (Layer 2)
            } else if (keyboardState.mode.isLatin()) {
                keyboardState = keyboardState.toggleShift() // Shift Latin (Capitalization)
            } else {
                commitCurrentInput()
                keyboardState = keyboardState.toggleLanguage() // Swap Indic -> EN
                updateBase()
            }
            updateUI()
        }
        symBtn = layout.findViewById(R.id.key_sym)
        symBtn?.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            val wasLatinMode = keyboardState.mode.isLatin()
            keyboardState = keyboardState.toggleSymbol(wasLatinMode)
            updateUI()
        }
    }

    private fun isFlickGesture(path: List<PointF>): Boolean {
        if (path.size < 2) return false
        val start = path.first(); val end = path.last()
        val dist = pathDist(path)
        
        val verticalDist = start.y - end.y // Positive is upward
        val horizontalDist = Math.abs(end.x - start.x)
        
        return verticalDist > FLICK_VERTICAL_THRESHOLD_DP * density && 
               horizontalDist < verticalDist * FLICK_VERTICALITY_RATIO && 
               dist > FLICK_MIN_DISTANCE_DP * density
    }

    private fun getCleanOutput(cfg: KeyConfig, isFlick: Boolean): String {
        val scriptData = ScriptManager.getScriptData(keyboardState.script)
        val (b, f) = cfg.getResolvedStrings(
            keyboardState.mode, keyboardState.currentBaseChar, scriptData
        )
        val out = if (isFlick) f else b
        val currentBaseChar = keyboardState.currentBaseChar

        return if (currentBaseChar.isNotEmpty() && out.startsWith(currentBaseChar) && out.length > currentBaseChar.length) {
            // It's a combined form (e.g., prefix 'ಕ' + matra 'ಿ' = 'ಕಿ'), so strip the prefix
            out.substring(currentBaseChar.length)
        } else if (out.startsWith("◌")) {
            // It's a placeholder form (e.g., '◌ಿ'), strip the circle
            out.substring(1)
        } else {
            // It's a standalone character (e.g., repeat consonant 'ಕ' when prefix is 'ಕ')
            out
        }
    }

    private fun findSpecialKeyAt(x: Float, y: Float): View? {
        val container = gestureTrailView?.rootView?.findViewById<ViewGroup>(R.id.keyboard_rows) ?: return null
        val r = Rect()
        val specialIds = listOf(R.id.key_shift, R.id.key_backspace, R.id.key_sym, R.id.key_globe, R.id.key_space, R.id.key_enter)
        for (id in specialIds) {
            val v = viewPool[id] ?: continue
            v.getDrawingRect(r)
            try {
                container.offsetDescendantRectToMyCoords(v, r)
                if (r.contains(x.toInt(), y.toInt())) return v
            } catch (e: Exception) {}
        }
        return null
    }

    private fun handleGlobalTouch(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            if (findSpecialKeyAt(event.x, event.y) != null) return false
        }

        if (!keyboardState.mode.isLatin() || keyboardState.mode.isSymbol()) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    capturedKey = keyLocator.findKeyAt(event.x, event.y)
                    capturedKey?.let {
                        it.isPressed = true
                        it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        val scriptData = ScriptManager.getScriptData(keyboardState.script)
                        val cfg = scriptData.keyConfigs[it.id] ?: return@let
                        val (b, _) = cfg.getResolvedStrings(
                            keyboardState.mode, keyboardState.currentBaseChar, scriptData
                        )
                        it.showPopup(b)
                    }
                    gesturePath.clear(); gesturePath.add(PointF(event.x, event.y))
                }
                MotionEvent.ACTION_MOVE -> {
                    gesturePath.add(PointF(event.x, event.y))
                    capturedKey?.let { k ->
                        val scriptData = ScriptManager.getScriptData(keyboardState.script)
                        val cfg = scriptData.keyConfigs[k.id] ?: return@let
                        val (b, f) = cfg.getResolvedStrings(
                            keyboardState.mode, keyboardState.currentBaseChar, scriptData
                        )
                        k.showPopup(if (isFlickGesture(gesturePath)) f else b)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    capturedKey?.let {
                        it.isPressed = false; it.dismissPopup()
                        val scriptData = ScriptManager.getScriptData(keyboardState.script)
                        val cfg = scriptData.keyConfigs[it.id] ?: return@let
                        val isFlick = isFlickGesture(gesturePath)
                        val clean = getCleanOutput(cfg, isFlick)
                        onKeyInput(it, clean, isFlick)
                    }
                    capturedKey = null
                }
            }
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                gesturePath.clear()
                gesturePath.add(PointF(event.x, event.y))
                isGestureTyping = false
                activeKey = keyLocator.findKeyAt(event.x, event.y)
                activeKey?.let { k ->
                    k.isPressed = true
                    k.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    val scriptData = ScriptManager.getScriptData(keyboardState.script)
                    val cfg = scriptData.keyConfigs[k.id] ?: return@let
                    val (b, _) = cfg.getResolvedStrings(
                        keyboardState.mode, keyboardState.currentBaseChar, scriptData
                    )
                    lastPopupText = b
                    k.showPopup(b)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val idx = event.findPointerIndex(activePointerId)
                if (idx < 0) return true
                
                val px = event.getX(idx)
                val py = event.getY(idx)
                gesturePath.add(PointF(px, py))
                
                if (!isGestureTyping) {
                    val currentKey = keyLocator.findKeyAt(px, py)
                    val startPos = gesturePath[0]
                    
                    val verticalDist = startPos.y - py // Positive is upward
                    val horizontalDist = Math.abs(px - startPos.x)
                    
                    val movedToNewKey = currentKey != null && currentKey != activeKey && horizontalDist > (activeKey?.width ?: 0) * SWIPE_NEW_KEY_RATIO
                    val isLikelyFlick = verticalDist > 0 && horizontalDist < verticalDist * FLICK_VERTICALITY_RATIO
                    
                    activeKey?.let { k ->
                        val scriptData = ScriptManager.getScriptData(keyboardState.script)
                        val cfg = scriptData.keyConfigs[k.id] ?: return@let
                        val (b, f) = cfg.getResolvedStrings(
                            keyboardState.mode, keyboardState.currentBaseChar, scriptData
                        )
                        val text = if (verticalDist > FLICK_VISUAL_THRESHOLD_DP * density) f else b
                        if (text != lastPopupText) {
                            lastPopupText = text
                            k.showPopup(text)
                        }
                    }
                    if ((pathDist(gesturePath) > SWIPE_START_DISTANCE_DP * density && !isLikelyFlick) || 
                        (movedToNewKey && !isLikelyFlick) || 
                        pathDist(gesturePath) > SWIPE_FORCE_DISTANCE_DP * density) {
                        isGestureTyping = true
                        commitCurrentInput()
                        
                        val before = currentInputConnection?.getTextBeforeCursor(1, 0)
                        if (!before.isNullOrEmpty() && !".?! \n".contains(before.last())) {
                            currentInputConnection?.commitText(" ", 1)
                        }

                        activeKey?.let { 
                            it.isPressed = false
                            it.dismissPopup() 
                        }
                        gestureTrailView?.visibility = View.VISIBLE
                        gestureTrailView?.setPoints(gesturePath)
                        showCandidates(emptyList())
                    }
                }
                if (isGestureTyping) {
                    gestureTrailView?.addPoint(px, py)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val finalKey = activeKey
                activeKey?.let { 
                    it.isPressed = false
                    it.dismissPopup() 
                }
                activeKey = null
                activePointerId = MotionEvent.INVALID_POINTER_ID
                
                if (isGestureTyping) {
                    gestureTrailView?.visibility = View.GONE
                    performGestureTyping()
                } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                    handleFlickOrTap(gesturePath, finalKey)
                }
                
                isGestureTyping = false
                gestureTrailView?.clear()
            }
        }
        return true
    }

    private fun handleFlickOrTap(path: List<PointF>, key: FlickKeyView?) {
        if (path.isEmpty() || key == null) return
        val scriptData = ScriptManager.getScriptData(keyboardState.script)
        val cfg = scriptData.keyConfigs[key.id] ?: return
        val isFlick = isFlickGesture(path)
        val clean = getCleanOutput(cfg, isFlick)
        onKeyInput(key, clean, isFlick)
    }

    private fun updateUI() {
        val mode = keyboardState.mode
        val script = keyboardState.script

        candidateBar?.visibility = if (!mode.isSymbol()) View.VISIBLE else View.GONE

        val tf = if (!mode.isLatin()) {
            when (script) {
                BrahmiScript.SIDDHAM -> siddhamTypeface
                BrahmiScript.GRANTHA -> granthaTypeface
                BrahmiScript.SHARADA -> sharadaTypeface
                BrahmiScript.BRAHMI -> brahmiTypeface
                else -> Typeface.DEFAULT
            }
        } else Typeface.DEFAULT

        shiftBtn?.text = if (mode.isSymbol() || mode.isLatin()) {
            if (keyboardState.isShiftLocked) "⇪" else "⇧"
        } else "EN"

        symBtn?.text = if (mode.isSymbol()) {
            if (mode.isLatin()) "abc" else "अल्".toBrahmiScript(script)
        } else {
            "१२३".toBrahmiScript(script)
        }

        spaceBtn?.text = if (mode.isLatin() && !mode.isSymbol()) "English" else script.nativeName
        spaceBtn?.typeface = tf

        val scriptData = ScriptManager.getScriptData(keyboardState.script)
        
        // Check if layout needs rebuilding (e.g. switching to/from Tamil)
        val targetLayout = if (mode.isLatin()) baseLayout else scriptData.layout
        if (currentLayoutGrid != targetLayout) {
            rebuildKeyboardGrid(targetLayout)
        }

        allKeys.forEach { k ->
            k.setTypeface(tf)
            val cfg = scriptData.keyConfigs[k.id] ?: return@forEach
            val (b, f) = cfg.getResolvedStrings(
                keyboardState.mode, keyboardState.currentBaseChar, scriptData
            )
            k.setVisualState(b, f, false)
        }
    }

    private fun rebuildKeyboardGrid(layout: List<List<Int>>) {
        val rootLayout = this.window.window?.decorView ?: return
        val container = rootLayout.findViewById<LinearLayout>(R.id.keyboard_rows) ?: return
        
        container.removeAllViews()

        for (rowIds in layout) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            
            for (id in rowIds) {
                val keyView = viewPool[id] ?: continue
                
                // Remove view from its previous parent row if it has one
                (keyView.parent as? ViewGroup)?.removeView(keyView)
                
                // Ensure it keeps its relative weight mapping from original instantiation
                if (keyView.layoutParams == null) {
                    keyView.layoutParams = LinearLayout.LayoutParams(0, (50f * density).toInt(), 1f).apply {
                        setMargins((3f * density).toInt(), (3f * density).toInt(), (3f * density).toInt(), (3f * density).toInt())
                    }
                }
                
                rowLayout.addView(keyView)
            }
            container.addView(rowLayout)
        }
        
        // Update allKeys to only contain FlickKeyViews in the current layout
        val activeKeyIds = layout.flatten().toSet()
        allKeys = viewPool.values.filterIsInstance<FlickKeyView>().filter { it.id in activeKeyIds }
        keyLocator.initialize(container, allKeys)
        container.post {
            keyLocator.rebuildCache()
        }
        currentLayoutGrid = layout
    }

    private fun updateBase() {
        val ic = currentInputConnection ?: return
        val text = ic.getTextBeforeCursor(2, 0)
        val baseChar = if (!text.isNullOrEmpty()) {
            val last = if (text.length >= 2 && Character.isSurrogatePair(text[text.length-2], text[text.length-1])) {
                text.substring(text.length-2)
            } else {
                text.substring(text.length-1)
            }
            if (prefixable.toBrahmiScript(keyboardState.script).contains(last)) last else ""
        } else ""

        keyboardState = keyboardState.withBaseChar(baseChar)
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)

        // Detect cursor jumps (user tapped elsewhere) or text selection to abandon current word
        val cursorJumped = expectedSelStart != -1 && Math.abs(newSelStart - expectedSelStart) > 2
        val hasSelection = newSelStart != newSelEnd
        if (hasSelection || cursorJumped) {
            currentPeckedWord.setLength(0)
            lastGestureWord = null
            expectedSelStart = -1
            showCandidates(emptyList())
        }

        updateBase()
        updateUI()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        userDict.load()

        // Validate that the current script is still enabled (user might have disabled it in Settings)
        val prefs = getSharedPreferences("krkey_prefs", Context.MODE_PRIVATE)
        val isCurrentEnabled = !keyboardState.script.isExperimental && 
                               prefs.getBoolean("script_${keyboardState.script.name}", keyboardState.script == BrahmiScript.DEVANAGARI)
        
        val oldScript = keyboardState.script
        if (!isCurrentEnabled) {
            val enabled = BrahmiScript.values().filter {
                !it.isExperimental && prefs.getBoolean("script_${it.name}", it == BrahmiScript.DEVANAGARI)
            }
            val newScript = if (enabled.isNotEmpty()) enabled.first() else BrahmiScript.DEVANAGARI
            keyboardState = keyboardState.withScript(newScript)
            prefs.edit().putString("last_script", newScript.name).apply()
        }

        // Clear predictor cache to pick up any dictionary preference changes
        predictionManager.clearCache()

        if (!restarting || keyboardState.script != oldScript) {
            currentPeckedWord.setLength(0)
            lastGestureWord = null
            expectedSelStart = -1
            showCandidates(emptyList())
        }
        updateBase()
        updateUI()
    }

    private fun updatePeckedCandidates() {
        if (keyboardState.mode.isSymbol() || currentPeckedWord.length < 2) {
            candidateView?.showCandidates(emptyList())
            return
        }

        predictionManager.ensurePredictor(
            keyboardState.mode.isLatin(),
            keyboardState.script,
            userDict.getLearnedWords()
        )
        val matches = predictionManager.getPrefixMatches(currentPeckedWord.toString())

        // Determine capitalization
        val firstChar = currentPeckedWord.firstOrNull()
        val shouldCaps = (firstChar != null && firstChar.isUpperCase()) || isSentenceStart()
        candidateView?.showCandidates(matches, shouldCaps)
    }

    override fun onKeyInput(view: FlickKeyView, text: String, isFlick: Boolean) {
        if (lastGestureWord != null) {
            commitCurrentInput()
        }

        val isWordChar = !listOf("।", "॥", ".", ",", "!", "?", "/", "'", "\"", "\\", " ").contains(text)
        if (!keyboardState.mode.isSymbol() && isWordChar && text.isNotBlank()) {
            val ic = currentInputConnection ?: return
            val isFirstChar = currentPeckedWord.isEmpty()

            if (isFirstChar && expectedSelStart == -1) {
                expectedSelStart = ic.getTextBeforeCursor(10000, 0)?.length ?: 0
            }

            currentPeckedWord.append(text)
            ic.commitText(text, 1)
            expectedSelStart += text.length
            updatePeckedCandidates()
        } else {
            if (currentPeckedWord.isNotEmpty()) {
                commitCurrentInput()
            }
            currentInputConnection?.commitText(text, 1)
        }
        
        updateBase()
        updateUI()
    }

    private fun performGestureTyping(): Boolean {
        predictionManager.ensurePredictor(
            keyboardState.mode.isLatin(),
            keyboardState.script,
            userDict.getLearnedWords()
        )
        val res = predictionManager.predictGesture(gesturePath)

        if (res.isNotEmpty() && res[0].second <= 8.0) {
            val word = res[0].first
            val shouldCaps = isSentenceStart()
            lastGestureWord = word
            var out = word
            if (shouldCaps) out = out.replaceFirstChar { it.uppercase() }
            
            currentInputConnection?.commitText(out, 1)
            expectedSelStart = (currentInputConnection?.getTextBeforeCursor(10000, 0)?.length ?: 0)
            
            candidateView?.showCandidates(res.map { it.first }, shouldCaps)
            return true
        }
        return false
    }

    private fun showCandidates(words: List<String>) {
        val firstChar = currentPeckedWord.firstOrNull()
        val shouldCaps = (firstChar != null && firstChar.isUpperCase()) ||
                        (currentPeckedWord.isEmpty() && isSentenceStart())
        candidateView?.showCandidates(words, shouldCaps)
    }

    private fun deleteLastChar() {
        val ic = currentInputConnection ?: return
        
        if (lastGestureWord != null) {
            ic.deleteSurroundingText(lastGestureWord!!.length, 0)
            lastGestureWord = null
            expectedSelStart = -1
            showCandidates(emptyList())
            updateBase()
            updateUI()
            return
        }

        if (!keyboardState.mode.isSymbol() && currentPeckedWord.isNotEmpty()) {
            val len = if (currentPeckedWord.length >= 2 && Character.isSurrogatePair(currentPeckedWord[currentPeckedWord.length - 2], currentPeckedWord[currentPeckedWord.length - 1])) 2 else 1
            currentPeckedWord.delete(currentPeckedWord.length - len, currentPeckedWord.length)
            ic.deleteSurroundingText(len, 0)
            if (currentPeckedWord.isEmpty()) {
                showCandidates(emptyList())
                expectedSelStart = -1
            } else {
                if (expectedSelStart != -1) expectedSelStart -= len
                updatePeckedCandidates()
            }
            updateBase()
            updateUI()
            return
        }

        val text = ic.getTextBeforeCursor(2, 0) ?: return
        if (text.length == 2 && Character.isSurrogatePair(text[0], text[1])) ic.deleteSurroundingText(2, 0) else ic.deleteSurroundingText(1, 0)
        updateBase(); updateUI()
    }

    private fun commitCurrentInput() {
        val wordToLearn = if (currentPeckedWord.isNotEmpty()) {
            currentPeckedWord.toString()
        } else {
            lastGestureWord
        }

        if (wordToLearn != null && wordToLearn.length > 1) {
            userDict.learnWord(wordToLearn)
        }
        currentPeckedWord.setLength(0)
        lastGestureWord = null
        expectedSelStart = -1
        showCandidates(emptyList())
    }

    private fun isSentenceStart(): Boolean {
        if (keyboardState.mode.isShifted()) return true

        var text = currentInputConnection?.getTextBeforeCursor(50, 0)?.toString() ?: return true
        
        val currentWordLen = if (currentPeckedWord.isNotEmpty()) currentPeckedWord.length else 0
        if (currentWordLen > 0 && text.length >= currentWordLen) {
            text = text.substring(0, text.length - currentWordLen)
        }
        
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

    override fun onFinishInput() {
        super.onFinishInput()
        showCandidates(emptyList())
        keyboardState = keyboardState.clearBaseChar()
    }
}

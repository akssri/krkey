package com.akssri.krkey

import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Typeface
import android.view.WindowManager
import android.content.Intent
import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.Gravity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.akssri.krkey.state.KeyboardState
import com.akssri.krkey.state.InputMode
import com.akssri.krkey.gesture.GestureDetector
import com.akssri.krkey.gesture.TouchHandler
import com.akssri.krkey.location.KeyLocator
import com.akssri.krkey.ui.CandidateView
import com.akssri.krkey.prediction.PredictionManager
import android.graphics.PointF

class KrKeyIME : InputMethodService(), FlickKeyView.OnKeyListener {

    // New state management (Phase 1)
    private var keyboardState = KeyboardState()
    private lateinit var gestureDetector: GestureDetector
    private lateinit var keyLocator: KeyLocator
    private lateinit var touchHandler: TouchHandler

    // Phase 3: View & Performance
    private lateinit var predictionManager: PredictionManager
    private var candidateView: CandidateView? = null

    // Logic managers
    private lateinit var userDict: UserDictionaryManager

    // Views
    private var allKeys: List<FlickKeyView> = emptyList()
    private var shiftBtn: Button? = null
    private var symBtn: Button? = null
    private var spaceBtn: Button? = null
    private var gestureTrailView: GestureTrailView? = null
    private var candidateBar: View? = null
    
    private var siddhamTypeface: Typeface? = null
    private var granthaTypeface: Typeface? = null
    private var sharadaTypeface: Typeface? = null
    private var brahmiTypeface: Typeface? = null

    // Gesture State
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var activeKey: FlickKeyView? = null
    private val gesturePath = mutableListOf<android.graphics.PointF>()
    private var gestureStartTime = 0L
    private var isGestureTyping = false
    private var lastPopupText: String? = null
    private var density = 1f
    private var capturedKey: FlickKeyView? = null
    private val currentPeckedWord = StringBuilder()
    private var lastComposedWord: String? = null

    private val prefixable = "़कखगघङचछजझञटठडढणतथदधनपफबभमयरलवशषसहळअआइईउऊऋॠऌॡएऐओऔ"

    private fun setupCandidateClickListener() {
        candidateView?.setOnCandidateClickListener { displayWord, originalWord ->
            currentInputConnection?.commitText(displayWord, 1)
            userDict.learnWord(originalWord)
            currentPeckedWord.setLength(0)
            lastComposedWord = null
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
                val cfg = configMap[key.id] ?: return Pair("", "")
                val flags = keyboardState.toOldFlags()
                val (base, flick) = cfg.getResolvedStrings(
                    flags.isLatinMode, flags.isSymbolMode, flags.isShifted,
                    flags.isLatinSymbolMode, flags.currentBaseChar, flags.currentScript
                )
                return Pair(base, flick)
            }

            override fun getKeyCommitText(key: FlickKeyView, isFlick: Boolean): String {
                val cfg = configMap[key.id] ?: return ""
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
            .getString("last_script", BrahmiScript.NAGARI.name)
        val script = try {
            BrahmiScript.valueOf(lastScript!!)
        } catch (e: Exception) {
            BrahmiScript.NAGARI
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

        // Initialize Phase 3 components
        candidateView = layout.findViewById(R.id.candidate_view)
        val container = layout.findViewById<ViewGroup>(R.id.keyboard_rows)

        // Initialize new components (Phase 1)
        gestureDetector = GestureDetector(density)
        keyLocator = KeyLocator(allKeys)
        keyLocator.initialize(container)

        touchHandler = TouchHandler(keyLocator, gestureDetector)
        setupTouchHandlerCallbacks()

        // Initialize Phase 3: PredictionManager
        predictionManager = PredictionManager(assets, keyLocator, allKeys, container)
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

        // Commit any composing text before moving cursor
        if (currentPeckedWord.isNotEmpty() || lastComposedWord != null) {
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
                        btn.isPressed = true; btn.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
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
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            commitCurrentInput(); currentInputConnection?.commitText(" ", 1)
            updateBase(); updateUI()
        }
        layout.findViewById<Button>(R.id.key_globe)?.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            val prefs = getSharedPreferences("krkey_prefs", MODE_PRIVATE)
            val enabled = BrahmiScript.values().filter {
                prefs.getBoolean("script_${it.name}", it == BrahmiScript.NAGARI)
            }
            if (enabled.size > 1) {
                val currentScript = keyboardState.script
                val newScript = enabled[(enabled.indexOf(currentScript) + 1) % enabled.size]
                keyboardState = keyboardState.withScript(newScript)
                prefs.edit().putString("last_script", newScript.name).apply()
                updateUI()
            }
        }
        layout.findViewById<Button>(R.id.key_enter)?.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            commitCurrentInput(); currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            updateBase(); updateUI()
        }
        shiftBtn = layout.findViewById(R.id.key_shift)
        shiftBtn?.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            keyboardState = keyboardState.toggleShift()
            updateUI()
        }
        symBtn = layout.findViewById(R.id.key_sym)
        symBtn?.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            val wasLatinMode = keyboardState.mode.isLatin()
            keyboardState = keyboardState.toggleSymbol(wasLatinMode)
            updateUI()
        }
    }

    private fun isFlickGesture(path: List<android.graphics.PointF>): Boolean {
        if (path.size < 2) return false
        val start = path.first(); val end = path.last()
        val dist = pathDist(path)
        return (end.y - start.y) < -15f * density && Math.abs(end.x - start.x) < Math.abs(end.y - start.y) * 0.8 && dist > 10f * density
    }

    private fun getCleanOutput(cfg: KeyConfig, isFlick: Boolean): String {
        val flags = keyboardState.toOldFlags()
        val (b, f) = cfg.getResolvedStrings(
            flags.isLatinMode, flags.isSymbolMode, flags.isShifted,
            flags.isLatinSymbolMode, flags.currentBaseChar, flags.currentScript
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

    private fun handleGlobalTouch(event: MotionEvent): Boolean {
        if (!keyboardState.mode.isLatin() || keyboardState.mode.isSymbol()) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    capturedKey = findKeyAt(event.x, event.y)
                    capturedKey?.let {
                        it.isPressed = true
                        it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        val cfg = configMap[it.id] ?: return@let
                        val flags = keyboardState.toOldFlags()
                        val (b, _) = cfg.getResolvedStrings(
                            flags.isLatinMode, flags.isSymbolMode, flags.isShifted,
                            flags.isLatinSymbolMode, flags.currentBaseChar, flags.currentScript
                        )
                        it.showPopup(b)
                    }
                    gesturePath.clear(); gesturePath.add(android.graphics.PointF(event.x, event.y))
                }
                MotionEvent.ACTION_MOVE -> {
                    gesturePath.add(android.graphics.PointF(event.x, event.y))
                    capturedKey?.let { k ->
                        val cfg = configMap[k.id] ?: return@let
                        val flags = keyboardState.toOldFlags()
                        val (b, f) = cfg.getResolvedStrings(
                            flags.isLatinMode, flags.isSymbolMode, flags.isShifted,
                            flags.isLatinSymbolMode, flags.currentBaseChar, flags.currentScript
                        )
                        k.showPopup(if (isFlickGesture(gesturePath)) f else b)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    capturedKey?.let {
                        it.isPressed = false; it.dismissPopup()
                        val cfg = configMap[it.id] ?: return@let
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
                activePointerId = event.getPointerId(0); gesturePath.clear(); gesturePath.add(android.graphics.PointF(event.x, event.y))
                gestureStartTime = System.currentTimeMillis(); isGestureTyping = false; activeKey = findKeyAt(event.x, event.y)
                activeKey?.let { k ->
                    k.isPressed = true
                    k.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    val flags = keyboardState.toOldFlags()
                    val (b, _) = configMap[k.id]!!.getResolvedStrings(
                        flags.isLatinMode, flags.isSymbolMode, flags.isShifted,
                        flags.isLatinSymbolMode, flags.currentBaseChar, flags.currentScript
                    )
                    lastPopupText = b
                    k.showPopup(b)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val idx = event.findPointerIndex(activePointerId); if (idx < 0) return true
                val px = event.getX(idx); val py = event.getY(idx); gesturePath.add(android.graphics.PointF(px, py))
                if (!isGestureTyping) {
                    val currentKey = findKeyAt(px, py); val startPos = gesturePath[0]
                    val movedToNewKey = currentKey != null && currentKey != activeKey && Math.abs(px - startPos.x) > (activeKey?.width ?: 0) * 0.6
                    val isLikelyFlick = (py - startPos.y) < 0 && Math.abs(px - startPos.x) < Math.abs(py - startPos.y) * 0.8
                    activeKey?.let { k ->
                        val flags = keyboardState.toOldFlags()
                        val (b, f) = configMap[k.id]!!.getResolvedStrings(
                            flags.isLatinMode, flags.isSymbolMode, flags.isShifted,
                            flags.isLatinSymbolMode, flags.currentBaseChar, flags.currentScript
                        )
                        val text = if (py - startPos.y < -10f * density) f else b
                        if (text != lastPopupText) {
                            lastPopupText = text
                            k.showPopup(text)
                        }
                    }
                    if ((pathDist(gesturePath) > 50f * density && !isLikelyFlick) || (movedToNewKey && !isLikelyFlick) || pathDist(gesturePath) > 200f * density) {
                        isGestureTyping = true
                        commitCurrentInput()
                        
                        val before = currentInputConnection?.getTextBeforeCursor(1, 0)
                        if (!before.isNullOrEmpty() && !".?! \n".contains(before.last())) {
                            currentInputConnection?.commitText(" ", 1)
                        }

                        activeKey?.let { it.isPressed = false; it.dismissPopup() }
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
                val finalKey = activeKey; activeKey?.let { it.isPressed = false; it.dismissPopup() }; activeKey = null; activePointerId = MotionEvent.INVALID_POINTER_ID
                if (isGestureTyping) {
                    gestureTrailView?.visibility = View.GONE
                    performGestureTyping()
                } else if (event.actionMasked == MotionEvent.ACTION_UP) handleFlickOrTap(gesturePath, finalKey)
                isGestureTyping = false; gestureTrailView?.clear()
            }
        }
        return true
    }

    private fun handleFlickOrTap(path: List<android.graphics.PointF>, key: FlickKeyView?) {
        if (path.isEmpty() || key == null) return
        val cfg = configMap[key.id] ?: return
        val isFlick = isFlickGesture(path)
        val clean = getCleanOutput(cfg, isFlick)
        onKeyInput(key, clean, isFlick)
    }

    private fun updateUI() {
        val mode = keyboardState.mode
        val script = keyboardState.script
        val flags = keyboardState.toOldFlags()

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

        symBtn?.text = if (mode.isSymbol()) {
            if (mode is InputMode.LatinSymbol) "ABC" else "अल्".toBrahmiScript(script)
        } else {
            "१२३".toBrahmiScript(script)
        }

        shiftBtn?.text = if (mode.isSymbol()) {
            if (keyboardState.isShiftLocked) "⇪" else "⇧"
        } else "EN"

        spaceBtn?.text = if (mode.isLatin() && !mode.isSymbol()) "English" else script.nativeName
        spaceBtn?.typeface = tf

        allKeys.forEach { k ->
            k.setTypeface(tf)
            val cfg = configMap[k.id] ?: return@forEach
            val (b, f) = cfg.getResolvedStrings(
                flags.isLatinMode, flags.isSymbolMode, flags.isShifted,
                flags.isLatinSymbolMode, flags.currentBaseChar, flags.currentScript
            )
            k.setVisualState(b, f, false)
        }
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
        
        // If the composing region was cleared externally (e.g. Firefox X button)
        // or the user tapped elsewhere, reset our internal buffers.
        if (candidatesStart == -1 && candidatesEnd == -1) {
            if (currentPeckedWord.isNotEmpty() || lastComposedWord != null) {
                currentPeckedWord.setLength(0)
                lastComposedWord = null
                showCandidates(emptyList())
            }
        }
        
        updateBase()
        updateUI()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        userDict.load()
        currentPeckedWord.setLength(0)
        lastComposedWord = null
        showCandidates(emptyList())
        updateBase()
        updateUI()
    }

    private fun updatePeckedCandidates() {
        if (keyboardState.mode.isSymbol()) {
            candidateView?.showCandidates(emptyList())
            return
        }
        if (currentPeckedWord.length < 2) {
            candidateView?.showCandidates(emptyList())
            return
        }

        // Use PredictionManager
        predictionManager.ensurePredictor(
            keyboardState.mode.isLatin(),
            keyboardState.script,
            userDict.getLearnedWords()
        )
        val matches = predictionManager.getPrefixMatches(currentPeckedWord.toString())

        // Determine capitalization
        val firstChar = currentPeckedWord.firstOrNull()
        val shouldCaps = firstChar != null && firstChar.isUpperCase()
        candidateView?.showCandidates(matches, shouldCaps)
    }

    override fun onKeyInput(view: FlickKeyView, text: String, isFlick: Boolean) {
        if (lastComposedWord != null) {
            commitCurrentInput()
        }

        val isWordChar = !listOf("।", "॥", ".", ",", "!", "?", "/", "'", "\"", "\\", " ").contains(text)
        if (!keyboardState.mode.isSymbol() && isWordChar && text.isNotBlank()) {
            currentPeckedWord.append(text)
            currentInputConnection?.setComposingText(currentPeckedWord.toString(), 1)
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
        // Use PredictionManager
        predictionManager.ensurePredictor(
            keyboardState.mode.isLatin(),
            keyboardState.script,
            userDict.getLearnedWords()
        )
        val res = predictionManager.predictGesture(gesturePath)

        if (res.isNotEmpty() && res[0].second <= 8.0) {
            val word = res[0].first
            lastComposedWord = word
            var out = word
            val shouldCaps = isSentenceStart()
            if (shouldCaps) out = out.replaceFirstChar { it.uppercase() }
            currentInputConnection?.setComposingText(out, 1)
            candidateView?.showCandidates(res.map { it.first }, shouldCaps)
            return true
        }
        return false
    }

    // Old showCandidates - kept for backward compatibility, redirects to new CandidateView
    private fun showCandidates(words: List<String>) {
        val firstChar = currentPeckedWord.firstOrNull()
        val shouldCaps = (firstChar != null && firstChar.isUpperCase()) ||
                        (currentPeckedWord.isEmpty() && isSentenceStart())
        candidateView?.showCandidates(words, shouldCaps)
    }

    private fun deleteLastChar() {
        val ic = currentInputConnection ?: return
        
        if (lastComposedWord != null) {
            ic.commitText("", 1)
            lastComposedWord = null
            showCandidates(emptyList())
            updateBase()
            updateUI()
            return
        }

        if (!keyboardState.mode.isSymbol() && currentPeckedWord.isNotEmpty()) {
            val len = if (currentPeckedWord.length >= 2 && Character.isSurrogatePair(currentPeckedWord[currentPeckedWord.length - 2], currentPeckedWord[currentPeckedWord.length - 1])) 2 else 1
            currentPeckedWord.delete(currentPeckedWord.length - len, currentPeckedWord.length)
            if (currentPeckedWord.isEmpty()) {
                ic.commitText("", 1)
                showCandidates(emptyList())
            } else {
                ic.setComposingText(currentPeckedWord.toString(), 1)
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
        val ic = currentInputConnection ?: return
        val wordToLearn = if (currentPeckedWord.isNotEmpty()) {
            currentPeckedWord.toString()
        } else {
            lastComposedWord
        }

        ic.finishComposingText()
        if (wordToLearn != null && wordToLearn.length > 1) {
            userDict.learnWord(wordToLearn)
        }
        currentPeckedWord.setLength(0)
        lastComposedWord = null
        showCandidates(emptyList())
    }
    private fun isSentenceStart(): Boolean {
        var text = currentInputConnection?.getTextBeforeCursor(50, 0)?.toString() ?: return true
        
        val composingLen = if (currentPeckedWord.isNotEmpty()) currentPeckedWord.length else (lastComposedWord?.length ?: 0)
        if (composingLen > 0 && text.length >= composingLen) {
            text = text.substring(0, text.length - composingLen)
        }
        
        if (text.isEmpty()) return true
        val trimmed = text.trimEnd()
        return trimmed.isEmpty() || ".?! \n".contains(trimmed.last()) 
    }
    private fun findKeyAt(x: Float, y: Float): FlickKeyView? {
        var closest: FlickKeyView? = null; var minDist = Double.MAX_VALUE; val r = android.graphics.Rect(); val container = gestureTrailView?.rootView?.findViewById<ViewGroup>(R.id.keyboard_rows) ?: return null
        allKeys.forEach { k -> k.getDrawingRect(r); container.offsetDescendantRectToMyCoords(k, r); if (r.contains(x.toInt(), y.toInt())) return k; val d = Math.pow(x - r.centerX().toDouble(), 2.0) + Math.pow(y - r.centerY().toDouble(), 2.0); if (d < minDist) { minDist = d; closest = k } }
        return if (Math.sqrt(minDist) < 150.0) closest else null
    }
    private fun pathDist(p: List<android.graphics.PointF>): Double { if (p.size < 2) return 0.0; return Math.sqrt(Math.pow((p.last().x - p.first().x).toDouble(), 2.0) + Math.pow((p.last().y - p.first().y).toDouble(), 2.0)) }

    override fun onFinishInput() {
        super.onFinishInput()
        showCandidates(emptyList())
        keyboardState = keyboardState.clearBaseChar()
    }

    override fun isGestureEnabled() = keyboardState.mode.isLatin() && !keyboardState.mode.isSymbol()
}

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

class KrKeyIME : InputMethodService(), FlickKeyView.OnKeyListener {

    // State
    private var isLatinMode = false
    private var isSymbolMode = false
    private var isLatinSymbolMode = false
    private var isShifted = false
    private var isShiftLocked = false
    private var currentScript = BrahmiScript.NAGARI
    private var currentBaseChar = ""
    
    // Logic managers
    private lateinit var userDict: UserDictionaryManager
    private var cachedDicts = mutableMapOf<String, List<String>>()
    private var currentDictFile: String? = null
    private var wordPredictor: WordPredictor? = null
    
    // Views
    private var allKeys: List<FlickKeyView> = emptyList()
    private var shiftBtn: Button? = null
    private var symBtn: Button? = null
    private var spaceBtn: Button? = null
    private var candidateContainer: LinearLayout? = null
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

        override fun onCreate() {

            super.onCreate()

            density = resources.displayMetrics.density

            userDict = UserDictionaryManager(this)

            

            val lastScript = getSharedPreferences("krkey_prefs", MODE_PRIVATE).getString("last_script", BrahmiScript.NAGARI.name)
        currentScript = try { BrahmiScript.valueOf(lastScript!!) } catch (e: Exception) { BrahmiScript.NAGARI }
    }

    override fun onCreateInputView(): View {
        loadFonts()
        val themedContext = ContextThemeWrapper(this, R.style.Theme_KrKey)
        val layout = LayoutInflater.from(themedContext).inflate(R.layout.keyboard_view, null) as LinearLayout
        candidateBar = layout.findViewById(R.id.candidate_bar)
        candidateContainer = layout.findViewById(R.id.candidate_container)
        gestureTrailView = layout.findViewById(R.id.gesture_trail)
        allKeys = findAllFlickKeys(layout)
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
        spaceBtn?.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            commitCurrentInput(); currentInputConnection?.commitText(" ", 1)
            updateBase(); updateUI()
        }
        layout.findViewById<Button>(R.id.key_globe)?.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            val prefs = getSharedPreferences("krkey_prefs", MODE_PRIVATE)
            val enabled = BrahmiScript.values().filter { prefs.getBoolean("script_${it.name}", it == BrahmiScript.NAGARI) }
            if (enabled.size > 1) {
                currentScript = enabled[(enabled.indexOf(currentScript) + 1) % enabled.size]
                prefs.edit().putString("last_script", currentScript.name).apply()
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
            if (isSymbolMode) isShifted = !isShifted
            else { isLatinMode = !isLatinMode; if (!isLatinMode) isShifted = false }
            updateUI()
        }
        symBtn = layout.findViewById(R.id.key_sym)
        symBtn?.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            if (!isSymbolMode) isLatinSymbolMode = isLatinMode
            isSymbolMode = !isSymbolMode; isShifted = false; updateUI()
        }
    }

    private fun isFlickGesture(path: List<android.graphics.PointF>): Boolean {
        if (path.size < 2) return false
        val start = path.first(); val end = path.last()
        val dist = pathDist(path)
        return (end.y - start.y) < -15f * density && Math.abs(end.x - start.x) < Math.abs(end.y - start.y) * 0.8 && dist > 10f * density
    }

    private fun getCleanOutput(cfg: KeyConfig, isFlick: Boolean): String {
        val (b, f) = cfg.getResolvedStrings(isLatinMode, isSymbolMode, isShifted, isLatinSymbolMode, currentBaseChar, currentScript)
        val out = if (isFlick) f else b
        return if (out.startsWith(currentBaseChar) && currentBaseChar.isNotEmpty()) out.substring(currentBaseChar.length)
               else if (out.startsWith("◌")) out.substring(1) else out
    }

    private fun handleGlobalTouch(event: MotionEvent): Boolean {
        if (!isLatinMode || isSymbolMode) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    capturedKey = findKeyAt(event.x, event.y)
                    capturedKey?.let {
                        it.isPressed = true
                        it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        val cfg = configMap[it.id] ?: return@let
                        val (b, _) = cfg.getResolvedStrings(isLatinMode, isSymbolMode, isShifted, isLatinSymbolMode, currentBaseChar, currentScript)
                        it.showPopup(b)
                    }
                    gesturePath.clear(); gesturePath.add(android.graphics.PointF(event.x, event.y))
                }
                MotionEvent.ACTION_MOVE -> {
                    gesturePath.add(android.graphics.PointF(event.x, event.y))
                    capturedKey?.let { k ->
                        val cfg = configMap[k.id] ?: return@let
                        val (b, f) = cfg.getResolvedStrings(isLatinMode, isSymbolMode, isShifted, isLatinSymbolMode, currentBaseChar, currentScript)
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
                    val (b, _) = configMap[k.id]!!.getResolvedStrings(isLatinMode, isSymbolMode, isShifted, isLatinSymbolMode, currentBaseChar, currentScript); lastPopupText = b; k.showPopup(b)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val idx = event.findPointerIndex(activePointerId); if (idx < 0) return true
                val px = event.getX(idx); val py = event.getY(idx); gesturePath.add(android.graphics.PointF(px, py))
                if (!isGestureTyping) {
                    val currentKey = findKeyAt(px, py); val startPos = gesturePath[0]
                    val movedToNewKey = currentKey != null && currentKey != activeKey && Math.abs(px - startPos.x) > (activeKey?.width ?: 0) * 0.6
                    val isLikelyFlick = (py - startPos.y) < 0 && Math.abs(px - startPos.x) < Math.abs(py - startPos.y) * 0.8
                    activeKey?.let { k -> val (b, f) = configMap[k.id]!!.getResolvedStrings(isLatinMode, isSymbolMode, isShifted, isLatinSymbolMode, currentBaseChar, currentScript); val text = if (py - startPos.y < -10f * density) f else b; if (text != lastPopupText) { lastPopupText = text; k.showPopup(text) } }
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
                        if ((candidateContainer?.childCount ?: 0) > 0) { showCandidates(emptyList()) }
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
        candidateBar?.visibility = if (!isSymbolMode) View.VISIBLE else View.GONE
        val tf = if (!isLatinMode) {
            when (currentScript) { BrahmiScript.SIDDHAM -> siddhamTypeface; BrahmiScript.GRANTHA -> granthaTypeface; BrahmiScript.SHARADA -> sharadaTypeface; BrahmiScript.BRAHMI -> brahmiTypeface; else -> Typeface.DEFAULT }
        } else Typeface.DEFAULT
        symBtn?.text = if (isSymbolMode) (if (isLatinSymbolMode) "ABC" else "अल्".toBrahmiScript(currentScript)) else "१२३".toBrahmiScript(currentScript)
        shiftBtn?.text = if (isSymbolMode) (if (isShiftLocked) "⇪" else "⇧") else "EN"; spaceBtn?.text = if (isLatinMode && !isSymbolMode) "English" else currentScript.nativeName; spaceBtn?.typeface = tf
        allKeys.forEach { k -> k.setTypeface(tf); val cfg = configMap[k.id] ?: return@forEach; val (b, f) = cfg.getResolvedStrings(isLatinMode, isSymbolMode, isShifted, isLatinSymbolMode, currentBaseChar, currentScript); k.setVisualState(b, f, false) }
    }

    private fun updateBase() {
        val ic = currentInputConnection ?: return
        val text = ic.getTextBeforeCursor(2, 0)
        currentBaseChar = if (!text.isNullOrEmpty()) {
            val last = if (text.length >= 2 && Character.isSurrogatePair(text[text.length-2], text[text.length-1])) {
                text.substring(text.length-2)
            } else {
                text.substring(text.length-1)
            }
            if (prefixable.toBrahmiScript(currentScript).contains(last)) last else ""
        } else ""
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
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
        if (isSymbolMode) {
            showCandidates(emptyList())
            return
        }
        if (currentPeckedWord.length < 2) {
            showCandidates(emptyList())
            return
        }
        
        ensurePredictor()
        val matches = wordPredictor?.getPrefixMatches(currentPeckedWord.toString()) ?: emptyList()
        showCandidates(matches)
    }

    private fun ensurePredictor() {
        // Determine which static dictionary to load
        val dictFile = if (isLatinMode && !isSymbolMode) {
            "en_dict.txt"
        } else if (!isLatinMode && !isSymbolMode) {
            when (currentScript) {
                BrahmiScript.KANNADA -> "kn_dict.txt"
                BrahmiScript.NAGARI -> "sa_dict.txt"
                else -> null
            }
        } else null

        if (wordPredictor == null || dictFile != currentDictFile) {
            val container = gestureTrailView?.rootView?.findViewById<ViewGroup>(R.id.keyboard_rows) ?: return
            
            val currentStaticDict = if (dictFile != null) {
                cachedDicts.getOrPut(dictFile) {
                    try { assets.open(dictFile).bufferedReader().useLines { it.toList() } } catch (e: Exception) { emptyList() }
                }
            } else emptyList()

            val locs = allKeys.mapNotNull { k ->
                val cfg = configMap[k.id] ?: return@mapNotNull null
                val rawChar = if (isLatinMode) cfg.latinBase else cfg.base
                if (rawChar == null || rawChar.isEmpty()) return@mapNotNull null
                val char = if (isLatinMode) rawChar else rawChar.toBrahmiScript(currentScript)
                if (char.length != 1) return@mapNotNull null
                
                val r = android.graphics.Rect(); k.getDrawingRect(r)
                container.offsetDescendantRectToMyCoords(k, r)
                char.lowercase() to r
            }
            wordPredictor = WordPredictor(locs, currentStaticDict)
            currentDictFile = dictFile
        }
        
        // Always refresh learned words to catch latest additions
        wordPredictor?.setLearnedWords(userDict.getLearnedWords())
    }

    override fun onKeyInput(view: FlickKeyView, text: String, isFlick: Boolean) {
        if (lastComposedWord != null) {
            commitCurrentInput()
        }
        
        val isWordChar = !listOf("।", "॥", ".", ",", "!", "?", "/", "'", "\"", "\\", " ").contains(text)
        if (!isSymbolMode && isWordChar && text.isNotBlank()) {
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
        ensurePredictor()
        val res = wordPredictor?.predict(gesturePath) ?: emptyList()
        if (res.isNotEmpty() && res[0].second <= 8.0) {
            val word = res[0].first
            lastComposedWord = word
            var out = word
            if (isSentenceStart()) out = out.replaceFirstChar { it.uppercase() }
            currentInputConnection?.setComposingText(out, 1)
            showCandidates(res.map { it.first })
            return true
        }
        return false
    }

    private fun showCandidates(words: List<String>) {
        candidateContainer?.removeAllViews()
        
        // Use manual capitalization from currentPeckedWord if available, else check sentence start
        val firstChar = currentPeckedWord.firstOrNull()
        val shouldCaps = (firstChar != null && firstChar.isUpperCase()) || (currentPeckedWord.isEmpty() && isSentenceStart())
        
        words.forEach { word ->
            val display = if (shouldCaps) word.replaceFirstChar { it.uppercase() } else word
            val tv = TextView(ContextThemeWrapper(this, R.style.Theme_KrKey)).apply {
                text = display; textSize = 16f; setPadding(30, 0, 30, 0); gravity = Gravity.CENTER; setTextColor(ContextCompat.getColor(this@KrKeyIME, R.color.key_text_color)); background = ContextCompat.getDrawable(this@KrKeyIME, R.drawable.key_bg)
                setOnClickListener {
                    currentInputConnection?.commitText(display, 1)
                    userDict.learnWord(word)
                    currentPeckedWord.setLength(0)
                    lastComposedWord = null
                    showCandidates(emptyList())
                    updateBase()
                    updateUI()
                }
            }
            candidateContainer?.addView(tv, 0, LinearLayout.LayoutParams(-2, -1).apply { setMargins(8, 4, 8, 4) })
        }
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

        if (!isSymbolMode && currentPeckedWord.isNotEmpty()) {
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
    override fun onFinishInput() { super.onFinishInput(); showCandidates(emptyList()); currentBaseChar = "" }
    override fun isGestureEnabled() = isLatinMode && !isSymbolMode
}

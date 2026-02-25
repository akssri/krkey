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
import androidx.appcompat.app.AlertDialog
import android.view.WindowManager
import android.content.Intent
import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.Gravity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat

class KrKeyIME : InputMethodService(), FlickKeyView.OnKeyListener {

    private val vyanjanas = "़कखगघङचछजझञटठडढणतथदधनपफबभमयरलवशषसहळ"
    private var currentBase: String = ""
    
    // State Flags
    private var isLatinMode: Boolean = false
    private var isSymbolMode: Boolean = false
    private var isLatinSymbolMode: Boolean = false
    private var isShifted: Boolean = false
    private var isShiftLocked: Boolean = false
    private var lastShiftTime: Long = 0
    private var currentScript = BrahmiScript.NAGARI
    private lateinit var userDict: UserDictionaryManager
    private var currentPeckedWord = StringBuilder()
    
    override fun onCreate() {
        super.onCreate()
        userDict = UserDictionaryManager(this)
        val prefs = getSharedPreferences("krkey_prefs", Context.MODE_PRIVATE)
        val lastScript = prefs.getString("last_script", BrahmiScript.NAGARI.name)
        currentScript = try { BrahmiScript.valueOf(lastScript!!) } catch (e: Exception) { BrahmiScript.NAGARI }
    }
    
    // Views
    private var allKeys: List<FlickKeyView> = emptyList()
    private var shiftBtn: Button? = null
    private var symBtn: Button? = null
    private var spaceBtn: Button? = null
    private var previewText: TextView? = null
    private var candidateContainer: LinearLayout? = null
    private var gestureTrailView: GestureTrailView? = null
    private var wordPredictor: WordPredictor? = null
    private var siddhamTypeface: Typeface? = null
    private var granthaTypeface: Typeface? = null
    private var sharadaTypeface: Typeface? = null
    private var brahmiTypeface: Typeface? = null

    private var isGestureTyping = false
    private var isFlickDetected = false
    private var gesturePath = mutableListOf<android.graphics.PointF>()
    private var gestureStartTime = 0L
    private var activeKey: FlickKeyView? = null

    override fun onCreateInputView(): View {
        try {
            siddhamTypeface = ResourcesCompat.getFont(this, R.font.noto_sans_siddham)
            granthaTypeface = ResourcesCompat.getFont(this, R.font.noto_sans_grantha)
            sharadaTypeface = ResourcesCompat.getFont(this, R.font.noto_sans_sharada)
            brahmiTypeface = ResourcesCompat.getFont(this, R.font.noto_sans_brahmi)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val themedContext = ContextThemeWrapper(this, R.style.Theme_KrKey)
        val layout = LayoutInflater.from(themedContext).inflate(R.layout.keyboard_view, null) as LinearLayout
        previewText = layout.findViewById(R.id.preview_text)
        candidateContainer = layout.findViewById(R.id.candidate_container)
        gestureTrailView = layout.findViewById(R.id.gesture_trail)
        
        allKeys = findAllFlickKeys(layout)
        allKeys.forEach { it.setOnKeyListener(this) }
        
        setupSpecialKeys(layout)
        setupGestureTyping(layout)
        updateKeys() // Initial draw
        
        return layout
    }
    
    private fun setupSpecialKeys(layout: View) {
        val backspaceBtn = layout.findViewById<Button>(R.id.key_backspace)
        backspaceBtn?.let { btn ->
            val handler = Handler(Looper.getMainLooper())
            val repeatRunnable = object : Runnable {
                override fun run() {
                    deleteLastCharacter()
                    handler.postDelayed(this, 50)
                }
            }
            btn.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        btn.isPressed = true
                        btn.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        deleteLastCharacter()
                        handler.postDelayed(repeatRunnable, 400)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        btn.isPressed = false
                        handler.removeCallbacks(repeatRunnable)
                        true
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
            when (event.action) {
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
            val ic = currentInputConnection
            ic?.finishComposingText() // Finalize any draft word from swipe
            
            if (isLatinMode && !isSymbolMode && currentPeckedWord.isNotEmpty()) {
                userDict.learnWord(currentPeckedWord.toString())
                wordPredictor = null
                currentPeckedWord.clear()
            }

            ic?.commitText(" ", 1)
            showCandidates(emptyList()) // Clear suggestions
            updateBase()
        }
        
        val globeBtn = layout.findViewById<Button>(R.id.key_globe)
        globeBtn?.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            cycleScript(forward = true)
        }
        globeBtn?.setOnLongClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            showScriptPicker()
            true
        }
        
        layout.findViewById<Button>(R.id.key_enter)?.setOnClickListener {
            btn ->
            btn.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            
            if (isLatinMode && !isSymbolMode && currentPeckedWord.isNotEmpty()) {
                userDict.learnWord(currentPeckedWord.toString())
                wordPredictor = null
                currentPeckedWord.clear()
            }

            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            updateBase()
        }

        shiftBtn = layout.findViewById(R.id.key_shift)
        shiftBtn?.setOnClickListener {
            btn ->
            btn.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            if (isSymbolMode) {
                val now = System.currentTimeMillis()
                if (now - lastShiftTime < 300) {
                    isShiftLocked = true
                    isShifted = true
                } else {
                    isShiftLocked = false
                    isShifted = !isShifted
                }
                lastShiftTime = now
            } else {
                isLatinMode = !isLatinMode
                if (!isLatinMode) {
                    isShifted = false
                    isShiftLocked = false
                }
            }
            updateKeys()
        }
        
        symBtn = layout.findViewById(R.id.key_sym)
        symBtn?.setOnClickListener {
            btn ->
            btn.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            if (!isSymbolMode) {
                isLatinSymbolMode = isLatinMode
            }
            isSymbolMode = !isSymbolMode
            isShifted = false
            isShiftLocked = false
            updateKeys()
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        updateBase()
    }

    private fun findAllFlickKeys(view: View): List<FlickKeyView> {
        val keys = mutableListOf<FlickKeyView>()
        if (view is FlickKeyView) {
            keys.add(view)
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                keys.addAll(findAllFlickKeys(view.getChildAt(i)))
            }
        }
        return keys
    }

    private fun moveCursor(count: Int) {
        val ic = currentInputConnection ?: return
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

    private fun cycleScript(forward: Boolean) {
        val prefs = getSharedPreferences("krkey_prefs", Context.MODE_PRIVATE)
        val allScripts = BrahmiScript.values()
        val enabledScripts = allScripts.filter { 
            prefs.getBoolean("script_".plus(it.name), it == BrahmiScript.NAGARI) 
        }

        if (enabledScripts.size <= 1) return

        val currentIndex = enabledScripts.indexOf(currentScript)
        val nextIndex = if (forward) {
            (currentIndex + 1) % enabledScripts.size
        } else {
            (currentIndex - 1 + enabledScripts.size) % enabledScripts.size
        }

        currentScript = enabledScripts[nextIndex]
        prefs.edit().putString("last_script", currentScript.name).apply()
        
        spaceBtn?.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        
        updateKeys()
    }

    private fun showScriptPicker() {
        val prefs = getSharedPreferences("krkey_prefs", Context.MODE_PRIVATE)
        val allScripts = BrahmiScript.values()
        val enabledScripts = allScripts.filter { 
            prefs.getBoolean("script_".plus(it.name), it == BrahmiScript.NAGARI) 
        }

        if (enabledScripts.isEmpty()) return

        val currentIndex = enabledScripts.indexOf(currentScript).coerceAtLeast(0)

        val adapter = object : android.widget.BaseAdapter() {
            override fun getCount(): Int = enabledScripts.size
            override fun getItem(position: Int): Any = enabledScripts[position]
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val script = enabledScripts[position]
                val view = convertView ?: LayoutInflater.from(this@KrKeyIME).inflate(android.R.layout.simple_list_item_2, parent, false)
                val text1 = view.findViewById<TextView>(android.R.id.text1)
                val text2 = view.findViewById<TextView>(android.R.id.text2)
                
                text1.text = script.nativeName
                text1.typeface = when (script) {
                    BrahmiScript.SIDDHAM -> siddhamTypeface
                    BrahmiScript.GRANTHA -> granthaTypeface
                    BrahmiScript.SHARADA -> sharadaTypeface
                    BrahmiScript.BRAHMI -> brahmiTypeface
                    else -> Typeface.DEFAULT
                }
                
                text2.text = script.iastName
                text2.typeface = Typeface.DEFAULT
                
                return view
            }
        }

        val builder = AlertDialog.Builder(this, R.style.Theme_KrKey_Dialog)
        builder.setTitle("लिपि-चयन")
        builder.setSingleChoiceItems(adapter, currentIndex) { dialog, which ->
            currentScript = enabledScripts[which]
            prefs.edit().putString("last_script", currentScript.name).apply()
            updateKeys()
            dialog.dismiss()
        }
        builder.setPositiveButton("Settings") { dialog, _ ->
            val intent = Intent(this, SettingsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            dialog.dismiss()
        }
        
        val dialog = builder.create()
        dialog.window?.let { window ->
            val lp = window.attributes
            lp.token = this.window.window?.attributes?.token
            lp.type = WindowManager.LayoutParams.TYPE_INPUT_METHOD_DIALOG
            window.attributes = lp
            window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        }
        dialog.show()
    }

    private fun isSentenceStart(): Boolean {
        val ic = currentInputConnection ?: return true
        val text = ic.getTextBeforeCursor(20, 0) ?: return true
        if (text.isEmpty()) return true
        val trimmed = text.trimEnd()
        if (trimmed.isEmpty()) return true
        val lastChar = trimmed.last()
        return lastChar == '.' || lastChar == '?' || lastChar == '!' || lastChar == '\n'
    }

    private fun setupGestureTyping(layout: View) {
        val container = layout.findViewById<View>(R.id.keyboard_rows).parent as View
        
        container.setOnTouchListener { _, event ->
            if (!isLatinMode || isSymbolMode) return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val key = findKeyAt(event.x, event.y)
                    gesturePath.clear()
                    gesturePath.add(android.graphics.PointF(event.x, event.y))
                    gestureStartTime = System.currentTimeMillis()
                    isGestureTyping = false
                    isFlickDetected = false
                    gestureTrailView?.clear()
                    activeKey = key
                    key?.isPressed = true
                    true // Claim the gesture
                }
                MotionEvent.ACTION_MOVE -> {
                    gesturePath.add(android.graphics.PointF(event.x, event.y))
                    
                    val start = gesturePath.first()
                    val dx = event.x - start.x
                    val dy = event.y - start.y
                    val dist = Math.sqrt(Math.pow(dx.toDouble(), 2.0) + Math.pow(dy.toDouble(), 2.0))

                    if (!isGestureTyping && !isFlickDetected) {
                        // Show trail early for responsiveness
                        if (dist > 15 && gestureTrailView?.visibility != View.VISIBLE) {
                            gestureTrailView?.visibility = View.VISIBLE
                            gestureTrailView?.setPoints(gesturePath)
                        }

                        // Check for flick-up (primarily vertical, significant distance)
                        if (dy < -60 && Math.abs(dx) < Math.abs(dy) * 0.5) {
                            val key = activeKey
                            if (key != null) {
                                val config = configMap[key.id]
                                val text = config?.latinFlick ?: ""
                                onKeyInput(key, text, true)
                                key.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                
                                isFlickDetected = true
                                activeKey?.isPressed = false
                                activeKey = null
                                gestureTrailView?.visibility = View.GONE
                                gestureTrailView?.clear()
                                gesturePath.clear()
                                return@setOnTouchListener true
                            }
                        }

                        // Start swipe decoder only if distance is significant
                        if (dist > 80) { 
                            isGestureTyping = true
                            activeKey?.isPressed = false
                            activeKey = null

                            val ic = currentInputConnection
                            if (isLatinMode && !isSymbolMode && (candidateContainer?.childCount ?: 0) > 1) {
                                ic?.finishComposingText()
                                ic?.commitText(" ", 1)
                                showCandidates(emptyList())
                            }
                        }
                    }
                    
                    if (gestureTrailView?.visibility == View.VISIBLE) {
                        gestureTrailView?.addPoint(event.x, event.y)
                    } else if (!isFlickDetected) {
                        val key = findKeyAt(event.x, event.y)
                        if (key != activeKey) {
                            activeKey?.isPressed = false
                            activeKey = key
                            activeKey?.isPressed = true
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    activeKey?.isPressed = false
                    val finalKey = activeKey
                    activeKey = null
                    
                    if (isGestureTyping) {
                        gestureTrailView?.visibility = View.GONE
                        performGestureTyping()
                    } else if (!isFlickDetected && event.action == MotionEvent.ACTION_UP) {
                        finalKey?.let {
                            onKeyInput(it, "", false)
                            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    }
                    isGestureTyping = false
                    isFlickDetected = false
                    gestureTrailView?.clear()
                    true
                }
                else -> false
            }
        }
    }

    override fun isGestureEnabled(): Boolean {
        return isLatinMode && !isSymbolMode
    }

    private fun findKeyAt(x: Float, y: Float): FlickKeyView? {
        val rect = android.graphics.Rect()
        val container = gestureTrailView?.rootView?.findViewById<ViewGroup>(R.id.keyboard_rows) ?: return null
        for (key in allKeys) {
            key.getDrawingRect(rect)
            container.offsetDescendantRectToMyCoords(key, rect)
            if (rect.contains(x.toInt(), y.toInt())) {
                return key
            }
        }
        return null
    }

    private fun performGestureTyping() {
        if (wordPredictor == null) {
            val container = gestureTrailView?.rootView?.findViewById<ViewGroup>(R.id.keyboard_rows) ?: return
            val keyLocations = allKeys.mapNotNull { key ->
                val config = configMap[key.id]
                val latinChar = config?.latinBase ?: return@mapNotNull null
                val rect = android.graphics.Rect()
                key.getDrawingRect(rect)
                container.offsetDescendantRectToMyCoords(key, rect)
                latinChar to rect
            }
            wordPredictor = WordPredictor(keyLocations, userDict.getLearnedWords())
        }

        val candidates = wordPredictor?.predict(gesturePath) ?: emptyList()
        if (candidates.isNotEmpty()) {
            var bestMatch = candidates[0]
            if (isSentenceStart()) {
                bestMatch = bestMatch.replaceFirstChar { it.uppercase() }
            }
            
            currentInputConnection?.setComposingText(bestMatch, 1)
            updateBase()
            showCandidates(candidates)
        }
    }

    private fun showCandidates(candidates: List<String>) {
        candidateContainer?.let { container ->
            // Clear only dynamic views, keep preview_text
            for (i in container.childCount - 1 downTo 0) {
                val child = container.getChildAt(i)
                if (child.id != R.id.preview_text) {
                    container.removeViewAt(i)
                }
            }

            val isCaps = isSentenceStart()
            for (word in candidates) {
                val displayWord = if (isCaps) word.replaceFirstChar { it.uppercase() } else word
                val tv = TextView(ContextThemeWrapper(this, R.style.Theme_KrKey)).apply {
                    text = displayWord
                    textSize = 16f
                    setPadding(30, 0, 30, 0)
                    gravity = Gravity.CENTER
                    setTextColor(ContextCompat.getColor(this@KrKeyIME, R.color.key_text_color))
                    background = ContextCompat.getDrawable(this@KrKeyIME, R.drawable.key_bg)
                    setOnClickListener {
                        currentInputConnection?.commitText("$displayWord ", 1)
                        userDict.learnWord(word)
                        wordPredictor = null
                        updateBase()
                        showCandidates(emptyList())
                    }
                }
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
                lp.setMargins(8, 4, 8, 4)
                container.addView(tv, 0, lp) // Add at the beginning
            }
        }
    }

    override fun onKeyInput(view: FlickKeyView, text: String, isFlick: Boolean) {
        val ic = currentInputConnection ?: return
        
        if (isLatinMode && !isSymbolMode && (candidateContainer?.childCount ?: 0) > 1) { // > 1 because preview_text is child
            ic.finishComposingText()
            ic.commitText(" ", 1)
            showCandidates(emptyList())
            currentPeckedWord.clear()
        }

        val config = configMap[view.id]
        if (config != null) {
            val outText = getOutputTextForKey(config, isFlick)
            if (isLatinMode && !isSymbolMode) {
                if (outText.length == 1 && outText[0].isLetter()) {
                    currentPeckedWord.append(outText)
                } else {
                    if (currentPeckedWord.isNotEmpty()) {
                        userDict.learnWord(currentPeckedWord.toString())
                        wordPredictor = null
                        currentPeckedWord.clear()
                    }
                }
            }
            ic.commitText(outText, 1)
        } else {
            ic.commitText(text, 1)
        }
        updateBase()
    }

    private fun deleteLastCharacter() {
        val ic = currentInputConnection ?: return
        
        if ((candidateContainer?.childCount ?: 0) > 1) { 
            showCandidates(emptyList())
            ic.setComposingText("", 1)
            currentPeckedWord.clear()
            return
        }

        val textBefore = ic.getTextBeforeCursor(2, 0)
        if (textBefore.isNullOrEmpty()) {
            currentPeckedWord.clear()
            return
        }

        if (isLatinMode && !isSymbolMode && currentPeckedWord.isNotEmpty()) {
            currentPeckedWord.setLength(currentPeckedWord.length - 1)
        }

        if (textBefore.length == 2 && Character.isSurrogatePair(textBefore[0], textBefore[1])) {
            ic.deleteSurroundingText(2, 0)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
        updateBase()
    }

    private fun updateBase() {
        val ic = currentInputConnection ?: return
        val textBefore = ic.getTextBeforeCursor(2, 0)
        
        currentBase = if (!textBefore.isNullOrEmpty()) {
            if (textBefore.length == 2 && Character.isSurrogatePair(textBefore[0], textBefore[1])) {
                val cpString = textBefore.toString()
                if (vyanjanas.toBrahmiScript(currentScript).contains(cpString)) cpString else ""
            } else {
                val lastChar = textBefore.last().toString()
                if (vyanjanas.toBrahmiScript(currentScript).contains(lastChar)) lastChar else ""
            }
        } else {
            ""
        }

        if (isLatinMode && !isSymbolMode && !isShiftLocked) {
            val autoCaps = isSentenceStart()
            if (autoCaps != isShifted) {
                isShifted = autoCaps
            }
        }
        updatePreview()
        updateKeys()
    }

    private fun updateKeys() {
        updateLabels()
        val currentTf = if (!isLatinMode) {
            when (currentScript) {
                BrahmiScript.SIDDHAM -> siddhamTypeface
                BrahmiScript.GRANTHA -> granthaTypeface
                BrahmiScript.SHARADA -> sharadaTypeface
                BrahmiScript.BRAHMI -> brahmiTypeface
                else -> Typeface.DEFAULT
            }
        } else {
            Typeface.DEFAULT
        }
        previewText?.typeface = currentTf
        spaceBtn?.typeface = currentTf
        
        for (key in allKeys) {
            key.setTypeface(currentTf)
            val config = configMap[key.id] ?: continue
            val (baseLabel, flickLabel) = getLabelsForKey(config)
            key.setText(baseLabel, flickLabel)
        }
    }
    
    private fun updatePreview() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(50, 0) ?: ""
        val after = ic.getTextAfterCursor(50, 0) ?: ""
        previewText?.text = "$before|$after"
    }

    private fun getLabelsForKey(config: KeyConfig): Pair<String, String> {
        if (isSymbolMode) {
            var sBase = if (isShifted) (config.sym2Base ?: config.symBase ?: config.base) else (config.symBase ?: config.base)
            var sFlick = if (isShifted) (config.sym2Flick ?: config.symFlick ?: config.flick) else (config.symFlick ?: config.flick) 
            
            if (isLatinSymbolMode) {
                if (config.symBase != null && config.symFlick != null && 
                    config.symBase.length == 1 && config.symFlick.length == 1) {
                    val c1 = config.symBase[0]
                    val c2 = config.symFlick[0]
                    if ((c1 in '१'..'९' || c1 == '०') && (c2 in '0'..'9')) {
                        if (!isShifted) {
                            sBase = config.symFlick
                            sFlick = config.symBase
                        }
                    }
                }
            }
            return Pair(sBase.toBrahmiScript(currentScript), sFlick.toBrahmiScript(currentScript))
        } else if (isLatinMode) {
            var lBase = config.latinBase ?: config.base
            var lFlick = config.latinFlick ?: config.flick
            if (isShifted) {
                lBase = lBase.uppercase()
                lFlick = lFlick.uppercase()
            }
            return Pair(lBase, lFlick)
        } else {
            val pair = when (config.type) {
                KeyType.VOWEL -> {
                    if (currentBase.isNotEmpty()) Pair(config.matraBase ?: "", config.matraFlick ?: "")
                    else Pair(config.base, config.flick)
                }
                KeyType.MODIFIER -> {
                    val prefix = if (currentBase.isNotEmpty()) currentBase else "◌"
                    Pair(prefix + config.base, prefix + config.flick)
                }
                KeyType.CONSONANT, KeyType.SIMPLE -> {
                    Pair(config.base, config.flick)
                }
            }
            return Pair(pair.first.toBrahmiScript(currentScript), pair.second.toBrahmiScript(currentScript))
        }
    }
    
    private fun updateLabels() {
        if (isSymbolMode) {
            symBtn?.text = if (isLatinSymbolMode) "ABC" else "अल्"
            shiftBtn?.text = if (isShiftLocked) "⇪" else "⇧"
            spaceBtn?.text = currentScript.iastName
        } else if (isLatinMode) {
            symBtn?.text = "१२३"
            shiftBtn?.text = "EN"
            spaceBtn?.text = "English"
        } else {
            symBtn?.text = "१२३"
            shiftBtn?.text = "EN"
            spaceBtn?.text = currentScript.nativeName
        }
    }
    
    private fun getOutputTextForKey(config: KeyConfig, isFlick: Boolean): String {
        val outText = if (isSymbolMode) {
            if (isShifted) {
                if (isFlick) (config.sym2Flick ?: config.symFlick ?: config.flick)
                else (config.sym2Base ?: config.symBase ?: config.base)
            } else {
                if (isFlick) (config.symFlick ?: config.flick)
                else (config.symBase ?: config.base)
            }
        } else if (isLatinMode) {
            var text = if (isFlick) (config.latinFlick ?: config.flick)
                       else (config.latinBase ?: config.base)
            if (isShifted) text = text.uppercase()
            text
        } else {
            if (isFlick) config.flick else config.base
        }
        
        return if (isLatinMode && !isSymbolMode) outText else outText.toBrahmiScript(currentScript)
    }

    override fun onFinishInput() {
        super.onFinishInput()
        currentBase = ""
        showCandidates(emptyList())
        currentPeckedWord.clear()
    }
}

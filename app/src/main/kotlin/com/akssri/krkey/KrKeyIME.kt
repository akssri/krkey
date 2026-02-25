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
    
    // Views
    private var allKeys: List<FlickKeyView> = emptyList()
    private var shiftBtn: Button? = null
    private var symBtn: Button? = null

    override fun onCreateInputView(): View {
        val layout = layoutInflater.inflate(R.layout.keyboard_view, null) as LinearLayout
        
        allKeys = findAllFlickKeys(layout)
        allKeys.forEach { it.setOnKeyListener(this) }
        
        setupSpecialKeys(layout)
        updateKeys() // Initial draw
        
        return layout
    }
    
    private fun setupSpecialKeys(layout: View) {
        val backspaceBtn = layout.findViewById<Button>(R.id.key_backspace)
        backspaceBtn?.let { btn ->
            val handler = Handler(Looper.getMainLooper())
            val repeatRunnable = object : Runnable {
                override fun run() {
                    currentInputConnection?.deleteSurroundingText(1, 0)
                    updateBase()
                    handler.postDelayed(this, 50)
                }
            }
            btn.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        btn.isPressed = true
                        btn.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        currentInputConnection?.deleteSurroundingText(1, 0)
                        updateBase()
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
        
        layout.findViewById<Button>(R.id.key_space)?.setOnClickListener { btn ->
            btn.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            currentInputConnection?.commitText(" ", 1)
            updateBase()
        }
        
        layout.findViewById<Button>(R.id.key_enter)?.setOnClickListener { btn ->
            btn.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            updateBase()
        }

        shiftBtn = layout.findViewById(R.id.key_shift)
        shiftBtn?.setOnClickListener { btn ->
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
            }
            updateKeys()
        }
        
        symBtn = layout.findViewById(R.id.key_sym)
        symBtn?.setOnClickListener { btn ->
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

    override fun onFinishInput() {
        super.onFinishInput()
        currentBase = ""
    }
    
    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
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

    private fun updateBase() {
        val ic = currentInputConnection ?: return
        val textBefore = ic.getTextBeforeCursor(1, 0)
        
        currentBase = if (!textBefore.isNullOrEmpty()) {
            val lastChar = textBefore.last().toString()
            if (vyanjanas.contains(lastChar)) lastChar else ""
        } else {
            ""
        }
        
        updateKeys()
    }

    private fun updateKeys() {
        updateLabels()
        for (key in allKeys) {
            val config = configMap[key.id] ?: continue
            val (baseLabel, flickLabel) = getLabelsForKey(config)
            key.setText(baseLabel, flickLabel)
        }
    }
    
    private fun getLabelsForKey(config: KeyConfig): Pair<String, String> {
        if (isSymbolMode) {
            var sBase = if (isShifted) (config.sym2Base ?: config.symBase ?: config.base) else (config.symBase ?: config.base)
            var sFlick = if (isShifted) (config.sym2Flick ?: config.symFlick ?: config.flick) else (config.symFlick ?: config.flick)
            
            if (isLatinSymbolMode) {
                // Swap Devanagari numerals with Arabic if in Latin Symbol mode
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
            return Pair(sBase, sFlick)
        } else if (isLatinMode) {
            val lBase = config.latinBase ?: config.base
            val lFlick = config.latinFlick ?: config.flick
            return Pair(lBase, lFlick)
        } else {
            return when (config.type) {
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
        }
    }
    
    private fun updateLabels() {
        if (isSymbolMode) {
            symBtn?.text = if (isLatinSymbolMode) "ABC" else "अल्"
            shiftBtn?.text = if (isShiftLocked) "⇪" else "⇧"
            shiftBtn?.isSelected = isShifted || isShiftLocked
        } else {
            symBtn?.text = if (isLatinMode) "123" else "१२३"
            shiftBtn?.text = if (isLatinMode) "अ" else "EN"
            shiftBtn?.isSelected = isLatinMode
        }
    }

    override fun onKeyInput(view: FlickKeyView, text: String, isFlick: Boolean) {
        val ic = currentInputConnection ?: return
        val config = configMap[view.id]
        
        if (config != null) {
            val outText = getOutputTextForKey(config, isFlick)
            ic.commitText(outText, 1)
            
            if (isSymbolMode && isShifted && !isShiftLocked) {
                isShifted = false
                updateKeys()
            }
        } else {
            ic.commitText(text, 1)
        }
        
        updateBase()
    }
    
    private fun getOutputTextForKey(config: KeyConfig, isFlick: Boolean): String {
        if (isSymbolMode) {
            if (isShifted) {
                return if (isFlick) (config.sym2Flick ?: config.symFlick ?: config.flick) else (config.sym2Base ?: config.symBase ?: config.base)
            } else {
                val sBase = config.symBase ?: config.base
                val sFlick = config.symFlick ?: config.flick
                
                if (isLatinSymbolMode && config.symBase != null && config.symFlick != null && 
                    config.symBase.length == 1 && config.symFlick.length == 1) {
                    val c1 = config.symBase[0]
                    val c2 = config.symFlick[0]
                    if ((c1 in '१'..'९' || c1 == '०') && (c2 in '0'..'9')) {
                        return if (isFlick) config.symBase else config.symFlick
                    }
                }
                return if (isFlick) sFlick else sBase
            }
        } else if (isLatinMode) {
            return if (isFlick) (config.latinFlick ?: config.flick) else (config.latinBase ?: config.base)
        } else {
            return when (config.type) {
                KeyType.VOWEL -> {
                    if (currentBase.isNotEmpty()) {
                        if (isFlick) config.matraFlick ?: "" else config.matraBase ?: ""
                    } else {
                        if (isFlick) config.flick else config.base
                    }
                }
                KeyType.MODIFIER -> if (isFlick) config.flick else config.base
                KeyType.CONSONANT, KeyType.SIMPLE -> if (isFlick) config.flick else config.base
            }
        }
    }
}
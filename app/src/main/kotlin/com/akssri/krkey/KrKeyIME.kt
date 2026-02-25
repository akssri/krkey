package com.akssri.krkey

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.view.inputmethod.InputConnection
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent

class KrKeyIME : InputMethodService(), FlickKeyView.OnKeyListener {

    private val vyanjanas = "़कखगघङचछजझञटठडढणतथदधनपफबभमयरलवशषसहळ"
    private var currentBase: String = ""
    private var isShifted: Boolean = false
    private var isSymbolMode: Boolean = false
    private var allKeys: List<FlickKeyView> = emptyList()

    enum class KeyType { SIMPLE, VOWEL, MODIFIER, CONSONANT }

    data class KeyConfig(
        val id: Int,
        val type: KeyType,
        val base: String,
        val flick: String,
        val matraBase: String? = null,
        val matraFlick: String? = null,
        val symBase: String? = null,
        val symFlick: String? = null
    )

    private val keyConfigs = listOf(
                // Consonants (Row 1)
                KeyConfig(R.id.r1c2, KeyType.CONSONANT, "क", "ख", symBase = "२", symFlick = "2"),
                KeyConfig(R.id.r1c3, KeyType.CONSONANT, "भ", "ब", symBase = "३", symFlick = "3"),
                KeyConfig(R.id.r1c4, KeyType.CONSONANT, "ड", "ढ", symBase = "४", symFlick = "4"),
                KeyConfig(R.id.r1c5, KeyType.CONSONANT, "ट", "ठ", symBase = "५", symFlick = "5"),
                KeyConfig(R.id.r1c7, KeyType.CONSONANT, "ह", "ङ", symBase = "७", symFlick = "7"),
                KeyConfig(R.id.r1c8, KeyType.CONSONANT, "ग", "घ", symBase = "८", symFlick = "8"),
                KeyConfig(R.id.r1c9, KeyType.CONSONANT, "द", "ध", symBase = "९", symFlick = "9"),
                KeyConfig(R.id.r1c10, KeyType.CONSONANT, "ज", "झ", symBase = "०", symFlick = "0"),
        
                // Consonants (Row 2)
                KeyConfig(R.id.r2c5, KeyType.CONSONANT, "य", "ळ", symBase = "=", symFlick = "§"),
                KeyConfig(R.id.r2c6, KeyType.CONSONANT, "प", "फ", symBase = "(", symFlick = "{"),
                KeyConfig(R.id.r2c7, KeyType.CONSONANT, "र", "ष", symBase = ")", symFlick = "}"),
                KeyConfig(R.id.r2c8, KeyType.CONSONANT, "व", "ल", symBase = "@", symFlick = "%"),
                KeyConfig(R.id.r2c9, KeyType.CONSONANT, "त", "थ", symBase = ";", symFlick = ":"),
        
                // Consonants (Row 3)
                KeyConfig(R.id.r3c4, KeyType.CONSONANT, "म", "ण", symBase = "\\", symFlick = "/"),
                KeyConfig(R.id.r3c5, KeyType.CONSONANT, "न", "ञ", symBase = "'", symFlick = "\""),
                KeyConfig(R.id.r3c7, KeyType.CONSONANT, "च", "छ", symBase = "]", symFlick = "~"),
                KeyConfig(R.id.r3c8, KeyType.CONSONANT, "स", "श", symBase = "₹", symFlick = "$"),
        
                // Vowels (Row 1)
                KeyConfig(R.id.r1c1, KeyType.VOWEL, "ओ", "ऒ", "ो", "ॊ", symBase = "१", symFlick = "1"),
                KeyConfig(R.id.r1c6, KeyType.VOWEL, "ऋ", "ॠ", "ृ", "ॄ", symBase = "६", symFlick = "6"),
        
        // Vowels (Row 2)
        KeyConfig(R.id.r2c1, KeyType.VOWEL, "उ", "ऊ", "ु", "ू", symBase = "*", symFlick = "`"),
        KeyConfig(R.id.r2c2, KeyType.VOWEL, "ए", "ऎ", "े", "ॆ", symBase = "#", symFlick = "^"),
        KeyConfig(R.id.r2c3, KeyType.VOWEL, "अ", "आ", "्", "ा", symBase = "+", symFlick = "|"),
        KeyConfig(R.id.r2c4, KeyType.VOWEL, "इ", "ई", "ि", "ी", symBase = "-", symFlick = "_"),
        
        // Vowels (Row 3)
        KeyConfig(R.id.r3c2, KeyType.VOWEL, "ऐ", "औ", "ै", "ौ", symBase = "ऌ", symFlick = "ॡ"),
        
        // Modifiers (Row 3)
        KeyConfig(R.id.r3c3, KeyType.MODIFIER, "ं", "ँ", symBase = "़", symFlick = "ॐ"),
        KeyConfig(R.id.r3c6, KeyType.MODIFIER, "ः", "ऽ", symBase = "[", symFlick = "&"),
        
        // Simple (Row 4)
        KeyConfig(R.id.r4c2, KeyType.SIMPLE, "/", "'", symBase = ",", symFlick = "?"),
        KeyConfig(R.id.r4c4, KeyType.SIMPLE, "।", "!", symBase = ".", symFlick = "!")
    )
    
    private val configMap by lazy { keyConfigs.associateBy { it.id } }

    override fun onCreateInputView(): View {
        val layout = layoutInflater.inflate(R.layout.keyboard_view, null) as LinearLayout
        
        val keys = mutableListOf<FlickKeyView>()
        findAllFlickKeys(layout, keys)
        allKeys = keys

        for (key in allKeys) {
            key.setOnKeyListener(this)
        }
        
        val backspaceBtn = layout.findViewById<Button>(R.id.key_backspace)
        if (backspaceBtn != null) {
            val handler = Handler(Looper.getMainLooper())
            val repeatRunnable = object : Runnable {
                override fun run() {
                    currentInputConnection?.deleteSurroundingText(1, 0)
                    updateBase()
                    handler.postDelayed(this, 50)
                }
            }
            backspaceBtn.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        backspaceBtn.isPressed = true
                        currentInputConnection?.deleteSurroundingText(1, 0)
                        updateBase()
                        handler.postDelayed(repeatRunnable, 400)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        backspaceBtn.isPressed = false
                        handler.removeCallbacks(repeatRunnable)
                        true
                    }
                    else -> false
                }
            }
        }
        
        setupButton(layout, R.id.key_space) {
            currentInputConnection?.commitText(" ", 1)
            updateBase()
        }
        
        setupButton(layout, R.id.key_enter) {
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            updateBase()
        }

        val shiftBtn = layout.findViewById<Button>(R.id.key_shift)
        shiftBtn?.setOnClickListener {
            isShifted = !isShifted
            shiftBtn.isSelected = isShifted
            updateKeys()
        }
        
        val symBtn = layout.findViewById<Button>(R.id.key_sym)
        symBtn?.setOnClickListener {
            isSymbolMode = !isSymbolMode
            symBtn.text = if (isSymbolMode) "अल्" else "?123"
            updateKeys()
        }
        
        return layout
    }
    
    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
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

    private fun findAllFlickKeys(view: View, list: MutableList<FlickKeyView>) {
        if (view is FlickKeyView) {
            list.add(view)
        } else if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                findAllFlickKeys(view.getChildAt(i), list)
            }
        }
    }
    
    private fun setupButton(parent: View, id: Int, onClick: () -> Unit) {
        parent.findViewById<Button>(id)?.setOnClickListener { onClick() }
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
        for (key in allKeys) {
            val config = configMap[key.id]
            if (config != null) {
                if (isSymbolMode) {
                    val sBase = config.symBase ?: config.base
                    val sFlick = config.symFlick ?: config.flick
                    if (isShifted) key.setText(sFlick, sBase)
                    else key.setText(sBase, sFlick)
                } else {
                    when (config.type) {
                        KeyType.VOWEL -> {
                            if (currentBase.isNotEmpty()) {
                                if (isShifted) key.setText(config.matraFlick ?: "", config.matraBase ?: "")
                                else key.setText(config.matraBase ?: "", config.matraFlick ?: "")
                            } else {
                                if (isShifted) key.setText(config.flick, config.base)
                                else key.setText(config.base, config.flick)
                            }
                        }
                        KeyType.MODIFIER -> {
                             val prefix = if (currentBase.isNotEmpty()) currentBase else "◌"
                             if (isShifted) key.setText(prefix + config.flick, prefix + config.base)
                             else key.setText(prefix + config.base, prefix + config.flick)
                        }
                        KeyType.CONSONANT, KeyType.SIMPLE -> {
                             if (isShifted) key.setText(config.flick, config.base)
                             else key.setText(config.base, config.flick)
                        }
                    }
                }
            }
        }
    }

    override fun onKeyInput(view: FlickKeyView, text: String, isFlick: Boolean) {
        val ic = currentInputConnection ?: return
        val config = configMap[view.id]
        val effectiveIsFlick = if (isShifted) !isFlick else isFlick
        
        if (config != null) {
            if (isSymbolMode) {
                val outText = if (effectiveIsFlick) (config.symFlick ?: config.flick) else (config.symBase ?: config.base)
                ic.commitText(outText, 1)
            } else {
                when (config.type) {
                    KeyType.VOWEL -> {
                        if (currentBase.isNotEmpty()) {
                            val matra = if (effectiveIsFlick) config.matraFlick else config.matraBase
                            ic.commitText(matra ?: "", 1)
                        } else {
                            val vowel = if (effectiveIsFlick) config.flick else config.base
                            ic.commitText(vowel, 1)
                        }
                    }
                    KeyType.MODIFIER -> {
                        val mod = if (effectiveIsFlick) config.flick else config.base
                        ic.commitText(mod, 1)
                    }
                    KeyType.CONSONANT, KeyType.SIMPLE -> {
                        val outText = if (effectiveIsFlick) config.flick else config.base
                        ic.commitText(outText, 1)
                    }
                }
            }
        } else {
            ic.commitText(text, 1)
        }
        
        if (isShifted) {
            isShifted = false
            view.rootView.findViewById<Button>(R.id.key_shift)?.isSelected = false
        }
        
        updateBase()
    }
}
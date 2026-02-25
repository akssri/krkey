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
    private var allKeys: List<FlickKeyView> = emptyList()

    enum class KeyType { SIMPLE, VOWEL, MODIFIER }

    data class KeyConfig(
        val id: Int,
        val type: KeyType,
        val base: String,
        val flick: String,
        val matraBase: String? = null,
        val matraFlick: String? = null
    )

    private val keyConfigs = listOf(
        KeyConfig(R.id.r1c1, KeyType.VOWEL, "ओ", "ऒ", "ो", "ॊ"),
        KeyConfig(R.id.r1c6, KeyType.VOWEL, "ऋ", "ॠ", "ृ", "ॄ"),
        KeyConfig(R.id.r2c1, KeyType.VOWEL, "उ", "ऊ", "ु", "ू"),
        KeyConfig(R.id.r2c2, KeyType.VOWEL, "ए", "ऎ", "े", "ॆ"),
        KeyConfig(R.id.r2c3, KeyType.VOWEL, "अ", "आ", "्", "ा"),
        KeyConfig(R.id.r2c4, KeyType.VOWEL, "इ", "ई", "ि", "ी"),
        KeyConfig(R.id.r3c2, KeyType.VOWEL, "ऐ", "औ", "ै", "ौ"),
        
        KeyConfig(R.id.r3c3, KeyType.MODIFIER, "ं", "ँ"),
        KeyConfig(R.id.r3c6, KeyType.MODIFIER, "ः", "ऽ")
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
                    handler.postDelayed(this, 50) // Repeat delay
                }
            }
            backspaceBtn.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        backspaceBtn.isPressed = true
                        currentInputConnection?.deleteSurroundingText(1, 0)
                        updateBase()
                        handler.postDelayed(repeatRunnable, 400) // Initial delay
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

        setupButton(layout, R.id.key_shift) { }
        setupButton(layout, R.id.key_sym) { }
        
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
                when (config.type) {
                    KeyType.VOWEL -> {
                        if (currentBase.isNotEmpty()) {
                            key.setText(config.matraBase ?: "", config.matraFlick ?: "")
                        } else {
                            key.setText(config.base, config.flick)
                        }
                    }
                    KeyType.MODIFIER -> {
                         val prefix = if (currentBase.isNotEmpty()) currentBase else "◌"
                         key.setText(prefix + config.base, prefix + config.flick)
                    }
                    else -> { }
                }
            }
        }
    }

    override fun onKeyInput(view: FlickKeyView, text: String, isFlick: Boolean) {
        val ic = currentInputConnection ?: return
        val config = configMap[view.id]
        
        if (config != null) {
            when (config.type) {
                KeyType.VOWEL -> {
                    if (currentBase.isNotEmpty()) {
                        val matra = if (isFlick) config.matraFlick else config.matraBase
                        ic.commitText(matra ?: "", 1)
                    } else {
                        val vowel = if (isFlick) config.flick else config.base
                        ic.commitText(vowel, 1)
                    }
                }
                KeyType.MODIFIER -> {
                    val mod = if (isFlick) config.flick else config.base
                    ic.commitText(mod, 1)
                }
                KeyType.SIMPLE -> {
                    ic.commitText(text, 1)
                }
            }
        } else {
            ic.commitText(text, 1)
        }
        updateBase()
    }
}

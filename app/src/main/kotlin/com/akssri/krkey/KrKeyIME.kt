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
    
    override fun onCreate() {
        super.onCreate()
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
    private var siddhamTypeface: Typeface? = null
    private var granthaTypeface: Typeface? = null
    private var sharadaTypeface: Typeface? = null
    private var brahmiTypeface: Typeface? = null

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
            currentInputConnection?.commitText(" ", 1)
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
        
        // Ensure current script is still enabled
        val prefs = getSharedPreferences("krkey_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("script_${currentScript.name}", currentScript == BrahmiScript.NAGARI)) {
            val allScripts = BrahmiScript.values()
            val firstEnabled = allScripts.find { 
                prefs.getBoolean("script_${it.name}", it == BrahmiScript.NAGARI) 
            }
            currentScript = firstEnabled ?: BrahmiScript.NAGARI
        }
        
        updateKeys()
        updatePreview()
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
            prefs.getBoolean("script_${it.name}", it == BrahmiScript.NAGARI) 
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
            prefs.getBoolean("script_${it.name}", it == BrahmiScript.NAGARI) 
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

    private fun deleteLastCharacter() {
        val ic = currentInputConnection ?: return
        val textBefore = ic.getTextBeforeCursor(2, 0)
        if (textBefore.isNullOrEmpty()) {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
        } else {
            if (textBefore.length == 2 && Character.isSurrogatePair(textBefore[0], textBefore[1])) {
                ic.deleteSurroundingText(2, 0)
            } else {
                ic.deleteSurroundingText(1, 0)
            }
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
        updatePreview()
        
        for (key in allKeys) {
            key.setTypeface(currentTf)
            val config = configMap[key.id] ?: continue
            val (baseLabel, flickLabel) = getLabelsForKey(config)
            key.setText(baseLabel, flickLabel)
        }
    }
    
    private fun updatePreview() {
        val ic = currentInputConnection ?: return
        // Fetch text around cursor to show in preview bar
        val before = ic.getTextBeforeCursor(50, 0) ?: ""
        val after = ic.getTextAfterCursor(50, 0) ?: ""
        previewText?.text = "$before|$after"
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
            return Pair(sBase.toBrahmiScript(currentScript), sFlick.toBrahmiScript(currentScript))
        } else if (isLatinMode) {
            val lBase = config.latinBase ?: config.base
            val lFlick = config.latinFlick ?: config.flick
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
            shiftBtn?.isSelected = isShifted || isShiftLocked
            spaceBtn?.text = " "
        } else {
            symBtn?.text = if (isLatinMode) "123" else "१२३".toBrahmiScript(currentScript)
            shiftBtn?.text = if (isLatinMode) "अ" else "EN"
            shiftBtn?.isSelected = isLatinMode
            spaceBtn?.text = if (isLatinMode) "English" else currentScript.nativeName
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
        val outText = if (isSymbolMode) {
            if (isShifted) {
                if (isFlick) (config.sym2Flick ?: config.symFlick ?: config.flick) else (config.sym2Base ?: config.symBase ?: config.base)
            } else {
                val sBase = config.symBase ?: config.base
                val sFlick = config.symFlick ?: config.flick
                
                if (isLatinSymbolMode && config.symBase != null && config.symFlick != null && 
                    config.symBase.length == 1 && config.symFlick.length == 1) {
                    val c1 = config.symBase[0]
                    val c2 = config.symFlick[0]
                    if ((c1 in '१'..'९' || c1 == '०') && (c2 in '0'..'9')) {
                        if (isFlick) config.symBase else config.symFlick
                    } else {
                         if (isFlick) sFlick else sBase
                    }
                } else {
                    if (isFlick) sFlick else sBase
                }
            }
        } else if (isLatinMode) {
            if (isFlick) (config.latinFlick ?: config.flick) else (config.latinBase ?: config.base)
        } else {
            when (config.type) {
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
        
        return if (isSymbolMode) {
            outText.toBrahmiScript(currentScript)
        } else if (isLatinMode) {
            outText
        } else {
            outText.toBrahmiScript(currentScript)
        }
    }
}
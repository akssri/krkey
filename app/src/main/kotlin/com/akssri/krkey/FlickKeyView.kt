package com.akssri.krkey

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.graphics.Typeface
import androidx.core.content.ContextCompat

class FlickKeyView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val baseTextView: TextView
    private val flickTextView: TextView
    private val hintTextView: TextView

    private val density = context.resources.displayMetrics.density
    private val popupYOffset = (-80f * density).toInt()
    private val hintPaddingTop = (-1f * density).toInt()
    private val hintPaddingRight = (3f * density).toInt()

    private var popupWindow: PopupWindow? = null
    private var popupTextView: TextView? = null

    init {
        setBackgroundResource(R.drawable.key_bg)
        isClickable = false // Let parent handle all touches
        isFocusable = false

        baseTextView = TextView(context).apply {
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(context, R.color.key_text_color))
        }

        flickTextView = TextView(context).apply {
            textSize = 22f
            gravity = Gravity.CENTER
            visibility = View.INVISIBLE
            setTextColor(ContextCompat.getColor(context, R.color.flick_text_color))
        }

        hintTextView = TextView(context).apply {
            textSize = 12f
            gravity = Gravity.TOP or Gravity.END
            setPadding(0, hintPaddingTop, hintPaddingRight, 0)
            setTextColor(ContextCompat.getColor(context, R.color.hint_text_color))
        }

        addView(baseTextView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(flickTextView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(hintTextView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        initPopup()
    }

    private fun initPopup() {
        val popupView = LayoutInflater.from(context).inflate(R.layout.popup_preview, null)
        popupTextView = popupView.findViewById(R.id.popup_text)
        popupWindow = PopupWindow(popupView, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            isTouchable = false
            isFocusable = false
            setBackgroundDrawable(null)
        }
    }

    fun setVisualState(base: String, flick: String, isFlickActive: Boolean) {
        baseTextView.text = base
        hintTextView.text = flick
        flickTextView.text = flick
        
        if (isFlickActive) {
            baseTextView.visibility = View.INVISIBLE
            flickTextView.visibility = View.VISIBLE
        } else {
            baseTextView.visibility = View.VISIBLE
            flickTextView.visibility = View.INVISIBLE
        }
    }

    fun showPopup(text: String?) {
        if (text == null) return
        popupTextView?.text = text
        if (popupWindow?.isShowing != true) {
            try { popupWindow?.showAsDropDown(this, 0, -this.height + popupYOffset) } catch (e: Exception) {}
        }
        popupWindow?.update()
    }

    fun dismissPopup() {
        if (popupWindow?.isShowing == true) popupWindow?.dismiss()
    }

    fun setTypeface(typeface: Typeface?) {
        baseTextView.typeface = typeface
        flickTextView.typeface = typeface
        hintTextView.typeface = typeface
        popupTextView?.typeface = typeface
    }
    
    // We keep onKeyListener interface for the IME but remove logic from here
    interface OnKeyListener {
        fun onKeyInput(view: FlickKeyView, text: String, isFlick: Boolean)
    }
}
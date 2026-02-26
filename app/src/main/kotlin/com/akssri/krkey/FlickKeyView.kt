package com.akssri.krkey

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
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

    private var textBase: String? = null
    private var textFlick: String? = null

    private val baseTextView: TextView
    private val flickTextView: TextView
    private val hintTextView: TextView

    private var listener: OnKeyListener? = null
    private var startY: Float = 0f
    private var isFlick: Boolean = false

    // Configuration variables (converted from dp to px for consistency across devices)
    private val density = context.resources.displayMetrics.density
    private val flickThreshold = 6f * density // 12dp equivalent
    private val popupYOffset = (-80f * density).toInt() // -80dp above the key
    private val hintPaddingTop = (-1f * density).toInt() // -1dp
    private val hintPaddingRight = (3f * density).toInt() // 3dp

    private var popupWindow: PopupWindow? = null
    private var popupTextView: TextView? = null

    interface OnKeyListener {
	fun onKeyInput(view: FlickKeyView, text: String, isFlick: Boolean)
	fun isGestureEnabled(): Boolean
    }

    init {
	// Load attributes
	context.theme.obtainStyledAttributes(
	    attrs,
	    R.styleable.FlickKeyView,
	    0, 0
	).apply {
	    try {
		textBase = getString(R.styleable.FlickKeyView_textBase)
		textFlick = getString(R.styleable.FlickKeyView_textFlick)
	    } finally {
		recycle()
	    }
	}

	// Setup views
	setBackgroundResource(R.drawable.key_bg)
	isClickable = true
	isFocusable = true

	baseTextView = TextView(context).apply {
	    text = textBase
	    textSize = 22f
	    gravity = Gravity.CENTER
	    setTextColor(ContextCompat.getColor(context, R.color.key_text_color))
	}

	// This is the one that appears during flick
	flickTextView = TextView(context).apply {
	    text = textFlick
	    textSize = 22f
	    gravity = Gravity.CENTER
	    visibility = View.INVISIBLE
	    setTextColor(ContextCompat.getColor(context, R.color.flick_text_color))
	}

	// This is the permanent gray hint
	hintTextView = TextView(context).apply {
	    text = textFlick
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
	val inflater = LayoutInflater.from(context)
	val popupView = inflater.inflate(R.layout.popup_preview, null)
	popupTextView = popupView.findViewById(R.id.popup_text)

	popupWindow = PopupWindow(popupView,
	    FrameLayout.LayoutParams.WRAP_CONTENT,
	    FrameLayout.LayoutParams.WRAP_CONTENT).apply {
	    isTouchable = false
	    isFocusable = false
	    inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
	    setBackgroundDrawable(null)
	}
    }

    private fun showPopup(text: String?) {
	if (text == null) return
	popupTextView?.text = text

	if (popupWindow?.isShowing != true) {
	    // Position above the key using milestone-3 logic
	    try {
		popupWindow?.showAsDropDown(this, 0, -this.height + popupYOffset)
	    } catch (e: Exception) {
		e.printStackTrace()
	    }
	}
	popupWindow?.update()
    }

    private fun dismissPopup() {
	if (popupWindow?.isShowing == true) {
	    popupWindow?.dismiss()
	}
    }

    fun setTypeface(typeface: Typeface?) {
	baseTextView.typeface = typeface
	flickTextView.typeface = typeface
	hintTextView.typeface = typeface
	popupTextView?.typeface = typeface
    }

    fun setText(base: String, flick: String) {
	textBase = base
	textFlick = flick
	baseTextView.text = base
	flickTextView.text = flick
	hintTextView.text = flick
    }

    fun setOnKeyListener(listener: OnKeyListener) {
	this.listener = listener
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
	if (listener?.isGestureEnabled() == true) {
	    return false // Let parent handle it for trail/swipe
	}

	when (event.action) {
	    MotionEvent.ACTION_DOWN -> {
		startY = event.y
		isFlick = false
		isPressed = true
		performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
		showPopup(textBase)
		return true
	    }
	    MotionEvent.ACTION_MOVE -> {
		if (!isFlick) {
		    if (startY - event.y > flickThreshold) {
			isFlick = true
			baseTextView.visibility = View.INVISIBLE
			flickTextView.visibility = View.VISIBLE
			showPopup(textFlick)
		    }
		} else {
		    if (startY - event.y < flickThreshold / 2) {
			 isFlick = false
			 baseTextView.visibility = View.VISIBLE
			 flickTextView.visibility = View.INVISIBLE
			 showPopup(textBase)
		    }
		}
		return true
	    }
	    MotionEvent.ACTION_UP -> {
		isPressed = false
		baseTextView.visibility = View.VISIBLE
		flickTextView.visibility = View.INVISIBLE
		dismissPopup()

		if (isFlick) {
		    textFlick?.let { listener?.onKeyInput(this, it, true) }
		} else {
		    textBase?.let { listener?.onKeyInput(this, it, false) }
		}
		return true
	    }
	    MotionEvent.ACTION_CANCEL -> {
		isPressed = false
		baseTextView.visibility = View.VISIBLE
		flickTextView.visibility = View.INVISIBLE
		dismissPopup()
		return true
	    }
	}
	return super.onTouchEvent(event)
    }
}

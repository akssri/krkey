/*
 *  Copyright (c) 2026 Akshay Srinivasan <akssri@vakra.xyz>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.akssri.krkey

import android.content.Context
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * setText re-shapes the text and requests a layout of the whole keyboard even when the text is
 * the same; updateUI relabels every key after every keystroke, so it must skip unchanged labels.
 * Returns whether the text changed.
 */
internal fun TextView.setTextIfChanged(s: CharSequence): Boolean {
    if (TextUtils.equals(text, s)) return false
    text = s
    return true
}

/**
 * A character key: its label, the up-flick hint, and (for four-direction keys only) hints for the
 * other directions. Kept to as few text views as possible: every layer switch measures all keys.
 */
class FlickKeyView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : FrameLayout(context, attrs, defStyleAttr) {
        private val baseTextView: TextView
        private val hintTextView: TextView

        // Hints for four-direction keys (left, right, down), created on first use; up uses hintTextView
        private var leftHintView: TextView? = null
        private var rightHintView: TextView? = null
        private var downHintView: TextView? = null
        private var hintTypeface: Typeface? = null

        /** Set for four-direction keys (the tag still holds `base to up`). */
        var flickKey: FlickKey? = null

        private val density = context.resources.displayMetrics.density
        private val hintPaddingTop = (-1f * density).toInt()
        private val hintPaddingRight = (3f * density).toInt()

        // Label size, shrunk only when a label (e.g. a conjunct with its vowel sign) is wider
        // than the key. Fitted with one measureText when the text, typeface or size changes,
        // not with TextView auto-sizing, which searches sizes on every measure
        private val labelSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, LABEL_SP, resources.displayMetrics)
        private val minLabelSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, MIN_LABEL_SP, resources.displayMetrics)
        private val measurePaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)

        /** Where this key's preview is drawn; set by the IME for the current input view. */
        var previewLayer: KeyPreviewLayer? = null
        private var previewTypeface: Typeface? = null

        init {
            setBackgroundResource(R.drawable.key_bg)
            isClickable = false // Let parent handle all touches
            isFocusable = false

            baseTextView =
                TextView(context).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, labelSizePx)
                    maxLines = 1
                    gravity = Gravity.CENTER
                    setTextColor(ContextCompat.getColor(context, R.color.key_text_color))
                }

            hintTextView =
                TextView(context).apply {
                    textSize = 12f
                    gravity = Gravity.TOP or Gravity.END
                    setPadding(0, hintPaddingTop, hintPaddingRight, 0)
                    setTextColor(ContextCompat.getColor(context, R.color.hint_text_color))
                }

            addView(baseTextView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(hintTextView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }

        fun setVisualState(
            base: String,
            flick: String,
        ) {
            // updateUI calls this for every key after every keystroke; setText re-shapes the text and
            // requests a layout of the whole keyboard even when nothing changed, so skip unchanged labels
            if (baseTextView.setTextIfChanged(base)) fitLabel()
            hintTextView.setTextIfChanged(flick)
        }

        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int,
        ) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w != oldw) post { fitLabel() } // not during layout: changing the size requests one
        }

        private fun fitLabel() {
            val available = (width - baseTextView.paddingLeft - baseTextView.paddingRight) * 0.9f
            val label = baseTextView.text.toString()
            var size = labelSizePx
            if (available > 0 && label.isNotEmpty()) {
                measurePaint.typeface = baseTextView.typeface
                measurePaint.textSize = labelSizePx
                val textWidth = measurePaint.measureText(label)
                if (textWidth > available) size = (labelSizePx * available / textWidth).coerceAtLeast(minLabelSizePx)
            }
            if (Math.abs(baseTextView.textSize - size) > 0.5f) baseTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
        }

        /** Hints for the extra directions of a four-direction key; all null for a normal key. */
        fun setDirectionalHints(
            left: String?,
            right: String?,
            down: String?,
        ) {
            val directional = left != null || right != null || down != null
            // Four-direction keys show the up flick centred above, like the other directions
            hintTextView.gravity = if (directional) Gravity.TOP or Gravity.CENTER_HORIZONTAL else Gravity.TOP or Gravity.END
            if (!directional && leftHintView == null) return // never directional: no hint views at all
            if (leftHintView == null) {
                leftHintView = directionHint(Gravity.START or Gravity.CENTER_VERTICAL)
                rightHintView = directionHint(Gravity.END or Gravity.CENTER_VERTICAL)
                downHintView = directionHint(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            }
            for ((view, text) in listOf(leftHintView to left, rightHintView to right, downHintView to down)) {
                view!!.setTextIfChanged(text ?: "")
                view.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
        }

        private fun directionHint(g: Int): TextView {
            val view =
                TextView(context).apply {
                    textSize = 12f
                    gravity = g
                    setPadding(hintPaddingRight, 0, hintPaddingRight, 0)
                    setTextColor(ContextCompat.getColor(context, R.color.hint_text_color))
                    visibility = View.GONE
                    typeface = hintTypeface
                }
            addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            return view
        }

        fun setTypefaceForHints(typeface: Typeface?) {
            hintTypeface = typeface
            leftHintView?.typeface = typeface
            rightHintView?.typeface = typeface
            downHintView?.typeface = typeface
        }

        fun showPopup(text: String?) {
            if (text == null) return
            previewLayer?.show(this, text, previewTypeface)
        }

        fun dismissPopup() {
            previewLayer?.dismiss(this)
        }

        fun setTypeface(typeface: Typeface?) {
            if (baseTextView.typeface != typeface) {
                baseTextView.typeface = typeface
                fitLabel()
            }
            hintTextView.typeface = typeface
            previewTypeface = typeface
        }

        private companion object {
            const val LABEL_SP = 22f
            const val MIN_LABEL_SP = 10f
        }
    }

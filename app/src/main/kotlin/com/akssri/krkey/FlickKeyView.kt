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
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

class FlickKeyView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : FrameLayout(context, attrs, defStyleAttr) {
        private val baseTextView: TextView
        private val flickTextView: TextView
        private val hintTextView: TextView

        // Hints for four-direction keys (left, right, down); up uses hintTextView
        private val leftHintView: TextView
        private val rightHintView: TextView
        private val downHintView: TextView

        /** Set for four-direction keys (the tag still holds `base to up`). */
        var flickKey: FlickKey? = null

        private val density = context.resources.displayMetrics.density
        private val hintPaddingTop = (-1f * density).toInt()
        private val hintPaddingRight = (3f * density).toInt()

        /** Where this key's preview is drawn; set by the IME for the current input view. */
        var previewLayer: KeyPreviewLayer? = null
        private var previewTypeface: Typeface? = null

        init {
            setBackgroundResource(R.drawable.key_bg)
            isClickable = false // Let parent handle all touches
            isFocusable = false

            baseTextView =
                TextView(context).apply {
                    textSize = 22f
                    gravity = Gravity.CENTER
                    setTextColor(ContextCompat.getColor(context, R.color.key_text_color))
                }

            flickTextView =
                TextView(context).apply {
                    textSize = 22f
                    gravity = Gravity.CENTER
                    visibility = View.INVISIBLE
                    setTextColor(ContextCompat.getColor(context, R.color.flick_text_color))
                }

            hintTextView =
                TextView(context).apply {
                    textSize = 12f
                    gravity = Gravity.TOP or Gravity.END
                    setPadding(0, hintPaddingTop, hintPaddingRight, 0)
                    setTextColor(ContextCompat.getColor(context, R.color.hint_text_color))
                }

            val directionHint = { g: Int ->
                TextView(context).apply {
                    textSize = 12f
                    gravity = g
                    setPadding(hintPaddingRight, 0, hintPaddingRight, 0)
                    setTextColor(ContextCompat.getColor(context, R.color.hint_text_color))
                    visibility = View.GONE
                }
            }
            leftHintView = directionHint(Gravity.START or Gravity.CENTER_VERTICAL)
            rightHintView = directionHint(Gravity.END or Gravity.CENTER_VERTICAL)
            downHintView = directionHint(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)

            addView(baseTextView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(flickTextView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(hintTextView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(leftHintView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(rightHintView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(downHintView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }

        fun setVisualState(
            base: String,
            flick: String,
            isFlickActive: Boolean,
        ) {
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

        /** Hints for the extra directions of a four-direction key; all null for a normal key. */
        fun setDirectionalHints(
            left: String?,
            right: String?,
            down: String?,
        ) {
            val directional = left != null || right != null || down != null
            // Four-direction keys show the up flick centred above, like the other directions
            hintTextView.gravity = if (directional) Gravity.TOP or Gravity.CENTER_HORIZONTAL else Gravity.TOP or Gravity.END
            for ((view, text) in listOf(leftHintView to left, rightHintView to right, downHintView to down)) {
                view.text = text ?: ""
                view.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
        }

        fun setTypefaceForHints(typeface: Typeface?) {
            leftHintView.typeface = typeface
            rightHintView.typeface = typeface
            downHintView.typeface = typeface
        }

        fun showPopup(text: String?) {
            if (text == null) return
            previewLayer?.show(this, text, previewTypeface)
        }

        fun dismissPopup() {
            previewLayer?.dismiss(this)
        }

        fun setTypeface(typeface: Typeface?) {
            baseTextView.typeface = typeface
            flickTextView.typeface = typeface
            hintTextView.typeface = typeface
            previewTypeface = typeface
        }
    }

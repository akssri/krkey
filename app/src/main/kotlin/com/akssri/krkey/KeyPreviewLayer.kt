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
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Key previews drawn inside the keyboard's own window, laid over the whole input view.
 *
 * A PopupWindow per preview costs window-manager binder calls (add, first relayout, remove)
 * on every key press, which take 25-130 ms each on some devices (ZTE). Here a preview is a
 * view that is moved with translation and shown or hidden, so no window-manager calls are
 * made. Each key has its own bubble, so two thumbs can show previews at once. Previews of the
 * top row are clamped to the top of the input view (over the candidate bar).
 */
class KeyPreviewLayer(
    context: Context,
) : FrameLayout(context) {
    private val previews = HashMap<FlickKeyView, TextView>()
    private val yOffset = 80f * context.resources.displayMetrics.density
    private val keyLoc = IntArray(2)
    private val myLoc = IntArray(2)

    init {
        clipChildren = false
    }

    // Previews are never touchable: touches go to the keyboard underneath
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean = false

    fun show(
        key: FlickKeyView,
        text: String,
        typeface: Typeface?,
    ) {
        val tv = previews.getOrPut(key) { newPreview() }
        // setText always requests a layout, so only set it when it changes (every touch move lands here)
        if (tv.visibility == View.VISIBLE && tv.text.toString() == text && tv.typeface == typeface) return
        tv.typeface = typeface
        tv.text = text

        tv.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        key.getLocationInWindow(keyLoc)
        getLocationInWindow(myLoc)
        val x = keyLoc[0] - myLoc[0] + (key.width - tv.measuredWidth) / 2f
        val y = keyLoc[1] - myLoc[1] - yOffset
        tv.translationX = x.coerceIn(0f, (width - tv.measuredWidth).coerceAtLeast(0).toFloat())
        tv.translationY = y.coerceAtLeast(0f)
        tv.visibility = View.VISIBLE
    }

    fun dismiss(key: FlickKeyView) {
        previews[key]?.visibility = View.INVISIBLE
    }

    private fun newPreview(): TextView {
        val tv = LayoutInflater.from(context).inflate(R.layout.popup_preview, this, false) as TextView
        tv.visibility = View.INVISIBLE
        addView(tv)
        return tv
    }
}

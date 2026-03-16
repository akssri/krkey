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
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class GestureTrailView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val trailColor = Color.parseColor("#64B5F6")
    private val maxStrokeWidth = 10f * resources.displayMetrics.density
    private val minStrokeWidth = 2f * resources.displayMetrics.density
    private val fadeDurationMs = 300L

    private val paint =
        Paint().apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }

    private class TrailPoint(val x: Float, val y: Float, val time: Long)

    private val points = mutableListOf<TrailPoint>()
    private var isActive = false

    fun addPoint(
        x: Float,
        y: Float,
    ) {
        points.add(TrailPoint(x, y, System.currentTimeMillis()))
        isActive = true
        invalidate()
    }

    fun setPoints(newPoints: List<PointF>) {
        points.clear()
        val now = System.currentTimeMillis()
        for (p in newPoints) {
            points.add(TrailPoint(p.x, p.y, now))
        }
        isActive = true
        invalidate()
    }

    fun clear() {
        isActive = false
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.size < 2) return

        val now = System.currentTimeMillis()
        var hasVisible = false

        for (i in 1 until points.size) {
            val p0 = points[i - 1]
            val p1 = points[i]

            val age = if (isActive) 0L else now - p1.time
            if (age > fadeDurationMs) continue

            val fade =
                if (isActive) {
                    // While drawing: older segments fade based on position
                    val posRatio = i.toFloat() / points.size
                    0.3f + 0.7f * posRatio
                } else {
                    // After release: time-based fade out
                    1f - (age.toFloat() / fadeDurationMs)
                }

            val alpha = (fade * 200).toInt().coerceIn(0, 255)
            if (alpha == 0) continue

            paint.color = Color.argb(alpha, Color.red(trailColor), Color.green(trailColor), Color.blue(trailColor))
            paint.strokeWidth = minStrokeWidth + (maxStrokeWidth - minStrokeWidth) * fade

            canvas.drawLine(p0.x, p0.y, p1.x, p1.y, paint)
            hasVisible = true
        }

        if (!isActive && hasVisible) {
            postInvalidateOnAnimation()
        } else if (!isActive && !hasVisible) {
            points.clear()
        }
    }
}

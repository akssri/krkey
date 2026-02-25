package com.akssri.krkey

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class GestureTrailView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val path = Path()
    private val paint = Paint().apply {
        color = Color.parseColor("#64B5F6") // Light blue trail
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val points = mutableListOf<PointF>()

    fun addPoint(x: Float, y: Float) {
        if (points.isEmpty()) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
        points.add(PointF(x, y))
        invalidate()
    }

    fun setPoints(newPoints: List<PointF>) {
        path.reset()
        points.clear()
        if (newPoints.isEmpty()) {
            invalidate()
            return
        }
        path.moveTo(newPoints[0].x, newPoints[0].y)
        for (i in 1 until newPoints.size) {
            path.lineTo(newPoints[i].x, newPoints[i].y)
        }
        points.addAll(newPoints)
        invalidate()
    }

    fun clear() {
        path.reset()
        points.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!path.isEmpty) {
            canvas.drawPath(path, paint)
        }
    }
}

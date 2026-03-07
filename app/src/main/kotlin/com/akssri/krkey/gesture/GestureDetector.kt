package com.akssri.krkey.gesture

import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Unified gesture detection for both Indic (flick) and Latin (gesture typing) modes.
 * Replaces dual code paths with single classification logic.
 */
class GestureDetector(
    private val density: Float,
    private val flickVerticalThresholdDp: Float = 15f,
    private val flickVerticalityRatio: Float = 0.8f,
    private val flickMinDistanceDp: Float = 10f,
    private val swipeStartDistanceDp: Float = 50f,
    private val swipeForceDistanceDp: Float = 200f,
    private val tapMaxDistanceDp: Float = 10f,
) {
    sealed class GestureResult {
        data object Tap : GestureResult()

        data class Flick(val path: List<PointF>) : GestureResult()

        data class GestureTyping(val path: List<PointF>) : GestureResult()
    }

    /**
     * Classify a touch gesture based on the path taken.
     *
     * @param path List of points from touch DOWN to UP
     * @return GestureResult indicating the type of gesture
     */
    fun detectGesture(path: List<PointF>): GestureResult {
        if (path.size < 2) {
            return GestureResult.Tap
        }

        val start = path.first()
        val end = path.last()
        val totalDistance = calculatePathDistance(path)
        val directDistance = calculateDistance(start, end)

        // Flick gesture: upward movement, vertical threshold, mostly vertical
        val verticalDist = start.y - end.y // Positive means upward
        val horizontalDist = abs(end.x - start.x)

        val isUpwardMovement = verticalDist > flickVerticalThresholdDp * density
        val isMostlyVertical = horizontalDist < abs(verticalDist) * flickVerticalityRatio
        val hasMinimumMovement = totalDistance > flickMinDistanceDp * density

        if (isUpwardMovement && isMostlyVertical && hasMinimumMovement) {
            return GestureResult.Flick(path)
        }

        // Gesture typing: long horizontal movement or significant total path
        val isLongHorizontal = horizontalDist > swipeStartDistanceDp * density
        val isLongPath = totalDistance > swipeForceDistanceDp * density

        if (isLongHorizontal || isLongPath) {
            return GestureResult.GestureTyping(path)
        }

        // Default to tap if no other gesture detected
        return if (directDistance < tapMaxDistanceDp * density) {
            GestureResult.Tap
        } else {
            // Short drag, treat as tap
            GestureResult.Tap
        }
    }

    /**
     * Calculate total distance traveled along the path.
     */
    private fun calculatePathDistance(path: List<PointF>): Float {
        if (path.size < 2) return 0f

        var total = 0f
        for (i in 1 until path.size) {
            total += calculateDistance(path[i - 1], path[i])
        }
        return total
    }

    /**
     * Calculate straight-line distance between two points.
     */
    private fun calculateDistance(
        p1: PointF,
        p2: PointF,
    ): Float {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        return sqrt(dx.pow(2) + dy.pow(2))
    }

    /**
     * Helper for direct distance from start to end (used in KrKeyIME).
     */
    fun getDirectDistance(path: List<PointF>): Float {
        if (path.size < 2) return 0f
        return calculateDistance(path.first(), path.last())
    }
}

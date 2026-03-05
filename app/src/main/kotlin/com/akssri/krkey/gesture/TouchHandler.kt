package com.akssri.krkey.gesture

import android.graphics.PointF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import com.akssri.krkey.FlickKeyView
import com.akssri.krkey.location.KeyLocator

/**
 * Manages touch event lifecycle (DOWN → MOVE → UP/CANCEL).
 * Delegates to GestureDetector for classification and KeyLocator for key finding.
 * Provides clean callbacks for IME integration.
 */
class TouchHandler(
    private val keyLocator: KeyLocator,
    private val gestureDetector: GestureDetector,
) {
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var activeKey: FlickKeyView? = null
    private val gesturePath = mutableListOf<PointF>()
    private var gestureStartTime = 0L
    private var isGestureTypingActive = false

    /**
     * Callbacks for IME to handle gesture events.
     */
    interface Callbacks {
        /**
         * Called when a key is pressed (ACTION_DOWN).
         * @param key The key that was pressed
         * @param displayText The text to show in popup
         */
        fun onKeyPress(
            key: FlickKeyView,
            displayText: String,
        )

        /**
         * Called when a key is released (ACTION_UP).
         * @param key The key that was released
         * @param text The text to commit
         * @param isFlick Whether this was a flick gesture
         */
        fun onKeyRelease(
            key: FlickKeyView,
            text: String,
            isFlick: Boolean,
        )

        /**
         * Called during gesture typing when path updates.
         * @param path Current gesture path
         * @param isActive Whether gesture typing has activated
         */
        fun onGestureUpdate(
            path: List<PointF>,
            isActive: Boolean,
        )

        /**
         * Called when gesture typing completes.
         * @param path Final gesture path
         */
        fun onGestureComplete(path: List<PointF>)

        /**
         * Get display text for a key (base or flick text based on current gesture).
         * @param key The key to get text for
         * @param isFlickActive Whether flick gesture is currently active
         * @return Pair of (baseText, flickText)
         */
        fun getKeyDisplayText(
            key: FlickKeyView,
            isFlickActive: Boolean,
        ): Pair<String, String>

        /**
         * Get the text to commit when key is released.
         * @param key The key being released
         * @param isFlick Whether this was a flick gesture
         * @return Clean text to commit (with composition prefixes removed)
         */
        fun getKeyCommitText(
            key: FlickKeyView,
            isFlick: Boolean,
        ): String

        /**
         * Check if gesture typing should be enabled.
         */
        fun isGestureTypingEnabled(): Boolean
    }

    private var callbacks: Callbacks? = null

    fun setCallbacks(callbacks: Callbacks) {
        this.callbacks = callbacks
    }

    /**
     * Handle touch events from the keyboard container.
     * @return true if event was handled
     */
    fun handleTouch(event: MotionEvent): Boolean {
        val callbacks = this.callbacks ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleDown(event, callbacks)
            MotionEvent.ACTION_MOVE -> handleMove(event, callbacks)
            MotionEvent.ACTION_UP -> handleUp(event, callbacks)
            MotionEvent.ACTION_CANCEL -> handleCancel(callbacks)
        }
        return true
    }

    private fun handleDown(
        event: MotionEvent,
        callbacks: Callbacks,
    ) {
        activePointerId = event.getPointerId(0)
        gesturePath.clear()
        gesturePath.add(PointF(event.x, event.y))
        gestureStartTime = System.currentTimeMillis()
        isGestureTypingActive = false

        activeKey = keyLocator.findKeyAt(event.x, event.y)
        activeKey?.let { key ->
            key.isPressed = true
            key.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

            val (baseText, _) = callbacks.getKeyDisplayText(key, false)
            callbacks.onKeyPress(key, baseText)
        }
    }

    private fun handleMove(
        event: MotionEvent,
        callbacks: Callbacks,
    ) {
        val idx = event.findPointerIndex(activePointerId)
        if (idx < 0) return

        val x = event.getX(idx)
        val y = event.getY(idx)
        gesturePath.add(PointF(x, y))

        if (!isGestureTypingActive && callbacks.isGestureTypingEnabled()) {
            // Check if we should activate gesture typing
            val result = gestureDetector.detectGesture(gesturePath)

            if (result is GestureDetector.GestureResult.GestureTyping) {
                isGestureTypingActive = true
                activeKey?.let {
                    it.isPressed = false
                    it.dismissPopup()
                }
                callbacks.onGestureUpdate(gesturePath, true)
            } else {
                // Update popup for potential flick
                activeKey?.let { key ->
                    val isFlickActive = result is GestureDetector.GestureResult.Flick
                    val (baseText, flickText) = callbacks.getKeyDisplayText(key, isFlickActive)
                    key.showPopup(if (isFlickActive) flickText else baseText)
                }
            }
        } else if (isGestureTypingActive) {
            callbacks.onGestureUpdate(gesturePath, true)
        }
    }

    private fun handleUp(
        @Suppress("UNUSED_PARAMETER") event: MotionEvent,
        callbacks: Callbacks,
    ) {
        val finalKey = activeKey

        activeKey?.let {
            it.isPressed = false
            it.dismissPopup()
        }

        if (isGestureTypingActive) {
            callbacks.onGestureComplete(gesturePath)
        } else {
            finalKey?.let { key ->
                val result = gestureDetector.detectGesture(gesturePath)
                val isFlick = result is GestureDetector.GestureResult.Flick
                val commitText = callbacks.getKeyCommitText(key, isFlick)
                callbacks.onKeyRelease(key, commitText, isFlick)
            }
        }

        reset()
    }

    private fun handleCancel(callbacks: Callbacks) {
        activeKey?.let {
            it.isPressed = false
            it.dismissPopup()
        }

        if (isGestureTypingActive) {
            callbacks.onGestureUpdate(emptyList(), false)
        }

        reset()
    }

    private fun reset() {
        activeKey = null
        activePointerId = MotionEvent.INVALID_POINTER_ID
        isGestureTypingActive = false
        gesturePath.clear()
    }

    /**
     * Get current gesture path (for external use, e.g., trail drawing).
     */
    fun getCurrentPath(): List<PointF> = gesturePath.toList()

    /**
     * Check if gesture typing is currently active.
     */
    fun isGestureTyping(): Boolean = isGestureTypingActive
}

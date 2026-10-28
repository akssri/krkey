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
import android.graphics.PointF
import android.graphics.Rect
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Records swipes for offline replay (tools/swipe-sim/replay.py). Debug builds only: release
 * builds compile the no-op version in src/release instead.
 *
 * Each line of files/swipes.jsonl holds the key layout, the sampled path with timestamps,
 * the offered candidates, and what the user did with the result:
 *   accepted (kept the top word), picked (chose a candidate), rejected (backspaced it).
 * Pull with: adb shell run-as com.akssri.krkey cat files/swipes.jsonl
 */
class SwipeLogger(context: Context) {
    private val file = File(context.filesDir, "swipes.jsonl")
    private var pending: JSONObject? = null

    fun begin(
        keys: List<Pair<String, Rect>>,
        path: List<PointF>,
        times: List<Long>,
        candidates: List<Pair<String, Double>>,
    ) {
        discard() // a record still pending here has an unknown outcome
        val t0 = times.firstOrNull() ?: 0L
        pending =
            JSONObject().apply {
                put("time", System.currentTimeMillis())
                put("keys", JSONArray(keys.map { (label, r) -> JSONArray(listOf(label, r.left, r.top, r.right, r.bottom)) }))
                put("path", JSONArray(path.indices.map { i -> JSONArray(listOf(path[i].x.toDouble(), path[i].y.toDouble(), (times.getOrNull(i) ?: t0) - t0)) }))
                put("candidates", JSONArray(candidates.map { JSONArray(listOf(it.first, it.second)) }))
            }
    }

    /** Completes the pending record; label is the word the user ended up with (null if rejected). */
    fun finish(
        outcome: String,
        label: String?,
    ) {
        val record = pending ?: return
        pending = null
        record.put("outcome", outcome)
        record.put("label", label ?: JSONObject.NULL)
        try {
            if (file.length() > MAX_BYTES) return
            file.appendText(record.toString() + "\n")
        } catch (e: Exception) {
        }
    }

    fun discard() {
        pending = null
    }

    private companion object {
        const val MAX_BYTES = 8L * 1024 * 1024
    }
}

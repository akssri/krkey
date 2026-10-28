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

/** Release builds: swipe logging is compiled out (see src/debug for the real logger). */
@Suppress("UNUSED_PARAMETER")
class SwipeLogger(context: Context) {
    fun begin(
        keys: List<Pair<String, Rect>>,
        path: List<PointF>,
        times: List<Long>,
        candidates: List<Pair<String, Double>>,
    ) {}

    fun finish(
        outcome: String,
        label: String?,
    ) {}

    fun discard() {}
}

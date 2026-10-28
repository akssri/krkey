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
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Debug-build logging (tag KrKeyIME). Release builds compile the no-op version in src/release.
 *
 * Besides logcat, which is a rolling buffer, events are appended to files/debug_events.log
 * (rotated to .1 at 2 MB) so a session of dog-fooding can be reviewed later:
 *   adb shell run-as com.akssri.krkey cat files/debug_events.log
 */
object DebugLog {
    private const val MAX_BYTES = 2L * 1024 * 1024
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile private var file: File? = null

    fun init(context: Context) {
        file = File(context.filesDir, "debug_events.log")
    }

    inline fun d(message: () -> String) {
        val m = message()
        Log.d("KrKeyIME", m)
        write(m)
    }

    @PublishedApi
    internal fun write(message: String) {
        val f = file ?: return
        try {
            if (f.length() > MAX_BYTES) f.renameTo(File(f.parentFile, f.name + ".1"))
            f.appendText(timeFormat.format(Date()) + " " + message + "\n")
        } catch (e: Exception) {
        }
    }
}

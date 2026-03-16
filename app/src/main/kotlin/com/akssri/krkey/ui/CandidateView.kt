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

package com.akssri.krkey.ui

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.akssri.krkey.R

/**
 * RecyclerView-based candidate display.
 * Replaces dynamic TextView creation with view recycling.
 * Performance: Eliminates GC pressure from repeated view allocation.
 */
class CandidateView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : RecyclerView(context, attrs, defStyleAttr) {
        private val adapter = CandidateAdapter()
        private var onCandidateClickListener: ((String, String) -> Unit)? = null

        init {
            layoutManager = LinearLayoutManager(context, HORIZONTAL, false)
            setAdapter(adapter)
            overScrollMode = OVER_SCROLL_NEVER
            isHorizontalScrollBarEnabled = false
        }

        /**
         * Show candidate words.
         * @param words List of candidate words
         * @param shouldCapitalize Whether to capitalize first letter
         */
        fun showCandidates(
            words: List<String>,
            shouldCapitalize: Boolean = false,
        ) {
            adapter.updateCandidates(words, shouldCapitalize)
        }

        /**
         * Set click listener for candidate selection.
         * @param listener Called with (displayWord, originalWord)
         */
        fun setOnCandidateClickListener(listener: (String, String) -> Unit) {
            onCandidateClickListener = listener
            adapter.setClickListener(listener)
        }

        /**
         * Adapter for candidate words with ViewHolder pattern.
         */
        private inner class CandidateAdapter : RecyclerView.Adapter<CandidateViewHolder>() {
            private var candidates: List<String> = emptyList()
            private var capitalize: Boolean = false
            private var clickListener: ((String, String) -> Unit)? = null

            fun updateCandidates(
                words: List<String>,
                shouldCapitalize: Boolean,
            ) {
                candidates = words
                capitalize = shouldCapitalize
                notifyDataSetChanged()
            }

            fun setClickListener(listener: (String, String) -> Unit) {
                clickListener = listener
            }

            override fun onCreateViewHolder(
                parent: ViewGroup,
                viewType: Int,
            ): CandidateViewHolder {
                val textView =
                    TextView(parent.context).apply {
                        textSize = 16f
                        setPadding(30, 0, 30, 0)
                        gravity = Gravity.CENTER
                        setTextColor(ContextCompat.getColor(context, R.color.key_text_color))
                        background = ContextCompat.getDrawable(context, R.drawable.key_bg)
                        layoutParams =
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            ).apply {
                                setMargins(8, 4, 8, 4)
                            }
                    }
                return CandidateViewHolder(textView)
            }

            override fun onBindViewHolder(
                holder: CandidateViewHolder,
                position: Int,
            ) {
                val originalWord = candidates[position]
                val displayWord =
                    if (capitalize) {
                        originalWord.replaceFirstChar { it.uppercase() }
                    } else {
                        originalWord
                    }
                holder.bind(displayWord, originalWord, clickListener)
            }

            override fun getItemCount(): Int = candidates.size
        }

        /**
         * ViewHolder for candidate word.
         */
        private class CandidateViewHolder(private val textView: TextView) :
            RecyclerView.ViewHolder(textView) {
            fun bind(
                displayWord: String,
                originalWord: String,
                clickListener: ((String, String) -> Unit)?,
            ) {
                textView.text = displayWord
                textView.setOnClickListener {
                    clickListener?.invoke(displayWord, originalWord)
                }
            }
        }
    }

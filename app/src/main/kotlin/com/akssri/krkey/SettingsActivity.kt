package com.akssri.krkey

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "कृ-keyboard"

        val recyclerView = findViewById<RecyclerView>(R.id.scripts_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val typefaces =
            mapOf(
                BrahmiScript.SIDDHAM to ResourcesCompat.getFont(this, R.font.noto_sans_siddham),
                BrahmiScript.GRANTHA to ResourcesCompat.getFont(this, R.font.noto_sans_grantha),
                BrahmiScript.SHARADA to ResourcesCompat.getFont(this, R.font.noto_sans_sharada),
                BrahmiScript.BRAHMI to ResourcesCompat.getFont(this, R.font.noto_sans_brahmi),
            )

        recyclerView.adapter = ScriptAdapter(typefaces)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.settings_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_clear_dictionary) {
            AlertDialog.Builder(this)
                .setTitle("गृहीतपदानि लोपय")
                .setMessage("व्यक्तिगतशब्दकोशात् सर्वे अधीतशब्दाः लोपनीयाः इति खचितं किम्")
                .setPositiveButton("आम् लोपय") { _, _ ->
                    UserDictionaryManager(this).clear()
                    Toast.makeText(this, "Dictionary cleared", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("मास्तु", null)
                .show()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    inner class ScriptAdapter(private val typefaces: Map<BrahmiScript, Typeface?>) : RecyclerView.Adapter<ScriptAdapter.ViewHolder>() {
        private val scripts = BrahmiScript.values().filter { !it.isExperimental }
        private val prefs = getSharedPreferences("krkey_prefs", MODE_PRIVATE)

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.script_name)
            val nativeName: TextView = view.findViewById(R.id.script_native_name)
            val checkbox: CheckBox = view.findViewById(R.id.script_checkbox)
            val dictContainer: ViewGroup = view.findViewById(R.id.dict_container)
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_script_toggle, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(
            holder: ViewHolder,
            position: Int,
        ) {
            val script = scripts[position]
            holder.name.text = script.nativeName
            holder.nativeName.text = script.iastName
            holder.name.typeface = typefaces[script] ?: Typeface.DEFAULT
            holder.nativeName.typeface = Typeface.DEFAULT

            // Default: Devanagari is enabled by default if no pref exists
            val isEnabled = prefs.getBoolean("script_${script.name}", script == BrahmiScript.DEVANAGARI)

            holder.checkbox.setOnCheckedChangeListener(null) // Clear listener before setting state
            holder.checkbox.isChecked = isEnabled

            val updatePref = { checked: Boolean ->
                val enabledCount =
                    scripts.count {
                        if (it == script) checked else prefs.getBoolean("script_${it.name}", it == BrahmiScript.DEVANAGARI)
                    }

                if (enabledCount > 0) {
                    prefs.edit().putBoolean("script_${script.name}", checked).apply()
                    true
                } else {
                    holder.checkbox.isChecked = true // Revert UI
                    false
                }
            }

            holder.itemView.setOnClickListener {
                val newState = !holder.checkbox.isChecked
                if (updatePref(newState)) {
                    holder.checkbox.isChecked = newState
                }
            }

            holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
                updatePref(isChecked)
                // Show/hide dictionary options
                updateDictionaryUI(holder, script, isChecked)
            }

            // Initialize dictionary UI
            updateDictionaryUI(holder, script, isEnabled)
        }

        private fun updateDictionaryUI(
            holder: ViewHolder,
            script: BrahmiScript,
            isEnabled: Boolean,
        ) {
            holder.dictContainer.removeAllViews()

            if (!isEnabled) {
                holder.dictContainer.visibility = View.GONE
                return
            }

            val dictionaries = script.getAvailableDictionaries()
            if (dictionaries.isEmpty()) {
                holder.dictContainer.visibility = View.GONE
                return
            }

            holder.dictContainer.visibility = View.VISIBLE
            val defaults = script.getDefaultDictionaries()

            dictionaries.forEach { (dictFile, displayName) ->
                val checkbox =
                    CheckBox(holder.itemView.context).apply {
                        text = displayName
                        textSize = 14f
                        setPadding(0, 8, 0, 8)

                        val isChecked = prefs.getBoolean("dict_${script.name}_$dictFile", dictFile in defaults)
                        setOnCheckedChangeListener(null)
                        this.isChecked = isChecked

                        setOnCheckedChangeListener { _, checked ->
                            prefs.edit().putBoolean("dict_${script.name}_$dictFile", checked).apply()
                        }
                    }
                holder.dictContainer.addView(checkbox)
            }
        }

        override fun getItemCount() = scripts.size
    }
}

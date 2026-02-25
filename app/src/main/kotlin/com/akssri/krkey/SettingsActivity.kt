package com.akssri.krkey

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import androidx.core.content.res.ResourcesCompat
import android.graphics.Typeface

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "KrKey Scripts"

        val recyclerView = findViewById<RecyclerView>(R.id.scripts_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        val typefaces = mapOf(
            BrahmiScript.SIDDHAM to ResourcesCompat.getFont(this, R.font.noto_sans_siddham),
            BrahmiScript.GRANTHA to ResourcesCompat.getFont(this, R.font.noto_sans_grantha),
            BrahmiScript.SHARADA to ResourcesCompat.getFont(this, R.font.noto_sans_sharada),
            BrahmiScript.BRAHMI to ResourcesCompat.getFont(this, R.font.noto_sans_brahmi)
        )
        
        recyclerView.adapter = ScriptAdapter(typefaces)
    }

    inner class ScriptAdapter(private val typefaces: Map<BrahmiScript, Typeface?>) : RecyclerView.Adapter<ScriptAdapter.ViewHolder>() {
        private val scripts = BrahmiScript.values()
        private val prefs = getSharedPreferences("krkey_prefs", MODE_PRIVATE)

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.script_name)
            val nativeName: TextView = view.findViewById(R.id.script_native_name)
            val checkbox: CheckBox = view.findViewById(R.id.script_checkbox)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_script_toggle, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val script = scripts[position]
            holder.name.text = script.nativeName
            holder.nativeName.text = script.iastName
            holder.name.typeface = typefaces[script] ?: Typeface.DEFAULT
            holder.nativeName.typeface = Typeface.DEFAULT
            
            // Default: Nagari is enabled by default if no pref exists
            val isEnabled = prefs.getBoolean("script_${script.name}", script == BrahmiScript.NAGARI)
            
            holder.checkbox.setOnCheckedChangeListener(null) // Clear listener before setting state
            holder.checkbox.isChecked = isEnabled

            val updatePref = { checked: Boolean ->
                val enabledCount = scripts.count { 
                    if (it == script) checked else prefs.getBoolean("script_${it.name}", it == BrahmiScript.NAGARI)
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
            }
        }

        override fun getItemCount() = scripts.size
    }
}

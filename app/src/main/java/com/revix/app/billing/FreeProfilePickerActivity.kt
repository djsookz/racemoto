package com.revix.app.billing

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.revix.app.Profile
import com.revix.app.R
import com.revix.app.applySystemBarsPaddingToRoot
import com.revix.app.data.ProfileStorage
import com.revix.app.settings.LanguageManager

/**
 * Blocking screen: free user with 2+ profiles must pick the one writable profile.
 * Back is disabled until a choice is confirmed.
 */
class FreeProfilePickerActivity : AppCompatActivity() {

    private var selectedId: Long = -1L

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_free_profile_picker)
        applySystemBarsPaddingToRoot()

        if (!bindOrFinish()) return

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(
                    this@FreeProfilePickerActivity,
                    R.string.free_profile_picker_must_choose,
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        bindOrFinish()
    }

    /**
     * @return false if the activity finished (no longer needs a pick).
     */
    private fun bindOrFinish(): Boolean {
        ProAccess.reconcile(this)
        if (ProAccess.hasFullAccess(this)) {
            finish()
            return false
        }

        val profiles = ProfileStorage.loadProfiles(this)
        if (profiles.size <= 1) {
            // Single profile does not need a permanent "chosen" flag.
            finish()
            return false
        }

        if (ProAccess.hasChosenFreeProfile(this)) {
            finish()
            return false
        }

        val recycler = findViewById<RecyclerView>(R.id.rvFreeProfiles)
        val btnConfirm = findViewById<MaterialButton>(R.id.btnConfirmFreeProfile)
        if (recycler.adapter == null) {
            val adapter = Adapter(profiles) { id ->
                selectedId = id
                btnConfirm.isEnabled = true
            }
            recycler.layoutManager = LinearLayoutManager(this)
            recycler.adapter = adapter
            btnConfirm.setOnClickListener {
                if (selectedId <= 0L) return@setOnClickListener
                ProAccess.setFreeProfileChoice(this, selectedId)
                Toast.makeText(this, R.string.free_profile_picker_done, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        return true
    }

    private class Adapter(
        private val profiles: List<Profile>,
        private val onSelected: (Long) -> Unit
    ) : RecyclerView.Adapter<Adapter.Holder>() {
        private var selectedId: Long = -1L

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val row: View = view.findViewById(R.id.rowFreeProfile)
            val name: TextView = view.findViewById(R.id.tvFreeProfileName)
            val mark: TextView = view.findViewById(R.id.tvFreeProfileSelected)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_free_profile_choice, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val profile = profiles[position]
            holder.name.text = profile.name
            holder.mark.visibility = if (profile.id == selectedId) View.VISIBLE else View.GONE
            holder.row.setBackgroundResource(
                if (profile.id == selectedId) R.drawable.bg_pro_plan_card_featured
                else R.drawable.bg_pro_plan_card
            )
            holder.row.setOnClickListener {
                selectedId = profile.id
                onSelected(profile.id)
                notifyDataSetChanged()
            }
        }

        override fun getItemCount(): Int = profiles.size
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, FreeProfilePickerActivity::class.java)
    }
}

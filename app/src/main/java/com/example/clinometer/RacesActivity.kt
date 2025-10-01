package com.example.clinometer

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.clinometer.settings.LanguageManager
import com.google.android.material.button.MaterialButton

class RacesActivity : AppCompatActivity() {
    
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    private lateinit var adapter: RaceAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var btnBack: MaterialButton
    private val racesList = mutableListOf<Race>()
    private var backPressedTime: Long = 0
    private lateinit var backToast: Toast

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_races)
        
        recyclerView = findViewById(R.id.rvRaces)
        emptyView = findViewById(R.id.tvEmptyView)
        btnBack = findViewById(R.id.btnBack)

        loadRaces()

        adapter = RaceAdapter(
            races = racesList,
            onItemClick = { race ->
                val intent = Intent(this@RacesActivity, MapActivity::class.java).apply {
                    putExtra("RACE_ID", race.id)
                }
                startActivity(intent)
            },
            onDeleteClick = { race ->
                showDeleteConfirmation(race)
            },
            onRename = { race, newName ->
                val all = RouteStorage.loadRaces(this).toMutableList()
                val idx = all.indexOfFirst { it.id == race.id }
                if (idx >= 0) {
                    all[idx].name = newName
                    RouteStorage.saveRaces(this, all)
                }
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Настройка на бутона за назад
        btnBack.setOnClickListener {
            navigateToMap()
        }

        checkEmptyList()
    }

    private fun loadRaces() {
        racesList.clear()
        val allRaces = RouteStorage.loadRaces(this)

        // Филтриране по текущ профил
        val currentProfileId = ProfileStorage.getSelectedProfileId(this)
        val filteredRaces = allRaces.filter { it.profileId == currentProfileId }

        racesList.addAll(filteredRaces)
    }

    override fun onResume() {
        super.onResume()
        loadRaces()
        adapter.notifyDataSetChanged()
        checkEmptyList()
    }

    private fun checkEmptyList() {
        if (racesList.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
        }
    }

    override fun onBackPressed() {
        navigateToMap()
    }

    private fun navigateToMap() {
        val intent = Intent(this, MainMapActivity::class.java)
        startActivity(intent)
        overridePendingTransition(0, 0)
        finish()
    }

    private fun showDeleteConfirmation(race: Race) {
        AlertDialog.Builder(this)
            .setTitle("Изтриване на сесия")
            .setMessage(getString(R.string.delete_confirmation, race.name ?: "Session"))
            .setPositiveButton(getString(R.string.delete_button)) { _, _ ->
                // Изтриваме от базата данни
                val all = RouteStorage.loadRaces(this).toMutableList()
                all.removeAll { it.id == race.id }
                RouteStorage.saveRaces(this, all)
                
                // Презареждаме всичко
                loadRaces()
                adapter.updateRaces(racesList)
                checkEmptyList()
                
                Toast.makeText(this, "✅ Сесията е изтрита", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel_button), null)
            .show()
    }
}

package com.example.clinometer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class RacesActivity : BaseActivity() {
    override fun getLayoutResourceId(): Int = R.layout.activity_races
    override fun getNavigationItemId(): Int = R.id.navSession

    private lateinit var adapter: RaceAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private val racesList = mutableListOf<Race>()
    private var backPressedTime: Long = 0
    private lateinit var backToast: Toast

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recyclerView = findViewById(R.id.rvRaces)
        emptyView = findViewById(R.id.tvEmptyView)

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
                val all = RouteStorage.loadRaces(this).toMutableList()
                val idx = all.indexOfFirst { it.id == race.id }
                if (idx >= 0) {
                    all.removeAt(idx)
                    RouteStorage.saveRaces(this, all)
                    val posInList = racesList.indexOfFirst { it.id == race.id }
                    if (posInList >= 0) {
                        racesList.removeAt(posInList)
                        adapter.notifyItemRemoved(posInList)
                    }
                }

                checkEmptyList()
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
        if (backPressedTime + 2000 > System.currentTimeMillis()) {
            backToast.cancel()
            super.onBackPressed()
            return
        } else {
            backToast = Toast.makeText(baseContext, "Натиснете отново за изход", Toast.LENGTH_SHORT)
            backToast.show()
        }

        backPressedTime = System.currentTimeMillis()
    }
}

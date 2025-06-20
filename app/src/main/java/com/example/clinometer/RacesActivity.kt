package com.example.clinometer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class RacesActivity : AppCompatActivity() {

    private lateinit var adapter: RaceAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private val racesList = mutableListOf<Race>()  // Инициализираме веднага

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_races)

        recyclerView = findViewById(R.id.rvRaces)
        emptyView = findViewById(R.id.tvEmptyView)
        val btnNewRoute = findViewById<Button>(R.id.btnNewRoute)

        // Зареждаме списъка с сесии
        loadRaces()

        // Слушател за бутона "Нов маршрут"
        btnNewRoute.setOnClickListener {
            startActivity(Intent(this, CountdownActivity::class.java))
        }

        adapter = RaceAdapter(
            races = racesList,
            onItemClick = { race ->
                // Предаваме целия Race обект на MapActivity
                val intent = Intent(this@RacesActivity, MapActivity::class.java).apply {
                    putExtra("RACE", race)
                }
                startActivity(intent)
            },
            onDeleteClick = { race ->
                // 1) Прочитаме всички маршрути
                val all = RouteStorage.loadRaces(this).toMutableList()
                // 2) Намираме индекса и го махаме
                val idx = all.indexOfFirst { it.id == race.id }
                if (idx >= 0) {
                    all.removeAt(idx)
                    // 3) Записваме обратно
                    RouteStorage.saveRaces(this, all)
                    // 4) Обновяваме локалния списък и UI
                    val posInList = racesList.indexOfFirst { it.id == race.id }
                    if (posInList >= 0) {
                        racesList.removeAt(posInList)
                        adapter.notifyItemRemoved(posInList)
                    }
                }

                checkEmptyList()
            },
            onRename = { race, newName ->
                // Записваме новото име в хранилището:
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
        val loadedRaces = RouteStorage.loadRaces(this)
        racesList.addAll(loadedRaces)
    }

    override fun onResume() {
        super.onResume()
        // При връщане, презареждаме списъка и обновяваме адаптера
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
}
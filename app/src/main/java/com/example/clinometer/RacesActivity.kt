package com.example.clinometer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.Button


class RacesActivity : AppCompatActivity() {

    private lateinit var adapter: RaceAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private val racesList = mutableListOf<Race>()  // Инициализираме веднага

    private fun computeDuration(pts: List<RoutePoint>): Long {
        if (pts.isEmpty()) return 0L
        val times = pts.map { it.timestamp }
        return (times.maxOrNull() ?: 0L) - (times.minOrNull() ?: 0L)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_races)

        recyclerView = findViewById(R.id.rvRaces)
        emptyView   = findViewById(R.id.tvEmptyView)
        val btnNewRoute = findViewById<Button>(R.id.btnNewRoute)


        // Зареждаме списъка още в onCreate

        // Зареждаме списъка още в onCreate
        racesList.clear()
        val loadedPoints: List<List<RoutePoint>> = RouteStorage.loadRoutes(this)
        val loadedRaces: List<Race> = loadedPoints.map { pts ->
            // timestamp на началото на маршрута
            val startTs = pts.firstOrNull()?.timestamp ?: 0L
            // duration = крайно време минус начално (или последната стойност)
            val endTs   = pts.lastOrNull()?.timestamp  ?: 0L
            val duration = endTs - startTs

            Race(
                id = startTs,            // използваме началното време като уникален ID
                routePoints = pts,
                timestamp    = startTs,  // записваме кога е започнал
                duration     = duration  // обща продължителност
            )
        }
        racesList.addAll(loadedRaces)





        // Слушател за бутона "Нов маршрут"
        btnNewRoute.setOnClickListener {
            startActivity(Intent(this, CountdownActivity::class.java))
        }

        adapter = RaceAdapter(
            races = racesList,
            onItemClick = { race ->
                val realDuration = race.routePoints.maxOfOrNull { it.timestamp } ?: 0L
                val intent = Intent(this@RacesActivity, MapActivity::class.java).apply {
                    putParcelableArrayListExtra("ROUTE", ArrayList(race.routePoints))
                    putExtra("TOTAL_TIME", realDuration)
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

    override fun onResume() {
        super.onResume()
        // При връщане, презареждаме списъка и обновяваме адаптера
        racesList.clear()
        val loadedPoints: List<List<RoutePoint>> = RouteStorage.loadRoutes(this)
        val loadedRaces: List<Race> = RouteStorage.loadRaces(this)
        racesList.clear()
        racesList.addAll(loadedRaces)



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

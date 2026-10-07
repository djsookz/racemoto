package com.revix.app.main.session

import android.content.Context
import android.util.Log
import com.revix.app.Race
import com.revix.app.RoutePoint
import com.revix.app.RouteSnapshotGenerator
import com.revix.app.RouteStorage
import java.util.concurrent.Executors

object SessionSaveCoordinator {

    fun persistRace(context: Context, race: Race, routePoints: List<RoutePoint>) {
        RouteStorage.saveRoutePoints(context, race.id, routePoints)

        val allRaces = RouteStorage.loadRaces(context).toMutableList()
        allRaces.add(race)
        RouteStorage.saveRaces(context, allRaces)

        if (routePoints.isNotEmpty()) {
            generateSnapshotAsync(context, race.id, routePoints)
        }
    }

    private fun generateSnapshotAsync(
        context: Context,
        raceId: Long,
        routePoints: List<RoutePoint>
    ) {
        Executors.newSingleThreadExecutor().execute {
            try {
                RouteSnapshotGenerator.generateAndSaveSnapshot(
                    context = context,
                    raceId = raceId,
                    routePoints = routePoints
                ) { success ->
                    Log.d(
                        "SessionSaveCoordinator",
                        if (success) "✅ Snapshot generated for race $raceId" else "❌ Failed to generate snapshot for race $raceId"
                    )
                }
            } catch (error: Exception) {
                Log.e("SessionSaveCoordinator", "Error generating snapshot", error)
            }
        }
    }
}

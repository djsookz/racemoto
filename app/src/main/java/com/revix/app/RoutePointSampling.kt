package com.revix.app

import kotlin.math.roundToInt

/**
 * Uniform sampling that always keeps first and last points.
 * Used for map polylines, charts, and snapshots — full fidelity stays in points_*.json.
 */
fun sampleRoutePoints(points: List<RoutePoint>, maxPoints: Int): List<RoutePoint> {
    if (points.size <= maxPoints || maxPoints < 3) return points

    val out = ArrayList<RoutePoint>(maxPoints)
    out.add(points.first())
    val lastIndex = points.lastIndex
    val step = lastIndex.toFloat() / (maxPoints - 1).toFloat()
    for (i in 1 until maxPoints - 1) {
        val index = (i * step).roundToInt().coerceIn(1, lastIndex - 1)
        out.add(points[index])
    }
    out.add(points.last())
    return out
}

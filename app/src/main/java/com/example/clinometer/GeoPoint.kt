package com.example.clinometer

import android.os.Parcel
import android.os.Parcelable

/**
 * Simple GeoPoint class to replace OSMDroid GeoPoint
 * Contains latitude and longitude
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readDouble(),
        parcel.readDouble()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeDouble(latitude)
        parcel.writeDouble(longitude)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<GeoPoint> {
        override fun createFromParcel(parcel: Parcel): GeoPoint = GeoPoint(parcel)
        override fun newArray(size: Int): Array<GeoPoint?> = arrayOfNulls(size)
    }

    /**
     * Calculate distance to another GeoPoint in meters using Haversine formula
     */
    fun distanceToAsDouble(other: GeoPoint): Double {
        val R = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(other.latitude - this.latitude)
        val dLon = Math.toRadians(other.longitude - this.longitude)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(this.latitude)) * Math.cos(Math.toRadians(other.latitude)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }
}


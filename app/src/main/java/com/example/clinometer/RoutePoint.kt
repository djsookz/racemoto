package com.example.clinometer

import android.os.Parcel
import android.os.Parcelable
import org.osmdroid.util.GeoPoint

data class RoutePoint(
    val geoPoint: GeoPoint,
    val speed: Float,
    val angle: Float,
    val timestamp: Long,
    val absoluteTime: Long
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readParcelable(GeoPoint::class.java.classLoader)!!,
        parcel.readFloat(),
        parcel.readFloat(),
        parcel.readLong(),
        parcel.readLong()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelable(geoPoint, flags)
        parcel.writeFloat(speed)
        parcel.writeFloat(angle)
        parcel.writeLong(timestamp)
        parcel.writeLong(absoluteTime)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<RoutePoint> {
        override fun createFromParcel(parcel: Parcel): RoutePoint = RoutePoint(parcel)
        override fun newArray(size: Int): Array<RoutePoint?> = arrayOfNulls(size)
    }
}
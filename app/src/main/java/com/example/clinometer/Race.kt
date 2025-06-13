package com.example.clinometer

import android.os.Parcel
import android.os.Parcelable

// Модел за съхранение на пробег (сесия)
data class Race(
    val id: Long,
    val routePoints: List<RoutePoint>,
    val timestamp: Long,
    val duration: Long,
    val absoluteTimestamp: Long = timestamp,
    val maxLeftAngle: Float = 0f,
    val maxRightAngle: Float = 0f,
    val maxSpeed: Float = 0f,
    var name: String? = null
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.createTypedArrayList(RoutePoint.CREATOR) ?: emptyList(),
        parcel.readLong(),
        parcel.readLong(),
        parcel.readLong(),
        parcel.readFloat(),
        parcel.readFloat(),
        parcel.readFloat(),
        parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(id)
        parcel.writeTypedList(routePoints)
        parcel.writeLong(timestamp)
        parcel.writeLong(duration)
        parcel.writeLong(absoluteTimestamp)
        parcel.writeFloat(maxLeftAngle)
        parcel.writeFloat(maxRightAngle)
        parcel.writeFloat(maxSpeed)
        parcel.writeString(name)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Race> {
        override fun createFromParcel(parcel: Parcel): Race = Race(parcel)
        override fun newArray(size: Int): Array<Race?> = arrayOfNulls(size)
    }
}

package com.example.clinometer

import android.os.Parcel
import android.os.Parcelable
import com.example.clinometer.RoutePoint

data class Race(
    val id: Long,
    val routePoints: List<RoutePoint>,
    val timestamp: Long,
    val duration: Long,
    var name: String? = null
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.createTypedArrayList(RoutePoint.CREATOR)!!,
        parcel.readLong(),
        parcel.readLong(),
        parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(id)
        parcel.writeTypedList(routePoints)
        parcel.writeLong(timestamp)
        parcel.writeLong(duration)
        parcel.writeString(name)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Race> {
        override fun createFromParcel(parcel: Parcel): Race = Race(parcel)
        override fun newArray(size: Int): Array<Race?> = arrayOfNulls(size)
    }
}
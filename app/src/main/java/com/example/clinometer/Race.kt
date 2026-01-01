package com.example.clinometer

import android.os.Parcel
import android.os.Parcelable

// Модел за съхранение на пробег (сесия)
data class Race(
    val id: Long = System.currentTimeMillis(),
    val profileId: Long,
    val routePoints: List<RoutePoint>,
    val timestamp: Long,
    val duration: Long,
    val absoluteTimestamp: Long = timestamp,
    val maxLeftAngle: Float = 0f,
    val maxRightAngle: Float = 0f,
    val maxSpeed: Float = 0f,
    var name: String? = null,
    var trackName: String? = null,
    val time0to100: Long = -1L,
    val time0to200: Long = -1L,
    val time100to200: Long = -1L,
    val distance: Double = 0.0,
    var isFavorite: Boolean = false,
    var favoriteTimestamp: Long? = null,
    var description: String? = null,
    var photoPaths: List<String> = emptyList()
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readLong(),
        parcel.createTypedArrayList(RoutePoint.CREATOR) ?: emptyList(),
        parcel.readLong(),
        parcel.readLong(),
        parcel.readLong(),
        parcel.readFloat(),
        parcel.readFloat(),
        parcel.readFloat(),
        parcel.readString(),
        parcel.readString(),
        parcel.readLong(),
        parcel.readLong(),
        parcel.readLong(),
        distance = parcel.readDouble(),
        isFavorite = parcel.readByte() != 0.toByte(),
        favoriteTimestamp = if (parcel.readByte() != 0.toByte()) parcel.readLong() else null,
        description = parcel.readString(),
        photoPaths = parcel.createStringArrayList() ?: emptyList()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(id)
        parcel.writeLong(profileId)
        parcel.writeTypedList(routePoints)
        parcel.writeLong(timestamp)
        parcel.writeLong(duration)
        parcel.writeLong(absoluteTimestamp)
        parcel.writeFloat(maxLeftAngle)
        parcel.writeFloat(maxRightAngle)
        parcel.writeFloat(maxSpeed)
        parcel.writeString(name)
        parcel.writeString(trackName)
        parcel.writeLong(time0to100)
        parcel.writeLong(time0to200)
        parcel.writeLong(time100to200)
        parcel.writeDouble(distance)
        parcel.writeByte(if (isFavorite) 1 else 0)
        parcel.writeByte(if (favoriteTimestamp != null) 1 else 0)
        favoriteTimestamp?.let { parcel.writeLong(it) }
        parcel.writeString(description)
        parcel.writeStringList(photoPaths)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Race> {
        override fun createFromParcel(parcel: Parcel): Race = Race(parcel)
        override fun newArray(size: Int): Array<Race?> = arrayOfNulls(size)
    }
}
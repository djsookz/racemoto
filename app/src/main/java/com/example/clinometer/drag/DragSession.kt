package com.example.clinometer

import android.os.Parcel
import android.os.Parcelable
import android.util.Log

// Един опит в драг сесията
data class DragAttempt(
    var id: Long = System.currentTimeMillis(),
    var time0to100: Long = -1L,
    var time0to200: Long = -1L,
    var time100to200: Long = -1L,
    var time0to402: Long = -1L,
    var maxSpeed: Float = 0f,
    var timestamp: Long = System.currentTimeMillis(),
    var temperature: Float? = null,
    var altitude: Float? = null,
    var humidity: Int? = null,
    var windKph: Float? = null,
    var weatherIcon: Int? = null,
    val gSamples: List<Float> = emptyList(),
    val gpsAccelSamples: List<Float> = emptyList(),
    val startTime: Long = 0L,
    val timeStamps: List<Long> = emptyList(),
    val gpsTimeStamps: List<Long> = emptyList(),
    val duration: Long = 0L,
    val speedSamples: List<Float> = emptyList(),
    val speedTimeStamps: List<Long> = emptyList()
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readLong(),
        parcel.readLong(),
        parcel.readLong(),
        parcel.readLong(),
        parcel.readFloat(),
        parcel.readLong(),
        parcel.readValue(Float::class.java.classLoader) as? Float,
        parcel.readValue(Float::class.java.classLoader) as? Float,
        parcel.readValue(Int::class.java.classLoader) as? Int,
        parcel.readValue(Float::class.java.classLoader) as? Float,
        parcel.readValue(Int::class.java.classLoader) as? Int,
        parcel.createFloatArray()?.toList() ?: emptyList(),
        parcel.createFloatArray()?.toList() ?: emptyList(),
        parcel.readLong(),
        mutableListOf<Long>().apply { parcel.readList(this, Long::class.java.classLoader) },
        mutableListOf<Long>().apply { parcel.readList(this, Long::class.java.classLoader) },
        parcel.readLong(),
        parcel.createFloatArray()?.toList() ?: emptyList(),
        mutableListOf<Long>().apply { parcel.readList(this, Long::class.java.classLoader) }

    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(id)
        parcel.writeLong(time0to100)
        parcel.writeLong(time0to200)
        parcel.writeLong(time100to200)
        parcel.writeLong(time0to402)
        parcel.writeFloat(maxSpeed)
        parcel.writeLong(timestamp)
        parcel.writeValue(temperature)
        parcel.writeValue(altitude)
        parcel.writeValue(humidity)
        parcel.writeValue(windKph)
        parcel.writeValue(weatherIcon)
        parcel.writeFloatArray(gSamples.toFloatArray())
        parcel.writeFloatArray(gpsAccelSamples.toFloatArray())
        parcel.writeLong(startTime)
        parcel.writeList(timeStamps)
        parcel.writeList(gpsTimeStamps)
        parcel.writeLong(duration)
        parcel.writeFloatArray(speedSamples.toFloatArray())
        parcel.writeList(speedTimeStamps)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<DragAttempt> {
        override fun createFromParcel(parcel: Parcel): DragAttempt = DragAttempt(parcel)
        override fun newArray(size: Int): Array<DragAttempt?> = arrayOfNulls(size)
    }
}

// Драг сесия съдържаща множество опити
data class DragSession(
    var id: Long = System.currentTimeMillis(),
    var profileId: Long = -1L,
    var name: String? = null,
    var timestamp: Long = System.currentTimeMillis(),
    var attempts: MutableList<DragAttempt> = mutableListOf(),
    var best0to100: Long = -1L,
    var best0to200: Long = -1L,
    var best100to200: Long = -1L,
    var best0to402: Long = -1L,
    var temperature: Float? = null,
    var altitude: Float? = null,
    val measurementMode: String? = null,
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readLong(),
        parcel.readString(),
        parcel.readLong(),
        mutableListOf<DragAttempt>().apply {
            parcel.readTypedList(this, DragAttempt.CREATOR)
        },
        parcel.readLong(),
        parcel.readLong(),
        parcel.readLong(),
        parcel.readLong(),
        parcel.readValue(Float::class.java.classLoader) as? Float,
        parcel.readValue(Float::class.java.classLoader) as? Float
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(id)
        parcel.writeLong(profileId)
        parcel.writeString(name)
        parcel.writeLong(timestamp)
        parcel.writeTypedList(attempts)
        parcel.writeLong(best0to100)
        parcel.writeLong(best0to200)
        parcel.writeLong(best100to200)
        parcel.writeLong(best0to402)
        parcel.writeValue(temperature)
        parcel.writeValue(altitude)
    }

    override fun describeContents(): Int = 0

    fun updateBestTimes() {
        
        best0to100 = -1L
        best0to200 = -1L
        best100to200 = -1L
        best0to402 = -1L

        attempts.forEachIndexed { index, attempt ->
            
            if (attempt.time0to100 > 0 && (best0to100 == -1L || attempt.time0to100 < best0to100)) {
                best0to100 = attempt.time0to100
            }
            if (attempt.time0to200 > 0 && (best0to200 == -1L || attempt.time0to200 < best0to200)) {
                best0to200 = attempt.time0to200
            }
            if (attempt.time100to200 > 0 && (best100to200 == -1L || attempt.time100to200 < best100to200)) {
                best100to200 = attempt.time100to200
            }
            if (attempt.time0to402 > 0 && (best0to402 == -1L || attempt.time0to402 < best0to402)) {
                best0to402 = attempt.time0to402
            }
        }
        
    }

    companion object CREATOR : Parcelable.Creator<DragSession> {
        override fun createFromParcel(parcel: Parcel): DragSession = DragSession(parcel)
        override fun newArray(size: Int): Array<DragSession?> = arrayOfNulls(size)
    }
}
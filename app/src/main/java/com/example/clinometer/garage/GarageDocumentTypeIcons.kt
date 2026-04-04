package com.example.clinometer.garage

import com.example.clinometer.R
import java.util.Locale

object GarageDocumentTypeIcons {
    fun resolveIconRes(documentType: String?): Int? {
        return when (documentType
            ?.trim()
            ?.lowercase(Locale.ROOT)
        ) {
            "fine" -> R.drawable.fine
            "parking fee", "parking" -> R.drawable.parking_fee
            "insurance", "insrance" -> R.drawable.insurance
            "technical inspection", "technical isnpection" -> R.drawable.technical_isnpection
            "tax" -> R.drawable.tax
            "complex insurance", "complex incurance" -> R.drawable.complex_insurance
            "vehicle registration", "registration" -> R.drawable.registration
            else -> null
        }
    }
}
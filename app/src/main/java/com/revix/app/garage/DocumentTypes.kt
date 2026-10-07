package com.revix.app.garage

import android.content.Context
import com.revix.app.R
import java.util.Locale

object DocumentTypes {
    val defaultTypes = listOf(
        "Fine",
        "Parking Fee",
        "Insurance",
        "Technical Inspection",
        "Tax",
        "Complex Insurance",
        "Vehicle Registration"
    )

    fun localizedLabel(context: Context, documentType: String): String {
        return when (documentType.trim().lowercase(Locale.ROOT)) {
            "fine" -> context.getString(R.string.document_type_fine)
            "parking fee", "parking" -> context.getString(R.string.document_type_parking_fee)
            "insurance", "insrance" -> context.getString(R.string.document_type_insurance)
            "technical inspection", "technical isnpection" ->
                context.getString(R.string.document_type_technical_inspection)
            "tax" -> context.getString(R.string.document_type_tax)
            "complex insurance", "complex incurance" -> context.getString(R.string.document_type_complex_insurance)
            "vehicle registration", "registration" -> context.getString(R.string.document_type_vehicle_registration)
            else -> documentType.trim()
        }
    }
}

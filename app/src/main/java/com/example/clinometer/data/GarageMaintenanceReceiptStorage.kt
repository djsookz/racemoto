package com.example.clinometer.data

import android.content.Context
import android.net.Uri
import java.io.File

object GarageMaintenanceReceiptStorage {
    private const val RECEIPTS_DIR = "maintenance_receipts"

    fun saveTempReceipt(context: Context, uri: Uri, profileId: Long, entryId: Long): String? {
        return GarageReceiptImageStorage.saveTempReceipt(context, uri, RECEIPTS_DIR, profileId, entryId)
    }

    fun promoteTempReceipt(context: Context, relativePath: String, profileId: Long, entryId: Long): String? {
        return GarageReceiptImageStorage.promoteTempReceipt(context, relativePath, RECEIPTS_DIR, profileId, entryId)
    }

    fun deleteReceipt(context: Context, relativePath: String?) {
        GarageReceiptImageStorage.deleteReceipt(context, relativePath)
    }

    fun resolveReceiptFile(context: Context, relativePath: String?): File? {
        return GarageReceiptImageStorage.resolveReceiptFile(context, relativePath)
    }
}
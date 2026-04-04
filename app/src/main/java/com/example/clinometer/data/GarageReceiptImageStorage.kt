package com.example.clinometer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

object GarageReceiptImageStorage {
    private const val MAX_IMAGE_SIDE_PX = 1600
    private const val JPEG_QUALITY = 78

    fun saveTempReceipt(
        context: Context,
        uri: Uri,
        bucketDir: String,
        profileId: Long,
        entryId: Long
    ): String? {
        val decodedBitmap = decodeSampledBitmap(context, uri) ?: return null
        val orientedBitmap = correctImageOrientation(context, uri, decodedBitmap)
        val scaledBitmap = scaleBitmapIfNeeded(orientedBitmap)
        val relativePath = "$bucketDir/temp/profile_${profileId}_entry_${entryId}_${System.currentTimeMillis()}.jpg"

        return if (writeBitmap(context, scaledBitmap, relativePath)) {
            relativePath
        } else {
            null
        }
    }

    fun promoteTempReceipt(
        context: Context,
        relativePath: String,
        bucketDir: String,
        profileId: Long,
        entryId: Long
    ): String? {
        val normalizedPath = relativePath.trim()
        if (normalizedPath.isEmpty()) {
            return null
        }

        val finalRelativePath = "$bucketDir/profile_${profileId}_entry_${entryId}.jpg"
        if (normalizedPath == finalRelativePath) {
            return finalRelativePath
        }

        val sourceFile = resolveReceiptFile(context, normalizedPath) ?: return null
        if (!sourceFile.exists()) {
            return null
        }

        val targetFile = File(baseDir(context), finalRelativePath)
        targetFile.parentFile?.mkdirs()
        sourceFile.copyTo(targetFile, overwrite = true)
        sourceFile.delete()
        return finalRelativePath
    }

    fun deleteReceipt(context: Context, relativePath: String?) {
        val file = resolveReceiptFile(context, relativePath) ?: return
        if (file.exists()) {
            file.delete()
        }
    }

    fun resolveReceiptFile(context: Context, relativePath: String?): File? {
        val normalizedPath = relativePath?.trim().orEmpty()
        if (normalizedPath.isEmpty()) {
            return null
        }

        return File(baseDir(context), normalizedPath)
    }

    private fun writeBitmap(context: Context, bitmap: Bitmap, relativePath: String): Boolean {
        return runCatching {
            val file = File(baseDir(context), relativePath)
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
                outputStream.flush()
            }
        }.isSuccess
    }

    private fun decodeSampledBitmap(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, bounds)
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
        }

        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, options)
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        val largestSide = max(width, height)

        while (largestSide / sampleSize > MAX_IMAGE_SIDE_PX) {
            sampleSize *= 2
        }

        return sampleSize.coerceAtLeast(1)
    }

    private fun correctImageOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ExifInterface(inputStream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        if (orientation == ExifInterface.ORIENTATION_NORMAL) {
            return bitmap
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }

            else -> return bitmap
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap): Bitmap {
        val largestSide = max(bitmap.width, bitmap.height)
        if (largestSide <= MAX_IMAGE_SIDE_PX) {
            return bitmap
        }

        val scale = MAX_IMAGE_SIDE_PX.toFloat() / largestSide.toFloat()
        val targetWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun baseDir(context: Context): File {
        return context.getExternalFilesDir(null) ?: context.filesDir
    }
}
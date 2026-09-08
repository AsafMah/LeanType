// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import helium314.keyboard.latin.utils.Log

object ScreenshotHelper {
    private const val TAG = "ScreenshotHelper"
    private const val MAX_DIMENSION = 1920

    fun getLatestScreenshotUri(context: Context): Uri? {
        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.RELATIVE_PATH,
                MediaStore.Images.Media.DATE_ADDED
            )
        } else {
            @Suppress("DEPRECATION")
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_ADDED
            )
        }

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        return try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                } else {
                    @Suppress("DEPRECATION")
                    cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                }

                var count = 0
                while (cursor.moveToNext() && count < 20) {
                    count++
                    val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "" else ""
                    val path = if (pathCol >= 0) cursor.getString(pathCol) ?: "" else ""

                    if (name.contains("screenshot", ignoreCase = true) ||
                        path.contains("screenshot", ignoreCase = true) ||
                        path.contains("screenshots", ignoreCase = true) ||
                        name.startsWith("Screenshot", ignoreCase = true)
                    ) {
                        val id = cursor.getLong(idCol)
                        return Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying latest screenshot", e)
            null
        }
    }

    fun loadScaledBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            var sampleSize = 1
            while (options.outWidth / sampleSize > MAX_DIMENSION || options.outHeight / sampleSize > MAX_DIMENSION) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode bitmap from URI: $uri", e)
            null
        }
    }
}

package io.github.chatgpt01.petai.animation

import android.graphics.BitmapFactory
import java.io.File

fun validAnimationFrame(file: File): Boolean {
        val header = ByteArray(21)
        val count = file.inputStream().use { it.read(header) }
        if (count < 21 || String(header, 0, 4, Charsets.US_ASCII) != "RIFF" ||
            String(header, 8, 4, Charsets.US_ASCII) != "WEBP") return false
        if (String(header, 12, 4, Charsets.US_ASCII) == "VP8X" && (header[20].toInt() and 2) != 0) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth != 640 || bounds.outHeight != 360 || bounds.outMimeType != "image/webp") return false
        val bitmap = BitmapFactory.decodeFile(file.path) ?: return false
        bitmap.recycle()
        return true
    }

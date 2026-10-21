package it.oggisimangia.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object RecipePhotoStore {
    /** Bound transfer and decode; no full-size source file is saved. */
    fun load(url: String): Bitmap? {
        require(url.startsWith("https://"))
        var address = URL(url)
        var bytes: ByteArray? = null
        for (redirect in 0..3) {
            require(address.protocol == "https" && address.userInfo == null)
            val connection = address.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 6000; connection.readTimeout = 8000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; OggiSiMangia)")
                val status = connection.responseCode
                if (status in 300..399) {
                    address = URL(address, connection.getHeaderField("Location") ?: return null)
                    continue
                }
                if (status !in 200..299 || connection.contentLengthLong > 4_000_000L) return null
                bytes = connection.inputStream.use { input ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (out.size() + n > 4_000_000) return null
                        out.write(buffer, 0, n)
                    }
                    out.toByteArray()
                }
                break
            } finally { connection.disconnect() }
        }
        val data = bytes ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth.toLong() > 960L * sample || bounds.outHeight.toLong() > 960L * sample) sample *= 2
        return BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    fun save(context: Context, url: String): String {
        if (url.isBlank()) return ""
        val hash = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        val folder = File(context.filesDir, "photos").apply { mkdirs() }
        val file = File(folder, "web-$hash.jpg")
        if (file.isFile && file.length() > 0) return file.absolutePath
        val bitmap = load(url) ?: return ""
        val tmp = File(folder, "web-$hash.tmp")
        try {
            val compressed = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 68, compressed)
            tmp.writeBytes(compressed.toByteArray())
            check(tmp.renameTo(file))
            return file.absolutePath
        } finally { bitmap.recycle(); tmp.delete() }
    }
}

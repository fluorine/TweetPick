package dev.tweetpick

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.text.TextPaint
import android.text.TextUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Captures {
    // Must match the provider's android:authorities in the manifest.
    const val AUTHORITY = "dev.tweetpick.documents"
    const val ROOT_DOC = "root"
    private const val CREDIT = "github.com/fluorine/tweetpick"
    private const val ALBUM = "TweetPick"

    fun dir(context: Context): File =
        File(context.filesDir, "captures").apply { mkdirs() }

    fun uriOf(file: File) = DocumentsContract.buildDocumentUri(AUTHORITY, file.name)

    fun withFooter(shot: Bitmap, urls: List<String>, bg: Int, density: Float): Bitmap {
        val dark = Color.luminance(bg) < 0.5f
        val pad = 12 * density
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (dark) 0xFF71767B.toInt() else 0xFF536471.toInt()
            textSize = 13 * density
            typeface = Typeface.SANS_SERIF
        }
        val credit = TextPaint(paint).apply {
            textSize = 11 * density
            alpha = 170
            textAlign = Paint.Align.RIGHT
        }
        val lines = urls.take(4)
        val lineH = paint.textSize * 1.4f
        val footerH = (pad * 2 + lineH * lines.size + credit.textSize * 1.4f).toInt()
        val out = Bitmap.createBitmap(shot.width, shot.height + footerH, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(bg)
        c.drawBitmap(shot, 0f, 0f, null)
        val divider = Paint().apply {
            color = if (dark) 0xFF2F3336.toInt() else 0xFFEFF3F4.toInt()
        }
        c.drawRect(0f, shot.height.toFloat(), out.width.toFloat(), shot.height + density, divider)
        var y = shot.height + pad - paint.ascent()
        val avail = out.width - 2 * pad
        for (url in lines) {
            val fit = TextPaint(paint)
            val w = fit.measureText(url)
            if (w > avail) fit.textSize = maxOf(fit.textSize * avail / w, 7 * density)
            val text = TextUtils.ellipsize(url, fit, avail, TextUtils.TruncateAt.END)
            c.drawText(text.toString(), pad, y, fit)
            y += lineH
        }
        c.drawText(CREDIT, out.width - pad, y, credit)
        return out
    }

    fun save(context: Context, image: Bitmap): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir(context), "tweet_$stamp.png")
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        context.contentResolver.notifyChange(
            DocumentsContract.buildChildDocumentsUri(AUTHORITY, ROOT_DOC), null
        )
        return file
    }

    /**
     * Copies [file] to Pictures/TweetPick so galleries and pickers list it.
     * Returns its media URI, or null when it could not be written.
     */
    fun publish(context: Context, file: File): Uri? {
        val mime = if (file.extension == "jpg") "image/jpeg" else "image/png"
        return try {
            if (Build.VERSION.SDK_INT >= 29) publishMediaStore(context, file, mime)
            else publishLegacy(context, file, mime)
        } catch (e: Exception) {
            null
        }
    }

    private fun publishMediaStore(context: Context, file: File, mime: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    // Needs WRITE_EXTERNAL_STORAGE, which the activity asks for below API 29.
    @Suppress("DEPRECATION")
    private fun publishLegacy(context: Context, file: File, mime: String): Uri? {
        val granted = context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM)
        dir.mkdirs()
        val dest = File(dir, file.name)
        file.copyTo(dest, overwrite = true)
        MediaScannerConnection.scanFile(context, arrayOf(dest.path), arrayOf(mime), null)
        return Uri.fromFile(dest)
    }

    fun unpublish(context: Context, uri: Uri) {
        try {
            if (uri.scheme == "file") File(uri.path!!).delete()
            else context.contentResolver.delete(uri, null, null)
        } catch (_: Exception) {
        }
    }
}

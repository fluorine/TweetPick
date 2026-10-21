package dev.tweetpick

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.provider.DocumentsContract
import android.text.TextPaint
import android.text.TextUtils
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Captures {
    // Must match the provider's android:authorities in the manifest.
    const val AUTHORITY = "dev.tweetpick.documents"
    const val ROOT_DOC = "root"
    private const val CREDIT = "github.com/fluorine/tweetpick"

    // Most 4chan boards reject files above 4 MiB.
    private const val MAX_BYTES = 4 * 1024 * 1024 - 64 * 1024

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
        for (url in lines) {
            val text = TextUtils.ellipsize(url, paint, out.width - 2 * pad, TextUtils.TruncateAt.END)
            c.drawText(text.toString(), pad, y, paint)
            y += lineH
        }
        c.drawText(CREDIT, out.width - pad, y, credit)
        return out
    }

    /** Writes PNG, or JPEG at falling quality when the PNG is above [MAX_BYTES]. */
    fun save(context: Context, image: Bitmap): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val buf = ByteArrayOutputStream()
        image.compress(Bitmap.CompressFormat.PNG, 100, buf)
        var ext = "png"
        var quality = 95
        while (buf.size() > MAX_BYTES && quality >= 40) {
            buf.reset()
            image.compress(Bitmap.CompressFormat.JPEG, quality, buf)
            ext = "jpg"
            quality -= 10
        }
        val file = File(dir(context), "tweet_$stamp.$ext")
        file.writeBytes(buf.toByteArray())
        context.contentResolver.notifyChange(
            DocumentsContract.buildChildDocumentsUri(AUTHORITY, ROOT_DOC), null
        )
        return file
    }
}

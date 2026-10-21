package dev.tweetpick

import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Point
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException

class CapturesProvider : DocumentsProvider() {

    private val rootCols = arrayOf(
        Root.COLUMN_ROOT_ID, Root.COLUMN_FLAGS, Root.COLUMN_TITLE,
        Root.COLUMN_DOCUMENT_ID, Root.COLUMN_MIME_TYPES, Root.COLUMN_ICON
    )
    private val docCols = arrayOf(
        Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
        Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS
    )

    override fun onCreate() = true

    private fun dir() = Captures.dir(context!!)

    private fun fileOf(docId: String): File {
        val f = File(dir(), docId)
        if (f.parentFile != dir() || !f.isFile) throw FileNotFoundException(docId)
        return f
    }

    private fun mimeOf(f: File) = if (f.extension == "jpg") "image/jpeg" else "image/png"

    override fun queryRoots(projection: Array<String>?): Cursor =
        MatrixCursor(projection ?: rootCols).apply {
            newRow()
                .add(Root.COLUMN_ROOT_ID, "tweetpick")
                .add(Root.COLUMN_FLAGS, Root.FLAG_LOCAL_ONLY or Root.FLAG_SUPPORTS_RECENTS)
                .add(Root.COLUMN_TITLE, "TweetPick")
                .add(Root.COLUMN_DOCUMENT_ID, Captures.ROOT_DOC)
                .add(Root.COLUMN_MIME_TYPES, "image/*")
                .add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
        }

    override fun queryDocument(documentId: String, projection: Array<String>?): Cursor =
        MatrixCursor(projection ?: docCols).apply { addDoc(this, documentId) }

    override fun queryChildDocuments(
        parentDocumentId: String, projection: Array<String>?, sortOrder: String?
    ): Cursor = MatrixCursor(projection ?: docCols).apply {
        files().forEach { addDoc(this, it.name) }
        setNotificationUri(
            context!!.contentResolver,
            DocumentsContract.buildChildDocumentsUri(Captures.AUTHORITY, parentDocumentId)
        )
    }

    override fun queryRecentDocuments(rootId: String, projection: Array<String>?): Cursor =
        MatrixCursor(projection ?: docCols).apply {
            files().take(20).forEach { addDoc(this, it.name) }
        }

    private fun files() =
        (dir().listFiles() ?: emptyArray()).filter { it.isFile }.sortedByDescending { it.lastModified() }

    private fun addDoc(c: MatrixCursor, docId: String) {
        val row = c.newRow().add(Document.COLUMN_DOCUMENT_ID, docId)
        if (docId == Captures.ROOT_DOC) {
            row.add(Document.COLUMN_DISPLAY_NAME, "TweetPick")
                .add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
                .add(Document.COLUMN_FLAGS, 0)
            return
        }
        val f = fileOf(docId)
        row.add(Document.COLUMN_DISPLAY_NAME, f.name)
            .add(Document.COLUMN_MIME_TYPE, mimeOf(f))
            .add(Document.COLUMN_SIZE, f.length())
            .add(Document.COLUMN_LAST_MODIFIED, f.lastModified())
            .add(Document.COLUMN_FLAGS, Document.FLAG_SUPPORTS_THUMBNAIL or Document.FLAG_SUPPORTS_DELETE)
    }

    override fun openDocument(
        documentId: String, mode: String, signal: CancellationSignal?
    ): ParcelFileDescriptor =
        ParcelFileDescriptor.open(fileOf(documentId), ParcelFileDescriptor.MODE_READ_ONLY)

    override fun openDocumentThumbnail(
        documentId: String, sizeHint: Point?, signal: CancellationSignal?
    ): AssetFileDescriptor {
        val pfd = ParcelFileDescriptor.open(fileOf(documentId), ParcelFileDescriptor.MODE_READ_ONLY)
        return AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    override fun deleteDocument(documentId: String) {
        fileOf(documentId).delete()
    }
}

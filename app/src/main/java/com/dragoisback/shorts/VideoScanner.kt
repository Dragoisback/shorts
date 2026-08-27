package com.dragoisback.shorts

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import java.util.ArrayDeque

/** A single playable video discovered inside the chosen library folder. */
data class VideoItem(val uri: Uri, val name: String)

object VideoScanner {

    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "m4v", "mkv", "webm", "3gp", "mov", "avi", "ts", "mpeg", "mpg", "flv"
    )

    /**
     * Recursively walks the document tree rooted at [treeUri] and returns every video file it
     * finds. Runs entirely on the caller's thread, so call it off the main thread.
     */
    fun scan(context: Context, treeUri: Uri): List<VideoItem> {
        val resolver = context.contentResolver
        val results = ArrayList<VideoItem>()
        val queue = ArrayDeque<String>()

        val rootDocId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (e: Exception) {
            return emptyList()
        }
        queue.add(rootDocId)

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        var guard = 0
        while (queue.isNotEmpty() && guard < 5000) {
            guard++
            val parentId = queue.poll() ?: break
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            var cursor: Cursor? = null
            try {
                cursor = resolver.query(childrenUri, projection, null, null, null)
                while (cursor != null && cursor.moveToNext()) {
                    val docId = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: ""
                    val mime = cursor.getString(2) ?: ""
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        queue.add(docId)
                    } else if (isVideo(mime, name)) {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        results.add(VideoItem(docUri, name))
                    }
                }
            } catch (e: Exception) {
                // Unreadable folder – skip it and keep going.
            } finally {
                cursor?.close()
            }
        }

        results.sortBy { it.name.lowercase() }
        return results
    }

    private fun isVideo(mime: String, name: String): Boolean {
        if (mime.startsWith("video/")) return true
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext.isNotEmpty() && ext in VIDEO_EXTENSIONS
    }
}

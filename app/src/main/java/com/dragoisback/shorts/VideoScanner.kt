package com.dragoisback.shorts

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import java.util.ArrayDeque

/** A single playable video discovered inside the chosen library folder. */
data class VideoItem(val uri: Uri, val name: String)

/** Outcome of scanning a user-picked folder. */
sealed class ScanResult {
    /** The folder was readable; [items] may still be empty. */
    data class Success(val items: List<VideoItem>) : ScanResult()

    /** The folder could not be read at all (revoked permission, unmounted SD card/USB, …). */
    data object Inaccessible : ScanResult()
}

object VideoScanner {

    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "m4v", "mkv", "webm", "3gp", "mov", "avi", "ts", "mpeg", "mpg", "flv"
    )

    /** Safety valve so a pathological folder tree can never pin the scanner forever. */
    private const val MAX_DIRECTORIES = 5_000

    /**
     * Recursively walks the document tree rooted at [treeUri] and returns every video file it
     * finds. Runs entirely on the caller's thread, so call it off the main thread.
     */
    fun scan(context: Context, treeUri: Uri): ScanResult {
        val resolver = context.contentResolver
        val results = ArrayList<VideoItem>()
        val queue = ArrayDeque<String>()

        val rootDocId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (e: Exception) {
            return ScanResult.Inaccessible
        }
        queue.add(rootDocId)

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        var visited = 0
        var rootReadable = false
        while (queue.isNotEmpty() && visited < MAX_DIRECTORIES) {
            // Cooperate with Activity teardown (executor shutdownNow interrupts us).
            if (Thread.currentThread().isInterrupted) break
            val parentId = queue.poll() ?: break
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            var readable = false
            var cursor: Cursor? = null
            try {
                cursor = resolver.query(childrenUri, projection, null, null, null)
                if (cursor != null) {
                    readable = true
                    while (cursor.moveToNext()) {
                        val docId = cursor.getString(0) ?: continue
                        val name = cursor.getString(1) ?: continue
                        if (name.startsWith(".")) continue // skip dot-files/folders
                        val mime = cursor.getString(2) ?: ""
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            queue.add(docId)
                        } else if (isVideo(mime, name)) {
                            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            results.add(VideoItem(docUri, name))
                        }
                    }
                }
            } catch (e: Exception) {
                // Unreadable folder – skip it and keep going.
            } finally {
                cursor?.close()
            }
            if (visited == 0) rootReadable = readable
            visited++
        }

        if (!rootReadable) return ScanResult.Inaccessible
        results.sortBy { it.name.lowercase() }
        return ScanResult.Success(results)
    }

    private fun isVideo(mime: String, name: String): Boolean {
        if (mime.startsWith("video/")) return true
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext.isNotEmpty() && ext in VIDEO_EXTENSIONS
    }
}

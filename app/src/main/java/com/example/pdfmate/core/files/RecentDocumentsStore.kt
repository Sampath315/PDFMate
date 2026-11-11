package com.example.pdfmate.core.files

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

data class RecentPdf(
    val uri: Uri,
    val name: String,
    val openedAt: Long = 0L,
    val pageCount: Int = 0,
    val sizeBytes: Long = 0L
)

class RecentDocumentsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "recent_documents",
        Context.MODE_PRIVATE
    )

    fun load(): List<RecentPdf> {
        val saved = preferences.getString(KEY_RECENTS, null) ?: return emptyList()
        return runCatching {
            val entries = JSONArray(saved)
            List(entries.length()) { index ->
                val entry = entries.getJSONObject(index)
                RecentPdf(
                    uri = Uri.parse(entry.getString("uri")),
                    name = entry.optString("name", "PDF document"),
                    openedAt = entry.optLong("openedAt", 0L),
                    pageCount = entry.optInt("pages", 0),
                    sizeBytes = entry.optLong("size", 0L)
                )
            }
        }.getOrDefault(emptyList())
    }

    fun remember(recent: RecentPdf): List<RecentPdf> =
        save(listOf(recent) + load().filterNot { it.uri == recent.uri })

    fun remove(uri: Uri): List<RecentPdf> = save(load().filterNot { it.uri == uri })

    fun clear(): List<RecentPdf> = save(emptyList())

    private fun save(items: List<RecentPdf>): List<RecentPdf> {
        val updated = items.take(MAX_RECENTS)
        val entries = JSONArray().apply {
            updated.forEach { recent ->
                put(
                    JSONObject()
                        .put("uri", recent.uri.toString())
                        .put("name", recent.name)
                        .put("openedAt", recent.openedAt)
                        .put("pages", recent.pageCount)
                        .put("size", recent.sizeBytes)
                )
            }
        }
        preferences.edit { putString(KEY_RECENTS, entries.toString()) }
        return updated
    }

    private companion object {
        const val KEY_RECENTS = "items"
        const val MAX_RECENTS = 12
    }
}

package com.nullvariant.official

import android.content.res.AssetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

class LibraryCatalogClient(private val assets: AssetManager) {

    suspend fun load(): Result<JSONObject> = withContext(Dispatchers.IO) {
        runCatching {
            val json = assets.open(CATALOG_ASSET)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
                .trim()
            if (json.isBlank()) throw IOException("Empty catalog")
            JSONObject(json)
        }
    }

    private companion object {
        const val CATALOG_ASSET = "catalog.json"
    }
}

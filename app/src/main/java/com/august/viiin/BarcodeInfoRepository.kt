package com.august.viiin

import com.august.viiin.model.LookupItem
import com.august.viiin.model.LookupResponse
import com.august.viiin.util.Keywords
import com.google.gson.Gson
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import kotlin.coroutines.resume

fun LookupItem.isWine(): Boolean {
    val itemTokens = listOfNotNull(category, title, description, brand, model)
        .joinToString(" ")
        .lowercase()
        .split(Regex("\\W+"))
        .toSet()

    return Keywords.dataTokens.any { it in itemTokens }
}

class BarcodeInfoRepository {
    suspend fun fetchBarcodeInfo(code: String): LookupItem? = suspendCancellableCoroutine { cont ->
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://api.upcitemdb.com/prod/trial/lookup?upc=$code")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resume(null)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: run {
                        cont.resume(null)
                        return
                    }

                    val lookupResponse = Gson().fromJson(body, LookupResponse::class.java)
                    val firstItem = lookupResponse.items.firstOrNull()
                    cont.resume(firstItem)
                } catch (e: Exception) {
                    cont.resume(null)
                }
            }
        })

        cont.invokeOnCancellation {
            client.dispatcher.cancelAll()
        }
    }
}
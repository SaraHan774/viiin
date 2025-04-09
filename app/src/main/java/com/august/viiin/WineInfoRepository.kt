package com.august.viiin

import com.google.gson.Gson
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import kotlin.coroutines.resume

data class LookupResponse(
    val items: List<LookupItem>
)

data class LookupItem(
    val title: String,
    val brand: String?
)

class WineInfoRepository {
    suspend fun fetchWineInfo(code: String): String = suspendCancellableCoroutine { cont ->
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://api.upcitemdb.com/prod/trial/lookup?upc=$code")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resume("API 요청 실패: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: run {
                        cont.resume("No response")
                        return
                    }

                    val lookupResponse = Gson().fromJson(body, LookupResponse::class.java)
                    val firstItem = lookupResponse.items.firstOrNull()

                    if (firstItem != null) {
                        val brand = firstItem.brand ?: ""
                        cont.resume("${firstItem.title} ($brand)")
                    } else {
                        cont.resume("상품 정보를 찾을 수 없습니다.")
                    }

                } catch (e: Exception) {
                    cont.resume("응답 처리 실패: ${e.message}")
                }
            }
        })

        cont.invokeOnCancellation {
            client.dispatcher.cancelAll()
        }
    }
}
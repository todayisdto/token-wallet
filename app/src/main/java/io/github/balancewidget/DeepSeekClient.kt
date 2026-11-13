package io.github.balancewidget

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class DeepSeekClient(
    private val client: OkHttpClient = defaultHttpClient()
) {

    fun fetch(apiKey: String): ProviderBalance {
        val request = Request.Builder()
            .url("https://api.deepseek.com/user/balance")
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ApiException(deepSeekError(response.code, body))
            }

            try {
                val json = JSONObject(body)
                val infos = json.optJSONArray("balance_infos")
                    ?: throw ApiException("接口未返回余额信息")
                if (infos.length() == 0) {
                    throw ApiException("接口未返回余额信息")
                }

                val parsed = (0 until infos.length()).map { infos.getJSONObject(it) }
                val info = parsed.firstOrNull {
                    it.optString("currency").equals("CNY", ignoreCase = true)
                } ?: parsed.first()

                val currency = info.optString("currency", "CNY")
                val total = info.optString("total_balance").ifBlank { "0.00" }
                val granted = info.optString("granted_balance")
                val toppedUp = info.optString("topped_up_balance")
                val symbol = currencySymbol(currency)

                val details = buildList {
                    if (granted.isNotBlank() && granted != "0.00") {
                        add("赠金 $symbol$granted")
                    }
                    if (toppedUp.isNotBlank() && toppedUp != "0.00") {
                        add("充值 $symbol$toppedUp")
                    }
                }.joinToString(" · ").ifBlank { "可用余额" }

                return ProviderBalance(
                    provider = Provider.DEEPSEEK,
                    amount = total,
                    currency = currency,
                    detail = details,
                    updatedAt = System.currentTimeMillis()
                )
            } catch (e: ApiException) {
                throw e
            } catch (e: Exception) {
                throw ApiException("DeepSeek 返回格式异常", e)
            }
        }
    }

    private fun deepSeekError(code: Int, body: String): String {
        val message = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
        }.getOrNull()

        return when (code) {
            401 -> "API Key 无效"
            402 -> "账户余额不足"
            403 -> "该 API Key 无权访问"
            429 -> "请求过于频繁，请稍后再试"
            in 500..599 -> "DeepSeek 服务暂时不可用"
            else -> message?.takeIf { it.isNotBlank() } ?: "DeepSeek 请求失败（HTTP $code）"
        }
    }
}

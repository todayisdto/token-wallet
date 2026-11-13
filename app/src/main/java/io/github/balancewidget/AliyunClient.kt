package io.github.balancewidget

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.UUID

class AliyunClient(
    private val client: OkHttpClient = defaultHttpClient(),
    private val endpoint: String = "https://business.aliyuncs.com/"
) {

    fun fetch(accessKeyId: String, accessKeySecret: String): ProviderBalance {
        val params = linkedMapOf(
            "Action" to "QueryAccountBalance",
            "Version" to "2017-12-14",
            "Format" to "JSON",
            "AccessKeyId" to accessKeyId,
            "SignatureMethod" to "HMAC-SHA1",
            "SignatureNonce" to UUID.randomUUID().toString(),
            "SignatureVersion" to "1.0",
            "Timestamp" to AliyunSigner.timestamp()
        )
        val query = AliyunSigner.buildQuery(params, accessKeySecret)

        val request = Request.Builder()
            .url(endpoint + "?" + query)
            .header("Accept", "application/json")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(body) }.getOrNull()

            if (!response.isSuccessful || json == null) {
                throw ApiException(aliyunError(response.code, json, body))
            }
            if (!json.optBoolean("Success", false)) {
                throw ApiException(aliyunError(response.code, json, body))
            }

            val data = json.optJSONObject("Data")
                ?: throw ApiException("阿里云未返回余额数据")
            val amount = data.optString("AvailableAmount").ifBlank { "0.00" }
            val cash = data.optString("AvailableCashAmount")
            val credit = data.optString("CreditAmount")
            val currency = data.optString("Currency", "CNY")
            val symbol = currencySymbol(currency)

            val details = buildList {
                if (cash.isNotBlank()) {
                    add("现金 $symbol$cash")
                }
                if (credit.isNotBlank() && credit != "0.00") {
                    add("信用 $symbol$credit")
                }
            }.joinToString(" · ").ifBlank { "可用余额" }

            return ProviderBalance(
                provider = Provider.ALIYUN,
                amount = amount,
                currency = currency,
                detail = details,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    private fun aliyunError(code: Int, json: JSONObject?, raw: String): String {
        val errorCode = json?.optString("Code").orEmpty()
        val message = json?.optString("Message").orEmpty()

        return when {
            errorCode == "NoPermission" ||
                errorCode == "NotAuthorized" ||
                errorCode == "Forbidden.RAM" ->
                "RAM 权限不足，请授予 AliyunBSSReadOnlyAccess"

            errorCode.startsWith("InvalidAccessKeyId") ->
                "AccessKey ID 不存在或已禁用"

            errorCode == "SignatureDoesNotMatch" ->
                "AccessKey Secret 不正确"

            errorCode == "InvalidTimeStamp.Expired" ->
                "手机时间不准，请校准后重试"

            errorCode == "NotApplicable" ->
                "该账号不支持余额接口，请确认阿里云站点"

            message.isNotBlank() -> message
            code == 403 -> "阿里云拒绝访问，请检查 RAM 权限"
            else -> "阿里云请求失败（HTTP $code）"
        }
    }
}

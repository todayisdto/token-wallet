package io.github.balancewidget

import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Aliyun RPC API signature (version 1.0).
 *
 * The canonical query string is sorted by parameter name, percent-encoded with
 * RFC 3986 rules, then signed with HMAC-SHA1 using "<AccessKeySecret>&" as key.
 */
object AliyunSigner {

    private val timestampFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .withZone(ZoneOffset.UTC)

    fun timestamp(): String = timestampFormat.format(Instant.now())

    fun sign(
        params: Map<String, String>,
        accessKeySecret: String,
        method: String = "GET",
        path: String = "/"
    ): String {
        val canonicalQuery = canonicalQuery(params)
        val stringToSign = "$method&${percentEncode(path)}&${percentEncode(canonicalQuery)}"

        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec((accessKeySecret + "&").toByteArray(Charsets.UTF_8), "HmacSHA1"))
        val digest = mac.doFinal(stringToSign.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(digest)
    }

    fun buildQuery(params: Map<String, String>, accessKeySecret: String): String {
        val signed = params.toMutableMap()
        signed["Signature"] = sign(params, accessKeySecret)
        return canonicalQuery(signed)
    }

    fun percentEncode(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")
    }

    private fun canonicalQuery(params: Map<String, String>): String {
        return params.toSortedMap().entries.joinToString("&") { (key, value) ->
            "${percentEncode(key)}=${percentEncode(value)}"
        }
    }
}

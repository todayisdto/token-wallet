package io.github.balancewidget

enum class Provider {
    DEEPSEEK,
    ALIYUN
}

data class ProviderBalance(
    val provider: Provider,
    val amount: String? = null,
    val currency: String = "CNY",
    val detail: String? = null,
    val error: String? = null,
    val updatedAt: Long = 0L
)

data class BalanceSnapshot(
    val deepSeek: ProviderBalance = ProviderBalance(Provider.DEEPSEEK),
    val aliyun: ProviderBalance = ProviderBalance(Provider.ALIYUN),
    val lastUpdated: Long = 0L
)

class ApiException(
    val userMessage: String,
    cause: Throwable? = null
) : Exception(userMessage, cause)

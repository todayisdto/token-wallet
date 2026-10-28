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
    /** Last fetch attempt, used to decide whether a new refresh is due. */
    val lastUpdated: Long = 0L,
    /** Last attempt where at least one provider actually returned data. */
    val lastSuccessAt: Long = 0L,
    /** Set when the user taps refresh, so the widget can show a pending state. */
    val refreshRequestedAt: Long = 0L
)

class ApiException(
    val userMessage: String,
    cause: Throwable? = null
) : Exception(userMessage, cause)

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
    /** When the last fetch was attempted, whether it succeeded or not. */
    val updatedAt: Long = 0L,
    /**
     * When this provider last actually returned a balance. Kept apart from
     * [updatedAt] so a failed attempt cannot make old numbers look fresh.
     */
    val lastSuccessAt: Long = 0L
)

data class BalanceSnapshot(
    val deepSeek: ProviderBalance = ProviderBalance(Provider.DEEPSEEK),
    val aliyun: ProviderBalance = ProviderBalance(Provider.ALIYUN),
    /** Last fetch attempt, used to decide whether a new refresh is due. */
    val lastUpdated: Long = 0L,
    /**
     * Most recent success across providers. Derived from the per-provider
     * [ProviderBalance.lastSuccessAt] values, never stamped blindly with "now":
     * saving one provider must not make the other one look freshly updated.
     */
    val lastSuccessAt: Long = 0L,
    /** Set when the user taps refresh, so the widget can show a pending state. */
    val refreshRequestedAt: Long = 0L
) {

    /**
     * Replaces a single provider's balance after an in-app save and keeps the
     * derived timestamps consistent.
     */
    fun withProvider(balance: ProviderBalance, now: Long): BalanceSnapshot {
        val updatedDeepSeek = if (balance.provider == Provider.DEEPSEEK) balance else deepSeek
        val updatedAliyun = if (balance.provider == Provider.ALIYUN) balance else aliyun
        return copy(
            deepSeek = updatedDeepSeek,
            aliyun = updatedAliyun,
            lastUpdated = now,
            lastSuccessAt = maxOf(updatedDeepSeek.lastSuccessAt, updatedAliyun.lastSuccessAt),
            refreshRequestedAt = 0L
        )
    }
}

class ApiException(
    val userMessage: String,
    cause: Throwable? = null
) : Exception(userMessage, cause)

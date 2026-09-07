package app.vpncheck.data.repo

import app.vpncheck.core.network.NetworkType

sealed class RunState {
    data object Idle : RunState()

    data class Fetching(val done: Int, val total: Int) : RunState()

    data class Running(
        val network: NetworkType,
        val done: Int,
        val total: Int,
        val ok: Int,
        val current: List<String>,
    ) : RunState()

    data class Finished(
        val network: NetworkType,
        val total: Int,
        val done: Int,
        val ok: Int,
        val cancelled: Boolean,
        val finishedAt: Long,
        val note: String?,
    ) : RunState()

    data class Failed(val message: String) : RunState()

    val isActive: Boolean get() = this is Fetching || this is Running
}

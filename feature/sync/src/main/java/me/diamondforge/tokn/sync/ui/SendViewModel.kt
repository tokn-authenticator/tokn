package me.diamondforge.tokn.sync.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.usecase.GetAccountsUseCase
import me.diamondforge.tokn.security.EncryptionManager
import me.diamondforge.tokn.sync.AppInfo
import me.diamondforge.tokn.sync.SyncPayload
import me.diamondforge.tokn.sync.SyncProtocol
import me.diamondforge.tokn.sync.crypto.Handshake
import me.diamondforge.tokn.sync.lan.DeviceName
import me.diamondforge.tokn.sync.lan.LanSyncServer
import me.diamondforge.tokn.sync.qr.QrChunkCodec
import me.diamondforge.tokn.sync.wfd.WfdManager
import me.diamondforge.tokn.sync.wfd.WfdSyncTransport
import org.json.JSONObject
import javax.inject.Inject

data class SendUiState(
    val pairingCode: String = "",
    val status: Status = Status.Idle,
    val qrFrames: List<String> = emptyList(),
    val currentFrame: Int = 0,
    val errorMessage: String? = null,
    val versionMismatch: VersionMismatchInfo? = null,
    val deviceName: String = "",
) {
    enum class Status { Idle, Waiting, Transferring, Done }
}

@HiltViewModel
class SendViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getAccountsUseCase: GetAccountsUseCase,
    private val encryptionManager: EncryptionManager,
    private val appInfo: AppInfo,
) : ViewModel() {

    val localVersionLabel: String get() = "${appInfo.versionName} (build ${appInfo.versionCode})"

    private val _uiState = MutableStateFlow(SendUiState())
    val uiState: StateFlow<SendUiState> = _uiState.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<Long>?>(null)

    val accounts: StateFlow<List<OtpAccount>> = getAccountsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val wfdManager = WfdManager(context)
    val wfdSupported: Boolean get() = wfdManager.isSupported

    private var lanJob: Job? = null
    private var wfdJob: Job? = null

    fun setSelection(ids: Set<Long>) {
        _selectedIds.value = ids
    }

    /**
     * Returns the accounts to send. When the user has explicitly picked a
     * subset via [setSelection] we honor it; otherwise we default to all
     * accounts so first-launch / direct-deep-link flows still work.
     */
    private suspend fun selectedAccounts(): List<OtpAccount> {
        val all = getAccountsUseCase().first()
        val ids = _selectedIds.value ?: return all
        return all.filter { it.id in ids }
    }

    fun startLanSend() {
        if (lanJob?.isActive == true) return
        val code = Handshake.newPairingCode()
        val deviceName = DeviceName.build()
        _uiState.update {
            SendUiState(
                pairingCode = code,
                status = SendUiState.Status.Waiting,
                deviceName = deviceName,
            )
        }
        lanJob = viewModelScope.launch {
            runCatching {
                val payload = SyncPayload.serialize(selectedAccounts())
                    .toByteArray(Charsets.UTF_8)
                val server = LanSyncServer(context)
                val result = server.sendPayload(
                    serviceName = deviceName,
                    code = code,
                    payload = payload,
                    localApp = appInfo.versionName,
                    localBuild = appInfo.versionCode,
                    onReady = { /* no-op */ },
                )
                when (result) {
                    LanSyncServer.SendResult.Ok ->
                        _uiState.update { it.copy(status = SendUiState.Status.Done) }
                    LanSyncServer.SendResult.BadCode ->
                        _uiState.update {
                            it.copy(
                                status = SendUiState.Status.Idle,
                                errorMessage = context.getString(me.diamondforge.tokn.sync.R.string.sync_bad_code),
                            )
                        }
                    is LanSyncServer.SendResult.VersionMismatch ->
                        _uiState.update {
                            it.copy(
                                status = SendUiState.Status.Idle,
                                versionMismatch = versionMismatchInfo(result.peerApp, result.peerBuild),
                            )
                        }
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        status = SendUiState.Status.Idle,
                        errorMessage = e.message ?: context.getString(me.diamondforge.tokn.sync.R.string.sync_error_transfer),
                    )
                }
            }
        }
    }

    fun cancelLan() {
        lanJob?.cancel()
        lanJob = null
        _uiState.update { SendUiState() }
    }

    fun startWfdSend() {
        if (wfdJob?.isActive == true) return
        if (!wfdManager.isSupported) {
            _uiState.update { it.copy(errorMessage = context.getString(me.diamondforge.tokn.sync.R.string.sync_wfd_unsupported)) }
            return
        }
        val code = Handshake.newPairingCode()
        val deviceName = DeviceName.build()
        _uiState.update {
            SendUiState(
                pairingCode = code,
                status = SendUiState.Status.Waiting,
                deviceName = deviceName,
            )
        }
        wfdJob = viewModelScope.launch {
            wfdManager.register()
            runCatching {
                val groupOk = wfdManager.createGroup()
                if (!groupOk) error(context.getString(me.diamondforge.tokn.sync.R.string.sync_error_wfd_start_group))
                val payload = SyncPayload.serialize(selectedAccounts())
                    .toByteArray(Charsets.UTF_8)
                WfdSyncTransport.sendOverWfd(
                    code = code,
                    payload = payload,
                    localApp = appInfo.versionName,
                    localBuild = appInfo.versionCode,
                )
            }.onSuccess { result ->
                when (result) {
                    WfdSyncTransport.SendResult.Ok ->
                        _uiState.update { it.copy(status = SendUiState.Status.Done) }
                    WfdSyncTransport.SendResult.BadCode ->
                        _uiState.update {
                            it.copy(
                                status = SendUiState.Status.Idle,
                                errorMessage = context.getString(me.diamondforge.tokn.sync.R.string.sync_bad_code),
                            )
                        }
                    is WfdSyncTransport.SendResult.VersionMismatch ->
                        _uiState.update {
                            it.copy(
                                status = SendUiState.Status.Idle,
                                versionMismatch = versionMismatchInfo(result.peerApp, result.peerBuild),
                            )
                        }
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        status = SendUiState.Status.Idle,
                        errorMessage = e.message ?: context.getString(me.diamondforge.tokn.sync.R.string.sync_error_wfd_transfer),
                    )
                }
            }
            runCatching { wfdManager.removeGroup() }
            wfdManager.unregister()
        }
    }

    fun cancelWfd() {
        wfdJob?.cancel()
        wfdJob = null
        viewModelScope.launch {
            runCatching { wfdManager.removeGroup() }
            wfdManager.unregister()
        }
        _uiState.update { SendUiState() }
    }

    fun prepareQr(passphrase: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(status = SendUiState.Status.Transferring, errorMessage = null) }
            runCatching {
                withContext(Dispatchers.Default) {
                    val plain = SyncPayload.serialize(selectedAccounts())
                    val payload = encryptionManager.encrypt(plain.toByteArray(Charsets.UTF_8), passphrase)
                    val wrapper = JSONObject().apply {
                        put("ciphertext", payload.ciphertext)
                        put("iv", payload.iv)
                        put("salt", payload.salt)
                        put("version", SyncPayload.VERSION)
                        put("protocol", SyncProtocol.VERSION)
                        put("app", appInfo.versionName)
                        put("build", appInfo.versionCode)
                    }.toString()
                    QrChunkCodec.encode(wrapper.toByteArray(Charsets.UTF_8))
                }
            }.onSuccess { frames ->
                _uiState.update {
                    it.copy(
                        qrFrames = frames,
                        currentFrame = 0,
                        status = SendUiState.Status.Done,
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        status = SendUiState.Status.Idle,
                        errorMessage = e.message ?: context.getString(me.diamondforge.tokn.sync.R.string.sync_error_encrypt),
                    )
                }
            }
        }
    }

    fun advanceQrFrame() {
        val frames = _uiState.value.qrFrames
        if (frames.isEmpty()) return
        _uiState.update {
            it.copy(currentFrame = (it.currentFrame + 1) % frames.size)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun resetQr() {
        _uiState.update { SendUiState() }
    }

    fun clearVersionMismatch() {
        _uiState.update { it.copy(versionMismatch = null) }
    }

    private fun versionMismatchInfo(peerApp: String, peerBuild: Long) = VersionMismatchInfo(
        local = localVersionLabel,
        peer = "$peerApp (build $peerBuild)",
    )

    override fun onCleared() {
        super.onCleared()
        lanJob?.cancel()
        wfdJob?.cancel()
        runCatching { wfdManager.unregister() }
    }
}

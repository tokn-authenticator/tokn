package me.diamondforge.tokn.sync.ui

import android.content.Context
import android.net.wifi.p2p.WifiP2pDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.diamondforge.tokn.domain.usecase.ImportAccountsUseCase
import me.diamondforge.tokn.security.EncryptedPayload
import me.diamondforge.tokn.security.EncryptionManager
import me.diamondforge.tokn.sync.AppInfo
import me.diamondforge.tokn.sync.SyncPayload
import me.diamondforge.tokn.sync.SyncProtocol
import me.diamondforge.tokn.sync.lan.DiscoveredPeer
import me.diamondforge.tokn.sync.lan.LanSyncClient
import me.diamondforge.tokn.sync.lan.NsdDiscovery
import me.diamondforge.tokn.sync.qr.Gzip
import me.diamondforge.tokn.sync.qr.QrChunkCodec
import me.diamondforge.tokn.sync.wfd.WfdManager
import me.diamondforge.tokn.sync.wfd.WfdSyncTransport
import org.json.JSONObject
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
import javax.inject.Inject

data class ReceiveUiState(
    val status: Status = Status.Idle,
    val activePeer: DiscoveredPeer? = null,
    val qrSeen: Int = 0,
    val qrTotal: Int = 0,
    val qrComplete: Boolean = false,
    val errorMessage: String? = null,
    val versionMismatch: VersionMismatchInfo? = null,
    val importSummary: ImportAccountsUseCase.Summary? = null,
) {
    enum class Status { Idle, Connecting, Importing, Done }
}

@HiltViewModel
class ReceiveViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryptionManager: EncryptionManager,
    private val importAccountsUseCase: ImportAccountsUseCase,
    private val appInfo: AppInfo,
) : ViewModel() {

    private val localVersionLabel get() = "${appInfo.versionName} (build ${appInfo.versionCode})"

    private val _uiState = MutableStateFlow(ReceiveUiState())
    val uiState: StateFlow<ReceiveUiState> = _uiState.asStateFlow()

    private val nsd = NsdDiscovery(context)
    val peers: StateFlow<List<DiscoveredPeer>> = nsd.browse()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val wfdManager = WfdManager(context)
    val wfdSupported: Boolean get() = wfdManager.isSupported
    val wfdPeers: StateFlow<List<WifiP2pDevice>> get() = wfdManager.peers

    private val qrChunks = linkedMapOf<Int, ByteArray>()
    private var qrPayload: ByteArray? = null
    private var connectJob: Job? = null
    private var wfdJob: Job? = null

    fun connectAndReceive(peer: DiscoveredPeer, code: String) {
        if (connectJob?.isActive == true) return
        _uiState.update {
            it.copy(
                status = ReceiveUiState.Status.Connecting,
                activePeer = peer,
                errorMessage = null,
            )
        }
        connectJob = viewModelScope.launch {
            runCatching {
                val client = LanSyncClient()
                client.receivePayload(peer, code, appInfo.versionName, appInfo.versionCode)
            }.onSuccess { result ->
                when (result) {
                    is LanSyncClient.ReceiveResult.Ok -> applyJsonPayload(
                        String(
                            result.payload,
                            Charsets.UTF_8
                        )
                    )

                    LanSyncClient.ReceiveResult.BadCode ->
                        _uiState.update {
                            it.copy(
                                status = ReceiveUiState.Status.Idle,
                                activePeer = null,
                                errorMessage = context.getString(me.diamondforge.tokn.sync.R.string.sync_bad_code),
                            )
                        }

                    is LanSyncClient.ReceiveResult.VersionMismatch ->
                        _uiState.update {
                            it.copy(
                                status = ReceiveUiState.Status.Idle,
                                activePeer = null,
                                versionMismatch = versionMismatchInfo(
                                    result.peerApp,
                                    result.peerBuild
                                ),
                            )
                        }
                }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(
                        status = ReceiveUiState.Status.Idle,
                        activePeer = null,
                        errorMessage = e.message
                            ?: context.getString(me.diamondforge.tokn.sync.R.string.sync_error_connection),
                    )
                }
            }
        }
    }

    fun onQrFrameScanned(raw: String) {
        if (_uiState.value.qrComplete ||
            _uiState.value.errorMessage != null ||
            _uiState.value.versionMismatch != null
        ) return
        val chunk = QrChunkCodec.parseFrame(raw) ?: return
        if (qrChunks.containsKey(chunk.seq)) return
        qrChunks[chunk.seq] = chunk.data
        val complete = qrChunks.size == chunk.total
        if (!complete) {
            _uiState.update {
                it.copy(qrSeen = qrChunks.size, qrTotal = chunk.total)
            }
            return
        }
        val assembled = QrChunkCodec.assemble(qrChunks, chunk.total)
        if (assembled == null) {
            _uiState.update {
                it.copy(
                    qrSeen = qrChunks.size,
                    qrTotal = chunk.total,
                    errorMessage = context.getString(me.diamondforge.tokn.sync.R.string.sync_error_qr_reassemble)
                )
            }
            return
        }
        // Check protocol version BEFORE asking the user for a passphrase:
        // no point burning effort if we'll just reject the import anyway.
        val mismatch = inspectQrWrapper(assembled)
        if (mismatch != null) {
            _uiState.update {
                it.copy(
                    qrSeen = qrChunks.size,
                    qrTotal = chunk.total,
                    versionMismatch = mismatch,
                )
            }
            return
        }
        qrPayload = assembled
        _uiState.update {
            it.copy(qrSeen = qrChunks.size, qrTotal = chunk.total, qrComplete = true)
        }
    }

    private fun inspectQrWrapper(payload: ByteArray): VersionMismatchInfo? {
        return runCatching {
            val wrapper = JSONObject(String(payload, Charsets.UTF_8))
            val protocol = wrapper.optInt("protocol", -1)
            if (protocol == -1 || protocol == SyncProtocol.VERSION) return@runCatching null
            val peerApp = wrapper.optString("app").ifBlank { "?" }
            val peerBuild = wrapper.optLong("build", 0L)
            versionMismatchInfo(peerApp, peerBuild)
        }.getOrNull()
    }

    fun decryptAndImport(passphrase: String) {
        val payload = qrPayload ?: return
        _uiState.update { it.copy(status = ReceiveUiState.Status.Importing, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                val wrapper = JSONObject(String(payload, Charsets.UTF_8))
                val encrypted = EncryptedPayload(
                    ciphertext = wrapper.getString("ciphertext"),
                    iv = wrapper.getString("iv"),
                    salt = wrapper.getString("salt"),
                )
                val plain = encryptionManager.decrypt(encrypted, passphrase)
                val decoded = if (Gzip.looksGzipped(plain)) Gzip.decompress(plain) else plain
                val accounts = SyncPayload.deserialize(String(decoded, Charsets.UTF_8))
                importAccountsUseCase(accounts)
            }.onSuccess { summary ->
                _uiState.update {
                    it.copy(
                        status = ReceiveUiState.Status.Done,
                        importSummary = summary,
                    )
                }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                val msg = when (e) {
                    is AEADBadTagException, is BadPaddingException ->
                        context.getString(me.diamondforge.tokn.sync.R.string.sync_qr_wrong_passphrase)

                    else -> e.message
                        ?: context.getString(me.diamondforge.tokn.sync.R.string.sync_error_decrypt)
                }
                _uiState.update {
                    it.copy(status = ReceiveUiState.Status.Idle, errorMessage = msg)
                }
            }
        }
    }

    fun resetQr() {
        qrChunks.clear()
        qrPayload = null
        _uiState.update {
            ReceiveUiState()
        }
    }

    fun startWfdDiscovery() {
        if (!wfdManager.isSupported) {
            _uiState.update { it.copy(errorMessage = context.getString(me.diamondforge.tokn.sync.R.string.sync_wfd_unsupported)) }
            return
        }
        wfdManager.register()
        viewModelScope.launch {
            wfdManager.discoverPeers()
        }
    }

    fun stopWfdDiscovery() {
        wfdManager.unregister()
    }

    fun connectViaWfd(device: WifiP2pDevice, code: String) {
        if (wfdJob?.isActive == true) return
        _uiState.update {
            it.copy(status = ReceiveUiState.Status.Connecting, errorMessage = null)
        }
        wfdJob = viewModelScope.launch {
            runCatching {
                val ok = wfdManager.connect(device)
                if (!ok) error(context.getString(me.diamondforge.tokn.sync.R.string.sync_error_wfd_connect))
                val info = withTimeoutOrNull(45_000) {
                    wfdManager.connectionInfo.first { it != null && it.groupFormed && it.groupOwnerAddress != null }
                }
                    ?: error(context.getString(me.diamondforge.tokn.sync.R.string.sync_error_wfd_group_form))
                WfdSyncTransport.receiveOverWfd(
                    groupOwner = info.groupOwnerAddress!!,
                    code = code,
                    localApp = appInfo.versionName,
                    localBuild = appInfo.versionCode,
                )
            }.onSuccess { result ->
                when (result) {
                    is WfdSyncTransport.ReceiveResult.Ok ->
                        applyJsonPayload(String(result.payload, Charsets.UTF_8))

                    WfdSyncTransport.ReceiveResult.BadCode ->
                        _uiState.update {
                            it.copy(
                                status = ReceiveUiState.Status.Idle,
                                errorMessage = context.getString(me.diamondforge.tokn.sync.R.string.sync_bad_code),
                            )
                        }

                    is WfdSyncTransport.ReceiveResult.VersionMismatch ->
                        _uiState.update {
                            it.copy(
                                status = ReceiveUiState.Status.Idle,
                                versionMismatch = versionMismatchInfo(
                                    result.peerApp,
                                    result.peerBuild
                                ),
                            )
                        }
                }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(
                        status = ReceiveUiState.Status.Idle,
                        errorMessage = e.message
                            ?: context.getString(me.diamondforge.tokn.sync.R.string.sync_error_wfd_connect),
                    )
                }
            }
        }
    }

    fun reset() {
        connectJob?.cancel()
        connectJob = null
        wfdJob?.cancel()
        wfdJob = null
        qrChunks.clear()
        qrPayload = null
        _uiState.update { ReceiveUiState() }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearVersionMismatch() {
        _uiState.update { it.copy(versionMismatch = null) }
    }

    private fun versionMismatchInfo(peerApp: String, peerBuild: Long) = VersionMismatchInfo(
        local = localVersionLabel,
        peer = "$peerApp (build $peerBuild)",
    )

    private suspend fun applyJsonPayload(json: String) {
        runCatching {
            val accounts = SyncPayload.deserialize(json)
            importAccountsUseCase(accounts)
        }.onSuccess { summary ->
            _uiState.update {
                it.copy(
                    status = ReceiveUiState.Status.Done,
                    importSummary = summary,
                )
            }
        }.onFailure { e ->
            if (e is CancellationException) throw e
            _uiState.update {
                it.copy(
                    status = ReceiveUiState.Status.Idle,
                    errorMessage = e.message
                        ?: context.getString(me.diamondforge.tokn.sync.R.string.sync_error_parse),
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        connectJob?.cancel()
        wfdJob?.cancel()
        runCatching { wfdManager.unregister() }
    }
}

package app.brain.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import app.brain.data.ai.AiQueueProcessor
import app.brain.data.backup.BackupManager
import app.brain.data.backup.BackupResult
import app.brain.data.admin.AdminServer
import app.brain.data.backup.RestoreResult
import app.brain.data.ai.AiTransport
import app.brain.data.ai.OrganizeRequest
import app.brain.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val transport: AiTransport,
    private val aiQueueProcessor: AiQueueProcessor,
    private val backupManager: BackupManager,
    private val adminServer: AdminServer,
) : ViewModel() {

    val model = MutableStateFlow(settings.model)
    val hasApiKey = MutableStateFlow(settings.hasApiKey)

    /** 已保存的管理端配置，用来把设置页输入框预填好，避免关掉之后再也开不起来。 */
    val savedAdminPort: Int = settings.adminPort
    val savedAdminPassword: String = settings.adminPassword

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _restartPending = MutableStateFlow(false)
    val restartPending: StateFlow<Boolean> = _restartPending.asStateFlow()

    private val _localBackups = MutableStateFlow<List<BackupResult>>(emptyList())
    val localBackups: StateFlow<List<BackupResult>> = _localBackups.asStateFlow()

    private val _adminRunning = MutableStateFlow(adminServer.isRunning)
    val adminRunning: StateFlow<Boolean> = _adminRunning.asStateFlow()

    val adminAddress: String get() = adminServer.localAddress()

    init {
        refreshLocalBackups()
    }

    private fun refreshLocalBackups() {
        _localBackups.value = backupManager.listLocalBackups()
    }

    fun save(key: String, modelName: String) {
        // 留空表示「不改密钥」，不再把已存的密钥顶掉。
        if (key.isNotBlank()) settings.saveApiKey(key)
        settings.model = modelName
        hasApiKey.value = settings.hasApiKey
        aiQueueProcessor.kick()
        _message.value = when {
            key.isNotBlank() -> "已保存"
            settings.hasApiKey -> "已保存，密钥没有改动"
            else -> "还没填密钥，AI 整理要等填好密钥才会开始"
        }
    }

    fun clearApiKey() {
        settings.clearApiKey()
        hasApiKey.value = false
        _message.value = "已清除密钥"
    }

    fun testConnection() {
        viewModelScope.launch {
            _testing.value = true
            _message.value = null
            try {
                transport.organize(
                    OrganizeRequest(
                        content = "今天下午要去医院复查，顺便把车送去保养。",
                    )
                )
                _message.value = "连接成功"
            } catch (e: Exception) {
                _message.value = "连接失败：${e.message}"
            } finally {
                _testing.value = false
            }
        }
    }

    fun createBackup() {
        viewModelScope.launch {
            _busy.value = true
            _message.value = null
            try {
                val result = backupManager.createBackup()
                refreshLocalBackups()
                _message.value = if (result.uri != null) {
                    "备份已保存到下载：${result.fileName}（${result.sizeBytes / 1024} KB）"
                } else {
                    "备份已生成：${result.fileName}"
                }
            } catch (e: Exception) {
                _message.value = "备份失败：${e.message}"
            } finally {
                _busy.value = false
            }
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            _message.value = null
            try {
                val result = backupManager.exportBackup(uri)
                _message.value = "已导出备份：${result.fileName}"
            } catch (e: Exception) {
                _message.value = "导出失败：${e.message}"
            } finally {
                _busy.value = false
            }
        }
    }

    fun restore(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            _message.value = null
            try {
                when (val result = backupManager.restore(uri)) {
                    is RestoreResult.Success -> {
                        _message.value = "恢复成功，正在重启应用…"
                        _restartPending.value = true
                    }
                    is RestoreResult.Error -> _message.value = "恢复失败：${result.message}"
                }
            } finally {
                _busy.value = false
            }
        }
    }

    fun toggleAdminServer(enabled: Boolean, port: Int, password: String) {
        try {
            if (enabled) {
                if (password.isBlank()) {
                    _message.value = "请先设置管理端密码"
                    return
                }
                adminServer.start(port, password)
                _message.value = "管理端已开启：${adminServer.localAddress()}"
            } else {
                adminServer.stop()
                _message.value = "管理端已关闭"
            }
            _adminRunning.value = adminServer.isRunning
        } catch (e: Exception) {
            _adminRunning.value = adminServer.isRunning
            _message.value = "启动失败：${e.message}"
        }
    }

    fun rollbackLastRestore() {
        viewModelScope.launch {
            _busy.value = true
            _message.value = null
            try {
                when (val result = backupManager.rollbackLastRestore()) {
                    is RestoreResult.Success -> {
                        _message.value = "已回滚，正在重启应用…"
                        _restartPending.value = true
                    }
                    is RestoreResult.Error -> _message.value = "回滚失败：${result.message}"
                }
            } finally {
                _busy.value = false
            }
        }
    }
}

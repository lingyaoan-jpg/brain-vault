package app.brain.ui.settings

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.brain.data.settings.SettingsRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val model by viewModel.model.collectAsStateWithLifecycle()
    val hasApiKey by viewModel.hasApiKey.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val testing by viewModel.testing.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val restartPending by viewModel.restartPending.collectAsStateWithLifecycle()
    val localBackups by viewModel.localBackups.collectAsStateWithLifecycle()
    val adminRunning by viewModel.adminRunning.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var keyInput by remember { mutableStateOf("") }
    var modelInput by remember { mutableStateOf(model) }
    var customModel by remember { mutableStateOf(model !in SettingsRepository.MODEL_OPTIONS) }
    var adminPortInput by remember { mutableStateOf(viewModel.savedAdminPort.toString()) }
    var adminPasswordInput by remember { mutableStateOf(viewModel.savedAdminPassword) }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.restore(it) }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { viewModel.exportBackup(it) }
    }

    LaunchedEffect(restartPending) {
        if (restartPending) {
            val activity = context as? Activity
            activity?.finishAffinity()
            Handler(Looper.getMainLooper()).postDelayed({
                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    context.startActivity(intent)
                }
            }, 500)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsCard(title = "DeepSeek 密钥") {
                Hint(
                    if (hasApiKey) "已保存在手机系统加密区（安装包里没有密钥，别人拿到也看不到）。"
                    else "还没有密钥。填好之后 AI 才会开始整理记录。"
                )
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (hasApiKey) "更换密钥（留空就不改）" else "API 密钥") },
                    placeholder = { Text("sk-…") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                ModelPicker(
                    selected = modelInput,
                    custom = customModel,
                    onSelect = { picked ->
                        modelInput = picked
                        customModel = false
                    },
                    onCustom = { customModel = true },
                    onCustomChange = { modelInput = it },
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(onClick = { viewModel.save(keyInput, modelInput.trim()) }) {
                        Text("保存")
                    }
                    TextButton(
                        onClick = viewModel::testConnection,
                        enabled = !testing && hasApiKey,
                    ) {
                        Text(if (testing) "测试中…" else "测试连接")
                    }
                    if (hasApiKey) {
                        TextButton(onClick = viewModel::clearApiKey) {
                            Text("清除密钥", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (testing) {
                        CircularProgressIndicator(modifier = Modifier.width(18.dp), strokeWidth = 2.dp)
                    }
                }
                message?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it.startsWith("失败") || it.startsWith("连接失败")) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }

            SettingsCard(title = "备份与恢复") {
                Hint("备份 = 把全部收容、分类、批注打包成一个文件，可存到「下载」或手机任意位置；换手机、重装后用它可以原样搬回来。")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(onClick = viewModel::createBackup, enabled = !busy) {
                        Text("立即备份")
                    }
                    OutlinedButton(
                        onClick = { restoreLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                        enabled = !busy,
                    ) {
                        Text("从文件恢复")
                    }
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.width(18.dp), strokeWidth = 2.dp)
                    }
                }
                Hint("恢复会覆盖现在的数据（覆盖前会自动先留一份当前数据，可撤销）。")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TextButton(
                        onClick = { exportLauncher.launch("脑内收容所备份.db") },
                        enabled = !busy,
                    ) {
                        Text("备份到指定文件")
                    }
                    TextButton(onClick = viewModel::rollbackLastRestore, enabled = !busy) {
                        Text("撤销上次恢复")
                    }
                }
                Text(
                    text = "本机自动留存 ${localBackups.size} 份（最多 7 份）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                localBackups.take(3).forEach { backup ->
                    Text(
                        text = backup.fileName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            SettingsCard(title = "电脑管理端") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Switch(
                        checked = adminRunning,
                        onCheckedChange = { enabled ->
                            viewModel.toggleAdminServer(
                                enabled,
                                adminPortInput.toIntOrNull() ?: SettingsRepository.DEFAULT_ADMIN_PORT,
                                adminPasswordInput,
                            )
                        },
                    )
                    Text(if (adminRunning) "已开启（仅同一 WiFi 能访问）" else "未开启")
                }
                if (adminRunning) {
                    Text(
                        text = "电脑浏览器打开：${viewModel.adminAddress}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = adminPortInput,
                        onValueChange = { adminPortInput = it.filter { c -> c.isDigit() } },
                        modifier = Modifier.width(110.dp),
                        label = { Text("端口") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = adminPasswordInput,
                        onValueChange = { adminPasswordInput = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("管理端密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
                Hint("给电脑浏览器用的：和手机共用同一份数据，可查看、搜索、批量导入修改、导出。开启前要先设好密码。")
            }

            SettingsCard(title = "AI 整理") {
                Hint("记录保存后自动排队等 AI 整理。没填密钥或没网时先挂着「待整理」，之后会自动补上，不会丢原文。")
            }

            Box(modifier = Modifier.padding(bottom = 12.dp))
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 模型下拉：日常用 flash，想更聪明就切 pro；也留了自定义口子。 */
@Composable
private fun ModelPicker(
    selected: String,
    custom: Boolean,
    onSelect: (String) -> Unit,
    onCustom: () -> Unit,
    onCustomChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "模型：${selected.ifBlank { "自定义" }}",
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SettingsRepository.MODEL_OPTIONS.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("自定义…") },
                onClick = {
                    expanded = false
                    onCustom()
                },
            )
        }
    }

    if (custom) {
        OutlinedTextField(
            value = selected,
            onValueChange = onCustomChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("自定义模型名") },
            singleLine = true,
        )
    }
}

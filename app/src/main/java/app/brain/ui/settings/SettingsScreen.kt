package app.brain.ui.settings

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
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
    var adminPortInput by remember { mutableStateOf("8080") }
    var adminPasswordInput by remember { mutableStateOf("") }
    var modelInput by remember { mutableStateOf(model) }

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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("DeepSeek API", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "密钥只保存在本机系统加密区，不会写入安装包。AI 整理时只发送当前记录原文和分类清单。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (hasApiKey) "API 密钥（已保存，输入可更换）" else "API 密钥") },
                placeholder = { Text("sk-…") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            OutlinedTextField(
                value = modelInput,
                onValueChange = { modelInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("模型") },
                singleLine = true,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = { viewModel.save(keyInput, modelInput) }) {
                    Text("保存")
                }
                TextButton(
                    onClick = viewModel::testConnection,
                    enabled = !testing && hasApiKey,
                ) {
                    Text(if (testing) "测试中…" else "测试连接")
                }
                if (testing) {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp), strokeWidth = 2.dp)
                }
            }
            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (it.startsWith("失败")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("备份与恢复", style = MaterialTheme.typography.titleMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = viewModel::createBackup, enabled = !busy) {
                    Text("创建完整备份")
                }
                TextButton(
                    onClick = {
                        restoreLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                    },
                    enabled = !busy,
                ) {
                    Text("恢复备份")
                }
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp), strokeWidth = 2.dp)
                }
            }
            Text(
                text = "备份会保存到“下载”文件夹；恢复前会自动备份当前数据，再替换为备份内容并重启应用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = {
                        exportLauncher.launch("脑内收容所备份.db")
                    },
                    enabled = !busy,
                ) {
                    Text("导出备份到文件")
                }
                TextButton(
                    onClick = viewModel::rollbackLastRestore,
                    enabled = !busy,
                ) {
                    Text("撤销上次恢复")
                }
            }
            Text(
                text = "本机自动保留备份：${localBackups.size} 份（最多 7 份）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            localBackups.take(7).forEach { backup ->
                Text(
                    text = backup.fileName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("电脑管理端", style = MaterialTheme.typography.titleMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Switch(
                    checked = adminRunning,
                    onCheckedChange = { enabled ->
                        viewModel.toggleAdminServer(enabled, adminPortInput.toIntOrNull() ?: 8080, adminPasswordInput)
                    },
                )
                Text(if (adminRunning) "已开启（仅局域网可访问）" else "未开启")
            }
            OutlinedTextField(
                value = adminPortInput,
                onValueChange = { adminPortInput = it.filter { c -> c.isDigit() } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("端口") },
                singleLine = true,
            )
            OutlinedTextField(
                value = adminPasswordInput,
                onValueChange = { adminPasswordInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("管理端密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            if (adminRunning) {
                Text(
                    text = "电脑浏览器打开：${viewModel.adminAddress}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = "电脑端与手机共用同一个本地数据库，可查看/搜索、批量导入、批量修改、导出和备份恢复；登录密码保存在手机系统加密区。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = "说明：记录保存后自动进入 AI 整理队列。未配置密钥或断网时，记录保持“待整理”，配置好密钥或联网后会自动继续。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

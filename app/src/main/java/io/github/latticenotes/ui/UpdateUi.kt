package io.github.latticenotes.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.github.latticenotes.update.AppUpdate
import io.github.latticenotes.update.UpdateManager
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun UpdateAvailableDialog(
    update: AppUpdate,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingApk by remember { mutableStateOf<File?>(null) }

    val sourceSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val apk = pendingApk
        if (
            apk != null &&
            context.packageManager.canRequestPackageInstalls()
        ) {
            runCatching { launchPackageInstaller(context, apk) }
                .onSuccess { message = "已打开系统安装界面，请确认安装" }
                .onFailure { message = "无法打开安装界面：${it.message}" }
        } else {
            message = "需要允许“安装未知应用”才能完成升级"
        }
    }

    fun install(apk: File) {
        pendingApk = apk
        if (context.packageManager.canRequestPackageInstalls()) {
            runCatching { launchPackageInstaller(context, apk) }
                .onSuccess { message = "已打开系统安装界面，请确认安装" }
                .onFailure { message = "无法打开安装界面：${it.message}" }
        } else {
            runCatching {
                sourceSettingsLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            }.onFailure {
                message = "无法打开安装权限设置：${it.message}"
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = { Text("发现新版本 ${update.versionName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = update.releaseNotes
                        .ifBlank { "包含界面修复和稳定性改进。" }
                        .take(1800),
                )
                if (downloading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = progress?.let { "正在下载 ${(it * 100).toInt()}%" }
                            ?: "正在下载更新…",
                    )
                }
                message?.let { Text(it) }
                Text("下载后会打开 Android 系统安装界面。出于系统安全限制，最后一步需要手动确认安装。")
            }
        },
        confirmButton = {
            TextButton(
                enabled = !downloading,
                onClick = {
                    downloading = true
                    message = null
                    progress = 0f
                    scope.launch {
                        runCatching {
                            UpdateManager.download(context, update) { progress = it }
                        }.onSuccess {
                            downloading = false
                            install(it)
                        }.onFailure {
                            downloading = false
                            message = it.message ?: "下载更新失败"
                        }
                    }
                },
            ) {
                if (downloading) {
                    CircularProgressIndicator()
                } else {
                    Text("下载并安装")
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !downloading, onClick = onDismiss) { Text("稍后") }
        },
    )
}

private fun launchPackageInstaller(context: Context, apk: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.files",
        apk,
    )
    context.startActivity(
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
}

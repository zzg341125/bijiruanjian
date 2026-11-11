package io.github.latticenotes.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.latticenotes.data.AppSettings
import io.github.latticenotes.data.CredentialVault
import io.github.latticenotes.data.NoteFormat
import io.github.latticenotes.data.NotesRepository
import io.github.latticenotes.data.OnlineSyncManager
import io.github.latticenotes.data.SettingsRepository
import io.github.latticenotes.update.AppUpdate
import io.github.latticenotes.update.UpdateManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class LaunchRequest(
    val id: Long,
    val destination: String,
    val sharedText: String?,
    val noteId: Long? = null,
)

@Composable
fun AppNav(
    repository: NotesRepository,
    settingsRepository: SettingsRepository,
    credentialVault: CredentialVault,
    settings: AppSettings,
    launchRequest: LaunchRequest?,
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var startSearch by remember { mutableStateOf(launchRequest?.destination == "search") }
    var availableUpdate by remember { mutableStateOf<AppUpdate?>(null) }

    LaunchedEffect(settings.autoUpdateCheck) {
        if (!settings.autoUpdateCheck || !UpdateManager.shouldAutoCheck(context)) {
            return@LaunchedEffect
        }
        runCatching { UpdateManager.checkForUpdate() }
            .onSuccess { availableUpdate = it }
            .onFailure { Log.d("AppUpdate", "Automatic update check failed: ${it.message}") }
        UpdateManager.markAutoChecked(context)
    }

    LaunchedEffect(settings.webDavUrl, settings.webDavAutoSync, lifecycleOwner) {
        if (!settings.webDavAutoSync || settings.webDavUrl.isBlank()) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                OnlineSyncManager.sync(
                    repository = repository,
                    settings = settings,
                    credentials = credentialVault,
                )?.let { Log.d("WebDavSync", it) }
                delay(OnlineSyncManager.FOREGROUND_SYNC_INTERVAL_MILLIS)
            }
        }
    }

    LaunchedEffect(launchRequest?.id) {
        val request = launchRequest ?: return@LaunchedEffect
        when {
            request.noteId != null -> navController.navigate("editor/${request.noteId}")
            request.sharedText != null -> {
                val id = repository.createNote(
                    title = "分享的笔记",
                    body = request.sharedText,
                    format = NoteFormat.TEXT,
                )
                navController.navigate("editor/$id")
            }
            request.destination == "new_note" -> {
                val format = runCatching { NoteFormat.valueOf(settings.defaultFormat) }
                    .getOrDefault(NoteFormat.TEXT)
                val id = repository.createNote(format = format)
                navController.navigate("editor/$id")
            }
            request.destination == "search" -> startSearch = true
        }
    }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                repository = repository,
                initialSearch = startSearch,
                onSearchConsumed = { startSearch = false },
                onCreateNote = { folderId ->
                    scope.launch {
                        val format = runCatching { NoteFormat.valueOf(settings.defaultFormat) }
                            .getOrDefault(NoteFormat.TEXT)
                        val id = repository.createNote(folderId = folderId, format = format)
                        navController.navigate("editor/$id")
                    }
                },
                onOpenNote = { navController.navigate("editor/$it") },
                onOpenSettings = { navController.navigate("settings") },
                onOpenKnowledge = { navController.navigate("knowledge") },
            )
        }
        composable(
            route = "editor/{noteId}",
            arguments = listOf(navArgument("noteId") { type = NavType.LongType }),
        ) { entry ->
            val noteId = entry.arguments?.getLong("noteId") ?: return@composable
            EditorScreen(
                noteId = noteId,
                repository = repository,
                onBack = { navController.popBackStack() },
            )
        }
        composable("settings") {
            SettingsScreen(
                repository = repository,
                settingsRepository = settingsRepository,
                credentialVault = credentialVault,
                settings = settings,
                onBack = { navController.popBackStack() },
            )
        }
        composable("knowledge") {
            KnowledgeScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
                onOpenNote = { navController.navigate("editor/$it") },
            )
        }
    }

    availableUpdate?.let { update ->
        UpdateAvailableDialog(update = update, onDismiss = { availableUpdate = null })
    }
}

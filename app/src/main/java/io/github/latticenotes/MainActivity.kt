package io.github.latticenotes

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.latticenotes.data.AppSettings
import io.github.latticenotes.ui.AppNav
import io.github.latticenotes.ui.LaunchRequest
import io.github.latticenotes.ui.LockScreen
import io.github.latticenotes.ui.theme.LatticeTheme

class MainActivity : ComponentActivity() {
    private var launchRequest by mutableStateOf<LaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            launchRequest = intent.toLaunchRequest()
        }

        setContent {
            val container = (application as LatticeApplication).container
            val settings by container.settings.settings.collectAsStateWithLifecycle(
                initialValue = AppSettings(),
            )
            var unlocked by remember(settings.appLockEnabled) {
                mutableStateOf(!settings.appLockEnabled)
            }

            LatticeTheme(settings) {
                if (settings.appLockEnabled && !unlocked) {
                    LockScreen(
                        settings = settings,
                        onUnlocked = { unlocked = true },
                    )
                } else {
                    AppNav(
                        repository = container.notes,
                        settingsRepository = container.settings,
                        credentialVault = container.credentials,
                        settings = settings,
                        launchRequest = launchRequest,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchRequest = intent.toLaunchRequest()
    }

    private fun Intent.toLaunchRequest(): LaunchRequest {
        val sharedText = if (action == Intent.ACTION_SEND) {
            getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        } else {
            null
        }
        return LaunchRequest(
            id = System.nanoTime(),
            destination = getStringExtra("destination") ?: "home",
            sharedText = sharedText,
            noteId = getLongExtra("noteId", 0).takeIf { it > 0 },
        )
    }
}

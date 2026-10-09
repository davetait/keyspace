package ai.stxt.keyspace

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = AppRepository(applicationContext)
        val gridStore = SlotStore(applicationContext, "grid")
        val dockStore = SlotStore(applicationContext, "dock")
        val stores = mapOf(SlotArea.Grid to gridStore, SlotArea.Dock to dockStore)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var apps by remember { mutableStateOf(emptyList<AppEntry>()) }
                var drawerState by remember { mutableStateOf(DrawerState.Closed) }
                val grid by gridStore.slots.collectAsState()
                val dock by dockStore.slots.collectAsState()

                var typed by rememberSaveable { mutableStateOf("") }
                var status by remember { mutableStateOf<String?>(null) }
                var listening by remember { mutableStateOf(false) }

                val speech = remember {
                    SpeechInput(
                        context = this,
                        onListeningChange = { listening = it },
                        onResult = { typed = TypedText.appendSpoken(typed, it) },
                        onError = { status = it },
                    )
                }
                DisposableEffect(speech) { onDispose { speech.destroy() } }

                val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    if (granted) speech.start() else status = "Microphone permission needed"
                }

                // Reload on every resume so installs/uninstalls show up.
                LifecycleResumeEffect(Unit) {
                    val scope = MainScope()
                    scope.launch { apps = repository.loadApps() }
                    onPauseOrDispose {
                        scope.cancel()
                        speech.stop()
                    }
                }

                // Back closes the drawer; on the bare home screen there is nothing to go back to.
                BackHandler { drawerState = DrawerState.Closed }

                HomeScreen(
                    apps = apps,
                    grid = grid,
                    // Fixed for now; will become a setting.
                    gridColumns = 5,
                    dock = dock,
                    drawerState = drawerState,
                    repository = repository,
                    onDrawerStateChange = { drawerState = it },
                    onPlace = { area, index, key -> stores.getValue(area).place(index, key) },
                    onRemove = { area, index -> stores.getValue(area).clear(index) },
                ) {
                    Column {
                        TypedLine(text = typed, status = status)
                        Keyboard(
                            listening = listening,
                            onLetter = {
                                status = null
                                typed = TypedText.type(typed, it)
                            },
                            onBackspace = {
                                status = null
                                typed = TypedText.backspace(typed)
                            },
                            onMic = {
                                status = null
                                when {
                                    listening -> speech.stop()
                                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) ==
                                        PackageManager.PERMISSION_GRANTED -> speech.start()
                                    else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

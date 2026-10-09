package ai.stxt.keyspace

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = AppRepository(applicationContext)
        val dockStore = DockStore(applicationContext)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var apps by remember { mutableStateOf(emptyList<AppEntry>()) }
                var drawerState by remember { mutableStateOf(DrawerState.Closed) }
                val dock by dockStore.dock.collectAsState()

                // Reload on every resume so installs/uninstalls show up.
                LifecycleResumeEffect(Unit) {
                    val scope = MainScope()
                    scope.launch { apps = repository.loadApps() }
                    onPauseOrDispose { scope.cancel() }
                }

                // Back closes the drawer; on the bare home screen there is nothing to go back to.
                BackHandler { drawerState = DrawerState.Closed }

                HomeScreen(
                    apps = apps,
                    dock = dock,
                    drawerState = drawerState,
                    repository = repository,
                    onDrawerStateChange = { drawerState = it },
                    onPlace = dockStore::place,
                    onRemove = dockStore::clear,
                )
            }
        }
    }
}

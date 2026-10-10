package ai.stxt.keyspace

import android.Manifest
import android.os.Build
import android.content.ComponentName
import android.appwidget.AppWidgetProviderInfo
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Bumped each time Home is pressed while Keyspace is already the current activity. */
    private val homePresses = MutableStateFlow(0)

    private lateinit var widgetHost: KeyspaceWidgetHost
    private lateinit var widgetStore: WidgetStore
    /** A widget being added: its id is allocated, waiting on the bind permission or configure screen. */
    private var pendingWidget: PlacedWidget? = null
    /** Messages from the widget add flow, shown in the typed line. */
    private val widgetMessages = MutableStateFlow<String?>(null)

    private val bindWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val pending = pendingWidget ?: return@registerForActivityResult
        if (result.resultCode == RESULT_OK) configureOrFinishWidget(pending) else cancelWidget(pending, null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePresses.value++
    }

    override fun onStart() {
        super.onStart()
        widgetHost.startListening()
    }

    override fun onStop() {
        super.onStop()
        widgetHost.stopListening()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingWidget?.let { outState.putIntArray(PENDING_WIDGET, intArrayOf(it.id, it.row, it.col, it.spanX, it.spanY)) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        widgetHost = KeyspaceWidgetHost(applicationContext)
        widgetStore = WidgetStore(applicationContext)
        pendingWidget = savedInstanceState?.getIntArray(PENDING_WIDGET)?.let { PlacedWidget(it[0], it[1], it[2], it[3], it[4]) }
        widgetHost.deleteOrphans(keep = widgetStore.widgets.value.map { it.id }.toSet() + listOfNotNull(pendingWidget?.id))
        val repository = AppRepository(applicationContext)
        val gridStore = SlotStore(applicationContext, "grid")
        val dockStore = SlotStore(applicationContext, "dock")
        val stores = mapOf(SlotArea.Grid to gridStore, SlotArea.Dock to dockStore)
        val launchStats = LaunchStats(applicationContext)
        val settingsStore = SettingsStore(applicationContext)

        setContent {
            KeyspaceTheme {
                var apps by remember { mutableStateOf(emptyList<AppEntry>()) }
                var drawerState by remember { mutableStateOf(DrawerState.Closed) }
                val grid by gridStore.slots.collectAsState()
                val dock by dockStore.slots.collectAsState()

                var typed by rememberSaveable { mutableStateOf("") }
                var status by remember { mutableStateOf<String?>(null) }
                var listening by remember { mutableStateOf(false) }

                val placedWidgets by widgetStore.widgets.collectAsState()
                var widgetChoices by remember { mutableStateOf(emptyList<WidgetChoice>()) }
                var gridMetrics by remember { mutableStateOf<GridMetrics?>(null) }
                LaunchedEffect(drawerState.overlay) {
                    if (drawerState.overlay == Overlay.Widgets) widgetChoices = loadWidgetChoices(this@MainActivity)
                }
                val widgetMessage by widgetMessages.collectAsState()
                LaunchedEffect(widgetMessage) {
                    widgetMessage?.let { status = it }
                    widgetMessages.value = null
                }

                val settings by settingsStore.settings.collectAsState()
                var iconPacks by remember { mutableStateOf(emptyList<IconPackInfo>()) }
                // Applied before icons are drawn; null if the chosen pack couldn't be loaded.
                val activeIconPack = remember(settings.iconPack) {
                    settings.iconPack.takeIf { repository.setIconPack(it) }
                }
                LaunchedEffect(activeIconPack, settings.iconPack) {
                    // The chosen pack is gone (uninstalled or broken): go back to system icons.
                    if (settings.iconPack != null && activeIconPack == null) settingsStore.setIconPack(null)
                }
                var showSettings by rememberSaveable { mutableStateOf(false) }
                // Refreshed on resume, e.g. after returning from the system Home app screen.
                var isHome by remember { mutableStateOf(isDefaultHome()) }
                val usage by launchStats.usage.collectAsState()
                val halfLife = settings.adaptSpeed.halfLifeDays
                val results = remember(apps, typed, usage, halfLife) {
                    if (typed.isBlank()) null else matchApps(apps, typed, usage, System.currentTimeMillis(), halfLife)
                }
                val litKeys = remember(apps, typed) { nextLetters(apps, typed) }

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
                    iconPacks = findIconPacks(this@MainActivity)
                    // The pack may have been uninstalled while Keyspace was in the background.
                    settingsStore.settings.value.iconPack?.let { chosen ->
                        if (iconPacks.none { it.packageName == chosen }) settingsStore.setIconPack(null)
                    }
                    isHome = isDefaultHome()
                    onPauseOrDispose {
                        scope.cancel()
                        speech.stop()
                    }
                }

                if (showSettings) {
                    SettingsDialog(
                        settings = settings,
                        isDefaultHome = isHome,
                        onColumnsChange = settingsStore::setColumns,
                        onAdaptSpeedChange = settingsStore::setAdaptSpeed,
                        onClearSearchHistory = launchStats::clear,
                        iconPacks = iconPacks,
                        onIconPackChange = settingsStore::setIconPack,
                        onFindIconPacks = { openIconPackStore() },
                        onOpenHomeSettings = { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) },
                        onDismiss = { showSettings = false },
                    )
                }

                // Search results show in the home area, which the open drawer would cover.
                fun closeDrawerForInput() {
                    if (drawerState.isOpen) drawerState = DrawerState.Closed
                }

                // Home resets the home screen: closes overlays and clears the search.
                val homePressCount by homePresses.collectAsState()
                LaunchedEffect(homePressCount) {
                    if (homePressCount == 0) return@LaunchedEffect
                    drawerState = DrawerState.Closed
                    showSettings = false
                    typed = ""
                    status = null
                }

                // Back closes the drawer; on the bare home screen there is nothing to go back to.
                BackHandler { drawerState = DrawerState.Closed }

                CompositionLocalProvider(LocalIconPack provides activeIconPack) {
                    HomeScreen(
                        apps = apps,
                        grid = grid,
                        gridColumns = settings.columns,
                        dock = dock,
                        drawerState = drawerState,
                        repository = repository,
                        onDrawerStateChange = { drawerState = it },
                        onPlace = { area, index, key -> stores.getValue(area).place(index, key) },
                        onRemove = { area, index -> stores.getValue(area).clear(index) },
                        results = results,
                        onLaunchResult = { app ->
                            repository.launch(app)
                            launchStats.record(app.key, halfLife)
                            typed = ""
                        },
                        homeMenu = HomeMenuActions(
                            onWallpaper = { openWallpaperPicker() },
                            onWidgets = { drawerState = DrawerState(Overlay.Widgets) },
                            onSettings = { showSettings = true },
                        ),
                        widgets = WidgetsUi(
                            host = widgetHost,
                            placed = placedWidgets,
                            choices = widgetChoices,
                            metrics = gridMetrics,
                            onMetrics = { gridMetrics = it },
                            onDrop = { row, col, provider, metrics ->
                                // Only widgets from the installed list; anything else is refused rather than trusted.
                                val choice = widgetChoices.firstOrNull { it.provider.flattenToString() == provider }
                                    ?: return@WidgetsUi false
                                val (spanX, spanY) =
                                    spanFor(choice.minWidthDp, choice.minHeightDp, choice.targetCellsX, choice.targetCellsY, metrics)
                                val appCells = grid.resolve(apps, metrics.rows * metrics.columns)
                                    .mapIndexedNotNull { index, app -> index.takeIf { app != null } }
                                    .toSet()
                                if (canPlace(row, col, spanX, spanY, metrics.columns, metrics.rows, placedWidgets, appCells)) {
                                    addWidget(choice.provider, PlacedWidget(-1, row, col, spanX, spanY))
                                    true
                                } else {
                                    status = "Not enough room there"
                                    false
                                }
                            },
                            onRemove = { id ->
                                widgetStore.remove(id)
                                widgetHost.deleteAppWidgetId(id)
                            },
                        ),
                    ) {
                        Column {
                            TypedLine(text = typed, status = status, noMatches = results?.isEmpty() == true)
                            Keyboard(
                                listening = listening,
                                litKeys = litKeys,
                                onLetter = {
                                    status = null
                                    closeDrawerForInput()
                                    typed = TypedText.type(typed, it)
                                },
                                onBackspace = {
                                    status = null
                                    closeDrawerForInput()
                                    typed = TypedText.backspace(typed)
                                },
                                onMic = {
                                    status = null
                                    closeDrawerForInput()
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

    /** Step 1 of adding a widget: allocate an id and bind it, asking the user for permission if needed. */
    private fun addWidget(provider: ComponentName, at: PlacedWidget) {
        val pending = at.copy(id = widgetHost.allocateAppWidgetId())
        pendingWidget = pending
        if (AppWidgetManager.getInstance(this).bindAppWidgetIdIfAllowed(pending.id, provider)) {
            configureOrFinishWidget(pending)
        } else {
            bindWidget.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pending.id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
            )
        }
    }

    /** Step 2: open the widget's own setup screen if it has a required one; otherwise place it. */
    private fun configureOrFinishWidget(pending: PlacedWidget) {
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(pending.id)
            ?: return cancelWidget(pending, "Couldn't add widget")
        val configOptional = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
        if (info.configure == null || configOptional) return finishWidget(pending)
        try {
            // The documented launcher route; it reports back through onActivityResult.
            widgetHost.startAppWidgetConfigureActivityForResult(this, pending.id, 0, REQUEST_CONFIGURE_WIDGET, null)
        } catch (e: Exception) {
            // ActivityNotFoundException or SecurityException from a broken provider.
            cancelWidget(pending, "Couldn't open widget setup")
        }
    }

    // AppWidgetHost.startAppWidgetConfigureActivityForResult only reports here; it has no Activity Result API form.
    @Deprecated("Needed for AppWidgetHost.startAppWidgetConfigureActivityForResult")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CONFIGURE_WIDGET) return
        val pending = pendingWidget ?: return
        if (resultCode == RESULT_OK) finishWidget(pending) else cancelWidget(pending, null)
    }

    private fun finishWidget(pending: PlacedWidget) {
        widgetStore.add(pending)
        pendingWidget = null
    }

    private fun cancelWidget(pending: PlacedWidget, message: String?) {
        widgetHost.deleteAppWidgetId(pending.id)
        pendingWidget = null
        widgetMessages.value = message
    }

    private companion object {
        const val PENDING_WIDGET = "pending_widget"
        const val REQUEST_CONFIGURE_WIDGET = 1
    }
}

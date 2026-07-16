package com.hereliesaz.logkitty.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.logkitty.core.AccessibilityActions
import com.hereliesaz.logkitty.ui.delegates.IndexedLogLine
import com.hereliesaz.logkitty.ui.delegates.StateDelegate
import com.hereliesaz.logkitty.ui.theme.CodingFont
import com.hereliesaz.logkitty.utils.LogcatReader
import com.hereliesaz.logkitty.utils.LogSourceClassifier
import com.hereliesaz.logkitty.utils.LogTagFilter
import com.hereliesaz.logkitty.utils.LogSources
import com.hereliesaz.logkitty.utils.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

/**
 * Represents a "Tab" in the UI.
 * Can be a system tab (all logs), an error tab (only errors), or an app-specific tab.
 */
data class LogTab(
    val id: String,
    val title: String,
    val type: TabType,
    val filterValue: String? = null,
    /**
     * `true` for user-pinned, app-specific tabs (from "Monitor specific apps" in settings) that
     * persist across sessions; `false` for the transient tabs auto-created from the foreground app.
     */
    val pinned: Boolean = false
)

enum class TabType {
    SYSTEM,
    ERRORS,
    APP,
    APP_STATS,
    SOURCE,
    GITHUB
}

/**
 * [MainViewModel] is the brain of the application.
 *
 * **Role:**
 * It acts as the bridge between the Data Layer ([LogcatReader], [UserPreferences]) and the UI Layer
 * ([LogBottomSheet], [SettingsScreen]).
 *
 * **Key Features:**
 * 1. **Singleton-like Scope:** It is instantiated manually in `MainApplication` to ensure that
 *    both the Overlay Service and the Settings Activity share the exact same instance and data.
 * 2. **Reactive Data Pipeline:** It combines raw logs, user filters, prohibited tags, and tab selection
 *    into a single `filteredSystemLog` flow that the UI simply observes.
 * 3. **Context Awareness:** It listens to the accessibility service to automatically create tabs for
 *    foreground applications.
 */
class MainViewModel(
    application: Application
) : AndroidViewModel(application) {

    // Repositories
    private val userPreferences = UserPreferences(application)
    val billingManager = com.hereliesaz.logkitty.billing.BillingManager(application)
    // Secure, backup-excluded store for the GitHub PAT (kept out of UserPreferences/backups).
    private val githubCredentials = com.hereliesaz.logkitty.utils.GitHubCredentials(application)

    // Cache of package name -> Android UID (-1 = resolved-but-unknown). Used to filter the log
    // stream to a specific app reliably (UID is stable across process restarts, unlike PID).
    private val appUidCache = java.util.concurrent.ConcurrentHashMap<String, Int>()

    private fun uidFor(pkg: String): Int? {
        appUidCache[pkg]?.let { return it.takeIf { v -> v >= 0 } }
        val resolved = try {
            getApplication<Application>().packageManager.getApplicationInfo(pkg, 0).uid
        } catch (e: Exception) { -1 }
        appUidCache[pkg] = resolved
        return resolved.takeIf { it >= 0 }
    }

    private fun appLabel(pkg: String): String = try {
        val pm = getApplication<Application>().packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }

    /** Packages the user has pinned for dedicated app-specific log tabs. */
    val monitoredApps: StateFlow<Set<String>> = userPreferences.monitoredApps

    fun addMonitoredApp(pkg: String) = userPreferences.addMonitoredApp(pkg)
    fun removeMonitoredApp(pkg: String) = userPreferences.removeMonitoredApp(pkg)

    // Classifies each log line's UID into source/category buckets for the per-source tabs.
    private val sourceClassifier = LogSourceClassifier(application)

    /** Source/category filter keys (see `LogSources`) the user has enabled as dedicated tabs. */
    val activeSourceFilters: StateFlow<Set<String>> = userPreferences.activeSourceFilters

    fun setSourceFilterEnabled(key: String, enabled: Boolean) =
        userPreferences.setSourceFilterEnabled(key, enabled)

    // Delegate to handle the heavy lifting of log buffering.
    val stateDelegate = StateDelegate(viewModelScope, bufferSizeFlow = userPreferences.bufferSize)

    // Developer-stats collection now lives in the on-demand `:feature:stats` module; the base only
    // exposes the root flag the feature needs (via isRootEnabled below) and hosts its UI slot.

    // --- State Flows ---

    // The package name of the app currently on screen (detected by Accessibility Service).
    private val _currentForegroundApp = MutableStateFlow<String?>(null)
    val currentForegroundApp: StateFlow<String?> = _currentForegroundApp

    // Target UIDs and Packages to filter the logcat stream
    private val targetApps = combine(monitoredApps, _currentForegroundApp) { monitored, fg ->
        val pkgs = monitored.toMutableSet()
        if (fg != null) pkgs.add(fg)
        val uids = pkgs.mapNotNull { uidFor(it) }.toSet()
        Pair(uids, pkgs.toSet())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Pair(emptySet<Int>(), emptySet<String>()))

    // Whether log capture is paused (frozen). Runtime-only: the logcat stream keeps running but new
    // lines are dropped while paused so the view holds still. Toggled from the sheet's play/pause
    // control and the notification's Start/Stop action.
    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused

    fun togglePause() { _isPaused.update { !it } }
    fun setPaused(paused: Boolean) { _isPaused.value = paused }

    // Preference Flows (Directly exposed from UserPreferences)
    val isContextModeEnabled: StateFlow<Boolean> = userPreferences.isContextModeEnabled
    val isHardContextMode: StateFlow<Boolean> = userPreferences.isHardContextMode
    val themeMode: StateFlow<String> = userPreferences.themeMode
    val customFilter: StateFlow<String> = userPreferences.customFilter
    val overlayOpacity: StateFlow<Float> = userPreferences.overlayOpacity
    val backgroundColor: StateFlow<Int> = userPreferences.backgroundColor
    val fontSize: StateFlow<Int> = userPreferences.fontSize
    val fontFamily: StateFlow<String> = userPreferences.fontFamily
    val isRootEnabled: StateFlow<Boolean> = userPreferences.isRootEnabled
    val isLogReversed: StateFlow<Boolean> = userPreferences.isLogReversed
    val prohibitedTags: StateFlow<Set<String>> = userPreferences.prohibitedTags
    val logColors: StateFlow<Map<LogLevel, Color>> = userPreferences.logColors
    val showTimestamp: StateFlow<Boolean> = userPreferences.showTimestamp
    val bufferSize: StateFlow<Int> = userPreferences.bufferSize
    val activeLogLevels: StateFlow<Set<String>> = userPreferences.activeLogLevels
    val colorScheme: StateFlow<LogColorScheme> = userPreferences.colorScheme
    val tagColoringEnabled: StateFlow<Boolean> = userPreferences.tagColoringEnabled
    val autoDeleteDurationDays: StateFlow<Int> = userPreferences.autoDeleteDurationDays
    val maxTotalLogSizeMegabytes: StateFlow<Int> = userPreferences.maxTotalLogSizeMegabytes

    // GitHub Actions config. Repo coordinates are non-secret (UserPreferences); the PAT lives in the
    // backup-excluded secure store. Only a boolean is exposed reactively — the decrypted secret is
    // produced on demand via [readGithubToken] (called off the main thread by the feature panel).
    val githubOwner: StateFlow<String> = userPreferences.githubOwner
    val githubRepo: StateFlow<String> = userPreferences.githubRepo
    val hasGithubToken: StateFlow<Boolean> = githubCredentials.hasToken
    fun setGithubOwner(owner: String) = userPreferences.setGithubOwner(owner)
    fun setGithubRepo(repo: String) = userPreferences.setGithubRepo(repo)

    /** Stores/clears the PAT off the main thread (AES/GCM + Keystore work). */
    fun setGithubToken(token: String?) {
        viewModelScope.launch(Dispatchers.IO) { githubCredentials.setToken(token) }
    }

    fun setAutoDeleteDurationDays(days: Int) = userPreferences.setAutoDeleteDurationDays(days)
    fun setMaxTotalLogSizeMegabytes(mb: Int) = userPreferences.setMaxTotalLogSizeMegabytes(mb)


    /** Decrypts the PAT on demand. Call off the main thread (it touches the Keystore). */
    fun readGithubToken(): String? = githubCredentials.readToken()

    /**
     * Per-tab "cleared" baseline. When the user clears a single tab we record the size of the
     * underlying log buffer at that moment and skip everything before it for that tab only.
     */
    private val _tabClearMarks = MutableStateFlow<Map<String, Long>>(emptyMap())

    // Tab Management
    private val systemTab = LogTab("system", application.getString(com.hereliesaz.logkitty.R.string.tab_all), TabType.SYSTEM)
    private val errorsTab = LogTab("errors", application.getString(com.hereliesaz.logkitty.R.string.tab_errors), TabType.ERRORS)
    // Always-present GitHub Actions tab; its content is the on-demand :feature:github panel, not the
    // logcat stream, so it carries no log filter (see the TabType.GITHUB branch below).
    private val githubTab = LogTab("github", application.getString(com.hereliesaz.logkitty.R.string.tab_github), TabType.GITHUB)

    private val _tabs = MutableStateFlow(listOf(systemTab, errorsTab, githubTab))
    val tabs: StateFlow<List<LogTab>> = _tabs

    private val _selectedTab = MutableStateFlow(systemTab)
    val selectedTab: StateFlow<LogTab> = _selectedTab

    private data class FilterInputs(
        val tab: LogTab,
        val userFilter: String,
        val prohibited: Set<String>,
        val levels: Set<String>,
        val marks: Map<String, Long>,
        val isContextMode: Boolean,
        val isHardContextMode: Boolean,
        val currentFgApp: String?
    )

    /**
     * Indexed, filtered view of the log stream for the current tab.
     * Each entry preserves its global id so the UI can stably select / copy / prohibit it.
     */
    val filteredIndexedLog: StateFlow<List<IndexedLogLine>> = run {
        val inputs = combine(
            _selectedTab, customFilter, prohibitedTags, activeLogLevels, _tabClearMarks,
            isContextModeEnabled, isHardContextMode, _currentForegroundApp
        ) { args -> 
            FilterInputs(
                tab = args[0] as LogTab,
                userFilter = args[1] as String,
                prohibited = args[2] as Set<String>,
                levels = args[3] as Set<String>,
                marks = args[4] as Map<String, Long>,
                isContextMode = args[5] as Boolean,
                isHardContextMode = args[6] as Boolean,
                currentFgApp = args[7] as String?
            )
        }
        combine(stateDelegate.systemLog, inputs) { logs, input ->
            val clearMark = input.marks[input.tab.id] ?: 0L
            var result: List<IndexedLogLine> = if (clearMark > 0L) logs.filter { it.id > clearMark } else logs

            if (input.levels.size < LogLevel.values().size) {
                result = result.filter { line -> input.levels.contains(LogLevel.fromLine(line.text).name) }
            }
            if (input.prohibited.isNotEmpty()) {
                // Hide by *tag* match (not arbitrary substring) — see [LogTagFilter.isProhibited].
                result = result.filter { line -> !LogTagFilter.isProhibited(line.text, input.prohibited) }
            }

            // Apply Context Mode (if enabled)
            if (input.isContextMode && input.isHardContextMode && !input.currentFgApp.isNullOrBlank()) {
                val targetUid = uidFor(input.currentFgApp)
                result = result.filter { line ->
                    if (targetUid != null && line.uid != null) line.uid == targetUid
                    else line.text.contains(input.currentFgApp, ignoreCase = true)
                }
            }

            when (input.tab.type) {
                TabType.SYSTEM -> { }
                TabType.ERRORS -> result = result.filter {
                    val lvl = LogLevel.fromLine(it.text)
                    lvl == LogLevel.ERROR || lvl == LogLevel.ASSERT
                }
                TabType.APP -> {
                    val pkg = input.tab.filterValue
                    if (!pkg.isNullOrBlank()) {
                        val targetUid = uidFor(pkg)
                        result = result.filter { line ->
                            // Use reliable UID matching when both the target UID and the line's UID
                            // are known (uid-annotated stream); otherwise fall back to package-name
                            // substring matching — e.g. when logcat is running in the plain
                            // `-v time` fallback format and lines carry no UID.
                            if (targetUid != null && line.uid != null) line.uid == targetUid
                            else line.text.contains(pkg, ignoreCase = true)
                        }
                    }
                }
                TabType.APP_STATS -> result = emptyList()
                TabType.SOURCE -> {
                    val key = input.tab.filterValue
                    if (!key.isNullOrBlank()) {
                        result = result.filter { sourceClassifier.classify(it.uid).contains(key) }
                    }
                }
                // The GitHub tab isn't backed by the logcat stream; its body is the feature panel.
                TabType.GITHUB -> result = emptyList()
            }
            if (input.userFilter.isNotBlank()) {
                result = result.filter { it.text.contains(input.userFilter, ignoreCase = true) }
            }

            // Evidence-Based Visibility: the main view only shows significant events.
            result = result.filter { isSignificantEvent(it) }

            result
        }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }

    private fun isSignificantEvent(line: IndexedLogLine): Boolean {
        val level = LogLevel.fromLine(line.text)
        if (level == LogLevel.ERROR || level == LogLevel.WARNING || level == LogLevel.ASSERT) return true
        val textLower = line.text.lowercase()
        // Network calls
        if (textLower.contains("retrofit") || textLower.contains("okhttp") || 
            textLower.contains("socket") || textLower.contains("http") || textLower.contains("network")) return true
        // Lifecycle events
        if (textLower.contains("oncreate") || textLower.contains("onresume") || 
            textLower.contains("activitymanager") || textLower.contains("onstart")) return true
        return false
    }

    /**
     * Backward-compatible textual view retained for callers (e.g. FileSaverActivity).
     */
    val filteredSystemLog: StateFlow<List<String>> = filteredIndexedLog
        .map { list -> list.map { it.text } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var activeSessionPackage: String? = null
    private var activeSessionUid: Int? = null
    private val _sessionFinishedChannel = kotlinx.coroutines.channels.Channel<java.io.File>(kotlinx.coroutines.channels.Channel.BUFFERED)
    val sessionFinishedEvent = _sessionFinishedChannel.receiveAsFlow()

    private val _attentionColor = MutableStateFlow<androidx.compose.ui.graphics.Color?>(null)
    val attentionColor = _attentionColor.asStateFlow()

    // --- Accessibility Receiver ---
    // Listens for foreground-app broadcasts from ForegroundAppMonitor (Context Mode).
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AccessibilityActions.ACTION_FOREGROUND_APP_CHANGED) {
                val pkg = intent.getStringExtra(AccessibilityActions.EXTRA_PACKAGE_NAME)
                _currentForegroundApp.value = pkg

                // Automatically add a tab for the current app if desired.
                if (!pkg.isNullOrBlank()) {
                    addAppTab(pkg)
                }

                // Session Management
                val oldPkg = activeSessionPackage
                if (oldPkg != pkg) {
                    // Close old session if it was monitored
                    if (oldPkg != null && userPreferences.monitoredApps.value.contains(oldPkg)) {
                        viewModelScope.launch(Dispatchers.IO) {
                            val file = com.hereliesaz.logkitty.data.SessionLogFileWriter.closeSession(oldPkg)
                            if (file != null) {
                                _sessionFinishedChannel.trySend(file)
                            }
                        }
                    }

                    // Open new session if it is monitored
                    if (pkg != null && userPreferences.monitoredApps.value.contains(pkg)) {
                        viewModelScope.launch(Dispatchers.IO) {
                            com.hereliesaz.logkitty.data.SessionLogFileWriter.openSession(getApplication(), pkg)
                            activeSessionUid = uidFor(pkg)
                        }
                    } else {
                        activeSessionUid = null
                    }
                    activeSessionPackage = pkg
                }
            }
        }
    }

    fun setAttentionColor(color: androidx.compose.ui.graphics.Color?) {
        _attentionColor.value = color
    }

    private var logJob: Job? = null
    private var captureJob: Job? = null
    private var receiverRegistered = false

    init {
        // Session log writing
        viewModelScope.launch(Dispatchers.IO) {
            stateDelegate.newLinesEvent.collect { newLines ->
                val pkg = activeSessionPackage
                val targetUid = activeSessionUid
                if (pkg != null && targetUid != null) {
                    val relevantLogs = newLines.filter { it.uid == targetUid || it.text.contains(pkg, ignoreCase = true) }
                    for (line in relevantLogs) {
                        com.hereliesaz.logkitty.data.SessionLogFileWriter.appendLog(pkg, line.text)
                    }
                }
            }
        }

        // Keep pinned, app-specific tabs in sync with the user's "Monitor specific apps" choices.
        // Collected on IO because syncMonitoredTabs resolves app labels/UIDs via PackageManager.
        viewModelScope.launch(Dispatchers.IO) {
            userPreferences.monitoredApps.collect { syncMonitoredTabs(it) }
        }

        // Keep the per-source tabs in sync with the user's "Log sources" choices.
        viewModelScope.launch {
            userPreferences.activeSourceFilters.collect { syncSourceTabs(it) }
        }

        // Push target apps to StateDelegate
        viewModelScope.launch {
            targetApps.collect { (uids, pkgs) ->
                stateDelegate.setTargetApps(uids, pkgs)
            }
        }
    }

    /**
     * Starts the logcat capture pipeline and the foreground-app receiver. Idempotent.
     *
     * Capture used to run for the entire process lifetime (launched eagerly in `init`), draining
     * battery and holding a registered receiver even when nothing displayed logs. It's now started
     * by the only consumer of the live stream — [com.hereliesaz.logkitty.services.LogKittyOverlayService]
     * — when the overlay comes up, and torn down in [stopCapture] when it goes away.
     */
    fun startCapture() {
        if (captureJob != null) return
        registerForegroundReceiver()
        // Observe Root Mode toggle: when it changes, restart the reader with the new privileges.
        captureJob = viewModelScope.launch {
            isRootEnabled.collect { useRoot ->
                logJob?.cancel() // Stop existing reader
                stateDelegate.clearLog() // Clear old logs (optional, but cleaner)
                logJob = launch {
                    LogcatReader.observe(useRoot).collect {
                        if (!_isPaused.value) stateDelegate.appendSystemLog(it)
                    }
                }
            }
        }
    }

    /** Stops capture and unregisters the foreground-app receiver when no consumer needs the stream. */
    fun stopCapture() {
        captureJob?.cancel() // also cancels the child logJob
        captureJob = null
        logJob = null
        unregisterForegroundReceiver()
    }

    private fun registerForegroundReceiver() {
        if (receiverRegistered) return
        ContextCompat.registerReceiver(
            getApplication(),
            receiver,
            IntentFilter(AccessibilityActions.ACTION_FOREGROUND_APP_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
    }

    private fun unregisterForegroundReceiver() {
        if (!receiverRegistered) return
        try {
            getApplication<Application>().unregisterReceiver(receiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        receiverRegistered = false
    }

    /**
     * Reconciles the pinned app tabs with the set of [packages] the user is monitoring: adds a
     * pinned tab for each new package, promotes any matching transient (foreground) tab, and
     * removes pinned tabs that are no longer monitored.
     */
    private fun syncMonitoredTabs(packages: Set<String>) {
        // Resolve labels/UIDs up front (this runs off the main thread); uidFor also primes the
        // cache so the hot filter path never touches PackageManager on the main thread.
        val labels = packages.associateWith { pkg -> appLabel(pkg).also { uidFor(pkg) } }
        _tabs.update { current ->
            val kept = current.filterNot { it.pinned && it.type == TabType.APP && it.filterValue !in packages }
            val result = kept.toMutableList()
            packages.forEach { pkg ->
                val title = labels[pkg] ?: pkg
                val logsId = "app_logs_$pkg"
                
                val logsIdx = result.indexOfFirst { it.id == logsId }
                if (logsIdx < 0) {
                    result.add(LogTab(logsId, title, TabType.APP, pkg, pinned = true))
                } else if (!result[logsIdx].pinned) {
                    result[logsIdx] = result[logsIdx].copy(pinned = true, title = title)
                }
            }
            result
        }
        if (_tabs.value.none { it.id == _selectedTab.value.id }) {
            _selectedTab.value = _tabs.value.firstOrNull() ?: systemTab
        }
    }

    /**
     * Reconciles the per-source tabs (id prefix `source_`) with the user's enabled source filters,
     * preserving order: Sources first, then Categories, matching the settings layout.
     */
    private fun syncSourceTabs(filters: Set<String>) {
        _tabs.update { current ->
            val withoutSources = current.filterNot { it.id.startsWith("source_") }
            val ordered = (LogSources.SOURCE_BUCKETS + LogSources.CATEGORY_BUCKETS).filter { it in filters }
            val sourceTabs = ordered.map { key ->
                LogTab("source_$key", LogSources.label(key), TabType.SOURCE, key)
            }
            withoutSources + sourceTabs
        }
        if (_tabs.value.none { it.id == _selectedTab.value.id }) {
            _selectedTab.value = _tabs.value.firstOrNull() ?: systemTab
        }
    }

    /**
     * Adds a transient tab for a foreground application package (auto-created via accessibility).
     * Runs on the main thread (broadcast receiver), so it intentionally uses the package name as the
     * title rather than a blocking PackageManager label lookup.
     */
    private fun addAppTab(pkg: String) {
        _tabs.update { currentTabs ->
            val logsId = "app_logs_$pkg"
            if (currentTabs.any { it.id == logsId }) {
                currentTabs // Tab already exists (transient or pinned)
            } else {
                currentTabs + listOf(
                    LogTab(logsId, pkg, TabType.APP, pkg)
                )
            }
        }
    }

    fun selectTab(tab: LogTab) {
        _selectedTab.value = tab
    }

    /** Move selection one tab forward / back, wrapping at the ends. */
    fun selectNextTab() = shiftSelection(+1)
    fun selectPreviousTab() = shiftSelection(-1)

    private fun shiftSelection(delta: Int) {
        val list = _tabs.value
        if (list.isEmpty()) return
        val idx = list.indexOf(_selectedTab.value).takeIf { it >= 0 } ?: 0
        val newIdx = ((idx + delta) % list.size + list.size) % list.size
        _selectedTab.value = list[newIdx]
    }

    fun closeTab(tab: LogTab) {
        if (tab.type == TabType.APP) {
            val pkg = tab.filterValue
            if (pkg != null) {
                if (tab.pinned) {
                    userPreferences.removeMonitoredApp(pkg)
                } else {
                    val logsId = "app_logs_$pkg"
                    _tabs.update { current -> current.filterNot { it.id == logsId } }
                    if (_selectedTab.value.id == logsId) {
                        _selectedTab.value = _tabs.value.firstOrNull() ?: systemTab
                    }
                }
                _tabClearMarks.update { it - "app_logs_$pkg" }
            }
        }
    }

    /**
     * Clears only the active tab by recording the current max id as that tab's "skip line".
     * Other tabs continue to see historical data.
     */
    fun clearActiveTab() {
        val tabId = _selectedTab.value.id
        _tabClearMarks.update { it + (tabId to stateDelegate.currentMaxId) }
    }

    override fun onCleared() {
        super.onCleared()
        stopCapture()
    }

    // --- Actions Delegate to Data Layer ---

    /** Clears the entire shared log buffer (all tabs). */
    fun clearLog() {
        stateDelegate.clearLog()
        _tabClearMarks.value = emptyMap()
    }

    fun setTagColoringEnabled(enabled: Boolean) {
        userPreferences.setTagColoringEnabled(enabled)
    }

    fun toggleContextMode() {
        userPreferences.setContextModeEnabled(!isContextModeEnabled.value)
    }

    fun setHardContextMode(enabled: Boolean) {
        userPreferences.setHardContextMode(enabled)
    }

    fun setThemeMode(mode: String) {
        userPreferences.setThemeMode(mode)
    }

    fun setCustomFilter(filter: String) {
        userPreferences.setCustomFilter(filter)
    }

    fun setOverlayOpacity(opacity: Float) {
        userPreferences.setOverlayOpacity(opacity)
    }

    fun setBackgroundColor(color: Int) {
        userPreferences.setBackgroundColor(color)
    }

    fun setFontSize(size: Int) {
        userPreferences.setFontSize(size)
    }

    fun setFontFamily(font: CodingFont) {
        userPreferences.setFontFamily(font.name)
    }

    fun setRootEnabled(enabled: Boolean) {
        userPreferences.setRootEnabled(enabled)
    }

    fun setLogReversed(enabled: Boolean) {
        userPreferences.setLogReversed(enabled)
    }
    
    fun setShowTimestamp(enabled: Boolean) {
        userPreferences.setShowTimestamp(enabled)
    }
    
    fun setBufferSize(size: Int) {
        userPreferences.setBufferSize(size)
    }
    
    fun toggleLogLevel(level: LogLevel, enabled: Boolean) {
        userPreferences.toggleLogLevel(level.name, enabled)
    }

    fun setLogColor(level: LogLevel, color: Color) {
        userPreferences.setLogColor(level, color)
    }

    fun resetLogColors() {
        userPreferences.resetLogColors()
    }

    fun prohibitLog(logLine: String) {
        val tag = LogLevel.tagFromLine(logLine)
            ?: logLine.split(":").firstOrNull()?.takeLast(20)?.trim()

        if (!tag.isNullOrBlank()) {
            userPreferences.addProhibitedTag(tag)
        }
    }

    fun removeProhibitedTag(tag: String) {
        userPreferences.removeProhibitedTag(tag)
    }

    fun exportPreferences() = userPreferences.exportPreferences()
    fun importPreferences(json: String) = userPreferences.importPreferences(json)

    fun setColorScheme(scheme: com.hereliesaz.logkitty.ui.LogColorScheme) {
        userPreferences.setColorScheme(scheme)
    }
}

package com.local.mewgenicsradio

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RadioViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val appContext = application.applicationContext
    private val cacheManager = RadioCacheManager(File(appContext.filesDir, "radio-cache"))
    private val resolver = RadioAssetResolver(cacheManager)
    private val player = RadioPlayer(
        context = appContext,
        onEnded = {
            consecutiveFailures = 0  // 成功播放完，重置计数器
            playNext()
        },
        onError = { error ->
            consecutiveFailures++
            if (consecutiveFailures >= maxConsecutiveFailures) {
                // 连续失败太多次，停止播放并显示错误
                _uiState.value = _uiState.value.copy(
                    isPlaying = false,
                    message = "Too many playback failures: ${error.message ?: "check network/cache"}",
                )
                consecutiveFailures = 0  // 重置计数器
            } else {
                // 自动跳过失败的曲目
                _uiState.value = _uiState.value.copy(
                    message = "Skipping failed track ($consecutiveFailures/$maxConsecutiveFailures): ${error.message ?: "unknown error"}",
                )
                playNext()
            }
        },
    )

    private var scheduler: RadioScheduler? = null
    private var catalog: RadioAssetCatalog? = null
    private var manifest: RemoteRadioManifest? = null
    private var consecutiveFailures = 0
    private val maxConsecutiveFailures = 5

    init {
        viewModelScope.launch {
            load()
        }
    }

    fun togglePlayback() {
        if (scheduler == null) {
            viewModelScope.launch { load() }
            return
        }

        val current = _uiState.value.currentSegment
        if (current == null) {
            playNext()
            return
        }

        player.togglePlayback()
        _uiState.value = _uiState.value.copy(isPlaying = player.isPlaying)
    }

    fun playNext() {
        viewModelScope.launch {
            val next = scheduler?.next(_uiState.value.mode)
            if (next == null) {
                _uiState.value = _uiState.value.copy(
                    isPlaying = false,
                    message = "No playable radio assets were found.",
                )
                return@launch
            }

            val resolved = withContext(Dispatchers.IO) {
                resolver.resolve(next)
            }

            if (resolved == null) {
                _uiState.value = _uiState.value.copy(
                    isPlaying = false,
                    currentSegment = next,
                    currentSource = PlaybackSource.Missing,
                    message = "Track is missing locally and remotely.",
                )
                return@launch
            }

            player.play(resolved)
            consecutiveFailures = 0  // 开始播放新曲目，重置失败计数器
            _uiState.value = _uiState.value.copy(
                isReady = true,
                isPlaying = true,
                currentSegment = next,
                currentSource = resolved.source,
                cacheBytes = cacheManager.cacheSizeBytes(),
                message = "Playing ${resolved.source.name}",
            )

            val remote = next.track.remote
            if (resolved.source == PlaybackSource.Remote && remote != null) {
                cacheInBackground(listOf(remote), "Caching current track")
            }
        }
    }

    fun setMode(mode: PlaybackMode) {
        _uiState.value = _uiState.value.copy(mode = mode)
        if (_uiState.value.isPlaying) {
            playNext()
        }
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            cacheManager.clear()
            updateCacheState("Cache cleared")
        }
    }

    fun cacheAllSongs() {
        val tracks = catalog?.allTracks
            .orEmpty()
            .filter { it.category == "songs" }
            .mapNotNull { it.remote }
        cacheInBackground(tracks, "Caching songs")
    }

    fun cacheAllRadio() {
        val tracks = catalog?.allTracks
            .orEmpty()
            .mapNotNull { it.remote }
        cacheInBackground(tracks, "Caching radio")
    }

    private suspend fun load() {
        try {
            val repository = RadioRepository(appContext)
            val loadedManifest = withContext(Dispatchers.IO) {
                val remoteUrl = BuildConfig.RADIO_MANIFEST_URL
                if (remoteUrl.isNotBlank()) {
                    repository.loadRemoteManifest(remoteUrl)
                } else {
                    repository.loadManifest()
                }
            }
            val loadedConfig = withContext(Dispatchers.IO) {
                repository.loadConfig(loadedManifest)
            }
            val loadedCatalog = withContext(Dispatchers.IO) {
                repository.loadCatalog(loadedManifest)
            }
            val visualizerStyle = withContext(Dispatchers.IO) {
                runCatching {
                    repository.loadVisualizerStyle(loadedManifest, cacheManager)
                }.getOrElse {
                    RadioVisualizerStyle()
                }
            }

            if (!loadedCatalog.hasAnyTracks()) {
                _uiState.value = _uiState.value.copy(
                    isReady = false,
                    message = "Radio audio assets are missing.",
                    visualizerStyle = visualizerStyle,
                )
                return
            }

            manifest = loadedManifest
            catalog = loadedCatalog
            scheduler = RadioScheduler(loadedConfig, loadedCatalog)
            _uiState.value = _uiState.value.copy(
                isReady = true,
                cacheBytes = cacheManager.cacheSizeBytes(),
                qualityLabel = loadedManifest?.qualityLabel ?: "Bundled Vorbis",
                visualizerStyle = visualizerStyle,
                message = if (loadedManifest == null) {
                    "Ready: bundled assets"
                } else {
                    "Ready: online cache"
                },
            )
        } catch (error: Throwable) {
            _uiState.value = _uiState.value.copy(
                isReady = false,
                message = error.message ?: "Could not load radio assets.",
            )
        }
    }

    private fun cacheInBackground(tracks: List<RemoteRadioTrack>, label: String) {
        if (tracks.isEmpty()) {
            _uiState.value = _uiState.value.copy(message = "No remote tracks to cache")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            var cached = 0
            tracks.forEach { track ->
                runCatching {
                    cacheManager.cacheTrack(track)
                    cached++
                }
                updateCacheState("$label: $cached/${tracks.size}")
            }
        }
    }

    private suspend fun updateCacheState(message: String) {
        val size = cacheManager.cacheSizeBytes()
        withContext(Dispatchers.Main) {
            _uiState.value = _uiState.value.copy(
                cacheBytes = size,
                message = message,
            )
        }
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }
}

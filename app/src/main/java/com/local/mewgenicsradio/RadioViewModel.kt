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
        onEnded = { playNext() },
        onError = { error ->
            recordError("Playback failed", error)
        },
    )

    private var scheduler: RadioScheduler? = null
    private var catalog: RadioAssetCatalog? = null
    private var manifest: RemoteRadioManifest? = null

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
                recordError("No playable radio assets were found.")
                return@launch
            }

            val resolved = withContext(Dispatchers.IO) {
                resolver.resolve(next)
            }

            if (resolved == null) {
                val message = "Track is missing locally and remotely."
                _uiState.value = withErrorLog(
                    state = _uiState.value.copy(
                        isPlaying = false,
                        currentSegment = next,
                        currentSource = PlaybackSource.Missing,
                        message = message,
                    ),
                    detail = message,
                )
                return@launch
            }

            player.play(resolved)
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

    fun cacheCurrentTrack() {
        val remote = _uiState.value.currentSegment?.track?.remote
        if (remote == null) {
            _uiState.value = _uiState.value.copy(message = "Current track is not remote-cacheable")
            return
        }
        cacheInBackground(listOf(remote), "Caching current track")
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
                val message = "Radio audio assets are missing."
                _uiState.value = withErrorLog(
                    state = _uiState.value.copy(
                        isReady = false,
                        message = message,
                        visualizerStyle = visualizerStyle,
                    ),
                    detail = message,
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
            recordError("Could not load radio assets", error)
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
                val result = runCatching {
                    cacheManager.cacheTrack(track)
                    cached++
                }
                result.exceptionOrNull()?.let { error ->
                    recordError("Cache failed for ${track.relativePath}", error)
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

    private fun recordError(label: String, error: Throwable? = null) {
        val detail = if (error == null) {
            label
        } else {
            "$label: ${error.message ?: error::class.java.simpleName}"
        }
        _uiState.value = withErrorLog(
            state = _uiState.value.copy(
                isPlaying = false,
                message = detail,
            ),
            detail = detail,
        )
    }

    private fun withErrorLog(state: PlayerUiState, detail: String): PlayerUiState {
        val nextLog = (state.errorLog + detail).takeLast(8)
        return state.copy(
            lastError = detail,
            errorLog = nextLog,
        )
    }
}

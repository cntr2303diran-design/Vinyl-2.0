package com.vinyl.launcher.ui.cover

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vinyl.launcher.data.AppSettings
import com.vinyl.launcher.data.CoverSettings
import com.vinyl.launcher.data.MusicSettings
import com.vinyl.launcher.data.SettingsRepository
import com.vinyl.launcher.media.MediaRepository
import com.vinyl.launcher.media.NowPlaying
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** ViewModel layar kunci vinyl; editor memakai draft dengan pratinjau langsung. */
class CoverViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SettingsRepository.get(app)

    val settings: StateFlow<AppSettings> = repo.settings
    val nowPlaying: StateFlow<NowPlaying> = MediaRepository.state

    /** Pengaturan musik (lirik online, warna sinematik) untuk layar kunci. */
    val music: StateFlow<MusicSettings> = repo.settings
        .map { it.music }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, MusicSettings())

    private val _draft = MutableStateFlow<CoverSettings?>(null)
    val draft: StateFlow<CoverSettings?> = _draft.asStateFlow()

    val cover: StateFlow<CoverSettings> = combine(settings, _draft) { s, d -> d ?: s.cover }
        .stateIn(viewModelScope, SharingStarted.Eagerly, CoverSettings())

    fun startEdit() { _draft.value = settings.value.cover }
    fun updateDraft(c: CoverSettings) = _draft.update { if (it != null) c else null }
    fun cancelEdit() { _draft.value = null }
    fun saveEdit() {
        val d = _draft.value ?: return
        viewModelScope.launch { repo.update { it.copy(cover = d) } }
        _draft.value = null
    }

    fun togglePlay() = MediaRepository.togglePlayPause()
}

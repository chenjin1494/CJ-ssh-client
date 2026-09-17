package io.github.chenjin.androidsshclient.feature.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chenjin.androidsshclient.core.model.AppSettings
import io.github.chenjin.androidsshclient.core.ssh.SshManager
import io.github.chenjin.androidsshclient.data.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val manager: SshManager,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    val tabs = manager.tabs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val selectedId = MutableStateFlow<String?>(null)

    fun send(id: String, text: String) = manager.send(id, text)
    fun close(id: String) = manager.closeTab(id)
    fun reconnect(id: String) = manager.reconnect(id)
    fun resize(id: String, columns: Int, rows: Int, width: Int, height: Int) = manager.resize(id, columns, rows, width, height)
}

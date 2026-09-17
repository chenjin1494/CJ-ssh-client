package io.github.chenjin.androidsshclient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chenjin.androidsshclient.core.model.AppSettings
import io.github.chenjin.androidsshclient.core.ssh.HostKeyPrompt
import io.github.chenjin.androidsshclient.core.ssh.SshManager
import io.github.chenjin.androidsshclient.data.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val sshManager: SshManager,
) : ViewModel() {
    val settings = settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    private val _hostPrompt = MutableStateFlow<HostKeyPrompt?>(null)
    val hostPrompt = _hostPrompt.asStateFlow()

    init { viewModelScope.launch { sshManager.prompts.collect { _hostPrompt.value = it } } }

    fun answerHostKey(accept: Boolean) {
        _hostPrompt.value?.let { sshManager.respondToHostKey(it.requestId, accept) }
        _hostPrompt.value = null
    }
}

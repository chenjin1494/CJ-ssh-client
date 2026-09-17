package io.github.chenjin.androidsshclient.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chenjin.androidsshclient.core.model.AppSettings
import io.github.chenjin.androidsshclient.data.SettingsRepository
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repository: SettingsRepository) : ViewModel() {
    val settings = repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun update(value: AppSettings) = viewModelScope.launch { repository.update(value) }

    fun importFont(name: String, input: InputStream) = viewModelScope.launch {
        runCatching { repository.importCustomFont(name, input) }
            .onSuccess { _message.value = "已导入并启用字体：${name.substringAfterLast('/')}" }
            .onFailure { _message.value = it.message ?: "无法读取该字体文件" }
    }

    fun removeCustomFont() = viewModelScope.launch {
        repository.removeCustomFont()
        _message.value = "已移除自定义字体"
    }

    fun showMessage(value: String) { _message.value = value }
    fun clearMessage() { _message.value = null }
}

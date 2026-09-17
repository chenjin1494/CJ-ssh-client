package io.github.chenjin.androidsshclient.feature.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chenjin.androidsshclient.core.model.ConnectionDraft
import io.github.chenjin.androidsshclient.core.model.ConnectionProfile
import io.github.chenjin.androidsshclient.core.model.SshKeyInfo
import io.github.chenjin.androidsshclient.core.ssh.SshManager
import io.github.chenjin.androidsshclient.data.ConnectionRepository
import io.github.chenjin.androidsshclient.data.KeyRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ConnectionsViewModel @Inject constructor(
    private val repository: ConnectionRepository,
    keyRepository: KeyRepository,
    private val sshManager: SshManager,
) : ViewModel() {
    val search = MutableStateFlow("")
    val connections = combine(repository.connections, search) { rows, query ->
        if (query.isBlank()) rows else rows.filter {
            it.name.contains(query, true) || it.host.contains(query, true) || it.username.contains(query, true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val keys = keyRepository.keys.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<SshKeyInfo>())

    fun save(draft: ConnectionDraft) = viewModelScope.launch { repository.save(draft) }
    fun delete(id: Long) = viewModelScope.launch { repository.delete(id) }
    fun delete(ids: Set<Long>) = viewModelScope.launch { ids.forEach { repository.delete(it) } }
    fun connect(profile: ConnectionProfile) = sshManager.connect(profile.id)
    suspend fun draft(id: Long) = repository.getDraft(id)
}

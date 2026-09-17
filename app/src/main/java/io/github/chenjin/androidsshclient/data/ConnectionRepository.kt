package io.github.chenjin.androidsshclient.data

import io.github.chenjin.androidsshclient.core.database.ConnectionDao
import io.github.chenjin.androidsshclient.core.database.ConnectionEntity
import io.github.chenjin.androidsshclient.core.database.SshKeyDao
import io.github.chenjin.androidsshclient.core.model.AuthType
import io.github.chenjin.androidsshclient.core.model.ConnectionCredentials
import io.github.chenjin.androidsshclient.core.model.ConnectionDraft
import io.github.chenjin.androidsshclient.core.model.ConnectionProfile
import io.github.chenjin.androidsshclient.core.security.SecretBox
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ConnectionRepository @Inject constructor(
    private val dao: ConnectionDao,
    private val keyDao: SshKeyDao,
    private val secretBox: SecretBox,
) {
    val connections: Flow<List<ConnectionProfile>> = dao.observeAll().map { rows -> rows.map { it.toProfile() } }

    suspend fun getDraft(id: Long): ConnectionDraft? = dao.get(id)?.let { row ->
        ConnectionDraft(
            id = row.id, name = row.name, host = row.host, port = row.port,
            username = row.username, authType = AuthType.valueOf(row.authType), keyId = row.keyId,
            autoReconnect = row.autoReconnect,
        )
    }

    suspend fun save(draft: ConnectionDraft): Long {
        require(draft.name.isNotBlank() && draft.host.isNotBlank() && draft.username.isNotBlank())
        require(draft.port in 1..65535)
        val previous = draft.id.takeIf { it != 0L }?.let { dao.get(it) }
        val password = draft.password.takeIf(String::isNotEmpty)?.encodeToByteArray()?.let(secretBox::seal)
        val passphrase = draft.keyPassphrase.takeIf(String::isNotEmpty)?.encodeToByteArray()?.let(secretBox::seal)
        return dao.upsert(
            ConnectionEntity(
                id = draft.id, name = draft.name.trim(), host = draft.host.trim(), port = draft.port,
                username = draft.username.trim(), authType = draft.authType.name, keyId = draft.keyId,
                passwordCipher = password?.ciphertext ?: previous?.passwordCipher,
                passwordIv = password?.iv ?: previous?.passwordIv,
                passphraseCipher = passphrase?.ciphertext ?: previous?.passphraseCipher,
                passphraseIv = passphrase?.iv ?: previous?.passphraseIv,
                autoReconnect = draft.autoReconnect, lastConnectedAt = previous?.lastConnectedAt,
            ),
        )
    }

    suspend fun credentials(id: Long): ConnectionCredentials {
        val row = requireNotNull(dao.get(id)) { "Connection not found" }
        val key = row.keyId?.let { keyDao.get(it) }
        return ConnectionCredentials(
            profile = row.toProfile(),
            password = decrypt(row.passwordCipher, row.passwordIv)?.decodeToString()?.toCharArray(),
            privateKey = key?.let { secretBox.open(it.privateCipher, it.privateIv) },
            keyPassphrase = decrypt(row.passphraseCipher, row.passphraseIv)?.decodeToString()?.toCharArray(),
        )
    }

    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun touch(id: Long) = dao.touch(id, System.currentTimeMillis())

    private fun decrypt(cipher: ByteArray?, iv: ByteArray?): ByteArray? =
        if (cipher != null && iv != null) secretBox.open(cipher, iv) else null

    private fun ConnectionEntity.toProfile() = ConnectionProfile(
        id, name, host, port, username, AuthType.valueOf(authType), keyId, autoReconnect, lastConnectedAt,
    )
}

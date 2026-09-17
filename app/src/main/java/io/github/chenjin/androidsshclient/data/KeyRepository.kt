package io.github.chenjin.androidsshclient.data

import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import io.github.chenjin.androidsshclient.core.database.SshKeyDao
import io.github.chenjin.androidsshclient.core.database.SshKeyEntity
import io.github.chenjin.androidsshclient.core.model.SshKeyInfo
import io.github.chenjin.androidsshclient.core.security.SecretBox
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class KeyRepository @Inject constructor(
    private val dao: SshKeyDao,
    private val secretBox: SecretBox,
) {
    val keys: Flow<List<SshKeyInfo>> = dao.observeAll().map { rows -> rows.map { it.info() } }

    suspend fun generate(name: String, type: String = "ED25519"): Long = withContext(Dispatchers.IO) {
        val kind = if (type == "RSA") KeyPair.RSA else KeyPair.ED25519
        val size = if (kind == KeyPair.RSA) 3072 else 256
        val pair = KeyPair.genKeyPair(JSch(), kind, size)
        try { store(name, pair) } finally { pair.dispose() }
    }

    suspend fun import(name: String, bytes: ByteArray, passphrase: CharArray?): Long = withContext(Dispatchers.IO) {
        val pair = KeyPair.load(JSch(), bytes, null)
        try {
            if (pair.isEncrypted) {
                val phrase = passphrase?.concatToString()?.encodeToByteArray()
                    ?: error("Private key passphrase required")
                require(pair.decrypt(phrase)) { "Private key passphrase is invalid" }
                phrase.fill(0)
            }
            store(name, pair)
        } finally { pair.dispose(); bytes.fill(0); passphrase?.fill('\u0000') }
    }

    suspend fun exportPublic(id: Long): String = requireNotNull(dao.get(id)).publicKey

    suspend fun delete(id: Long) {
        dao.get(id)?.let { dao.delete(it) }
    }

    private suspend fun store(name: String, pair: KeyPair): Long {
        val output = ByteArrayOutputStream()
        pair.writePrivateKey(output)
        val privateBytes = output.toByteArray()
        val sealed = secretBox.seal(privateBytes)
        privateBytes.fill(0)
        val algorithm = pair.keyTypeString
        val publicKey = "$algorithm ${Base64.getEncoder().encodeToString(pair.publicKeyBlob)} $name"
        return dao.insert(
            SshKeyEntity(
                name = name.trim(), algorithm = algorithm, publicKey = publicKey,
                privateCipher = sealed.ciphertext, privateIv = sealed.iv, createdAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun SshKeyEntity.info() = SshKeyInfo(id, name, algorithm, publicKey, createdAt)
}

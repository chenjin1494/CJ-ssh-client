package io.github.chenjin.androidsshclient.core.database

import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase

@Entity(tableName = "connections")
data class ConnectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val authType: String,
    val keyId: Long?,
    val passwordCipher: ByteArray?,
    val passwordIv: ByteArray?,
    val passphraseCipher: ByteArray?,
    val passphraseIv: ByteArray?,
    val autoReconnect: Boolean,
    val lastConnectedAt: Long?,
)

@Entity(tableName = "known_hosts", primaryKeys = ["host", "port"])
data class KnownHostEntity(
    val host: String,
    val port: Int,
    val algorithm: String,
    val publicKey: String,
    val fingerprint: String,
    val acceptedAt: Long,
)

@Entity(tableName = "ssh_keys")
data class SshKeyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val algorithm: String,
    val publicKey: String,
    val privateCipher: ByteArray,
    val privateIv: ByteArray,
    val createdAt: Long,
)

@Database(
    entities = [ConnectionEntity::class, KnownHostEntity::class, SshKeyEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun connectionDao(): ConnectionDao
    abstract fun knownHostDao(): KnownHostDao
    abstract fun keyDao(): SshKeyDao
}

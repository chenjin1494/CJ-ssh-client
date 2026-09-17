package io.github.chenjin.androidsshclient.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionDao {
    @Query("SELECT * FROM connections ORDER BY COALESCE(lastConnectedAt, 0) DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<ConnectionEntity>>

    @Query("SELECT * FROM connections WHERE id = :id")
    suspend fun get(id: Long): ConnectionEntity?

    @Upsert
    suspend fun upsert(entity: ConnectionEntity): Long

    @Query("DELETE FROM connections WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE connections SET lastConnectedAt = :timestamp WHERE id = :id")
    suspend fun touch(id: Long, timestamp: Long)
}

@Dao
interface KnownHostDao {
    @Query("SELECT * FROM known_hosts WHERE host = :host AND port = :port")
    suspend fun get(host: String, port: Int): KnownHostEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: KnownHostEntity)

    @Query("DELETE FROM known_hosts WHERE host = :host AND port = :port")
    suspend fun delete(host: String, port: Int)
}

@Dao
interface SshKeyDao {
    @Query("SELECT * FROM ssh_keys ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SshKeyEntity>>

    @Query("SELECT * FROM ssh_keys WHERE id = :id")
    suspend fun get(id: Long): SshKeyEntity?

    @Insert
    suspend fun insert(entity: SshKeyEntity): Long

    @Delete
    suspend fun delete(entity: SshKeyEntity)
}

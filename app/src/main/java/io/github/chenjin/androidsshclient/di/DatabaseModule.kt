package io.github.chenjin.androidsshclient.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.chenjin.androidsshclient.core.database.AppDatabase
import io.github.chenjin.androidsshclient.core.database.ConnectionDao
import io.github.chenjin.androidsshclient.core.database.KnownHostDao
import io.github.chenjin.androidsshclient.core.database.SshKeyDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "androidssh.db").build()

    @Provides fun connections(db: AppDatabase): ConnectionDao = db.connectionDao()
    @Provides fun knownHosts(db: AppDatabase): KnownHostDao = db.knownHostDao()
    @Provides fun keys(db: AppDatabase): SshKeyDao = db.keyDao()
}

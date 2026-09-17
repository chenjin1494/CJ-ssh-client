package io.github.chenjin.androidsshclient

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.chenjin.androidsshclient.core.ui.font.AppFonts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

@HiltAndroidApp
class AndroidSshApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val currentBc = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
        if (currentBc !is BouncyCastleProvider) {
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        }
        applicationScope.launch { AppFonts.initialize(this@AndroidSshApplication) }
    }
}

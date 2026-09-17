package io.github.chenjin.androidsshclient.core.logging

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureLogger @Inject constructor() {
    fun info(event: String, host: String? = null) = Log.i(TAG, message(event, host))
    fun warn(event: String, error: Throwable? = null) = Log.w(TAG, event, error?.redacted())
    fun error(event: String, error: Throwable? = null) = Log.e(TAG, event, error?.redacted())

    private fun message(event: String, host: String?): String =
        if (host == null) event else "$event host=${host.hashCode().toUInt().toString(16)}"

    private fun Throwable.redacted(): Throwable = SecurityException(
        this::class.java.simpleName + ": details redacted",
    )

    private companion object { const val TAG = "AndroidSSH" }
}

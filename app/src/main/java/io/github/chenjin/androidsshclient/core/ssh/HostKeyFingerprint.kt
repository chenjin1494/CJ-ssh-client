package io.github.chenjin.androidsshclient.core.ssh

import java.security.MessageDigest
import java.util.Base64

object HostKeyFingerprint {
    fun sha256(key: ByteArray): String = "SHA256:" + Base64.getEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(key))
}

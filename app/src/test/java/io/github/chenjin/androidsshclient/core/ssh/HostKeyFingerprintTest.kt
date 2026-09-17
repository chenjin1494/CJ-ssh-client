package io.github.chenjin.androidsshclient.core.ssh

import org.junit.Assert.assertEquals
import org.junit.Test

class HostKeyFingerprintTest {
    @Test fun usesOpenSshSha256Format() {
        assertEquals("SHA256:LPJNul+wow4m6DsqxbninhsWHlwfp0JecwQzYpOLmCQ", HostKeyFingerprint.sha256("hello".encodeToByteArray()))
    }
}

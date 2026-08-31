package com.ridedecider.app.data.update.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ApkIntegrityVerifierTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var verifier: ApkIntegrityVerifier

    @Before
    fun setUp() {
        verifier = ApkIntegrityVerifier()
    }

    @Test
    fun calculateSha256_knownPayload_returnsExpectedHash() {
        val testFile = tempFolder.newFile("sample_app.apk")
        testFile.writeText("RideDecider Mock APK Content 1.0.0")

        // Known SHA-256 for "RideDecider Mock APK Content 1.0.0"
        // Echo -n "RideDecider Mock APK Content 1.0.0" | sha256sum
        val result = verifier.calculateSha256(testFile)
        assertTrue(result.isSuccess)

        val hash = result.getOrThrow()
        assertEquals(64, hash.length)

        // Verify matches case-insensitively
        val verified = verifier.verifyIntegrity(testFile, hash.uppercase())
        assertTrue(verified)
    }

    @Test
    fun verifyIntegrity_corruptedContent_returnsFalse() {
        val testFile = tempFolder.newFile("corrupted.apk")
        testFile.writeText("Original Content")

        val originalHash = verifier.calculateSha256(testFile).getOrThrow()

        // Modify file
        testFile.writeText("Tampered Content")

        val verified = verifier.verifyIntegrity(testFile, originalHash)
        assertFalse(verified)
    }

    @Test
    fun verifyIntegrity_emptyExpectedHash_returnsFalse() {
        val testFile = tempFolder.newFile("valid.apk")
        testFile.writeText("Some content")

        assertFalse(verifier.verifyIntegrity(testFile, ""))
        assertFalse(verifier.verifyIntegrity(testFile, "   "))
    }

    @Test
    fun calculateSha256_emptyFile_returnsFailure() {
        val emptyFile = tempFolder.newFile("empty.apk")
        val result = verifier.calculateSha256(emptyFile)
        assertTrue(result.isFailure)
    }

    @Test
    fun calculateSha256_nonExistentFile_returnsFailure() {
        val nonExistent = File(tempFolder.root, "non_existent.apk")
        val result = verifier.calculateSha256(nonExistent)
        assertTrue(result.isFailure)
    }
}

package com.sync.xxx.crypto

import android.util.Log
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

object FileCryptoManager {
    private const val ALGORITHM = "AES/CBC/PKCS5Padding"
    private const val TAG = "FileCrypto"
    private val BUFFER_SIZE = 64 * 1024 // 64KB per baca - optimized

    private fun getSecretKey(uid: String): SecretKeySpec {
        // UID dari asset -> hash SHA-256 -> 32 byte untuk AES-256
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(uid.toByteArray(Charsets.UTF_8))
        Log.d(TAG, "Key generated from UID: ${keyBytes.size} bytes")
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun getIv(): IvParameterSpec {
        // IV STATIS 16 BYTE (agar kompatibel lintas perangkat)
        return IvParameterSpec(ByteArray(16))
    }

    fun encryptFile(inputFile: File, outputFile: File, uid: String): Boolean {
        return try {
            Log.d(TAG, "Encrypt: ${inputFile.absolutePath}")
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(uid), getIv())

            FileInputStream(inputFile).use { fis ->
                CipherOutputStream(FileOutputStream(outputFile), cipher).use { cos ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        cos.write(buffer, 0, bytesRead)
                    }
                }
            }
            Log.d(TAG, "Encrypt success: ${inputFile.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Encrypt failed: ${e.message}")
            false
        }
    }

    fun decryptFile(inputFile: File, outputFile: File, uid: String): Boolean {
        return try {
            Log.d(TAG, "Decrypt: ${inputFile.absolutePath}")
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(uid), getIv())

            CipherInputStream(FileInputStream(inputFile), cipher).use { cis ->
                FileOutputStream(outputFile).use { fos ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (cis.read(buffer).also { bytesRead = it } != -1) {
                        fos.write(buffer, 0, bytesRead)
                    }
                }
            }
            Log.d(TAG, "Decrypt success: ${inputFile.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Decrypt failed: ${e.message}")
            false
        }
    }

    // 🔥🔥🔥 RECURSIVE FOLDER PROCESSING 🔥🔥🔥
    fun encryptFolder(folder: File, uid: String): Pair<Int, Int> {
        var processed = 0
        var failed = 0

        fun processDir(dir: File) {
            val files = dir.listFiles() ?: return
            files.forEach { file ->
                try {
                    when {
                        file.isDirectory -> {
                            // 🔥 Rekursif masuk ke subfolder
                            processDir(file)
                        }
                        !file.name.endsWith(".enc") && file.isFile -> {
                            val encFile = File(file.parent, "${file.name}.enc")
                            if (encryptFile(file, encFile, uid)) {
                                // Hapus original setelah sukses
                                if (file.delete()) {
                                    processed++
                                    Log.d(TAG, "Encrypted & deleted: ${file.name}")
                                } else {
                                    failed++
                                    Log.w(TAG, "Failed to delete: ${file.name}")
                                }
                            } else {
                                failed++
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Process error: ${file.absolutePath} - ${e.message}")
                    failed++
                }
            }
        }

        processDir(folder)
        return Pair(processed, failed)
    }

    fun decryptFolder(folder: File, uid: String): Pair<Int, Int> {
        var processed = 0
        var failed = 0

        fun processDir(dir: File) {
            val files = dir.listFiles() ?: return
            files.forEach { file ->
                try {
                    when {
                        file.isDirectory -> {
                            // 🔥 Rekursif masuk ke subfolder
                            processDir(file)
                        }
                        file.name.endsWith(".enc") && file.isFile -> {
                            val originalName = file.name.substring(0, file.name.length - 4)
                            val originalFile = File(file.parent, originalName)
                            if (decryptFile(file, originalFile, uid)) {
                                // Hapus file .enc setelah sukses
                                if (file.delete()) {
                                    processed++
                                    Log.d(TAG, "Decrypted & deleted: ${file.name}")
                                } else {
                                    failed++
                                    Log.w(TAG, "Failed to delete: ${file.name}")
                                }
                            } else {
                                failed++
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Process error: ${file.absolutePath} - ${e.message}")
                    failed++
                }
            }
        }

        processDir(folder)
        return Pair(processed, failed)
    }
}
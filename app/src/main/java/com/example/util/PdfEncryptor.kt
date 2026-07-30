package com.example.util

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object PdfEncryptor {

    private val PADDING_STRING = byteArrayOf(
        0x28.toByte(), 0xBF.toByte(), 0x4E.toByte(), 0x5E.toByte(),
        0x4E.toByte(), 0x75.toByte(), 0x8A.toByte(), 0x41.toByte(),
        0x64.toByte(), 0x00.toByte(), 0x4E.toByte(), 0x56.toByte(),
        0xFF.toByte(), 0xFA.toByte(), 0x01.toByte(), 0x08.toByte(),
        0x2E.toByte(), 0x2E.toByte(), 0x00.toByte(), 0xB6.toByte(),
        0xD0.toByte(), 0x68.toByte(), 0x3E.toByte(), 0x80.toByte(),
        0x2F.toByte(), 0x0C.toByte(), 0xA9.toByte(), 0xFE.toByte(),
        0x64.toByte(), 0x53.toByte(), 0x69.toByte(), 0x7A.toByte()
    )

    private fun padPassword(password: String): ByteArray {
        val passBytes = password.toByteArray(Charsets.ISO_8859_1)
        val result = ByteArray(32)
        if (passBytes.size >= 32) {
            System.arraycopy(passBytes, 0, result, 0, 32)
        } else {
            System.arraycopy(passBytes, 0, result, 0, passBytes.size)
            System.arraycopy(PADDING_STRING, 0, result, passBytes.size, 32 - passBytes.size)
        }
        return result
    }

    private fun md5(vararg byteArrays: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("MD5")
        for (ba in byteArrays) {
            md.update(ba)
        }
        return md.digest()
    }

    private fun rc4Encrypt(key: ByteArray, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("ARCFOUR")
        val secretKey = SecretKeySpec(key, "ARCFOUR")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        return cipher.doFinal(data)
    }

    private fun computeOwnerValue(userPass: String, ownerPass: String): ByteArray {
        val userPadded = padPassword(userPass)
        val ownerPadded = padPassword(ownerPass.ifEmpty { userPass })

        var key = md5(ownerPadded)
        for (i in 0 until 50) {
            key = md5(key)
        }
        val key16 = key.copyOf(16)

        var encrypted = rc4Encrypt(key16, userPadded)
        for (i in 1..19) {
            val keyI = ByteArray(16)
            for (j in 0 until 16) {
                keyI[j] = (key16[j].toInt() xor i).toByte()
            }
            encrypted = rc4Encrypt(keyI, encrypted)
        }
        return encrypted
    }

    private fun computeEncryptionKey(
        userPass: String,
        oValue: ByteArray,
        pPermissions: Int,
        documentId: ByteArray
    ): ByteArray {
        val userPadded = padPassword(userPass)
        val pBytes = byteArrayOf(
            (pPermissions and 0xFF).toByte(),
            ((pPermissions shr 8) and 0xFF).toByte(),
            ((pPermissions shr 16) and 0xFF).toByte(),
            ((pPermissions shr 24) and 0xFF).toByte()
        )

        var hash = md5(userPadded, oValue, pBytes, documentId)
        for (i in 0 until 50) {
            hash = md5(hash)
        }
        return hash.copyOf(16)
    }

    private fun computeUserValue(encryptionKey: ByteArray, documentId: ByteArray): ByteArray {
        val hash = md5(PADDING_STRING, documentId)
        var encrypted = rc4Encrypt(encryptionKey, hash)
        for (i in 1..19) {
            val keyI = ByteArray(16)
            for (j in 0 until 16) {
                keyI[j] = (encryptionKey[j].toInt() xor i).toByte()
            }
            encrypted = rc4Encrypt(keyI, encrypted)
        }
        val uVal = ByteArray(32)
        System.arraycopy(encrypted, 0, uVal, 0, 16)
        System.arraycopy(PADDING_STRING, 0, uVal, 16, 16)
        return uVal
    }

    private fun computeObjectKey(fileKey: ByteArray, objNum: Int, genNum: Int): ByteArray {
        val b = ByteArray(fileKey.size + 5)
        System.arraycopy(fileKey, 0, b, 0, fileKey.size)
        b[fileKey.size] = (objNum and 0xFF).toByte()
        b[fileKey.size + 1] = ((objNum shr 8) and 0xFF).toByte()
        b[fileKey.size + 2] = ((objNum shr 16) and 0xFF).toByte()
        b[fileKey.size + 3] = (genNum and 0xFF).toByte()
        b[fileKey.size + 4] = ((genNum shr 8) and 0xFF).toByte()
        val hash = md5(b)
        val keyLen = minOf(fileKey.size + 5, 16)
        return hash.copyOf(keyLen)
    }

    private fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02X".format(it) }

    fun encryptPdfFile(inputFile: File, outputFile: File, password: String): Boolean {
        if (password.isEmpty()) return false
        try {
            val inputBytes = inputFile.readBytes()
            val pdfString = String(inputBytes, Charsets.ISO_8859_1)

            val pPermissions = -64
            val docId = md5("${inputFile.name}_${inputFile.length()}_${System.currentTimeMillis()}".toByteArray())

            val oValue = computeOwnerValue(password, password)
            val fileKey = computeEncryptionKey(password, oValue, pPermissions, docId)
            val uValue = computeUserValue(fileKey, docId)

            var maxObjId = 0
            val objRegex = Regex("(\\d+)\\s+(\\d+)\\s+obj")
            objRegex.findAll(pdfString).forEach { match ->
                val id = match.groupValues[1].toIntOrNull() ?: 0
                if (id > maxObjId) maxObjId = id
            }
            val encryptObjId = maxObjId + 1

            val encryptDict = "$encryptObjId 0 obj\n<<\n/Filter /Standard\n/V 2\n/R 3\n/Length 128\n/P $pPermissions\n/O <${toHex(oValue)}>\n/U <${toHex(uValue)}>\n>>\nendobj"

            val streamRegex = Regex("(\\d+)\\s+(\\d+)\\s+obj[\\s\\S]*?stream\\r?\\n", RegexOption.MULTILINE)
            val streamMatches = streamRegex.findAll(pdfString).toList()

            val os = FileOutputStream(outputFile)
            var inputIdx = 0

            for (match in streamMatches) {
                val objNum = match.groupValues[1].toInt()
                val genNum = match.groupValues[2].toInt()

                val streamHeaderEnd = match.range.last + 1
                val endStreamIndex = pdfString.indexOf("endstream", streamHeaderEnd)
                if (endStreamIndex == -1) continue

                var dataEnd = endStreamIndex
                if (dataEnd > 0 && inputBytes[dataEnd - 1] == '\n'.code.toByte()) dataEnd--
                if (dataEnd > 0 && inputBytes[dataEnd - 1] == '\r'.code.toByte()) dataEnd--

                // Write text up to start of stream data
                if (streamHeaderEnd > inputIdx) {
                    val textChunk = pdfString.substring(inputIdx, streamHeaderEnd)
                    os.write(textChunk.toByteArray(Charsets.ISO_8859_1))
                }

                // Encrypt stream bytes
                if (dataEnd > streamHeaderEnd) {
                    val plainStream = inputBytes.copyOfRange(streamHeaderEnd, dataEnd)
                    val objKey = computeObjectKey(fileKey, objNum, genNum)
                    val encStream = rc4Encrypt(objKey, plainStream)
                    os.write(encStream)
                }

                inputIdx = dataEnd
            }

            val remainingText = pdfString.substring(inputIdx)
            val updatedRemaining = remainingText.replace(
                "trailer\n<<",
                "$encryptDict\ntrailer\n<< /Encrypt $encryptObjId 0 R /ID [ <${toHex(docId)}> <${toHex(docId)}> ] "
            ).replace(
                "trailer\r\n<<",
                "$encryptDict\r\ntrailer\r\n<< /Encrypt $encryptObjId 0 R /ID [ <${toHex(docId)}> <${toHex(docId)}> ] "
            )

            os.write(updatedRemaining.toByteArray(Charsets.ISO_8859_1))
            os.flush()
            os.close()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}

package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

object OcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognizeTextFromPath(imagePath: String): String {
        if (imagePath.isEmpty() || !File(imagePath).exists()) return ""
        val bitmap = BitmapFactory.decodeFile(imagePath) ?: return ""
        return recognizeTextFromBitmap(bitmap)
    }

    suspend fun recognizeTextFromBitmap(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    continuation.resume(visionText.text)
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                    continuation.resume("")
                }
        } catch (e: Exception) {
            e.printStackTrace()
            continuation.resume("")
        }
    }
}

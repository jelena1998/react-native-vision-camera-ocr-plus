package com.rnvisioncameraocr

import android.net.Uri
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableNativeMap
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class PhotoRecognizerModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    init {
        instance = this
    }

    companion object {
        const val NAME = "PhotoRecognizerModule"
        private var instance: PhotoRecognizerModule? = null

        fun getContext(): ReactApplicationContext {
            return instance?.reactApplicationContext
                ?: throw IllegalStateException("PhotoRecognizerModule not initialized")
        }
    }

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    @ReactMethod
    fun process(uri: String, promise: Promise) {
        val parsedUri = Uri.parse(uri)
        val image = InputImage.fromFilePath(reactApplicationContext, parsedUri)
        val task: Task<Text> = recognizer.process(image)
        val data = WritableNativeMap()

        try {
            val text: Text = Tasks.await(task)
            if (text.text.isEmpty()) {
                promise.resolve(WritableNativeMap())
                return
            }

            data.putString("resultText", text.text)
            data.putArray("blocks", RNVisionCameraOCRPlugin.getBlocks(text.textBlocks))
            promise.resolve(data)
        } catch (e: Exception) {
            e.printStackTrace()
            promise.reject("Error", "Error processing image")
        }
    }

    override fun getName(): String = NAME
}
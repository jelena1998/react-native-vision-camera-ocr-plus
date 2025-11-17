package com.rnvisioncameraocr

import android.graphics.*
import android.util.Log
import android.util.Base64

import com.facebook.react.bridge.WritableNativeArray
import com.facebook.react.bridge.WritableNativeMap

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

import com.mrousavy.camera.frameprocessors.Frame
import com.mrousavy.camera.frameprocessors.FrameProcessorPlugin
import com.mrousavy.camera.frameprocessors.VisionCameraProxy
import java.io.File

class RNVisionCameraOCRPlugin(proxy: VisionCameraProxy, options: Map<String, Any>?) :
    FrameProcessorPlugin() {

    private var recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val latinOptions = TextRecognizerOptions.DEFAULT_OPTIONS
    private val chineseOptions = ChineseTextRecognizerOptions.Builder().build()
    private val devanagariOptions = DevanagariTextRecognizerOptions.Builder().build()
    private val japaneseOptions = JapaneseTextRecognizerOptions.Builder().build()
    private val koreanOptions = KoreanTextRecognizerOptions.Builder().build()

    init {
        val language = options?.get("language").toString()
        recognizer = when (language) {
            "latin" -> TextRecognition.getClient(latinOptions)
            "chinese" -> TextRecognition.getClient(chineseOptions)
            "devanagari" -> TextRecognition.getClient(devanagariOptions)
            "japanese" -> TextRecognition.getClient(japaneseOptions)
            "korean" -> TextRecognition.getClient(koreanOptions)
            else -> TextRecognition.getClient(latinOptions)
        }
    }

    override fun callback(frame: Frame, arguments: Map<String, Any>?): HashMap<String, Any?>? {
        val data = WritableNativeMap()

        val bitmap = BitmapUtils.getBitmap(frame)
   /*     val original = BitmapUtils.bitmap2Base64(bitmap)

        val cacheDir = PhotoRecognizerModule.getContext().getCacheDir()
        val fileName = "${System.currentTimeMillis()}.jpg"
        val path = BitmapUtils.saveImage(bitmap,cacheDir,fileName)
    */

        Log.d("OCRPlugin", "Original bitmap size: ${bitmap.width}x${bitmap.height}")
        Log.d("OCRPlugin", "arguments -> $arguments")

        val topLeft = arguments?.get("topLeft") as? Map<String, Any>
        val topRight = arguments?.get("topRight") as? Map<String, Any>
        val bottomRight = arguments?.get("bottomRight") as? Map<String, Any>
        val bottomLeft = arguments?.get("bottomLeft") as? Map<String, Any>

         if (topLeft == null || topRight == null || bottomRight == null || bottomLeft == null)
            return null

         val croppedBitmap = try {
                val polygonPoints = listOf(
                    PointF((topLeft["x"] as Number).toFloat(), (topLeft["y"] as Number).toFloat()),
                    PointF((topRight["x"] as Number).toFloat(), (topRight["y"] as Number).toFloat()),
                    PointF((bottomRight["x"] as Number).toFloat(), (bottomRight["y"] as Number).toFloat()),
                    PointF((bottomLeft["x"] as Number).toFloat(), (bottomLeft["y"] as Number).toFloat())
                )

                cropPolygon(bitmap, polygonPoints)
            } catch (e: Exception) {
                Log.e("OCRPlugin", "❌ Error parsing points: ${e.message}")
                bitmap
            }

 /*       val cacheDir = PhotoRecognizerModule.getContext().getCacheDir()
        val fileName = "${System.currentTimeMillis()}.jpg"
        val path = BitmapUtils.saveImage(croppedBitmap,cacheDir,fileName)
*/
        val inputImage = InputImage.fromBitmap(croppedBitmap, 0)
        val task: Task<Text> = recognizer.process(inputImage)

        return try {
            val text: Text = Tasks.await(task)
            if (text.text.isEmpty()) {
                return WritableNativeMap().toHashMap()
            }
            data.putString("resultText", text.text)
            data.putArray("blocks", getBlocks(text.textBlocks))
            data.toHashMap()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun cropPolygon(original: Bitmap, points: List<PointF>): Bitmap {
        require(points.size == 4) { "Poligon mora imati tačno 4 tačke." }

        val output = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            lineTo(points[1].x, points[1].y)
            lineTo(points[2].x, points[2].y)
            lineTo(points[3].x, points[3].y)
            close()
        }

        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        paint.color = Color.WHITE
        canvas.drawPath(path, paint)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(original, 0f, 0f, paint)
        paint.xfermode = null

        val bounds = RectF()
        path.computeBounds(bounds, true)
        return Bitmap.createBitmap(
            output,
            bounds.left.toInt(),
            bounds.top.toInt(),
            bounds.width().toInt(),
            bounds.height().toInt()
        )
    }

    companion object {
        fun getBlocks(blocks: MutableList<Text.TextBlock>): WritableNativeArray {
            val blockArray = WritableNativeArray()
            blocks.forEach { block ->
                val blockMap = WritableNativeMap().apply {
                    putString("blockText", block.text)
                    putArray("blockCornerPoints", block.cornerPoints?.let { getCornerPoints(it) })
                    putMap("blockFrame", getFrame(block.boundingBox))
                    putArray("lines", getLines(block.lines))
                }
                blockArray.pushMap(blockMap)
            }
            return blockArray
        }

        private fun getLines(lines: MutableList<Text.Line>): WritableNativeArray {
            val lineArray = WritableNativeArray()
            lines.forEach { line ->
                val lineMap = WritableNativeMap().apply {
                    putString("lineText", line.text)
                    putArray("lineCornerPoints", line.cornerPoints?.let { getCornerPoints(it) })
                    putMap("lineFrame", getFrame(line.boundingBox))
                    putArray(
                        "lineLanguages",
                        WritableNativeArray().apply { pushString(line.recognizedLanguage) })
                    putArray("elements", getElements(line.elements))
                }
                lineArray.pushMap(lineMap)
            }
            return lineArray
        }

        private fun getElements(elements: MutableList<Text.Element>): WritableNativeArray {
            val elementArray = WritableNativeArray()
            elements.forEach { element ->
                val elementMap = WritableNativeMap().apply {
                    putString("elementText", element.text)
                    putArray(
                        "elementCornerPoints",
                        element.cornerPoints?.let { getCornerPoints(it) })
                    putMap("elementFrame", getFrame(element.boundingBox))
                }
                elementArray.pushMap(elementMap)
            }
            return elementArray
        }

        private fun getCornerPoints(points: Array<Point>): WritableNativeArray {
            val cornerPoints = WritableNativeArray()
            points.forEach { point ->
                cornerPoints.pushMap(WritableNativeMap().apply {
                    putInt("x", point.x)
                    putInt("y", point.y)
                })
            }
            return cornerPoints
        }

        private fun getFrame(boundingBox: Rect?): WritableNativeMap {
            return WritableNativeMap().apply {
                boundingBox?.let {
                    putDouble("x", it.exactCenterX().toDouble())
                    putDouble("y", it.exactCenterY().toDouble())
                    putInt("width", it.width())
                    putInt("height", it.height())
                    putInt("boundingCenterX", it.centerX())
                    putInt("boundingCenterY", it.centerY())
                }
            }
        }
    }
}
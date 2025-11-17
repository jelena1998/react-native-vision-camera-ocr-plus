package com.rnvisioncameraocr

import android.annotation.TargetApi
import android.graphics.*
import android.media.Image
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.annotation.Nullable
import androidx.annotation.RequiresApi
import com.mrousavy.camera.core.FrameInvalidError
import com.mrousavy.camera.frameprocessors.Frame
import com.mrousavy.camera.core.types.Orientation
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer

object BitmapUtils {
    private const val TAG = "BitmapUtils"

    /** Converts NV21 format byte buffer to bitmap. */
    @Nullable
    fun getBitmap(data: ByteBuffer, metadata: FrameMetadata): Bitmap? {
        data.rewind()
        val imageInBuffer = ByteArray(data.limit())
        data.get(imageInBuffer, 0, imageInBuffer.size)
        return try {
            val yuvImage = YuvImage(
                imageInBuffer,
                ImageFormat.NV21,
                metadata.width,
                metadata.height,
                null
            )
            val stream = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, metadata.width, metadata.height), 80, stream)
            val bmp = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size())
            stream.close()
            rotateBitmap(bmp, metadata.rotation, false, false)
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
            null
        }
    }

    /** Converts a YUV_420_888 image from Vision Camera API to a bitmap. */
    @Throws(FrameInvalidError::class)
    fun getBitmap(frame: Frame): Bitmap {
     //   val degree = getRotationDegreeFromOrientation(frame.orientation)
        val frameMetadata = FrameMetadata.Builder()
            .setWidth(frame.width)
            .setHeight(frame.height)
     //       .setRotation(degree)
            .build()

        val nv21Buffer = yuv420ThreePlanesToNV21(frame.image.planes, frame.width, frame.height)
        return getBitmap(nv21Buffer, frameMetadata)!!
    }

    fun getRotationDegreeFromOrientation(orientation: Orientation): Int {
        return when (orientation) {
            Orientation.PORTRAIT -> 0
            Orientation.LANDSCAPE_LEFT -> 270
            Orientation.LANDSCAPE_RIGHT -> 90
            Orientation.PORTRAIT_UPSIDE_DOWN -> 180
            else -> 0
        }
    }

    fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int, flipX: Boolean, flipY: Boolean): Bitmap {
        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
            postScale(if (flipX) -1f else 1f, if (flipY) -1f else 1f)
        }
        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotatedBitmap != bitmap) {
            bitmap.recycle()
        }
        return rotatedBitmap
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    private fun yuv420ThreePlanesToNV21(planes: Array<Image.Plane>, width: Int, height: Int): ByteBuffer {
        val imageSize = width * height
        val out = ByteArray(imageSize + 2 * (imageSize / 4))

        if (areUVPlanesNV21(planes, width, height)) {
            planes[0].buffer.get(out, 0, imageSize)
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer
            vBuffer.get(out, imageSize, 1)
            uBuffer.get(out, imageSize + 1, 2 * imageSize / 4 - 1)
        } else {
            unpackPlane(planes[0], width, height, out, 0, 1)
            unpackPlane(planes[1], width, height, out, imageSize + 1, 2)
            unpackPlane(planes[2], width, height, out, imageSize, 2)
        }

        return ByteBuffer.wrap(out)
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    private fun areUVPlanesNV21(planes: Array<Image.Plane>, width: Int, height: Int): Boolean {
        val imageSize = width * height
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val vBufferPosition = vBuffer.position()
        val uBufferLimit = uBuffer.limit()

        vBuffer.position(vBufferPosition + 1)
        uBuffer.limit(uBufferLimit - 1)

        val areNV21 = (vBuffer.remaining() == 2 * imageSize / 4 - 2) && (vBuffer.compareTo(uBuffer) == 0)

        vBuffer.position(vBufferPosition)
        uBuffer.limit(uBufferLimit)

        return areNV21
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private fun unpackPlane(plane: Image.Plane, width: Int, height: Int, out: ByteArray, offset: Int, pixelStride: Int) {
        val buffer = plane.buffer
        buffer.rewind()

        val numRow = (buffer.limit() + plane.rowStride - 1) / plane.rowStride
        if (numRow == 0) return
        val scaleFactor = height / numRow
        val numCol = width / scaleFactor

        var outputPos = offset
        var rowStart = 0
        for (row in 0 until numRow) {
            var inputPos = rowStart
            for (col in 0 until numCol) {
                out[outputPos] = buffer.get(inputPos)
                outputPos += pixelStride
                inputPos += plane.pixelStride
            }
            rowStart += plane.rowStride
        }
    }

    fun base642Bitmap(base64: String): Bitmap {
        val decode = Base64.decode(base64, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(decode, 0, decode.size)
    }

    fun bitmap2Base64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
    }

    fun saveImage(bmp: Bitmap, dir: File, fileName: String): String {
        val file = File(dir, fileName)
        return try {
            FileOutputStream(file).use { fos ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 100, fos)
            }
            file.absolutePath
        } catch (e: IOException) {
            e.printStackTrace()
            ""
        }
    }
}

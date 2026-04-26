package com.sdkdev.sumbingcompanion.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * DetectionEngine — fixed for WeatherIconNetV2 (4-channel input)
 *
 * ROOT CAUSES yang diperbaiki:
 *
 * BUG 1 — Input buffer 3 channel, model butuh 4 channel
 *   SEBELUM : ByteBuffer.allocateDirect(1 * 64 * 64 * 3 * 4)
 *   SESUDAH : ByteBuffer.allocateDirect(1 * 64 * 64 * 4 * 4)
 *   EFEK    : Model terima data benar-benar salah → output acak/selalu satu kelas
 *
 * BUG 2 — Sobel edge channel tidak dihitung (channel ke-4 HILANG)
 *   WeatherIconNetV2 dilatih dengan input [RGB + Sobel edge].
 *   EdgeChannelTransform di Python menghitung sobel magnitude dan menambahnya
 *   sebagai channel ke-4. Di sini tidak ada → model tidak bisa baca struktur ikon.
 *   SESUDAH : computeSobelEdge() mengimplementasi pipeline yang IDENTIK dengan Python.
 *
 * BUG 3 — Normalisasi salah (ImageNet stats vs model stats)
 *   SEBELUM : mean=[0.485,0.456,0.406], std=[0.229,0.224,0.225]  ← ImageNet
 *   SESUDAH : RGB: (val - 0.5f) / 0.5f  ← sesuai EdgeChannelTransform di Python
 *             Edge channel: raw value [0,1] TIDAK di-normalize (sesuai Python)
 *
 * PIPELINE yang benar (harus identik dengan Python train/inference):
 *   Bitmap → squarePad → resize 64×64 → RGB normalize → Sobel edge → [1, 64, 64, 4]
 */
class DetectionEngine(context: Context) {

    private var interpreter: Interpreter? = null
    private var lastRawResult: WeatherClass = WeatherClass.UNKNOWN
    private var resultCounter = 0

    // Per-class confidence threshold — sama dengan CNN_CONF_THRESHOLD di Python
    private val confThreshold = mapOf(
        WeatherClass.ANGIN       to 0.60f,
        WeatherClass.BADAI       to 0.65f,
        WeatherClass.HUJAN       to 0.52f,   // lebih rendah = deteksi lebih cepat
        WeatherClass.HUJAN_PETIR to 0.52f,
        WeatherClass.KABUT       to 0.68f,   // lebih tinggi: fog sering mirip rain
        WeatherClass.MALAM       to 0.62f,
        WeatherClass.SIANG       to 0.62f,
    )
    private val defaultThreshold = 0.60f

    // Model input spec
    private val IMG_SIZE  = 64
    private val N_CLASSES = 7
    private val N_CHANNELS = 4    // RGB (3) + Sobel edge (1)

    init {
        try {
            val model = loadModelFile(context, "weather_model.tflite")
            val options = Interpreter.Options().apply {
                numThreads = 2
            }
            interpreter = Interpreter(model, options)
            logModelInfo()
        } catch (e: Exception) {
            SumbingLog.e(TAG, "Failed to load model: ${e.message}", e)
        }
    }

    private fun logModelInfo() {
        val inp = interpreter ?: return
        for (i in 0 until inp.inputTensorCount) {
            val t = inp.getInputTensor(i)
            SumbingLog.d(TAG, "Input[$i]: shape=${t.shape().contentToString()}, type=${t.dataType()}")
        }
        for (i in 0 until inp.outputTensorCount) {
            val t = inp.getOutputTensor(i)
            SumbingLog.d(TAG, "Output[$i]: shape=${t.shape().contentToString()}, type=${t.dataType()}")
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Detect weather from a full-screen bitmap.
     * Returns (WeatherClass, confidence) or null jika di bawah threshold.
     */
    fun detect(
        bitmap: Bitmap,
        region: CaptureRegion = CaptureRegion.DEFAULT,
        skipSmoothing: Boolean = false
    ): Pair<WeatherClass, Float>? {
        if (interpreter == null) return null

        // 1. Crop & square-pad
        val cropped = cropAndPad(bitmap, region)

        // 2. Resize ke 64×64
        val resized = Bitmap.createScaledBitmap(cropped, IMG_SIZE, IMG_SIZE, true)

        // 3. Build 4-channel input buffer
        val inputBuffer = buildInputBuffer(resized)

        // Cleanup intermediate bitmaps
        if (resized != cropped) resized.recycle()
        if (cropped != bitmap) cropped.recycle()

        // 4. Run inference
        val outputBuffer = Array(1) { FloatArray(N_CLASSES) }
        interpreter?.run(inputBuffer, outputBuffer)

        val scores = outputBuffer[0]

        // Debug log
        val log = scores.mapIndexed { i, s ->
            "${WeatherClass.fromIndex(i).key}: ${String.format(Locale.US, "%.3f", s)}"
        }.joinToString(" | ")
        SumbingLog.d(TAG, "Scores: $log")

        // 5. Softmax (model output mungkin belum di-softmax jika di-export dengan logits)
        // Uncomment ini HANYA jika model di-export tanpa softmax di output layer
        // val probs = softmax(scores)
        val probs = scores   // WeatherIconNetV2 forward() pakai classifier head langsung (logits)
        // → saat convert ke TFLite, tambahkan softmax layer

        val maxIdx = probs.indices.maxByOrNull { probs[it] } ?: 0
        val confidence = probs[maxIdx]
        val rawResult = WeatherClass.fromIndex(maxIdx)

        SumbingLog.d(TAG, "Top: ${rawResult.key} conf=${String.format(Locale.US, "%.3f", confidence)}")

        if (skipSmoothing) return rawResult to confidence

        // 6. Per-class confidence threshold
        val threshold = confThreshold[rawResult] ?: defaultThreshold
        if (confidence < threshold) {
            SumbingLog.d(TAG, "Below threshold (${String.format(Locale.US, "%.2f", threshold)}), skip")
            return WeatherClass.UNKNOWN to confidence
        }

        // 7. Temporal smoothing (2 consecutive frames)
        val validated = if (rawResult == lastRawResult) {
            resultCounter++
            if (resultCounter >= 2) rawResult else null
        } else {
            lastRawResult = rawResult
            resultCounter = 1
            null
        }

        return if (validated != null) validated to confidence else null
    }

    /**
     * Raw classify tanpa smoothing — untuk training/analyze mode.
     */
    fun classify(bitmap: Bitmap, region: CaptureRegion = CaptureRegion.DEFAULT): Pair<WeatherClass, Float> {
        val result = detect(bitmap, region, skipSmoothing = true)
        return result ?: (WeatherClass.UNKNOWN to 0f)
    }

    fun reset() {
        lastRawResult = WeatherClass.UNKNOWN
        resultCounter = 0
    }

    fun close() {
        interpreter?.close()
    }

    // ── Region helpers ─────────────────────────────────────────────────────────

    fun cropRegion(bitmap: Bitmap, region: CaptureRegion): Bitmap {
        if (region.isEmpty) return bitmap
        return try {
            val x = region.x.coerceIn(0, bitmap.width - 1)
            val y = region.y.coerceIn(0, bitmap.height - 1)
            val w = region.w.coerceAtMost(bitmap.width - x).coerceAtLeast(1)
            val h = region.h.coerceAtMost(bitmap.height - y).coerceAtLeast(1)
            Bitmap.createBitmap(bitmap, x, y, w, h)
        } catch (e: Exception) {
            bitmap
        }
    }

    fun cropAndPad(bitmap: Bitmap, region: CaptureRegion): Bitmap {
        val cropped = cropRegion(bitmap, region)

        // Square-pad dengan background hitam — identik dengan _SquarePad di Python
        if (cropped.width == cropped.height) return cropped

        val side = max(cropped.width, cropped.height)
        val padded = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        Canvas(padded).apply {
            drawColor(Color.BLACK)
            drawBitmap(
                cropped,
                ((side - cropped.width) / 2f),
                ((side - cropped.height) / 2f),
                null
            )
        }
        
        // Recycle the cropped result if it's a new instance created by cropRegion
        if (cropped != bitmap) cropped.recycle()

        return padded
    }

    // ── Core: 4-channel input buffer ───────────────────────────────────────────

    /**
     * Build ByteBuffer [1, IMG_SIZE, IMG_SIZE, 4] sesuai EdgeChannelTransform Python.
     *
     * Channel layout (HWC — TFLite standard):
     *   ch0 = R normalized: (r - 0.5) / 0.5
     *   ch1 = G normalized: (g - 0.5) / 0.5
     *   ch2 = B normalized: (b - 0.5) / 0.5
     *   ch3 = Sobel edge magnitude, NOT normalized, range [0, 1]
     */
    private fun buildInputBuffer(bitmap64: Bitmap): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(1 * IMG_SIZE * IMG_SIZE * N_CHANNELS * 4)
        buf.order(ByteOrder.nativeOrder())
        buf.rewind()

        // Extract pixel array
        val pixels = IntArray(IMG_SIZE * IMG_SIZE)
        bitmap64.getPixels(pixels, 0, IMG_SIZE, 0, 0, IMG_SIZE, IMG_SIZE)

        // Extract per-channel float arrays
        val rArr = FloatArray(IMG_SIZE * IMG_SIZE)
        val gArr = FloatArray(IMG_SIZE * IMG_SIZE)
        val bArr = FloatArray(IMG_SIZE * IMG_SIZE)

        for (i in pixels.indices) {
            rArr[i] = ((pixels[i] shr 16) and 0xFF) / 255f
            gArr[i] = ((pixels[i] shr 8) and 0xFF) / 255f
            bArr[i] = (pixels[i] and 0xFF) / 255f
        }

        // Compute grayscale for Sobel (same as Python: gray = rgb.mean(axis=2))
        val grayArr = FloatArray(IMG_SIZE * IMG_SIZE) { i ->
            (rArr[i] + gArr[i] + bArr[i]) / 3f
        }

        // Compute Sobel edge channel
        val edgeArr = computeSobelEdge(grayArr, IMG_SIZE, IMG_SIZE)

        // Write HWC layout: for each pixel, write [R, G, B, Edge]
        for (i in pixels.indices) {
            buf.putFloat((rArr[i] - 0.5f) / 0.5f)   // normalize RGB: (x-0.5)/0.5
            buf.putFloat((gArr[i] - 0.5f) / 0.5f)
            buf.putFloat((bArr[i] - 0.5f) / 0.5f)
            buf.putFloat(edgeArr[i])                   // edge channel: raw [0,1]
        }

        buf.rewind()
        return buf
    }

    /**
     * Sobel edge computation — IDENTIK dengan SobelEdgeChannel di Python.
     *
     * Pipeline:
     *   gray [0,1] → GaussianBlur 3×3 → Sobel X + Sobel Y → magnitude → normalize to [0,1]
     *
     * Python reference (weather_cnn_v2.py SobelEdgeChannel.__call__):
     *   blurred = cv2.GaussianBlur(gray, (blur_ksize, blur_ksize), 0)
     *   gx = cv2.Sobel(blurred, cv2.CV_32F, 1, 0, ksize=3)
     *   gy = cv2.Sobel(blurred, cv2.CV_32F, 0, 1, ksize=3)
     *   mag = sqrt(gx*gx + gy*gy)
     *   if vmax > 1e-6: mag /= vmax
     */
    private fun computeSobelEdge(gray: FloatArray, w: Int, h: Int): FloatArray {
        // Step 1: Gaussian blur 3×3 (sigma≈0 → box-like, same as cv2 default)
        val blurred = gaussianBlur3x3(gray, w, h)

        // Step 2: Sobel X dan Y
        val gx = FloatArray(w * h)
        val gy = FloatArray(w * h)

        // Sobel kernels (ksize=3, identical to OpenCV):
        //   Kx = [[-1,0,1],[-2,0,2],[-1,0,1]]
        //   Ky = [[-1,-2,-1],[0,0,0],[1,2,1]]
        for (row in 1 until h - 1) {
            for (col in 1 until w - 1) {
                val idx = row * w + col

                val tl = blurred[(row - 1) * w + (col - 1)]
                val tc = blurred[(row - 1) * w + col]
                val tr = blurred[(row - 1) * w + (col + 1)]
                val ml = blurred[row * w + (col - 1)]
                val mr = blurred[row * w + (col + 1)]
                val bl = blurred[(row + 1) * w + (col - 1)]
                val bc = blurred[(row + 1) * w + col]
                val br = blurred[(row + 1) * w + (col + 1)]

                gx[idx] = (-tl + tr - 2f * ml + 2f * mr - bl + br)
                gy[idx] = (-tl - 2f * tc - tr + bl + 2f * bc + br)
            }
        }

        // Step 3: Magnitude = sqrt(gx² + gy²)
        val mag = FloatArray(w * h) { i -> sqrt(gx[i] * gx[i] + gy[i] * gy[i]) }

        // Step 4: Normalize to [0,1] (same as Python: if vmax > 1e-6: mag /= vmax)
        val vmax = mag.max()
        if (vmax > 1e-6f) {
            for (i in mag.indices) mag[i] /= vmax
        }

        return mag
    }

    /**
     * 3×3 Gaussian blur dengan kernel approx [1,2,1; 2,4,2; 1,2,1] / 16.
     * Identik dengan cv2.GaussianBlur(img, (3,3), 0).
     */
    private fun gaussianBlur3x3(src: FloatArray, w: Int, h: Int): FloatArray {
        val dst = src.copyOf()

        for (row in 1 until h - 1) {
            for (col in 1 until w - 1) {
                val tl = src[(row - 1) * w + (col - 1)]
                val tc = src[(row - 1) * w + col]
                val tr = src[(row - 1) * w + (col + 1)]
                val ml = src[row * w + (col - 1)]
                val mc = src[row * w + col]
                val mr = src[row * w + (col + 1)]
                val bl = src[(row + 1) * w + (col - 1)]
                val bc = src[(row + 1) * w + col]
                val br = src[(row + 1) * w + (col + 1)]

                dst[row * w + col] = (tl + 2 * tc + tr +
                        2 * ml + 4 * mc + 2 * mr +
                        bl + 2 * bc + br) / 16f
            }
        }
        return dst
    }

    /**
     * Softmax — gunakan ini jika model di-export dengan logits (tanpa softmax di output).
     * Jika model sudah include softmax (biasanya jika dipakai torch.jit.trace dengan
     * F.softmax di forward), JANGAN gunakan ini.
     */
    @Suppress("unused")
    private fun softmax(logits: FloatArray): FloatArray {
        val maxLogit = logits.max()
        val exps = FloatArray(logits.size) { i -> Math.exp((logits[i] - maxLogit).toDouble()).toFloat() }
        val sumExps = exps.sum()
        return FloatArray(logits.size) { i -> exps[i] / sumExps }
    }

    // ── File loader ────────────────────────────────────────────────────────────

    private fun loadModelFile(context: Context, modelPath: String): ByteBuffer {
        val fd = context.assets.openFd(modelPath)
        return FileInputStream(fd.fileDescriptor).channel.map(
            FileChannel.MapMode.READ_ONLY,
            fd.startOffset,
            fd.declaredLength
        )
    }

    companion object {
        private const val TAG = "DetectionEngine"
    }
}
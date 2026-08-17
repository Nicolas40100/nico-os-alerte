package fr.nico.scouterdirect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.TensorBuffer
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

data class Detection(
    val classId: Int,
    val label: String,
    val score: Float,
    val rect: RectF,
)

class DirectDetector(private val context: Context) : AutoCloseable {
    private val labels: List<String>
    private val model: CompiledModel
    private val inputs: List<TensorBuffer>
    private val outputs: List<TensorBuffer>
    private val detOutputIndex: Int
    private val detShape: IntArray
    private val inputSize = 640
    private val input = FloatArray(inputSize * inputSize * 3)
    private val pixels = IntArray(inputSize * inputSize)
    private val modelBitmap = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    init {
        labels = context.assets.open("labels.txt").bufferedReader().readLines()
        val modelFile = File(context.filesDir, "yoloe-26s-seg-pf_w8a32.tflite")
        if (!modelFile.exists() || modelFile.length() < 1_000_000) {
            context.assets.open("yoloe-26s-seg-pf_w8a32.tflite").use { src ->
                modelFile.outputStream().use { dst -> src.copyTo(dst) }
            }
        }

        val options = CompiledModel.Options(Accelerator.CPU).apply {
            cpuOptions = CompiledModel.CpuOptions(numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
        }
        model = CompiledModel.create(modelFile.absolutePath, options)
        inputs = model.createInputBuffers()
        outputs = model.createOutputBuffers()

        inputs[0].writeFloat(FloatArray(input.size))
        model.run(inputs, outputs)
        val shapes = outputs.indices.map { i ->
            model.getOutputTensorType(outputName = "output_$i").layout?.dimensions?.toIntArray() ?: IntArray(0)
        }
        detOutputIndex = shapes.indexOfFirst { it.size == 3 }.takeIf { it >= 0 } ?: 0
        detShape = shapes[detOutputIndex]
        require(detShape.size == 3 && detShape[0] == 1 && detShape[2] >= 6) {
            "Unexpected detection tensor shape: ${detShape.toList()}"
        }
    }

    fun detect(source: Bitmap, minScore: Float = 0.02f): List<Detection> {
        val sw = source.width
        val sh = source.height
        if (sw <= 0 || sh <= 0) return emptyList()

        val gain = min(inputSize.toFloat() / sw, inputSize.toFloat() / sh)
        val rw = (sw * gain).roundToInt()
        val rh = (sh * gain).roundToInt()
        val padX = (inputSize - rw) / 2f
        val padY = (inputSize - rh) / 2f

        Canvas(modelBitmap).apply {
            drawColor(Color.BLACK)
            drawBitmap(source, null, RectF(padX, padY, padX + rw, padY + rh), paint)
        }

        modelBitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        val plane = inputSize * inputSize
        var ri = 0
        var gi = plane
        var bi = plane * 2
        for (p in pixels) {
            input[ri++] = ((p shr 16) and 0xFF) / 255f
            input[gi++] = ((p shr 8) and 0xFF) / 255f
            input[bi++] = (p and 0xFF) / 255f
        }

        inputs[0].writeFloat(input)
        model.run(inputs, outputs)
        val det = outputs[detOutputIndex].readFloat()
        val anchors = detShape[1]
        val fields = detShape[2]
        val out = ArrayList<Detection>(anchors)
        for (a in 0 until anchors) {
            val o = a * fields
            if (o + 5 >= det.size) break
            val score = det[o + 4]
            if (score < minScore || score > 1.01f) continue
            val cls = det[o + 5].roundToInt()
            if (cls !in labels.indices) continue
            val x1 = ((det[o] - padX) / gain).coerceIn(0f, sw.toFloat())
            val y1 = ((det[o + 1] - padY) / gain).coerceIn(0f, sh.toFloat())
            val x2 = ((det[o + 2] - padX) / gain).coerceIn(0f, sw.toFloat())
            val y2 = ((det[o + 3] - padY) / gain).coerceIn(0f, sh.toFloat())
            if (x2 <= x1 || y2 <= y1) continue
            out += Detection(cls, labels[cls], score, RectF(x1, y1, x2, y2))
        }

        // V1.3: keep every valid model candidate (up to the model's 300 outputs).
        // Free-scan UI still displays only its usual top items; target search can now inspect
        // low-ranked candidates that V1.2 used to discard after the first 30.
        return out.sortedByDescending { it.score }
    }

    override fun close() {
        inputs.forEach { runCatching { it.close() } }
        outputs.forEach { runCatching { it.close() } }
        runCatching { model.close() }
        runCatching { modelBitmap.recycle() }
    }
}

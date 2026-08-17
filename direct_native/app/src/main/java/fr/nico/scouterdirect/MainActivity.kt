package fr.nico.scouterdirect

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var previewView: PreviewView
    private lateinit var overlay: OverlayView
    private lateinit var queryEdit: EditText
    private lateinit var statusText: TextView
    private lateinit var seenText: TextView
    private lateinit var selfTestText: TextView

    private val inferenceExecutor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private var detector: DirectDetector? = null
    private var tts: TextToSpeech? = null
    private val tone by lazy { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90) }
    private var target: String? = null
    private var targetDisplay: String = ""
    private var lastInferenceMs = 0L

    // Target lock state. A short detection dropout does not create a new lock.
    private var targetLocked = false
    private var lastTargetSeenMs = 0L
    private var lockStartedMs = 0L
    private var announcedForSearch = false
    private val targetLostGraceMs = 2000L

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else statusText.text = "❌ Caméra refusée."
    }

    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
            if (!text.isNullOrEmpty()) {
                queryEdit.setText(text)
                setTarget(text)
            } else {
                statusText.text = "🎙️ Aucun mot reconnu."
            }
        } else {
            statusText.text = "🎙️ Reconnaissance vocale annulée."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        buildUi()
        selfTestText.text = "Autotest modèle : en cours…"
        statusText.text = "Chargement du moteur Android natif…"

        inferenceExecutor.execute {
            try {
                val d = DirectDetector(this)
                detector = d
                val testBitmap = assets.open("shoes_test.jpg").use { android.graphics.BitmapFactory.decodeStream(it) }
                val test = d.detect(testBitmap, 0.02f)
                val shoe = test.firstOrNull { canonicalLabel(it.label) == "shoe" }
                runOnUiThread {
                    selfTestText.text = if (shoe != null) {
                        "✅ Autotest : SHOE détecté ${(shoe.score * 100).toInt()} %"
                    } else {
                        "❌ Autotest : chaussure non détectée"
                    }
                    statusText.text = "Moteur prêt. Dis ou écris un objet."
                    ensureCameraPermission()
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    selfTestText.text = "❌ Autotest impossible"
                    statusText.text = "❌ Moteur IA : ${t.javaClass.simpleName}: ${t.message}"
                }
            }
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
            setBackgroundColor(0xFF080A0F.toInt())
        }

        val title = TextView(this).apply {
            text = "🔎 Nico Scouter DIRECT V1.1"
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, 10)
        }
        root.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        selfTestText = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFF7CFF9A.toInt())
        }
        root.addView(selfTestText)

        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        queryEdit = EditText(this).apply {
            hint = "chaussures, imprimante, bouteille…"
            setHintTextColor(0xFF8A8F98.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setSingleLine(true)
            setBackgroundColor(0xFF171B22.toInt())
            setPadding(18, 12, 18, 12)
        }
        searchRow.addView(queryEdit, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val mic = Button(this).apply {
            text = "🎙️"
            setOnClickListener { launchSpeech() }
        }
        searchRow.addView(mic, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(searchRow)

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val search = Button(this).apply {
            text = "CHERCHER"
            setOnClickListener { setTarget(queryEdit.text.toString()) }
        }
        val stop = Button(this).apply {
            text = "STOP"
            setOnClickListener {
                target = null
                targetDisplay = ""
                resetTargetLock(resetVoice = true)
                statusText.text = "⏹ Recherche arrêtée. L’IA continue d’afficher ce qu’elle voit."
            }
        }
        actions.addView(search, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(stop, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(actions)

        statusText = TextView(this).apply {
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(8, 10, 8, 10)
        }
        root.addView(statusText)

        seenText = TextView(this).apply {
            text = "IA voit : —"
            textSize = 13f
            setTextColor(0xFFFFD66B.toInt())
            setPadding(8, 0, 8, 8)
        }
        root.addView(seenText)

        val cameraFrame = FrameLayout(this).apply { setBackgroundColor(0xFF000000.toInt()) }
        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        overlay = OverlayView(this)
        cameraFrame.addView(previewView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        cameraFrame.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(cameraFrame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
    }

    private fun ensureCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                analysis.setAnalyzer(inferenceExecutor) { image -> analyzeFrame(image) }
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                statusText.text = if (target == null) "📷 Caméra active. L’IA analyse en direct." else "🔎 Recherche : $targetDisplay"
            } catch (t: Throwable) {
                statusText.text = "❌ Caméra : ${t.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzeFrame(image: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastInferenceMs < 300 || !busy.compareAndSet(false, true)) {
            image.close()
            return
        }
        lastInferenceMs = now
        try {
            val d = detector ?: return
            val bitmap = imageProxyToBitmap(image) ?: return
            val detections = d.detect(bitmap, 0.02f)
            val srcW = bitmap.width
            val srcH = bitmap.height
            val tgt = target
            val bestMatch = if (tgt == null) null else detections.firstOrNull { matchesTarget(it.label, tgt) && it.score >= 0.10f }
            val visible = detections.take(8)

            var acquiredNow = false
            if (tgt != null) {
                if (bestMatch != null) {
                    if (!targetLocked) {
                        targetLocked = true
                        lockStartedMs = now
                        acquiredNow = true
                    }
                    lastTargetSeenMs = now
                } else if (targetLocked && now - lastTargetSeenMs > targetLostGraceMs) {
                    targetLocked = false
                    lockStartedMs = 0L
                }
            }
            val lockTimeForOverlay = if (targetLocked) lockStartedMs else 0L

            runOnUiThread {
                seenText.text = if (visible.isEmpty()) {
                    "IA voit : rien au-dessus de 2 %"
                } else {
                    "IA voit : " + visible.joinToString(" • ") { "${it.label} ${(it.score * 100).toInt()}%" }
                }

                // Free scan stays exactly as before. Target graphics only activate during a search.
                overlay.update(detections, srcW, srcH, tgt, targetDisplay, lockTimeForOverlay)

                if (tgt != null) {
                    if (bestMatch != null) {
                        statusText.text = "🔴 TROUVÉ : $targetDisplay — ${(bestMatch.score * 100).toInt()} %"
                    } else if (targetLocked) {
                        statusText.text = "🔴 VERROUILLAGE : $targetDisplay"
                    } else {
                        statusText.text = "🔎 Recherche : $targetDisplay"
                    }
                    if (acquiredNow && !announcedForSearch) {
                        announcedForSearch = true
                        alertFoundOnce()
                    }
                }
            }
            bitmap.recycle()
        } catch (t: Throwable) {
            runOnUiThread { statusText.text = "❌ Inférence : ${t.javaClass.simpleName}: ${t.message}" }
        } finally {
            busy.set(false)
            image.close()
        }
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        if (image.format != PixelFormat.RGBA_8888 || image.planes.size != 1) return null
        val plane = image.planes[0]
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val paddedWidth = image.width + rowPadding / pixelStride
        val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        plane.buffer.rewind()
        padded.copyPixelsFromBuffer(plane.buffer)
        val cropped = if (rowPadding == 0) padded else Bitmap.createBitmap(padded, 0, 0, image.width, image.height).also { padded.recycle() }
        val degrees = image.imageInfo.rotationDegrees
        if (degrees == 0) return cropped
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true).also {
            if (it !== cropped) cropped.recycle()
        }
    }

    private fun launchSpeech() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.FRANCE.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Quel objet cherches-tu ?")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try {
            speechLauncher.launch(intent)
        } catch (t: Throwable) {
            statusText.text = "❌ Reconnaissance vocale Android indisponible."
        }
    }

    private fun setTarget(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            statusText.text = "Écris ou dis un objet."
            return
        }
        targetDisplay = trimmed
        target = translateTarget(trimmed)
        resetTargetLock(resetVoice = true)
        statusText.text = "🔎 Recherche : $trimmed → ${target ?: "?"}"
    }

    private fun resetTargetLock(resetVoice: Boolean) {
        targetLocked = false
        lastTargetSeenMs = 0L
        lockStartedMs = 0L
        if (resetVoice) announcedForSearch = false
    }

    private fun translateTarget(raw: String): String {
        val n = normalize(raw)
        val aliases = mapOf(
            "chaussure" to "shoe", "chaussures" to "shoe", "basket" to "shoe", "baskets" to "shoe", "sneaker" to "shoe", "sneakers" to "shoe",
            "imprimante" to "printer", "cafetiere" to "coffee maker", "machine a cafe" to "coffee maker",
            "telecommande" to "remote control", "bouteille" to "bottle", "tasse" to "cup", "verre" to "glass",
            "plante" to "plant", "lampe" to "lamp", "cle" to "key", "cles" to "key",
            "portefeuille" to "wallet", "sac" to "bag", "telephone" to "cell phone", "portable" to "cell phone",
            "livre" to "book", "chaise" to "chair", "table" to "table", "ordinateur" to "computer", "ecran" to "monitor",
            "clavier" to "keyboard", "souris" to "mouse", "casque" to "headphones", "lunettes" to "glasses", "montre" to "watch",
            "canape" to "sofa", "lit" to "bed", "frigo" to "refrigerator", "four" to "oven", "micro ondes" to "microwave",
            "aspirateur" to "vacuum cleaner", "ventilateur" to "fan", "miroir" to "mirror", "poubelle" to "trash can", "velo" to "bicycle",
            "moto" to "motorcycle", "voiture" to "car", "ballon" to "ball", "peluche" to "stuffed animal", "serviette" to "towel", "assiette" to "plate",
            "couteau" to "knife", "casserole" to "pot", "boite" to "box", "carton" to "box", "chargeur" to "charger", "cable" to "cable"
        )
        aliases[n]?.let { return it }
        for (word in n.split(" ")) aliases[word]?.let { return it }
        return when (n) {
            "shoes" -> "shoe"
            "sneakers" -> "shoe"
            "phone", "smartphone" -> "cell phone"
            "remote" -> "remote control"
            else -> n
        }
    }

    private fun normalize(s: String): String {
        val noAccents = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return noAccents.replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
    }

    private fun canonicalLabel(label: String): String = when (normalize(label)) {
        "shoes", "sneaker", "sneakers", "trainer", "trainers" -> "shoe"
        "phone", "smartphone", "cellphone" -> "cell phone"
        "remote" -> "remote control"
        else -> normalize(label)
    }

    private fun matchesTarget(label: String, tgt: String): Boolean {
        val c = canonicalLabel(label)
        if (c == tgt) return true
        return tgt.length >= 4 && (c.contains(tgt) || tgt.contains(c))
    }

    private fun alertFoundOnce() {
        tone.startTone(ToneGenerator.TONE_PROP_ACK, 160)
        tts?.speak("$targetDisplay trouvé", TextToSpeech.QUEUE_FLUSH, null, "found-once")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale.FRANCE
    }

    override fun onDestroy() {
        super.onDestroy()
        inferenceExecutor.shutdownNow()
        detector?.close()
        tts?.shutdown()
        tone.release()
    }
}

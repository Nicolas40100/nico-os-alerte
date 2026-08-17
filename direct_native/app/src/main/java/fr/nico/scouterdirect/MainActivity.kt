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
import android.os.SystemClock
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var previewView: PreviewView
    private lateinit var overlay: OverlayView
    private lateinit var queryEdit: EditText
    private lateinit var statusText: TextView
    private lateinit var seenText: TextView
    private lateinit var selfTestText: TextView

    private val inferenceExecutor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private val searchCounter = AtomicLong(0L)
    private val lockTracker = TargetLockTracker(lostGraceMs = 2000L)
    private val confirmationTracker = TargetConfirmationTracker(
        confirmHits = 2,
        maxGapMs = 900L,
        minIou = 0.05f,
    )

    private var detector: DirectDetector? = null
    private var tts: TextToSpeech? = null
    private val tone by lazy { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90) }

    @Volatile private var currentSearch: SearchSpec? = null
    @Volatile private var ttsReady = false
    @Volatile private var pendingSpeechToken = -1L
    @Volatile private var announcedSpeechToken = -1L

    private var lastInferenceMs = 0L
    private var lastTargetDetection: Detection? = null
    private var lastTargetDetectionMs = 0L
    private var lastTargetToken: Long? = null
    private val visualHoldMs = 900L
    private val targetCandidateMinScore = 0.04f
    private val immediateTargetMinScore = 0.10f

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
        buildUi()
        tts = TextToSpeech(this, this)

        selfTestText.text = "Autotest modèle : en cours…"
        statusText.text = "Chargement du moteur Android natif…"

        inferenceExecutor.execute {
            try {
                val d = DirectDetector(this)
                detector = d
                val testBitmap = assets.open("shoes_test.jpg").use { android.graphics.BitmapFactory.decodeStream(it) }
                val test = d.detect(testBitmap, 0.02f)
                val shoe = test.firstOrNull { SearchLogic.canonical(it.label) == "shoe" }
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
            text = "🔎 Nico Scouter DIRECT V1.3"
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
            hint = "chaussures, télécommande, appareil photo…"
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
            setOnClickListener { stopSearch() }
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
                val search = currentSearch
                statusText.text = if (search == null) "📷 Caméra active. L’IA analyse en direct." else "🔎 Recherche : ${search.display}"
            } catch (t: Throwable) {
                statusText.text = "❌ Caméra : ${t.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzeFrame(image: ImageProxy) {
        val startedAt = SystemClock.elapsedRealtime()
        if (startedAt - lastInferenceMs < 300L || !busy.compareAndSet(false, true)) {
            image.close()
            return
        }
        lastInferenceMs = startedAt

        try {
            val d = detector ?: return
            val bitmap = imageProxyToBitmap(image) ?: return
            val detections = d.detect(bitmap, 0.02f)
            val detectedAt = SystemClock.elapsedRealtime()
            val srcW = bitmap.width
            val srcH = bitmap.height
            val visible = detections.take(8)

            // Detection remains prompt-free. V1.3 searches every returned model candidate rather
            // than only V1.2's top 30, while keeping the free-scan display unchanged.
            val search = currentSearch
            val candidate = search?.let { spec ->
                detections.firstOrNull {
                    it.score >= targetCandidateMinScore && SearchLogic.matches(it.label, spec)
                }
            }

            val confirmation = confirmationTracker.update(
                search?.token,
                candidate?.rect?.let { CandidateBox(it.left, it.top, it.right, it.bottom) },
                detectedAt,
            )

            // Preserve V1.2 responsiveness for a confident target (>=10%). A weaker target
            // (4–9.9%) must appear coherently on two close frames before it can trigger LOCK.
            val bestMatch = when {
                candidate == null -> null
                candidate.score >= immediateTargetMinScore -> candidate
                confirmation.confirmed -> candidate
                else -> null
            }

            val lock = lockTracker.update(search?.token, bestMatch != null, detectedAt)
            if (search == null || !lock.locked) {
                lastTargetDetection = null
                lastTargetDetectionMs = 0L
                lastTargetToken = null
            } else if (bestMatch != null) {
                lastTargetDetection = bestMatch
                lastTargetDetectionMs = detectedAt
                lastTargetToken = search.token
            } else if (lastTargetToken != search.token) {
                lastTargetDetection = null
                lastTargetDetectionMs = 0L
                lastTargetToken = search.token
            }

            val overlayTarget = when {
                bestMatch != null -> bestMatch
                lock.locked && lastTargetToken == search?.token && detectedAt - lastTargetDetectionMs <= visualHoldMs -> lastTargetDetection
                else -> null
            }

            runOnUiThread {
                seenText.text = if (visible.isEmpty()) {
                    "IA voit : rien au-dessus de 2 %"
                } else {
                    "IA voit : " + visible.joinToString(" • ") { "${it.label} ${(it.score * 100).toInt()}%" }
                }

                // Never let a completed old UI callback paint over a newer search.
                if (currentSearch?.token != search?.token) {
                    overlay.update(detections, srcW, srcH, null, "", 0L)
                } else {
                    overlay.update(
                        detections,
                        srcW,
                        srcH,
                        overlayTarget,
                        search?.display.orEmpty(),
                        if (lock.locked) lock.lockStartedMs else 0L,
                    )

                    if (search != null) {
                        statusText.text = when {
                            bestMatch != null -> "🔴 TROUVÉ : ${search.display} — ${(bestMatch.score * 100).toInt()} %"
                            candidate != null -> "🔎 Vérification : ${search.display} — ${(candidate.score * 100).toInt()} %"
                            lock.locked -> "🔴 VERROUILLAGE : ${search.display}"
                            else -> "🔎 Recherche : ${search.display}"
                        }
                        if (lock.locked) maybeAnnounceFound(search)
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
        val token = searchCounter.incrementAndGet()
        val spec = SearchLogic.buildSpec(token, raw)
        if (spec == null) {
            statusText.text = "Écris ou dis un objet."
            return
        }

        tts?.stop()
        pendingSpeechToken = -1L
        currentSearch = spec
        overlay.clearTarget()
        statusText.text = "🔎 Recherche : ${spec.display}"
    }

    private fun stopSearch() {
        searchCounter.incrementAndGet()
        currentSearch = null
        tts?.stop()
        pendingSpeechToken = -1L
        overlay.clearTarget()
        statusText.text = "⏹ Recherche arrêtée. L’IA continue d’afficher ce qu’elle voit."
    }

    private fun maybeAnnounceFound(search: SearchSpec) {
        if (!ttsReady) return
        if (announcedSpeechToken == search.token || pendingSpeechToken == search.token) return
        val engine = tts ?: return
        val utteranceId = "found-${search.token}"
        val result = engine.speak(
            "${search.display} trouvé",
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId,
        )
        if (result == TextToSpeech.SUCCESS) {
            pendingSpeechToken = search.token
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 160)
        }
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            ttsReady = false
            return
        }
        val engine = tts ?: return
        val languageResult = engine.setLanguage(Locale.FRANCE)
        ttsReady = languageResult != TextToSpeech.LANG_MISSING_DATA && languageResult != TextToSpeech.LANG_NOT_SUPPORTED
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                val token = tokenFromUtterance(utteranceId) ?: return
                if (pendingSpeechToken == token) pendingSpeechToken = -1L
                announcedSpeechToken = token
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                val token = tokenFromUtterance(utteranceId) ?: return
                if (pendingSpeechToken == token) pendingSpeechToken = -1L
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                val token = tokenFromUtterance(utteranceId) ?: return
                if (pendingSpeechToken == token) pendingSpeechToken = -1L
            }
        })
    }

    private fun tokenFromUtterance(utteranceId: String?): Long? =
        utteranceId?.removePrefix("found-")?.toLongOrNull()

    override fun onDestroy() {
        super.onDestroy()
        inferenceExecutor.shutdownNow()
        detector?.close()
        tts?.shutdown()
        tone.release()
    }
}

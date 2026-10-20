package com.whatsappstatus.optimizer

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

data class VideoSegment(
    val partNumber: Int,
    val startSec: Double,
    val durationSec: Double
)

enum class SplitMode {
    BALANCED,
    FIXED,
    MANUAL
}

class MainActivity : AppCompatActivity() {

    private lateinit var tvVideoTitle: TextView
    private lateinit var tvVideoDetails: TextView
    private lateinit var tvOptimizationBadge: TextView
    private lateinit var btnSelectPhoto: Button
    private lateinit var btnSelectVideo: Button
    private lateinit var btnCompress: Button
    private lateinit var layoutProgress: View
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgressStatus: TextView
    private lateinit var tvProgressPercent: TextView
    private lateinit var layoutResults: View
    private lateinit var tvResultsHeader: TextView
    private lateinit var tvSelectAllToggle: TextView
    private lateinit var resultsContainer: LinearLayout
    private lateinit var btnShareSelected: MaterialButton

    // Vistas de Opciones de División (v1.5)
    private lateinit var cardSplitOptions: View
    private lateinit var toggleGroupLimit: MaterialButtonToggleGroup
    private lateinit var btnLimit30s: MaterialButton
    private lateinit var btnLimit60s: MaterialButton
    private lateinit var toggleGroupSplitMode: MaterialButtonToggleGroup
    private lateinit var btnModeBalanced: MaterialButton
    private lateinit var btnModeFixed: MaterialButton
    private lateinit var btnModeManual: MaterialButton
    private lateinit var tvAutoSplitSummary: TextView
    private lateinit var layoutManualEditor: View
    private lateinit var videoPreview: VideoView
    private lateinit var ivPlayPauseOverlay: ImageView
    private lateinit var tvCurrentVideoTime: TextView
    private lateinit var seekBarVideo: SeekBar
    private lateinit var tvTotalVideoTime: TextView
    private lateinit var btnMarkCutPoint: MaterialButton
    private lateinit var containerManualCuts: LinearLayout

    private var selectedMediaUri: Uri? = null
    private var isImage: Boolean = false
    private var videoDurationSec: Double = 0.0

    // Configuración de división
    private var selectedLimitSec: Double = 30.0
    private var currentSplitMode: SplitMode = SplitMode.BALANCED
    private val manualCutPoints = mutableListOf<Double>()

    // Handler para progreso del video preview
    private val videoHandler = Handler(Looper.getMainLooper())
    private val updateVideoProgressRunnable = object : Runnable {
        override fun run() {
            try {
                if (videoPreview.isPlaying) {
                    val currentPos = videoPreview.currentPosition
                    seekBarVideo.progress = currentPos
                    val currentSec = currentPos / 1000.0
                    tvCurrentVideoTime.text = formatSeconds(currentSec)
                    btnMarkCutPoint.text = "✂️ Marcar corte en ${formatSeconds(currentSec)}"
                }
            } catch (_: Exception) {}
            videoHandler.postDelayed(this, 250)
        }
    }

    // Gestión de partes generadas
    private val generatedFilesList = mutableListOf<File>()
    private val selectedIndices = mutableSetOf<Int>()
    private val checkBoxesList = mutableListOf<CheckBox>()

    private val selectVideoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onMediaSelected(it, isPhoto = false) }
    }

    private val selectPhotoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onMediaSelected(it, isPhoto = true) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvVideoTitle = findViewById(R.id.tvVideoTitle)
        tvVideoDetails = findViewById(R.id.tvVideoDetails)
        tvOptimizationBadge = findViewById(R.id.tvOptimizationBadge)
        btnSelectPhoto = findViewById(R.id.btnSelectPhoto)
        btnSelectVideo = findViewById(R.id.btnSelectVideo)
        btnCompress = findViewById(R.id.btnCompress)
        layoutProgress = findViewById(R.id.layoutProgress)
        progressBar = findViewById(R.id.progressBar)
        tvProgressStatus = findViewById(R.id.tvProgressStatus)
        tvProgressPercent = findViewById(R.id.tvProgressPercent)
        layoutResults = findViewById(R.id.layoutResults)
        tvResultsHeader = findViewById(R.id.tvResultsHeader)
        tvSelectAllToggle = findViewById(R.id.tvSelectAllToggle)
        resultsContainer = findViewById(R.id.resultsContainer)
        btnShareSelected = findViewById(R.id.btnShareSelected)

        // Inicializar vistas de división (v1.5)
        cardSplitOptions = findViewById(R.id.cardSplitOptions)
        toggleGroupLimit = findViewById(R.id.toggleGroupLimit)
        btnLimit30s = findViewById(R.id.btnLimit30s)
        btnLimit60s = findViewById(R.id.btnLimit60s)
        toggleGroupSplitMode = findViewById(R.id.toggleGroupSplitMode)
        btnModeBalanced = findViewById(R.id.btnModeBalanced)
        btnModeFixed = findViewById(R.id.btnModeFixed)
        btnModeManual = findViewById(R.id.btnModeManual)
        tvAutoSplitSummary = findViewById(R.id.tvAutoSplitSummary)
        layoutManualEditor = findViewById(R.id.layoutManualEditor)
        videoPreview = findViewById(R.id.videoPreview)
        ivPlayPauseOverlay = findViewById(R.id.ivPlayPauseOverlay)
        tvCurrentVideoTime = findViewById(R.id.tvCurrentVideoTime)
        seekBarVideo = findViewById(R.id.seekBarVideo)
        tvTotalVideoTime = findViewById(R.id.tvTotalVideoTime)
        btnMarkCutPoint = findViewById(R.id.btnMarkCutPoint)
        containerManualCuts = findViewById(R.id.containerManualCuts)

        // Estado inicial de toggles
        toggleGroupLimit.check(R.id.btnLimit30s)
        toggleGroupSplitMode.check(R.id.btnModeBalanced)

        toggleGroupLimit.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                selectedLimitSec = if (checkedId == R.id.btnLimit60s) 60.0 else 30.0
                updateSplitUi()
            }
        }

        toggleGroupSplitMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentSplitMode = when (checkedId) {
                    R.id.btnModeManual -> SplitMode.MANUAL
                    R.id.btnModeFixed -> SplitMode.FIXED
                    else -> SplitMode.BALANCED
                }
                updateSplitUi()
            }
        }

        setupVideoPreviewControls()

        btnSelectPhoto.setOnClickListener {
            selectPhotoLauncher.launch("image/*")
        }

        btnSelectVideo.setOnClickListener {
            selectVideoLauncher.launch("video/*")
        }

        btnCompress.setOnClickListener {
            selectedMediaUri?.let { uri ->
                if (isImage) {
                    startPhotoOptimization(uri)
                } else {
                    startVideoCompression(uri)
                }
            }
        }

        tvSelectAllToggle.setOnClickListener {
            if (selectedIndices.size == generatedFilesList.size) {
                // Desmarcar todas
                selectedIndices.clear()
                checkBoxesList.forEach { it.isChecked = false }
            } else {
                // Marcar todas
                selectedIndices.clear()
                for (i in generatedFilesList.indices) {
                    selectedIndices.add(i)
                }
                checkBoxesList.forEach { it.isChecked = true }
            }
            updateShareButtonState()
        }

        btnShareSelected.setOnClickListener {
            val selectedFiles = selectedIndices.sorted().map { generatedFilesList[it] }
            if (selectedFiles.isEmpty()) return@setOnClickListener

            if (selectedFiles.size == 1) {
                if (isImage) {
                    sharePhotoToWhatsApp(selectedFiles[0])
                } else {
                    shareVideoToWhatsApp(selectedFiles[0])
                }
            } else {
                shareMultipleVideosToWhatsApp(selectedFiles)
            }
        }

        // Si fue abierta desde el menú "Compartir" de Android
        handleIntent(intent)

        // Comprobar si hay actualizaciones en GitHub Releases
        checkForUpdates()
    }

    private fun checkForUpdates() {
        Thread {
            try {
                val url = java.net.URL("https://api.github.com/repos/AdrianP2001/status-hd-whatsapp/releases/latest")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.setRequestProperty("User-Agent", "StatusHD-App")
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                if (conn.responseCode == 200) {
                    val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = org.json.JSONObject(responseText)
                    val latestTag = json.optString("tag_name", "v1.5")
                    val currentVersion = "v1.5"

                    if (latestTag.isNotEmpty() && latestTag != currentVersion) {
                        val assets = json.optJSONArray("assets")
                        var downloadUrl = ""
                        if (assets != null) {
                            for (i in 0 until assets.length()) {
                                val asset = assets.getJSONObject(i)
                                if (asset.optString("name").endsWith(".apk")) {
                                    downloadUrl = asset.optString("browser_download_url")
                                    break
                                }
                            }
                        }
                        if (downloadUrl.isEmpty()) {
                            downloadUrl = json.optString("html_url")
                        }

                        runOnUiThread {
                            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                                .setTitle("🚀 Actualización disponible: $latestTag")
                                .setMessage("Hay una nueva versión de Status HD disponible. ¿Deseas descargarla ahora?\n(Se actualizará sin perder tus datos).")
                                .setPositiveButton("Actualizar") { _, _ ->
                                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                                    startActivity(browserIntent)
                                }
                                .setNegativeButton("Más tarde", null)
                                .show()
                        }
                    }
                }
            } catch (_: Exception) {}
        }.start()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    private fun handleIntent(intent: Intent) {
        if (Intent.ACTION_SEND == intent.action) {
            val type = intent.type ?: ""
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: intent.data
            uri?.let {
                val isPhoto = type.startsWith("image/")
                onMediaSelected(it, isPhoto = isPhoto)
            }
        }
    }

    private fun onMediaSelected(uri: Uri, isPhoto: Boolean) {
        selectedMediaUri = uri
        isImage = isPhoto
        layoutResults.visibility = View.GONE
        resultsContainer.removeAllViews()
        generatedFilesList.clear()
        selectedIndices.clear()
        checkBoxesList.clear()

        if (isPhoto) {
            handlePhotoSelected(uri)
        } else {
            handleVideoSelected(uri)
        }
    }

    private fun formatSeconds(sec: Double): String {
        val totalSec = sec.toInt()
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format(Locale.US, "%02d:%02d", m, s)
    }

    private fun setupVideoPreviewControls() {
        videoPreview.setOnClickListener {
            togglePlayPauseVideo()
        }
        ivPlayPauseOverlay.setOnClickListener {
            togglePlayPauseVideo()
        }

        videoPreview.setOnCompletionListener {
            ivPlayPauseOverlay.setImageResource(android.R.drawable.ic_media_play)
            ivPlayPauseOverlay.visibility = View.VISIBLE
        }

        seekBarVideo.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    videoPreview.seekTo(progress)
                    val currentSec = progress / 1000.0
                    tvCurrentVideoTime.text = formatSeconds(currentSec)
                    btnMarkCutPoint.text = "✂️ Marcar corte en ${formatSeconds(currentSec)}"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnMarkCutPoint.setOnClickListener {
            val currentSec = videoPreview.currentPosition / 1000.0
            if (currentSec <= 1.0 || currentSec >= (videoDurationSec - 1.0)) {
                Toast.makeText(this, "El corte debe estar al menos a 1s del inicio y del final", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val existsClose = manualCutPoints.any { kotlin.math.abs(it - currentSec) < 1.0 }
            if (existsClose) {
                Toast.makeText(this, "Ya existe un corte muy cerca de este segundo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            manualCutPoints.add(currentSec)
            manualCutPoints.sort()
            renderManualCutsUi()
            Toast.makeText(this, "Corte agregado en ${formatSeconds(currentSec)}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun togglePlayPauseVideo() {
        if (videoPreview.isPlaying) {
            videoPreview.pause()
            ivPlayPauseOverlay.setImageResource(android.R.drawable.ic_media_play)
            ivPlayPauseOverlay.visibility = View.VISIBLE
        } else {
            videoPreview.start()
            ivPlayPauseOverlay.setImageResource(android.R.drawable.ic_media_pause)
            ivPlayPauseOverlay.visibility = View.GONE
            videoHandler.post(updateVideoProgressRunnable)
        }
    }

    override fun onPause() {
        super.onPause()
        if (::videoPreview.isInitialized && videoPreview.isPlaying) {
            videoPreview.pause()
        }
        videoHandler.removeCallbacks(updateVideoProgressRunnable)
    }

    private fun updateSplitUi() {
        if (isImage || selectedMediaUri == null) {
            cardSplitOptions.visibility = View.GONE
            return
        }

        cardSplitOptions.visibility = View.VISIBLE
        val segments = calculateSegments()

        val is1080 = segments.all { it.durationSec <= 35.0 }
        tvOptimizationBadge.text = if (is1080) {
            "Modo: 1080p Máxima Nitidez (~14.5MB por estado)"
        } else {
            "Modo: 720p Optimizado (~14.5MB por estado)"
        }

        when (currentSplitMode) {
            SplitMode.BALANCED -> {
                tvAutoSplitSummary.visibility = View.VISIBLE
                layoutManualEditor.visibility = View.GONE
                if (::videoPreview.isInitialized && videoPreview.isPlaying) {
                    videoPreview.pause()
                }

                if (segments.size == 1) {
                    tvAutoSplitSummary.text = "1 sola parte completa (${String.format(Locale.US, "%.1fs", videoDurationSec)}) • No requiere corte"
                } else {
                    val partDur = segments[0].durationSec
                    tvAutoSplitSummary.text = "⚖️ Dividir en ${segments.size} partes equilibradas de ${String.format(Locale.US, "%.1fs", partDur)} cada una (ideal para WhatsApp)"
                }
            }
            SplitMode.FIXED -> {
                tvAutoSplitSummary.visibility = View.VISIBLE
                layoutManualEditor.visibility = View.GONE
                if (::videoPreview.isInitialized && videoPreview.isPlaying) {
                    videoPreview.pause()
                }

                if (segments.size == 1) {
                    tvAutoSplitSummary.text = "1 sola parte completa (${String.format(Locale.US, "%.1fs", videoDurationSec)})"
                } else {
                    val partsDesc = segments.map { String.format(Locale.US, "%.1fs", it.durationSec) }.joinToString(" + ")
                    tvAutoSplitSummary.text = "⏱️ Dividir en ${segments.size} partes fijas: $partsDesc"
                }
            }
            SplitMode.MANUAL -> {
                tvAutoSplitSummary.visibility = View.GONE
                layoutManualEditor.visibility = View.VISIBLE

                if (manualCutPoints.isEmpty() && videoDurationSec > selectedLimitSec) {
                    val numParts = ceil(videoDurationSec / selectedLimitSec).toInt().coerceAtLeast(1)
                    val partDur = videoDurationSec / numParts
                    for (i in 1 until numParts) {
                        manualCutPoints.add(i * partDur)
                    }
                    manualCutPoints.sort()
                }

                renderManualCutsUi()
            }
        }
    }

    private fun calculateSegments(): List<VideoSegment> {
        if (videoDurationSec <= selectedLimitSec) {
            return listOf(VideoSegment(1, 0.0, videoDurationSec))
        }

        return when (currentSplitMode) {
            SplitMode.BALANCED -> {
                val numParts = ceil(videoDurationSec / selectedLimitSec).toInt().coerceAtLeast(1)
                val partDur = videoDurationSec / numParts
                val list = mutableListOf<VideoSegment>()
                for (i in 0 until numParts) {
                    val start = i * partDur
                    val dur = min(partDur, videoDurationSec - start)
                    list.add(VideoSegment(i + 1, start, dur))
                }
                list
            }
            SplitMode.FIXED -> {
                val numParts = ceil(videoDurationSec / selectedLimitSec).toInt().coerceAtLeast(1)
                val list = mutableListOf<VideoSegment>()
                for (i in 0 until numParts) {
                    val start = i * selectedLimitSec
                    val dur = min(selectedLimitSec, videoDurationSec - start)
                    if (dur > 0.5) {
                        list.add(VideoSegment(i + 1, start, dur))
                    }
                }
                list
            }
            SplitMode.MANUAL -> {
                val validPoints = manualCutPoints
                    .filter { it in 1.0..(videoDurationSec - 1.0) }
                    .sorted()
                val allBoundaries = listOf(0.0) + validPoints + listOf(videoDurationSec)
                val list = mutableListOf<VideoSegment>()
                for (i in 0 until allBoundaries.size - 1) {
                    val start = allBoundaries[i]
                    val dur = allBoundaries[i + 1] - start
                    if (dur > 0.5) {
                        list.add(VideoSegment(i + 1, start, dur))
                    }
                }
                if (list.isEmpty()) {
                    listOf(VideoSegment(1, 0.0, videoDurationSec))
                } else {
                    list
                }
            }
        }
    }

    private fun renderManualCutsUi() {
        containerManualCuts.removeAllViews()
        val segments = calculateSegments()

        for ((idx, seg) in segments.withIndex()) {
            val rowCard = MaterialCardView(this).apply {
                radius = 16f
                strokeColor = Color.parseColor("#334155")
                strokeWidth = 2
                setCardBackgroundColor(Color.parseColor("#0F172A"))
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 10)
                }
                layoutParams = params
            }

            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 14, 16, 14)
            }

            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val tvTitle = TextView(this).apply {
                text = "Parte ${seg.partNumber}: ${formatSeconds(seg.startSec)} a ${formatSeconds(seg.startSec + seg.durationSec)} (${String.format(Locale.US, "%.1fs", seg.durationSec)})"
                setTextColor(Color.WHITE)
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            }

            val isValid = seg.durationSec <= (selectedLimitSec + 0.5)
            val tvBadge = TextView(this).apply {
                if (isValid) {
                    text = "✅ Válido"
                    setTextColor(Color.parseColor("#22C55E"))
                } else {
                    text = "⚠️ >${selectedLimitSec.toInt()}s"
                    setTextColor(Color.parseColor("#EF4444"))
                }
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
            }

            topRow.addView(tvTitle)
            topRow.addView(tvBadge)
            rowLayout.addView(topRow)

            // Controles de ajuste si hay corte al final de este segmento
            if (idx < manualCutPoints.size) {
                val cutIdx = idx
                val cutVal = manualCutPoints[cutIdx]

                val controlsRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 8, 0, 0)
                    }
                }

                val tvCutLabel = TextView(this).apply {
                    text = "Corte en: ${formatSeconds(cutVal)}"
                    setTextColor(Color.parseColor("#94A3B8"))
                    textSize = 12f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
                }

                val btnMinus = TextView(this).apply {
                    text = " -1s "
                    setTextColor(Color.parseColor("#38BDF8"))
                    setBackgroundColor(Color.parseColor("#1E293B"))
                    setPadding(16, 8, 16, 8)
                    textSize = 12f
                    setOnClickListener {
                        val newVal = (manualCutPoints[cutIdx] - 1.0).coerceAtLeast(1.0)
                        manualCutPoints[cutIdx] = newVal
                        renderManualCutsUi()
                    }
                }

                val spacer1 = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(12, 0)
                }

                val btnPlus = TextView(this).apply {
                    text = " +1s "
                    setTextColor(Color.parseColor("#38BDF8"))
                    setBackgroundColor(Color.parseColor("#1E293B"))
                    setPadding(16, 8, 16, 8)
                    textSize = 12f
                    setOnClickListener {
                        val newVal = (manualCutPoints[cutIdx] + 1.0).coerceAtMost(videoDurationSec - 1.0)
                        manualCutPoints[cutIdx] = newVal
                        renderManualCutsUi()
                    }
                }

                val spacer2 = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(16, 0)
                }

                val btnDelete = TextView(this).apply {
                    text = " ✕ "
                    setTextColor(Color.parseColor("#EF4444"))
                    setBackgroundColor(Color.parseColor("#1E293B"))
                    setPadding(16, 8, 16, 8)
                    textSize = 12f
                    setOnClickListener {
                        manualCutPoints.removeAt(cutIdx)
                        renderManualCutsUi()
                    }
                }

                controlsRow.addView(tvCutLabel)
                controlsRow.addView(btnMinus)
                controlsRow.addView(spacer1)
                controlsRow.addView(btnPlus)
                controlsRow.addView(spacer2)
                controlsRow.addView(btnDelete)
                rowLayout.addView(controlsRow)
            }

            rowCard.addView(rowLayout)
            containerManualCuts.addView(rowCard)
        }
    }

    private fun handlePhotoSelected(uri: Uri) {
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            val w = options.outWidth
            val h = options.outHeight

            tvVideoTitle.text = "Foto cargada ($w x $h)"
            tvVideoDetails.text = "Lienzo 9:16 vertical • Ajuste proporcional sin deformar"
            tvOptimizationBadge.text = "Modo: Foto HD (Máxima Nitidez JPEG 96%)"
            btnCompress.text = "Optimizar Foto para WhatsApp"
            btnCompress.isEnabled = true
            cardSplitOptions.visibility = View.GONE
        } catch (e: Exception) {
            Toast.makeText(this, "Error al leer imagen: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleVideoSelected(uri: Uri) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            videoDurationSec = durationMs / 1000.0

            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) ?: "1080"
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) ?: "1920"
            retriever.release()

            // Pre-configurar puntos de corte balanceados
            manualCutPoints.clear()
            if (videoDurationSec > selectedLimitSec) {
                val numParts = ceil(videoDurationSec / selectedLimitSec).toInt().coerceAtLeast(1)
                val partDur = videoDurationSec / numParts
                for (i in 1 until numParts) {
                    manualCutPoints.add(i * partDur)
                }
            }

            tvVideoTitle.text = "Video cargado (${String.format(Locale.US, "%.1f", videoDurationSec)} s)"
            tvVideoDetails.text = "Resolución origen: ${width}x${height}"

            btnCompress.text = "Optimizar Video para WhatsApp"
            btnCompress.isEnabled = true

            // Configurar reproductor preview
            videoPreview.setVideoURI(uri)
            videoPreview.setOnPreparedListener { mp ->
                seekBarVideo.max = mp.duration
                tvTotalVideoTime.text = formatSeconds(videoDurationSec)
                tvCurrentVideoTime.text = "00:00"
                btnMarkCutPoint.text = "✂️ Marcar corte en 00:00"
            }

            updateSplitUi()
        } catch (e: Exception) {
            Toast.makeText(this, "Error al leer metadatos de video: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startPhotoOptimization(uri: Uri) {
        btnCompress.isEnabled = false
        btnSelectPhoto.isEnabled = false
        btnSelectVideo.isEnabled = false
        cardSplitOptions.visibility = View.GONE
        layoutProgress.visibility = View.VISIBLE
        layoutResults.visibility = View.GONE
        resultsContainer.removeAllViews()

        progressBar.progress = 0
        tvProgressPercent.text = "0%"
        tvProgressStatus.text = "Cargando foto original..."

        Thread {
            try {
                // 1. Leer rotación EXIF
                runOnUiThread {
                    progressBar.progress = 25
                    tvProgressPercent.text = "25%"
                    tvProgressStatus.text = "Corrigiendo orientación EXIF..."
                }

                var rotation = 0
                contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    rotation = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                }

                // 2. Decodificar la imagen
                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOptions)
                }

                var inSample = 1
                while (boundsOptions.outWidth / inSample > 3840 || boundsOptions.outHeight / inSample > 3840) {
                    inSample *= 2
                }

                runOnUiThread {
                    progressBar.progress = 50
                    tvProgressPercent.text = "50%"
                    tvProgressStatus.text = "Creando lienzo vertical 9:16..."
                }

                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = inSample }
                var rawBmp = contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOptions)
                } ?: throw IllegalStateException("No se pudo cargar la imagen")

                if (rotation != 0) {
                    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                    val rotatedBmp = Bitmap.createBitmap(rawBmp, 0, 0, rawBmp.width, rawBmp.height, matrix, true)
                    rawBmp.recycle()
                    rawBmp = rotatedBmp
                }

                // 3. Crear lienzo vertical 9:16 (1080 x 1920)
                val targetW = 1080
                val targetH = 1920
                val canvasBmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(canvasBmp)
                canvas.drawColor(Color.BLACK)

                val scale = min(targetW.toFloat() / rawBmp.width, targetH.toFloat() / rawBmp.height)
                val scaledW = (rawBmp.width * scale).toInt()
                val scaledH = (rawBmp.height * scale).toInt()
                val left = (targetW - scaledW) / 2f
                val top = (targetH - scaledH) / 2f

                val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                canvas.drawBitmap(rawBmp, null, RectF(left, top, left + scaledW, top + scaledH), paint)

                runOnUiThread {
                    progressBar.progress = 80
                    tvProgressPercent.text = "80%"
                    tvProgressStatus.text = "Guardando en máxima calidad JPEG..."
                }

                val outFile = File(cacheDir, "estado_foto_${System.currentTimeMillis()}.jpg")
                FileOutputStream(outFile).use { out ->
                    canvasBmp.compress(Bitmap.CompressFormat.JPEG, 96, out)
                }

                rawBmp.recycle()
                canvasBmp.recycle()

                runOnUiThread {
                    progressBar.progress = 100
                    tvProgressPercent.text = "100%"
                    tvProgressStatus.text = "¡Foto optimizada con éxito!"

                    layoutProgress.visibility = View.GONE
                    btnCompress.isEnabled = true
                    btnSelectPhoto.isEnabled = true
                    btnSelectVideo.isEnabled = true

                    showGeneratedFiles(listOf(outFile))
                }
            } catch (e: Exception) {
                runOnUiThread {
                    layoutProgress.visibility = View.GONE
                    btnCompress.isEnabled = true
                    btnSelectPhoto.isEnabled = true
                    btnSelectVideo.isEnabled = true
                    Toast.makeText(this@MainActivity, "Error al optimizar foto: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun startVideoCompression(uri: Uri) {
        val segments = calculateSegments()
        if (segments.isEmpty()) {
            Toast.makeText(this, "No hay partes para procesar", Toast.LENGTH_SHORT).show()
            return
        }

        btnCompress.isEnabled = false
        btnSelectPhoto.isEnabled = false
        btnSelectVideo.isEnabled = false
        cardSplitOptions.visibility = View.GONE
        layoutProgress.visibility = View.VISIBLE
        layoutResults.visibility = View.GONE
        resultsContainer.removeAllViews()

        progressBar.progress = 0
        tvProgressPercent.text = "0%"
        tvProgressStatus.text = "Preparando video..."

        Thread {
            try {
                val tempInput = File(cacheDir, "input_temp_${System.currentTimeMillis()}.mp4")
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempInput).use { output ->
                        input.copyTo(output)
                    }
                }

                val generatedFiles = mutableListOf<File>()

                var currentPartStartSec = 0.0
                var currentPartDurationSec = 0.0
                var currentPartIdx = 0

                // Escuchar estadísticas en tiempo real de FFmpeg
                FFmpegKitConfig.enableStatisticsCallback { statistics ->
                    val encodedMs = statistics.time
                    if (encodedMs > 0 && currentPartDurationSec > 0) {
                        val currentOverallMs = (currentPartStartSec * 1000.0) + encodedMs
                        val totalDurationMs = max(videoDurationSec * 1000.0, 1.0)
                        val percent = ((currentOverallMs / totalDurationMs) * 100).toInt().coerceIn(0, 99)

                        runOnUiThread {
                            progressBar.progress = percent
                            tvProgressPercent.text = "$percent%"
                            tvProgressStatus.text = "Codificando parte ${currentPartIdx + 1} de ${segments.size} ($percent%)..."
                        }
                    }
                }

                for ((idx, seg) in segments.withIndex()) {
                    currentPartStartSec = seg.startSec
                    currentPartDurationSec = seg.durationSec
                    currentPartIdx = idx

                    val is1080 = seg.durationSec <= 35.0
                    val w = if (is1080) 1080 else 720
                    val h = if (is1080) 1920 else 1280
                    val calculatedVb = ((14.5 * 8192 - 128 * seg.durationSec) / seg.durationSec).toInt()
                    val vb = min(calculatedVb, if (is1080) 4800 else 3200).coerceAtLeast(1200)
                    val maxrate = (vb * 1.25).toInt()
                    val bufsize = vb * 2

                    val outFile = File(cacheDir, "estado_parte_${seg.partNumber}_${System.currentTimeMillis()}.mp4")

                    val vf = "scale=$w:$h:force_original_aspect_ratio=decrease,pad=$w:$h:(ow-iw)/2:(oh-ih)/2:black,fps=30"

                    val cmd = arrayOf(
                        "-y",
                        "-ss", String.format(Locale.US, "%.2f", seg.startSec),
                        "-t", String.format(Locale.US, "%.2f", seg.durationSec),
                        "-i", tempInput.absolutePath,
                        "-vf", vf,
                        "-c:v", "libx264",
                        "-preset", "medium",
                        "-profile:v", "high",
                        "-level", "4.1",
                        "-pix_fmt", "yuv420p",
                        "-b:v", "${vb}k",
                        "-maxrate", "${maxrate}k",
                        "-bufsize", "${bufsize}k",
                        "-c:a", "aac",
                        "-b:a", "128k",
                        "-ar", "44100",
                        "-movflags", "+faststart",
                        outFile.absolutePath
                    )

                    val session = FFmpegKit.execute(cmd.joinToString(" "))
                    if (ReturnCode.isSuccess(session.returnCode)) {
                        generatedFiles.add(outFile)
                    }
                }

                // Desactivar callback de estadísticas
                FFmpegKitConfig.enableStatisticsCallback(null)
                tempInput.delete()

                runOnUiThread {
                    progressBar.progress = 100
                    tvProgressPercent.text = "100%"
                    tvProgressStatus.text = "¡Video codificado con éxito!"

                    layoutProgress.visibility = View.GONE
                    cardSplitOptions.visibility = View.VISIBLE
                    btnCompress.isEnabled = true
                    btnSelectPhoto.isEnabled = true
                    btnSelectVideo.isEnabled = true

                    if (generatedFiles.isNotEmpty()) {
                        showGeneratedFiles(generatedFiles)
                    } else {
                        Toast.makeText(this@MainActivity, "Error al procesar el video", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                FFmpegKitConfig.enableStatisticsCallback(null)
                runOnUiThread {
                    layoutProgress.visibility = View.GONE
                    cardSplitOptions.visibility = View.VISIBLE
                    btnCompress.isEnabled = true
                    btnSelectPhoto.isEnabled = true
                    btnSelectVideo.isEnabled = true
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun showGeneratedFiles(files: List<File>) {
        layoutResults.visibility = View.VISIBLE
        resultsContainer.removeAllViews()
        generatedFilesList.clear()
        selectedIndices.clear()
        checkBoxesList.clear()

        generatedFilesList.addAll(files)

        // Seleccionar todas por defecto
        for (i in files.indices) {
            selectedIndices.add(i)
        }

        if (files.size <= 1) {
            tvSelectAllToggle.visibility = View.GONE
            tvResultsHeader.text = if (isImage) "Foto lista para publicar:" else "Video listo para publicar:"
        } else {
            tvSelectAllToggle.visibility = View.VISIBLE
            tvSelectAllToggle.text = "Desmarcar todas"
            tvResultsHeader.text = "Selecciona las partes a enviar:"
        }

        for ((index, file) in files.withIndex()) {
            val sizeMb = file.length() / (1024.0 * 1024.0)

            val card = MaterialCardView(this).apply {
                radius = 24f
                strokeColor = Color.parseColor("#334155")
                strokeWidth = 2
                setCardBackgroundColor(Color.parseColor("#1E293B"))
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 16)
                }
                layoutParams = params
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(20, 20, 20, 20)
            }

            val checkBox = CheckBox(this).apply {
                isChecked = true
                val partLabel = if (isImage) "Foto HD" else "Parte ${index + 1}"
                text = "$partLabel (${String.format("%.2f", sizeMb)} MB)"
                setTextColor(Color.WHITE)
                textSize = 15f
                buttonTintList = getColorStateList(R.color.whatsapp_green)

                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedIndices.add(index)
                    } else {
                        selectedIndices.remove(index)
                    }
                    updateShareButtonState()
                }
            }
            checkBoxesList.add(checkBox)

            val spacer = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, 0, 1.0f)
            }

            // Botón individual rápido
            val btnSoloEsta = TextView(this).apply {
                text = "Solo esta ➔"
                setTextColor(Color.parseColor("#38BDF8"))
                textSize = 12f
                setPadding(12, 12, 12, 12)
                setOnClickListener {
                    if (isImage) {
                        sharePhotoToWhatsApp(file)
                    } else {
                        shareVideoToWhatsApp(file)
                    }
                }
            }

            row.addView(checkBox)
            row.addView(spacer)
            if (files.size > 1) {
                row.addView(btnSoloEsta)
            }

            card.addView(row)
            resultsContainer.addView(card)
        }

        updateShareButtonState()
    }

    private fun updateShareButtonState() {
        val count = selectedIndices.size
        val total = generatedFilesList.size

        if (total > 1) {
            tvSelectAllToggle.text = if (count == total) "Desmarcar todas" else "Marcar todas"
        }

        when (count) {
            0 -> {
                btnShareSelected.isEnabled = false
                btnShareSelected.text = "Selecciona al menos una parte"
            }
            1 -> {
                btnShareSelected.isEnabled = true
                val label = if (isImage) "Foto" else "Parte"
                btnShareSelected.text = "📤 Enviar $label a WhatsApp"
            }
            else -> {
                btnShareSelected.isEnabled = true
                btnShareSelected.text = "📤 Enviar $count partes seleccionadas a WhatsApp"
            }
        }
    }

    private fun shareVideoToWhatsApp(file: File) {
        val uri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(shareIntent)
        } catch (e: Exception) {
            val chooser = Intent.createChooser(shareIntent, "Compartir video en WhatsApp")
            startActivity(chooser)
        }
    }

    private fun shareMultipleVideosToWhatsApp(files: List<File>) {
        val uris = ArrayList<Uri>()
        for (file in files) {
            uris.add(
                FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.fileprovider",
                    file
                )
            )
        }

        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "video/mp4"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(shareIntent)
        } catch (e: Exception) {
            val chooser = Intent.createChooser(shareIntent, "Compartir partes seleccionadas")
            startActivity(chooser)
        }
    }

    private fun sharePhotoToWhatsApp(file: File) {
        val uri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(shareIntent)
        } catch (e: Exception) {
            val chooser = Intent.createChooser(shareIntent, "Compartir foto en WhatsApp")
            startActivity(chooser)
        }
    }
}

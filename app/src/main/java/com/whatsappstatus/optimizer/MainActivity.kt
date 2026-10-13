package com.whatsappstatus.optimizer

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private lateinit var tvVideoTitle: TextView
    private lateinit var tvVideoDetails: TextView
    private lateinit var tvOptimizationBadge: TextView
    private lateinit var btnSelectVideo: Button
    private lateinit var btnCompress: Button
    private lateinit var layoutProgress: View
    private lateinit var tvProgressStatus: TextView
    private lateinit var layoutResults: View
    private lateinit var resultsContainer: LinearLayout

    private var selectedVideoUri: Uri? = null
    private var videoDurationSec: Double = 0.0

    private val selectVideoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onVideoSelected(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvVideoTitle = findViewById(R.id.tvVideoTitle)
        tvVideoDetails = findViewById(R.id.tvVideoDetails)
        tvOptimizationBadge = findViewById(R.id.tvOptimizationBadge)
        btnSelectVideo = findViewById(R.id.btnSelectVideo)
        btnCompress = findViewById(R.id.btnCompress)
        layoutProgress = findViewById(R.id.layoutProgress)
        tvProgressStatus = findViewById(R.id.tvProgressStatus)
        layoutResults = findViewById(R.id.layoutResults)
        resultsContainer = findViewById(R.id.resultsContainer)

        btnSelectVideo.setOnClickListener {
            selectVideoLauncher.launch("video/*")
        }

        btnCompress.setOnClickListener {
            selectedVideoUri?.let { uri -> startCompression(uri) }
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
                    val latestTag = json.optString("tag_name", "v1.0")
                    val currentVersion = "v1.0"

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
                                .setMessage("Hay una nueva versión de Status HD disponible. ¿Deseas descargarla ahora?\\n(Se actualizará sin perder tus datos).")
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
        if (Intent.ACTION_SEND == intent.action && intent.type?.startsWith("video/") == true) {
            val videoUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            videoUri?.let { onVideoSelected(it) }
        }
    }

    private fun onVideoSelected(uri: Uri) {
        selectedVideoUri = uri
        layoutResults.visibility = View.GONE
        resultsContainer.removeAllViews()

        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            videoDurationSec = durationMs / 1000.0

            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) ?: "1080"
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) ?: "1920"
            retriever.release()

            val numParts = ceil(videoDurationSec / 60.0).toInt().coerceAtLeast(1)

            tvVideoTitle.text = "Video cargado (${String.format("%.1f", videoDurationSec)} s)"
            tvVideoDetails.text = "Resolución origen: ${width}x${height} | Partes a generar: $numParts"

            if (videoDurationSec <= 35) {
                tvOptimizationBadge.text = "Modo: 1080p Máxima Nitidez (Tope ~14.5MB)"
            } else {
                tvOptimizationBadge.text = "Modo: 720p Optimizado 60s (Tope ~14.5MB)"
            }

            btnCompress.isEnabled = true
        } catch (e: Exception) {
            Toast.makeText(this, "Error al leer metadatos: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCompression(uri: Uri) {
        btnCompress.isEnabled = false
        btnSelectVideo.isEnabled = false
        layoutProgress.visibility = View.VISIBLE
        layoutResults.visibility = View.GONE
        resultsContainer.removeAllViews()

        Thread {
            try {
                // Copiar el video a la cache temporal local para que FFmpeg pueda acceder al archivo físico
                tvProgressStatus.post { tvProgressStatus.text = "Preparando archivo..." }
                val tempInput = File(cacheDir, "input_temp_${System.currentTimeMillis()}.mp4")
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempInput).use { output ->
                        input.copyTo(output)
                    }
                }

                val segmentLimit = 60.0
                val numParts = ceil(videoDurationSec / segmentLimit).toInt().coerceAtLeast(1)
                val generatedFiles = mutableListOf<File>()

                for (i in 0 until numParts) {
                    val start = i * segmentLimit
                    val rem = min(segmentLimit, videoDurationSec - start)
                    if (rem < 2.0 && i > 0) break

                    val is1080 = rem <= 35.0
                    val w = if (is1080) 1080 else 720
                    val h = if (is1080) 1920 else 1280
                    val calculatedVb = ((14.5 * 8192 - 128 * rem) / rem).toInt()
                    val vb = min(calculatedVb, if (is1080) 4800 else 3200).coerceAtLeast(1200)
                    val maxrate = (vb * 1.25).toInt()
                    val bufsize = vb * 2

                    val outFile = File(cacheDir, "estado_parte_${i + 1}_${System.currentTimeMillis()}.mp4")

                    val vf = "scale=$w:$h:force_original_aspect_ratio=decrease,pad=$w:$h:(ow-iw)/2:(oh-ih)/2:black,fps=30"

                    val cmd = arrayOf(
                        "-y",
                        "-ss", start.toString(),
                        "-t", rem.toString(),
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

                    tvProgressStatus.post {
                        tvProgressStatus.text = "Codificando parte ${i + 1} de $numParts (${if (is1080) "1080p" else "720p"})..."
                    }

                    val session = FFmpegKit.execute(cmd.joinToString(" "))
                    if (ReturnCode.isSuccess(session.returnCode)) {
                        generatedFiles.add(outFile)
                    }
                }

                tempInput.delete()

                runOnUiThread {
                    layoutProgress.visibility = View.GONE
                    btnCompress.isEnabled = true
                    btnSelectVideo.isEnabled = true

                    if (generatedFiles.isNotEmpty()) {
                        layoutResults.visibility = View.VISIBLE
                        showGeneratedFiles(generatedFiles)

                        // Si solo se generó 1 parte, abre directamente WhatsApp
                        if (generatedFiles.size == 1) {
                            shareToWhatsApp(generatedFiles[0])
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "Error al procesar el video", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    layoutProgress.visibility = View.GONE
                    btnCompress.isEnabled = true
                    btnSelectVideo.isEnabled = true
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun showGeneratedFiles(files: List<File>) {
        resultsContainer.removeAllViews()
        for ((index, file) in files.withIndex()) {
            val sizeMb = file.length() / (1024.0 * 1024.0)
            val btn = MaterialButton(this).apply {
                text = "📤 Parte ${index + 1} (${String.format("%.2f", sizeMb)} MB) ➔ Publicar en WhatsApp"
                setBackgroundColor(getColor(R.color.whatsapp_green))
                setTextColor(getColor(android.R.color.black))
                cornerRadius = 24
                setOnClickListener {
                    shareToWhatsApp(file)
                }
            }
            resultsContainer.addView(btn)
        }
    }

    private fun shareToWhatsApp(file: File) {
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
            // Si WhatsApp no está instalado o no acepta el paquete directo, abre el selector universal
            val chooser = Intent.createChooser(shareIntent, "Compartir video optimizado")
            startActivity(chooser)
        }
    }
}

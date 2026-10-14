package com.whatsappstatus.optimizer

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.media.ExifInterface
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
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private lateinit var tvVideoTitle: TextView
    private lateinit var tvVideoDetails: TextView
    private lateinit var tvOptimizationBadge: TextView
    private lateinit var btnSelectPhoto: Button
    private lateinit var btnSelectVideo: Button
    private lateinit var btnCompress: Button
    private lateinit var layoutProgress: View
    private lateinit var tvProgressStatus: TextView
    private lateinit var layoutResults: View
    private lateinit var resultsContainer: LinearLayout

    private var selectedMediaUri: Uri? = null
    private var isImage: Boolean = false
    private var videoDurationSec: Double = 0.0

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
        tvProgressStatus = findViewById(R.id.tvProgressStatus)
        layoutResults = findViewById(R.id.layoutResults)
        resultsContainer = findViewById(R.id.resultsContainer)

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
                    val latestTag = json.optString("tag_name", "v1.2")
                    val currentVersion = "v1.2"

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

        if (isPhoto) {
            handlePhotoSelected(uri)
        } else {
            handleVideoSelected(uri)
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
            tvVideoDetails.text = "Formato 9:16 vertical • Ajuste proporcional sin deformar"
            tvOptimizationBadge.text = "Modo: Foto HD (Máxima Nitidez JPEG 96%)"
            btnCompress.text = "Optimizar Foto para WhatsApp"
            btnCompress.isEnabled = true
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

            val numParts = ceil(videoDurationSec / 60.0).toInt().coerceAtLeast(1)

            tvVideoTitle.text = "Video cargado (${String.format("%.1f", videoDurationSec)} s)"
            tvVideoDetails.text = "Resolución origen: ${width}x${height} | Partes a generar: $numParts"

            if (videoDurationSec <= 35) {
                tvOptimizationBadge.text = "Modo: 1080p Máxima Nitidez (Tope ~14.5MB)"
            } else {
                tvOptimizationBadge.text = "Modo: 720p Optimizado 60s (Tope ~14.5MB)"
            }

            btnCompress.text = "Optimizar Video para WhatsApp"
            btnCompress.isEnabled = true
        } catch (e: Exception) {
            Toast.makeText(this, "Error al leer metadatos de video: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startPhotoOptimization(uri: Uri) {
        btnCompress.isEnabled = false
        btnSelectPhoto.isEnabled = false
        btnSelectVideo.isEnabled = false
        layoutProgress.visibility = View.VISIBLE
        layoutResults.visibility = View.GONE
        resultsContainer.removeAllViews()

        tvProgressStatus.text = "Optimizando foto en alta definición (9:16)..."

        Thread {
            try {
                // 1. Leer rotación EXIF
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

                // 2. Decodificar la imagen asegurando que no exceda memoria
                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOptions)
                }

                var inSample = 1
                while (boundsOptions.outWidth / inSample > 3840 || boundsOptions.outHeight / inSample > 3840) {
                    inSample *= 2
                }

                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = inSample }
                var rawBmp = contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOptions)
                } ?: throw IllegalStateException("No se pudo cargar la imagen")

                // Rotar si es necesario
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

                // 4. Guardar como JPEG de alta fidelidad (96%)
                val outFile = File(cacheDir, "estado_foto_${System.currentTimeMillis()}.jpg")
                FileOutputStream(outFile).use { out ->
                    canvasBmp.compress(Bitmap.CompressFormat.JPEG, 96, out)
                }

                rawBmp.recycle()
                canvasBmp.recycle()

                runOnUiThread {
                    layoutProgress.visibility = View.GONE
                    btnCompress.isEnabled = true
                    btnSelectPhoto.isEnabled = true
                    btnSelectVideo.isEnabled = true

                    val sizeMb = outFile.length() / (1024.0 * 1024.0)
                    layoutResults.visibility = View.VISIBLE
                    resultsContainer.removeAllViews()

                    val btn = MaterialButton(this@MainActivity).apply {
                        text = "📤 Foto HD (${String.format("%.2f", sizeMb)} MB) ➔ Publicar en WhatsApp"
                        setBackgroundColor(getColor(R.color.whatsapp_green))
                        setTextColor(getColor(android.R.color.black))
                        cornerRadius = 24
                        setOnClickListener {
                            sharePhotoToWhatsApp(outFile)
                        }
                    }
                    resultsContainer.addView(btn)

                    // Abrir directamente WhatsApp
                    sharePhotoToWhatsApp(outFile)
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
        btnCompress.isEnabled = false
        btnSelectPhoto.isEnabled = false
        btnSelectVideo.isEnabled = false
        layoutProgress.visibility = View.VISIBLE
        layoutResults.visibility = View.GONE
        resultsContainer.removeAllViews()

        Thread {
            try {
                tvProgressStatus.post { tvProgressStatus.text = "Preparando archivo de video..." }
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
                    btnSelectPhoto.isEnabled = true
                    btnSelectVideo.isEnabled = true

                    if (generatedFiles.isNotEmpty()) {
                        layoutResults.visibility = View.VISIBLE
                        showGeneratedFiles(generatedFiles)

                        if (generatedFiles.size == 1) {
                            shareVideoToWhatsApp(generatedFiles[0])
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "Error al procesar el video", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    layoutProgress.visibility = View.GONE
                    btnCompress.isEnabled = true
                    btnSelectPhoto.isEnabled = true
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
                    shareVideoToWhatsApp(file)
                }
            }
            resultsContainer.addView(btn)
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
            val chooser = Intent.createChooser(shareIntent, "Compartir video optimizado")
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
            val chooser = Intent.createChooser(shareIntent, "Compartir foto optimizada")
            startActivity(chooser)
        }
    }
}

package com.screentextcopier.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class OcrCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private val handler = Handler(Looper.getMainLooper())
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private var interval = 3000L
    private val task = object : Runnable {
        override fun run() {
            val image = reader?.acquireLatestImage()
            if (image != null) {
                try {
                    val plane = image.planes[0]
                    val buffer = plane.buffer
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val rowPadding = rowStride - pixelStride * image.width
                    val bitmap = Bitmap.createBitmap(image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
                    bitmap.copyPixelsFromBuffer(buffer)
                    val cropped = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
                    recognizer.process(InputImage.fromBitmap(cropped, 0)).addOnSuccessListener { result ->
                        CaptureStore.save(this@OcrCaptureService, result.text, "OCR")
                    }.addOnCompleteListener { cropped.recycle(); bitmap.recycle() }
                } catch (_: Exception) { } finally { image.close() }
            }
            if (projection != null) handler.postDelayed(this, interval)
        }
    }
    override fun onCreate() { super.onCreate(); val ch = NotificationChannel("ocr_capture", "Screen OCR", NotificationManager.IMPORTANCE_LOW); getSystemService(NotificationManager::class.java).createNotificationChannel(ch) }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopCapture(); stopSelf(); return START_NOT_STICKY }
        val data = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java) else @Suppress("DEPRECATION") intent?.getParcelableExtra(EXTRA_DATA)
        val code = intent?.getIntExtra(EXTRA_RESULT, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        interval = (intent?.getLongExtra(EXTRA_INTERVAL, 3L) ?: 3L).coerceIn(1, 30) * 1000L
        if (data == null || code != Activity.RESULT_OK) { stopSelf(); return START_NOT_STICKY }
        startForeground(4208, NotificationCompat.Builder(this, "ocr_capture").setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("Screen Text Copier").setContentText("Screen OCR is active — tap Stop to end").setOngoing(true).addAction(0, "Stop OCR", PendingIntent.getService(this, 9, Intent(this, OcrCaptureService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build(), if (Build.VERSION.SDK_INT >= 29) android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0)
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mgr.getMediaProjection(code, data)
        val metrics = resources.displayMetrics
        reader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
        display = projection?.createVirtualDisplay("ScreenTextCopier", metrics.widthPixels, metrics.heightPixels, metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader?.surface, null, handler)
        handler.post(task)
        return START_NOT_STICKY
    }
    private fun stopCapture() { handler.removeCallbacks(task); display?.release(); reader?.close(); projection?.stop(); display = null; reader = null; projection = null }
    override fun onDestroy() { stopCapture(); recognizer.close(); super.onDestroy() }
    override fun onBind(intent: Intent?) = null
    companion object { const val ACTION_STOP="OCR_STOP"; const val EXTRA_DATA="data"; const val EXTRA_RESULT="result"; const val EXTRA_INTERVAL="interval" }
}

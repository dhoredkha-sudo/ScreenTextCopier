package com.screentextcopier.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.ClipboardManager
import android.content.ClipData
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.LinearGradient
import android.graphics.Shader
import android.view.View
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

class FloatingService : Service() {
    private lateinit var wm: WindowManager
    private var root: LinearLayout? = null
    private var menu: LinearLayout? = null
    private var expanded = false
    private var capturing = false
    private lateinit var clipboardManager: ClipboardManager
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (capturing) saveCurrentClipboard()
    }

    override fun onCreate() {
        super.onCreate()
        active = true
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
        createChannel()
        startForeground(NOTIFICATION_ID, notification("Floating control is ready"))
        showOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { capturing = false; startService(Intent(this, OcrCaptureService::class.java).setAction(OcrCaptureService.ACTION_STOP)); stopSelf(); return START_NOT_STICKY }
            ACTION_CAPTURE_START -> { capturing = true; saveCurrentClipboard(); updateNotification("Clipboard watch enabled — visible session") }
            ACTION_CAPTURE_STOP -> { capturing = false; updateNotification("Capture stopped") }
        }
        return START_STICKY
    }

    private fun showOverlay() {
        val outer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(8, 8, 8, 8); background = rounded(0xDDF5F8FF.toInt(), 48f, 0x5537BFFF) }
        val avatar = HackerAvatarView(this).apply {
            layoutParams = LinearLayout.LayoutParams(58, 58)
            setOnClickListener { expanded = !expanded; menu?.visibility = if (expanded) View.VISIBLE else View.GONE }
        }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; visibility = View.GONE; setPadding(8, 0, 4, 0) }
        val play = TextView(this).apply { text = "▶"; textSize = 24f; gravity = Gravity.CENTER; setTextColor(AndroidColor.WHITE); background = rounded(0xFF087BFF.toInt(), 32f, 0xFF25D9FF.toInt()); layoutParams = LinearLayout.LayoutParams(82, 54).apply { rightMargin = 8 }; setOnClickListener { capturing = true; saveCurrentClipboard(); updateNotification("Clipboard watch enabled — visible session") } }
        val stop = TextView(this).apply { text = "■"; textSize = 24f; gravity = Gravity.CENTER; setTextColor(AndroidColor.WHITE); background = rounded(0xFFB12BCE.toInt(), 32f, 0xFFFF3DAD.toInt()); layoutParams = LinearLayout.LayoutParams(82, 54); setOnClickListener { capturing = false; startService(Intent(this@FloatingService, OcrCaptureService::class.java).setAction(OcrCaptureService.ACTION_STOP)); updateNotification("Capture stopped") } }
        actions.addView(play); actions.addView(stop)
        outer.addView(avatar); outer.addView(actions); menu = actions; root = outer
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL; x = 10; y = 0 }
        try { wm.addView(outer, params) } catch (_: Exception) { stopSelf() }
    }

    private fun saveCurrentClipboard() {
        if (!capturing) return
        try {
            if (!clipboardManager.hasPrimaryClip()) return
            val clip: ClipData = clipboardManager.primaryClip ?: return
            if (clip.itemCount <= 0) return
            val item = clip.getItemAt(0)
            val text = item.coerceToText(this)?.toString() ?: return
            CaptureStore.save(this, text, "clipboard")
        } catch (_: SecurityException) {
            // Android 10+ may restrict clipboard reads while this app is not foreground.
            // The listener remains active; the OS may allow a later read when the user returns.
        } catch (_: Exception) { }
    }

    private fun rounded(color: Int, radius: Float, stroke: Int) = GradientDrawable().apply { setColor(color); cornerRadius = radius; setStroke(2, stroke) }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Floating capture controls", NotificationManager.IMPORTANCE_LOW).apply { description = "Shows when the floating control is active" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, FloatingService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("Screen Text Copier").setContentText(text).setContentIntent(open).setOngoing(true).addAction(android.R.drawable.ic_media_pause, "Stop floating tool", stop).build()
    }

    private fun updateNotification(text: String) { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text)) }

    override fun onDestroy() {
        active = false; capturing = false
        try { clipboardManager.removePrimaryClipChangedListener(clipboardListener) } catch (_: Exception) { }
        try { root?.let { wm.removeView(it) } } catch (_: Exception) { }
        root = null; menu = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.screentextcopier.START"
        const val ACTION_STOP = "com.screentextcopier.STOP"
        const val ACTION_CAPTURE_START = "com.screentextcopier.CAPTURE_START"
        const val ACTION_CAPTURE_STOP = "com.screentextcopier.CAPTURE_STOP"
        const val CHANNEL_ID = "screen_text_copier_floating"
        const val NOTIFICATION_ID = 4107
        @Volatile var active = false
    }
}

private class HackerAvatarView(context: android.content.Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        paint.shader = LinearGradient(0f, 0f, w, h, intArrayOf(0xFF20DFFF.toInt(), 0xFF7650FF.toInt(), 0xFFFF4CB8.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(w/2, h/2, w*.49f, paint)
        paint.shader = null; paint.color = 0xFF101A3B.toInt(); canvas.drawCircle(w/2, h/2, w*.43f, paint)
        val hood = Path().apply { moveTo(w*.5f,h*.13f); cubicTo(w*.20f,h*.18f,w*.10f,h*.55f,w*.16f,h*.84f); lineTo(w*.84f,h*.84f); cubicTo(w*.90f,h*.55f,w*.80f,h*.18f,w*.5f,h*.13f); close() }
        paint.shader = LinearGradient(0f, h*.1f, w, h*.9f, 0xFF07142D.toInt(), 0xFF45116D.toInt(), Shader.TileMode.CLAMP); canvas.drawPath(hood, paint); paint.shader = null
        val face = Path().apply { moveTo(w*.27f,h*.46f); quadraticTo(w*.5f,h*.30f,w*.73f,h*.46f); lineTo(w*.65f,h*.72f); quadraticTo(w*.5f,h*.82f,w*.35f,h*.72f); close() }
        paint.color = 0xFF020817.toInt(); canvas.drawPath(face, paint)
        paint.color = 0xFF25DFFF.toInt(); paint.strokeWidth = w*.055f; paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(w*.34f,h*.52f,w*.46f,h*.56f,paint); canvas.drawLine(w*.54f,h*.56f,w*.66f,h*.52f,paint)
    }
}

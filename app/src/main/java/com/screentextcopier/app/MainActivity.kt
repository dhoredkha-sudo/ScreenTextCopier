package com.screentextcopier.app

import android.Manifest
import android.content.Intent
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import java.io.File

private val Bg = Color(0xFF080B18)
private val Panel = Color(0xFF11182B)
private val Muted = Color(0xFF8994B2)
private val Cyan = Color(0xFF25D9FF)
private val Blue = Color(0xFF087BFF)
private val Purple = Color(0xFF852BFF)
private val Pink = Color(0xFFFF3DAD)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ScreenTextApp() }
    }
}

@Composable
fun ScreenTextApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var running by remember { mutableStateOf(FloatingService.active) }
    var ocr by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(true) }
    var keyword by remember { mutableStateOf("") }
    var clipboardWatch by remember { mutableStateOf(false) }
    var interval by remember { mutableFloatStateOf(3f) }
    var savedCount by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showSaved by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val screenCaptureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val i = Intent(context, OcrCaptureService::class.java).apply {
                putExtra(OcrCaptureService.EXTRA_RESULT, result.resultCode)
                putExtra(OcrCaptureService.EXTRA_DATA, result.data)
                putExtra(OcrCaptureService.EXTRA_INTERVAL, interval.toLong())
            }
            ContextCompat.startForegroundService(context, i); ocr = true
        } else { ocr = false; message = "Screen capture permission was not granted." }
    }
    LaunchedEffect(Unit) {
        val f = File(context.filesDir, "captured_texts.txt")
        savedCount = if (f.exists()) f.readLines().count { it.startsWith("---------------- ENTRY") } else 0
        CaptureStore.load(context)
        keyword = CaptureStore.watchWords
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, background = Bg, surface = Panel)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF101A35), Bg, Color(0xFF090B16))))) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 34.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HackerAvatar(54)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("SCREEN TEXT", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                            Text("COPIER  /  LOCAL CAPTURE", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                        }
                        Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(.07f)).clickable { showSettings = true }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Tune, null, tint = Color.White)
                        }
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(Color(0xFF172747), Color(0xFF17152F), Color(0xFF21152E)))).border(1.dp, Color.White.copy(.10f), RoundedCornerShape(26.dp)).padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("CONTROL CENTER", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                Text(if (running) "Capture is active" else "Ready when you are", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Text(if (running) "Floating control is running" else "Start the floating control to begin", color = Muted, fontSize = 12.sp)
                            }
                            Box(Modifier.size(12.dp).clip(CircleShape).background(if (running) Color(0xFF39E6A4) else Color(0xFF65708B)))
                        }
                        Spacer(Modifier.height(24.dp))
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Color.White.copy(.07f)).border(1.dp, Color.White.copy(.14f), RoundedCornerShape(30.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            GradientAction("▶", "START", listOf(Color(0xFF00CFFF), Color(0xFF086BFF)), Modifier.weight(1f), running) {
                                if (!Settings.canDrawOverlays(context)) {
                                    message = "Allow Display over other apps to use the floating control."
                                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                                } else {
                                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    ContextCompat.startForegroundService(context, Intent(context, FloatingService::class.java).setAction(FloatingService.ACTION_START))
                                    running = true
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            GradientAction("■", "STOP", listOf(Purple, Pink), Modifier.weight(1f), !running) {
                                context.startService(Intent(context, FloatingService::class.java).setAction(FloatingService.ACTION_CAPTURE_STOP))
                                context.startService(Intent(context, OcrCaptureService::class.java).setAction(OcrCaptureService.ACTION_STOP))
                                context.stopService(Intent(context, FloatingService::class.java)); running = false; ocr = false; clipboardWatch = false
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Floating control stays visible above other apps. Capture is user-controlled.", color = Muted, fontSize = 11.sp)
                    }
                }
                item {
                    Text("LIVE FLOATING TOOL", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(.045f)).border(1.dp, Color.White.copy(.09f), RoundedCornerShape(22.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        HackerAvatar(62)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Hacker quick control", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Tap avatar to expand / collapse", color = Muted, fontSize = 11.sp)
                        }
                        Switch(checked = expanded, onCheckedChange = { expanded = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Purple))
                    }
                }
                item {
                    Text("CAPTURE OPTIONS", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                    Spacer(Modifier.height(10.dp))
                    SettingRow(Icons.Rounded.ContentCopy, "Clipboard saving", "Automatically save copied text while capture is active", clipboardWatch) { enabled ->
                        clipboardWatch = enabled
                        val action = if (enabled) FloatingService.ACTION_CAPTURE_START else FloatingService.ACTION_CAPTURE_STOP
                        if (FloatingService.active) context.startService(Intent(context, FloatingService::class.java).setAction(action))
                        else if (enabled) { message = "Start the floating service first, then enable Clipboard saving."; clipboardWatch = false }
                    }
                    Spacer(Modifier.height(9.dp))
                    SettingRow(Icons.Rounded.DocumentScanner, "Screen OCR watch", "Captures screen only after Android approval", ocr) { enabled ->
                        if (enabled) { val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager; screenCaptureLauncher.launch(mgr.createScreenCaptureIntent()) }
                        else { context.startService(Intent(context, OcrCaptureService::class.java).setAction(OcrCaptureService.ACTION_STOP)); ocr = false }
                    }
                    Spacer(Modifier.height(9.dp))
                    SettingRow(Icons.Rounded.FilterAlt, "Watch words", if (keyword.isBlank()) "All text will be accepted" else keyword, false) { showSettings = true }
                }
                item {
                    Text("YOUR LOCAL ARCHIVE", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard("$savedCount", "SAVED ENTRIES", Icons.Rounded.Description, Modifier.weight(1f))
                        StatCard("1", "LOCAL TEXT FILE", Icons.Rounded.Folder, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; val clip = cm.primaryClip; val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(context).toString() else ""; val ok = CaptureStore.save(context, text, "CLIPBOARD"); savedCount = File(context.filesDir, "captured_texts.txt").let { if (it.exists()) it.readLines().count { line -> line.startsWith("---------------- ENTRY") } else 0 }; message = if (ok) "Clipboard text saved to captured_texts.txt" else "No new clipboard text matched your filter." }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Cyan.copy(.5f))) { Icon(Icons.Rounded.ContentCopy, null, tint = Cyan); Spacer(Modifier.width(6.dp)); Text("SAVE CLIPBOARD", color = Color.White) }; OutlinedButton(onClick = { showSaved = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Cyan.copy(.5f))) { Icon(Icons.Rounded.Visibility, null, tint = Cyan); Spacer(Modifier.width(6.dp)); Text("VIEW FILE", color = Color.White) }
                        OutlinedButton(onClick = { CaptureStore.clear(context); savedCount = 0; message = "Local text file cleared." }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Pink.copy(.5f))) { Icon(Icons.Rounded.DeleteOutline, null, tint = Pink); Spacer(Modifier.width(6.dp)); Text("CLEAR FILE", color = Color.White) }
                    }
                }
                item { Text("PRIVATE BY DESIGN  •  NO CLOUD  •  NO ACCOUNT", modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = Muted.copy(.75f), fontSize = 9.sp, letterSpacing = 1.2.sp) }
            }

            if (showSettings) AlertDialog(onDismissRequest = { showSettings = false }, containerColor = Color(0xFF151D33), title = { Text("Capture settings", color = Color.White) }, text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Watch words (comma separated)", color = Muted, fontSize = 12.sp)
                    OutlinedTextField(value = keyword, onValueChange = { keyword = it; CaptureStore.setWatchWords(context, it) }, placeholder = { Text("invoice, order, reference") }, singleLine = false)
                    Text("OCR check interval: ${interval.toInt()} seconds", color = Color.White)
                    Slider(value = interval, onValueChange = { interval = it }, valueRange = 1f..10f, steps = 8)
                    Text("Saved locally in app-private storage.", color = Muted, fontSize = 11.sp)
                }
            }, confirmButton = { TextButton(onClick = { showSettings = false }) { Text("DONE", color = Cyan) } })
            if (showSaved) AlertDialog(onDismissRequest = { showSaved = false }, containerColor = Color(0xFF151D33), title = { Text("captured_texts.txt", color = Color.White) }, text = {
                val content = remember { File(context.filesDir, "captured_texts.txt").let { if (it.exists()) it.readText() else "No saved text yet." } }
                Text(content, color = Color(0xFFD7E2FF), fontSize = 11.sp, maxLines = 18)
            }, confirmButton = { TextButton(onClick = { showSaved = false }) { Text("CLOSE", color = Cyan) } })
            message?.let { msg -> AlertDialog(onDismissRequest = { message = null }, title = { Text("Screen Text Copier", color = Color.White) }, text = { Text(msg, color = Color.White) }, confirmButton = { TextButton(onClick = { message = null }) { Text("OK", color = Cyan) } }, containerColor = Panel) }
        }
    }
}

@Composable
private fun GradientAction(symbol: String, label: String, colors: List<Color>, modifier: Modifier, disabled: Boolean, onClick: () -> Unit) {
    Column(modifier.height(82.dp).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(colors)).clickable(enabled = !disabled, onClick = onClick).alpha(if (disabled) .42f else 1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(symbol, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light)
        Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
    }
}

@Composable
private fun HackerAvatar(size: Int) {
    Canvas(Modifier.size(size.dp).shadow(10.dp, CircleShape).clip(CircleShape).background(Brush.linearGradient(listOf(Cyan, Purple, Pink))).padding(3.dp)) {
        drawCircle(Color(0xFF101A3B))
        val w = this.size.width; val h = this.size.height
        val hood = Path().apply { moveTo(w*.5f,h*.12f); cubicTo(w*.22f,h*.2f,w*.12f,h*.55f,w*.16f,h*.82f); lineTo(w*.84f,h*.82f); cubicTo(w*.88f,h*.55f,w*.78f,h*.2f,w*.5f,h*.12f); close() }
        drawPath(hood, Brush.linearGradient(listOf(Color(0xFF07142D), Color(0xFF32105D))))
        val face = Path().apply { moveTo(w*.28f,h*.47f); quadraticTo(w*.5f,h*.28f,w*.72f,h*.47f); lineTo(w*.65f,h*.72f); quadraticTo(w*.5f,h*.83f,w*.35f,h*.72f); close() }
        drawPath(face, Color(0xFF020817))
        drawLine(Cyan, androidx.compose.ui.geometry.Offset(w*.34f,h*.52f), androidx.compose.ui.geometry.Offset(w*.47f,h*.56f), w*.055f)
        drawLine(Cyan, androidx.compose.ui.geometry.Offset(w*.53f,h*.56f), androidx.compose.ui.geometry.Offset(w*.66f,h*.52f), w*.055f)
        drawCircle(Color(0xFF172B58), w*.07f, androidx.compose.ui.geometry.Offset(w*.5f,h*.91f))
    }
}

@Composable
private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).border(1.dp, Color.White.copy(.06f), RoundedCornerShape(18.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(Cyan.copy(.10f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Cyan) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp); Text(subtitle, color = Muted, fontSize = 10.sp) }
        if (title == "Screen OCR watch") Switch(checked = checked, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Purple)) else Icon(Icons.Rounded.ChevronRight, null, tint = Muted)
    }
}

@Composable
private fun StatCard(value: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Panel).border(1.dp, Color.White.copy(.06f), RoundedCornerShape(18.dp)).padding(15.dp)) {
        Icon(icon, null, tint = Cyan, modifier = Modifier.size(20.dp)); Spacer(Modifier.height(9.dp)); Text(value, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold); Text(label, color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
    }
}

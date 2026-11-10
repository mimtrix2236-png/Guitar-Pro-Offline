package com.mimtrix.guitarprooffline

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class Screen(val title: String) { TUNER("Afinador"), TONES("Tonos"), METRO("Metrónomo"), SETTINGS("Ajustes") }

class MainActivity : ComponentActivity() {
    private var permissionGranted by mutableStateOf(false)
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        setContent { GuitarProApp(this, permissionGranted) }
    }

    fun requestMic() { permission.launch(Manifest.permission.RECORD_AUDIO) }
}

@Composable
private fun GuitarProApp(activity: MainActivity, hasMicPermission: Boolean) {
    val prefs = remember { activity.getSharedPreferences("guitar_pro_settings", Context.MODE_PRIVATE) }
    var screen by remember { mutableStateOf(Screen.TUNER) }
    var instrument by remember { mutableStateOf(prefs.getString("instrument", "Guitarra") ?: "Guitarra") }
    var tuningName by remember { mutableStateOf(prefs.getString("tuning", "Estándar") ?: "Estándar") }
    var a4 by remember { mutableFloatStateOf(prefs.getFloat("a4", 440f)) }
    var notation by remember { mutableStateOf(prefs.getBoolean("solfege", true)) }
    var tolerance by remember { mutableFloatStateOf(prefs.getFloat("tolerance", 5f)) }
    var listening by remember { mutableStateOf(false) }
    var pitch by remember { mutableStateOf<Pitch?>(null) }
    var bpm by remember { mutableFloatStateOf(100f) }
    var metronomeOn by remember { mutableStateOf(false) }
    var selectedTone by remember { mutableStateOf("A4") }
    val catalog = remember { TuningCatalog.all }
    val currentTunings = catalog.filter { it.instrument == instrument }
    val selectedTuning = currentTunings.firstOrNull { it.name == tuningName } ?: currentTunings.first()
    val toneNames = listOf("C3", "D3", "E3", "F3", "G3", "A3", "B3", "C4", "D4", "E4", "F4", "G4", "A4", "B4", "C5")

    LaunchedEffect(listening, hasMicPermission, a4) {
        if (!listening || !hasMicPermission) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val rate = 44100
            val minBytes = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBytes <= 0) {
                withContext(Dispatchers.Main) { listening = false }
                return@withContext
            }
            val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBytes * 2, 8192))
            try {
                recorder.startRecording()
                val buffer = ShortArray(8192)
                while (isActive && listening) {
                    val count = recorder.read(buffer, 0, buffer.size)
                    if (count > 0) {
                        val result = TunerEngine.detect(buffer.copyOf(count), rate, a4.toDouble())
                        withContext(Dispatchers.Main) { if (result != null) pitch = result }
                    }
                }
            } catch (_: SecurityException) {
                withContext(Dispatchers.Main) { listening = false }
            } catch (_: IllegalStateException) {
                withContext(Dispatchers.Main) { listening = false }
            } finally {
                try { recorder.stop() } catch (_: Exception) {}
                recorder.release()
            }
        }
    }

    LaunchedEffect(metronomeOn, bpm) {
        if (!metronomeOn) return@LaunchedEffect
        val generator = ToneGenerator(AudioManager.STREAM_MUSIC, 75)
        try {
            while (isActive && metronomeOn) {
                generator.startTone(ToneGenerator.TONE_PROP_BEEP, 65)
                delay((60000f / bpm).toLong().coerceAtLeast(100L))
            }
        } finally { generator.release() }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFFB6F36A), secondary = Color(0xFF85D6C5),
        background = Color(0xFF101411), surface = Color(0xFF1B221D),
        onBackground = Color(0xFFEAF0E8), onSurface = Color(0xFFEAF0E8)
    )) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    Screen.entries.forEach { item ->
                        NavigationBarItem(selected = screen == item, onClick = { screen = item }, icon = {
                            Text(when(item) { Screen.TUNER -> "♩"; Screen.TONES -> "♫"; Screen.METRO -> "◷"; Screen.SETTINGS -> "⚙" }, fontSize = 20.sp)
                        }, label = { Text(item.title) })
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(18.dp))
                Text("GUITAR PRO", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("OFFLINE", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f), fontSize = 11.sp, letterSpacing = 3.sp)
                Spacer(Modifier.height(18.dp))
                when (screen) {
                    Screen.TUNER -> TunerPage(
                        instrument, { instrument = it; tuningName = catalog.first { t -> t.instrument == it }.name; prefs.edit().putString("instrument", it).putString("tuning", tuningName).apply() },
                        currentTunings, selectedTuning, { tuningName = it; prefs.edit().putString("tuning", it).apply() },
                        listening, { if (!hasMicPermission) activity.requestMic() else listening = !listening },
                        pitch, a4.toDouble(), tolerance.toDouble(), notation
                    )
                    Screen.TONES -> TonePage(toneNames, selectedTone, { selectedTone = it }, a4.toDouble())
                    Screen.METRO -> MetroPage(bpm, { bpm = it }, metronomeOn, { metronomeOn = it })
                    Screen.SETTINGS -> SettingsPage(a4, { a4 = it; prefs.edit().putFloat("a4", it).apply() },
                        tolerance, { tolerance = it; prefs.edit().putFloat("tolerance", it).apply() },
                        notation, { notation = it; prefs.edit().putBoolean("solfege", it).apply() })
                }
            }
        }
    }
}

@Composable
private fun TunerPage(
    instrument: String, onInstrument: (String) -> Unit, tunings: List<TuningPreset>, selected: TuningPreset,
    onTuning: (String) -> Unit, listening: Boolean, onListen: () -> Unit, pitch: Pitch?,
    a4: Double, tolerance: Double, solfege: Boolean
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("AFINADOR CROMÁTICO", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f), fontSize = 12.sp, letterSpacing = 1.4.sp)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Guitarra", "Bajo", "Ukelele", "Violín", "Viola").forEach {
                FilterChip(selected = instrument == it, onClick = { onInstrument(it) }, label = { Text(it, fontSize = 11.sp) })
            }
        }
        Spacer(Modifier.height(10.dp))
        var expanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { expanded = true }) { Text(selected.name + "  ▾") }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                tunings.forEach { preset -> DropdownMenuItem(text = { Text(preset.name) }, onClick = { onTuning(preset.name); expanded = false }) }
            }
        }
        Spacer(Modifier.height(24.dp))
        val cents = pitch?.cents ?: 0.0
        val inTune = pitch != null && abs(cents) <= tolerance
        Text(pitch?.let { TunerEngine.noteForMidi(it.midi, solfege) } ?: "—", fontSize = 64.sp, fontWeight = FontWeight.Bold, color = if (inTune) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        Text(pitch?.let { "%.2f Hz".format(it.frequency) } ?: "Toca una cuerda", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
        Spacer(Modifier.height(18.dp))
        TuningMeter(cents.toFloat(), pitch != null, tolerance.toFloat())
        Text(
            when { pitch == null -> if (listening) "Escuchando instrumento…" else "Listo para afinar"; inTune -> "✓ AFINADO"; cents < 0 -> "DEMASIADO BAJO · SUBE LA CUERDA"; else -> "DEMASIADO ALTO · BAJA LA CUERDA" },
            color = if (inTune) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold, fontSize = 12.sp
        )
        Text(pitch?.let { "%+.1f cents".format(it.cents) } ?: "± cents", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
        Spacer(Modifier.height(22.dp))
        Button(onClick = onListen, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)) {
            Text(if (listening) "DETENER MICRÓFONO" else "INICIAR AFINACIÓN", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))
        Text("AFINACIÓN DE CUERDAS", modifier = Modifier.align(Alignment.Start), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        selected.strings.forEachIndexed { index, string ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("CUERDA ${string.number}", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f), fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text(if (solfege) string.solfege else string.note, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("  %.2f Hz".format(string.frequency(a4)), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f), fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("La detección es cromática: también reconoce notas fuera de la afinación seleccionada.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
    }
}

@Composable
private fun TuningMeter(cents: Float, active: Boolean, tolerance: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().height(92.dp)) {
            val mid = size.width / 2
            val centerY = size.height - 8.dp.toPx()
            for (i in -5..5) {
                val x = mid + i * size.width / 12
                drawLine(Color.Gray.copy(alpha = .5f), androidx.compose.ui.geometry.Offset(x, centerY - if (i == 0) 48.dp.toPx() else 25.dp.toPx()), androidx.compose.ui.geometry.Offset(x, centerY), if (i == 0) 3.dp.toPx() else 1.dp.toPx())
            }
            if (active) {
                val x = mid + (cents.coerceIn(-50f, 50f) / 50f) * (size.width * .42f)
                drawLine(Color(0xFFB6F36A), androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, centerY), 4.dp.toPx())
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("♭ -50", fontSize = 10.sp, color = Color.Gray)
            Text("0", fontSize = 10.sp, color = Color.Gray)
            Text("+50 ♯", fontSize = 10.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun TonePage(notes: List<String>, selected: String, onSelect: (String) -> Unit, a4: Double) {
    DisposableEffect(Unit) { onDispose { TonePlayer.stop() } }
    var playing by remember { mutableStateOf(false) }
    Column {
        Text("GENERADOR DE TONOS", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Escucha una nota de referencia para afinar tu instrumento de oído.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
        Spacer(Modifier.height(18.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(notes) { note ->
                val midi = noteMidi(note)
                val freq = a4 * Math.pow(2.0, (midi - 69) / 12.0)
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected == note, onClick = { onSelect(note) })
                    Text(note, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("%.2f Hz".format(freq), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
                }
            }
        }
        Button(onClick = { playing = !playing; if (playing) TonePlayer.play(a4 * Math.pow(2.0, (noteMidi(selected) - 69) / 12.0)) else TonePlayer.stop() }, modifier = Modifier.fillMaxWidth()) {
            Text(if (playing) "DETENER TONO" else "REPRODUCIR NOTA")
        }
    }
}

@Composable
private fun MetroPage(bpm: Float, onBpm: (Float) -> Unit, running: Boolean, onRunning: (Boolean) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("Metrónomo", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(30.dp))
        Text(bpm.roundToInt().toString(), fontSize = 76.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("BPM · pulsos por minuto", color = Color.Gray)
        Slider(value = bpm, onValueChange = onBpm, valueRange = 40f..240f)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("40 BPM"); Text("240 BPM") }
        Spacer(Modifier.height(30.dp))
        Button(onClick = { onRunning(!running) }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(if (running) "DETENER" else "INICIAR METRÓNOMO") }
        Spacer(Modifier.height(12.dp))
        Text("Pulso de referencia audible · funciona sin conexión", color = Color.Gray, fontSize = 12.sp)
    }
}

@Composable
private fun SettingsPage(a4: Float, onA4: (Float) -> Unit, tolerance: Float, onTolerance: (Float) -> Unit, solfege: Boolean, onSolfege: (Boolean) -> Unit) {
    Column {
        Text("CALIBRACIÓN", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text("Frecuencia de referencia A4: ${a4.roundToInt()} Hz")
        Slider(value = a4, onValueChange = onA4, valueRange = 410f..480f)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("410 Hz"); Text("480 Hz") }
        Spacer(Modifier.height(22.dp))
        Text("PRECISIÓN")
        Text("Tolerancia de afinado: ±${tolerance.roundToInt()} cents")
        Slider(value = tolerance, onValueChange = onTolerance, valueRange = 1f..15f)
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mostrar notas Do Re Mi", modifier = Modifier.weight(1f))
            Switch(checked = solfege, onCheckedChange = onSolfege)
        }
        Spacer(Modifier.height(24.dp))
        Text("PRIVACIDAD", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text("El micrófono se usa únicamente mientras el afinador está activo. El procesamiento es local. No hay anuncios, cuentas, rastreadores ni permiso de Internet.", color = Color.Gray)
        Spacer(Modifier.height(20.dp))
        Text("Guitar Pro Offline · versión 1.1.0", color = Color.Gray, fontSize = 12.sp)
    }
}

private fun noteMidi(note: String): Int {
    val match = Regex("([A-G])([#b]?)([0-8])").matchEntire(note) ?: return 69
    val base = mapOf("C" to 0, "D" to 2, "E" to 4, "F" to 5, "G" to 7, "A" to 9, "B" to 11)[match.groupValues[1]] ?: 9
    val accidental = when(match.groupValues[2]) { "#" -> 1; "b" -> -1; else -> 0 }
    return (match.groupValues[3].toInt() + 1) * 12 + base + accidental
}

private object TonePlayer {
    private var track: AudioTrack? = null
    fun play(frequency: Double) {
        stop()
        val rate = 44100
        val count = rate * 3
        val samples = ShortArray(count) { i ->
            (kotlin.math.sin(2.0 * Math.PI * frequency * i / rate) * 9000.0).toInt().toShort()
        }
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        val format = AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
        track = AudioTrack(attrs, format, samples.size * 2, AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE).also {
            it.write(samples, 0, samples.size)
            it.setLoopPoints(0, samples.size, -1)
            it.play()
        }
    }
    fun stop() { try { track?.stop(); track?.release() } catch (_: Exception) {}; track = null }
}

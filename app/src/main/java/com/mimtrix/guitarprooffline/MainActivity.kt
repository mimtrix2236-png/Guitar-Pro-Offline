package com.mimtrix.guitarprooffline

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) setContent { TunerScreen() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TunerScreen() }
    }

    @Composable
    private fun TunerScreen() {
        var instrument by remember { mutableStateOf("Guitarra") }
        var tuning by remember { mutableStateOf("Estándar") }
        var running by remember { mutableStateOf(false) }
        var pitch by remember { mutableStateOf<Pitch?>(null) }
        var status by remember { mutableStateOf("Listo para afinar") }
        val scope = rememberCoroutineScope()
        val tunings = mapOf(
            "Guitarra" to listOf("Estándar", "Drop D", "DADGAD", "Open G"),
            "Bajo" to listOf("Estándar", "Drop D", "5 cuerdas"),
            "Ukelele" to listOf("Estándar GCEA", "Barítono"),
            "Violín" to listOf("G-D-A-E"),
            "Viola" to listOf("C-G-D-A")
        )
        MaterialTheme {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("GUITAR PRO OFFLINE", style = MaterialTheme.typography.headlineMedium)
                    Text("Afinador cromático • 100% sin conexión", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(28.dp))
                    Text("Instrumento", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Guitarra", "Bajo", "Ukelele").forEach { item ->
                            FilterChip(selected = instrument == item, onClick = { instrument = item; tuning = tunings[item]!!.first() }, label = { Text(item) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Violín", "Viola").forEach { item ->
                            FilterChip(selected = instrument == item, onClick = { instrument = item; tuning = tunings[item]!!.first() }, label = { Text(item) })
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Afinación")
                    tunings[instrument]!!.forEach { item ->
                        if (item == tuning) AssistChip(onClick = {}, label = { Text("✓ $item") })
                        else AssistChip(onClick = { tuning = item }, label = { Text(item) })
                    }
                    Spacer(Modifier.height(32.dp))
                    Text(pitch?.note ?: "—", style = MaterialTheme.typography.displayLarge)
                    Text(pitch?.let { "%.2f Hz".format(it.frequency) } ?: "Toca una cuerda", style = MaterialTheme.typography.titleLarge)
                    val cents = pitch?.cents ?: 0.0
                    Text(pitch?.let { if (abs(cents) < 5) "AFINADO" else if (cents < 0) "Bajo • subir tono" else "Alto • bajar tono" } ?: status)
                    Slider(value = ((cents.coerceIn(-50.0, 50.0) + 50) / 100).toFloat(), onValueChange = {}, enabled = false)
                    Text(pitch?.let { "%+.1f cents".format(it.cents) } ?: " ")
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = {
                        if (running) { running = false; status = "Detenido"; return@Button }
                        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            permission.launch(Manifest.permission.RECORD_AUDIO); return@Button
                        }
                        running = true; status = "Escuchando…"
                        scope.launch(Dispatchers.IO) {
                            val rate = 44100
                            val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                            if (min <= 0) { running = false; status = "Micrófono no disponible"; return@launch }
                            val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 2)
                            try {
                                recorder.startRecording()
                                val buffer = ShortArray(4096)
                                while (isActive && running) {
                                    val n = recorder.read(buffer, 0, buffer.size)
                                    if (n > 0) {
                                        val result = TunerEngine.detect(buffer.copyOf(n), rate)
                                        launch(Dispatchers.Main) { if (result != null) pitch = result }
                                    }
                                }
                            } catch (_: Exception) { launch(Dispatchers.Main) { status = "Error de micrófono" } }
                            finally { try { recorder.stop() } catch (_: Exception) {}; recorder.release(); running = false }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(if (running) "Detener afinador" else "Iniciar afinador") }
                    Spacer(Modifier.height(18.dp))
                    Text("Incluye detección cromática para cualquier nota. Acerca el instrumento al micrófono y toca una cuerda individual.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

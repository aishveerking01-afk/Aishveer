package com.rui.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rui.app.ChatViewModel
import com.rui.app.engine.EngineState
import com.rui.app.engine.LocalModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private fun Long.fmt(): String = when {
    this >= 1L shl 30 -> "%.1f GB".format(this / (1L shl 30).toDouble())
    this >= 1L shl 20 -> "%.0f MB".format(this / (1L shl 20).toDouble())
    else -> "$this B"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(vm: ChatViewModel, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val models by vm.models.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val engine by vm.engineState.collectAsStateWithLifecycle()
    val progress by vm.importProgress.collectAsStateWithLifecycle()
    val loaded by vm.loadedModel.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<LocalModel?>(null) }

    // Live RAM readout while this screen is visible.
    LaunchedEffect(Unit) { while (isActive) { vm.refreshStats(); delay(2000) } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importModel) }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text("Models & storage") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background, titleContentColor = cs.onSurface, navigationIconContentColor = cs.onSurface),
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Surface(shape = RoundedCornerShape(18.dp), color = cs.surfaceContainer) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Meter("Memory (RAM)", stats.totalRam - stats.availRam, stats.totalRam, "${stats.availRam.fmt()} free")
                        Meter("Storage", stats.storageTotal - stats.storageFree, stats.storageTotal, "${stats.storageFree.fmt()} free")
                        Text("Models on this device: ${stats.modelsBytes.fmt()}", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { picker.launch(arrayOf("*/*")) }, enabled = progress == null, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Rounded.FileUpload, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Import model (.task)")
                    }
                    progress?.let { p ->
                        val anim by animateFloatAsState(p, label = "import")
                        LinearProgressIndicator(progress = { anim }, Modifier.fillMaxWidth())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Copying… ${(p * 100).toInt()}%", color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                            TextButton(onClick = vm::cancelImport) { Text("Cancel") }
                        }
                    }
                }
            }
            if (models.isEmpty()) item {
                Text("No models yet. Copy a .task model file to your phone (USB, SD card or Files app), then tap Import.", color = cs.onSurfaceVariant)
            }
            items(models, key = { it.name }) { m ->
                val isLoaded = loaded == m.name && engine !is EngineState.Idle
                val fits = m.estimatedRamBytes <= stats.availRam || isLoaded
                Surface(shape = RoundedCornerShape(18.dp), color = cs.surfaceContainer) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(m.name, color = cs.onSurface, style = MaterialTheme.typography.titleMedium)
                        Text("${m.sizeBytes.fmt()} on disk  ·  needs about ${m.estimatedRamBytes.fmt()} RAM",
                            color = if (fits) cs.onSurfaceVariant else cs.error, style = MaterialTheme.typography.bodyMedium)
                        if (!fits) Text("Not enough free RAM right now. Close other apps first.", color = cs.error, style = MaterialTheme.typography.labelSmall)
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (isLoaded) FilledTonalButton(onClick = vm::unloadModel) { Text("Unload") }
                            else Button(onClick = { vm.loadModel(m) }, enabled = engine !is EngineState.Loading && engine !is EngineState.Generating) { Text("Load") }
                            if (isLoaded) (engine as? EngineState.Ready)?.let { Text("Running on ${it.backend}", color = cs.secondary, style = MaterialTheme.typography.labelSmall) }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { toDelete = m }) { Icon(Icons.Rounded.Delete, "Delete", tint = cs.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete ${m.name}?") },
            text = { Text("This frees ${m.sizeBytes.fmt()}. You can import it again later.") },
            confirmButton = { TextButton(onClick = { vm.deleteModel(m); toDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Meter(label: String, used: Long, total: Long, trailing: String) {
    val cs = MaterialTheme.colorScheme
    val frac = if (total > 0) (used.toFloat() / total).coerceIn(0f, 1f) else 0f
    val anim by animateFloatAsState(frac, label = label)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(label, color = cs.onSurface, modifier = Modifier.weight(1f))
            Text(trailing, color = cs.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { anim }, modifier = Modifier.fillMaxWidth().height(8.dp),
            color = if (frac > 0.85f) cs.error else cs.primary, trackColor = cs.surfaceContainerHigh,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        Text("${used.fmt()} of ${total.fmt()} used", color = cs.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    }
}

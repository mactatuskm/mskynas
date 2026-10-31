@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mskynas.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.mskynas.tv.data.Updater
import kotlinx.coroutines.launch
import java.io.File

private sealed interface Step {
    data object Ask : Step
    data class Downloading(val progress: Float) : Step
    data class NeedsPermission(val file: File, val manual: Boolean) : Step
    data class Failed(val message: String) : Step
}

/** Ventana "Hay una versión nueva": novedades, descarga con avance e instalación. */
@Composable
fun UpdateDialog(update: Updater.Update, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf<Step>(Step.Ask) }

    fun installOrAskPermission(file: File) {
        if (Updater.canInstall(context)) {
            Updater.install(context, file)
            onDismiss()
        } else {
            val opened = Updater.openInstallPermission(context)
            step = Step.NeedsPermission(file, manual = !opened)
        }
    }

    fun startDownload() {
        step = Step.Downloading(0f)
        scope.launch {
            runCatching { Updater.download(context, update) { step = Step.Downloading(it) } }
                .onSuccess { installOrAskPermission(it) }
                .onFailure { step = Step.Failed("No pude descargar la actualización. Revisa la conexión e intenta de nuevo.") }
        }
    }

    Dialog(
        onDismissRequest = { if (step !is Step.Downloading) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .width(540.dp)
                .background(Panel, RoundedCornerShape(16.dp))
                .padding(28.dp)
        ) {
            Text("Hay una versión nueva", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "mskynas ${update.versionName}  ·  tienes la ${Updater.currentVersion}",
                color = Dim,
                fontSize = 14.sp,
            )

            if (update.notes.isNotBlank()) {
                Spacer(Modifier.height(18.dp))
                Text("NOVEDADES", color = Faint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    update.notes,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(24.dp))
            when (val s = step) {
                Step.Ask -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionButton(
                        "Actualizar",
                        onClick = { startDownload() },
                        modifier = Modifier.initialFocus(true),
                        highlighted = true,
                    )
                    ActionButton("Ahora no", onClick = onDismiss)
                }

                is Step.Downloading -> Column {
                    Text("Descargando…  ${(s.progress * 100).toInt()} %", color = Color.White, fontSize = 15.sp)
                    Spacer(Modifier.height(10.dp))
                    ProgressBar(s.progress, Modifier.fillMaxWidth())
                }

                is Step.NeedsPermission -> Column {
                    Text(
                        if (s.manual) {
                            "Para actualizar, permite que mskynas instale apps: ve a Ajustes → Mi Fire TV → " +
                                "Opciones de desarrollador → Instalar apps desconocidas y activa mskynas. " +
                                "Luego regresa aquí y presiona Instalar."
                        } else {
                            "Activa el permiso para que mskynas pueda instalar su actualización, " +
                                "luego regresa aquí y presiona Instalar."
                        },
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionButton(
                            "Instalar",
                            onClick = { installOrAskPermission(s.file) },
                            modifier = Modifier.initialFocus(true),
                            highlighted = true,
                        )
                        ActionButton("Ahora no", onClick = onDismiss)
                    }
                }

                is Step.Failed -> Column {
                    Text(s.message, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionButton(
                            "Reintentar",
                            onClick = { startDownload() },
                            modifier = Modifier.initialFocus(true),
                            highlighted = true,
                        )
                        ActionButton("Ahora no", onClick = onDismiss)
                    }
                }
            }
        }
    }
}

package com.example.automateclone.ui.components

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class AppEntry(val packageName: String, val label: String, val icon: ImageBitmap)

private fun Drawable.toBitmap(size: Int = 96): Bitmap {
    if (this is BitmapDrawable && bitmap != null) return bitmap
    val bmp = Bitmap.createBitmap(
        intrinsicWidth.takeIf { it > 0 } ?: size,
        intrinsicHeight.takeIf { it > 0 } ?: size,
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bmp)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bmp
}

@Composable
fun AppPickerDialog(onPick: (packageName: String, label: String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current

    val apps = remember {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        pm.queryIntentActivities(launcherIntent, 0)
            .distinctBy { it.activityInfo.packageName }
            .map { resolveInfo ->
                AppEntry(
                    packageName = resolveInfo.activityInfo.packageName,
                    label = resolveInfo.loadLabel(pm).toString(),
                    icon = resolveInfo.loadIcon(pm).toBitmap().asImageBitmap()
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    var query by remember { mutableStateOf("") }
    val filtered = remember(query, apps) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search apps") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))
            if (filtered.isEmpty()) {
                Text("No apps found", modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn {
                    items(filtered, key = { it.packageName }) { app ->
                        ListItem(
                            headlineContent = { Text(app.label) },
                            supportingContent = {
                                Text(app.packageName, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            },
                            leadingContent = {
                                Image(
                                    bitmap = app.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp)
                                )
                            },
                            modifier = Modifier.clickable { onPick(app.packageName, app.label) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

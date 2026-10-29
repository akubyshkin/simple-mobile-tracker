package app.traceday.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.traceday.R
import app.traceday.domain.DistanceFormatter
import app.traceday.service.TrackingService
import app.traceday.ui.components.ImportChip
import app.traceday.ui.components.StatPill
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

private val requiredPermissions: List<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}

@Composable
fun MapScreen(
    initialDay: String? = null,
    vm: MapViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.refreshImported()
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHost.showSnackbar(it)
            vm.consumeMessage()
        }
    }
    LaunchedEffect(state.shareUrl) {
        state.shareUrl?.let {
            clipboard.setText(AnnotatedString(it))
            snackbarHost.showSnackbar("Ссылка скопирована в буфер обмена")
            vm.consumeShareUrl()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            TrackingService.start(context)
        }
    }
    var showImport by remember { mutableStateOf(false) }
    if (showImport) {
        ImportDialog(
            onDismiss = { showImport = false },
            onImport = {
                vm.importLink(it)
                showImport = false
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SmallFloatingActionButton(
                    onClick = { showImport = true },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Icon(Icons.Filled.AddLink, contentDescription = stringResource(R.string.map_import))
                }
                Spacer(Modifier.height(12.dp))
                RecordFab(running = state.trackingRunning) {
                    onRecordClicked(context, permissionLauncher, state.trackingRunning)
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OsmMap(state)
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatsPanel(state)
                DayChipsRow(
                    selected = state.selectedDay,
                    onSelect = vm::selectDay
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RangeFilter.entries.forEach { filter ->
                        FilterChipCompact(
                            label = rangeLabel(filter),
                            selected = state.range == filter,
                            onClick = { vm.setRange(filter) }
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { vm.shareSelectedDay() }, enabled = !state.busy) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = stringResource(R.string.map_share_day),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            LazyRow(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(state.imported, key = { it.id }) { track ->
                    ImportChip(
                        title = track.title,
                        colorArgb = track.colorArgb,
                        enabled = track.enabled,
                        onToggle = { vm.toggleImported(track.id) },
                        onRemove = { vm.removeImported(track.id) }
                    )
                }
            }
        }
    }
}

private fun onRecordClicked(
    context: Context,
    launcher: ActivityResultLauncher<Array<String>>,
    running: Boolean
) {
    val allGranted = requiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    when {
        !allGranted -> launcher.launch(requiredPermissions.toTypedArray())
        running -> TrackingService.stop(context)
        else -> TrackingService.start(context)
    }
}

@Composable
private fun RecordFab(running: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    FloatingActionButton(
        onClick = onClick,
        containerColor = if (running) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.primary,
        contentColor = Color.White,
        modifier = Modifier
            .size(72.dp)
            .graphicsLayer {
                val s = if (running) scale else 1f
                scaleX = s
                scaleY = s
            }
    ) {
        Icon(
            if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow,
            contentDescription = stringResource(
                if (running) R.string.map_record_stop else R.string.map_record_start
            ),
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
private fun StatsPanel(state: MapUiState) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 2.dp
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatPill("дистанция", DistanceFormatter.formatDistance(state.selfStats.distanceMeters))
                StatPill("в пути", DistanceFormatter.formatDuration(state.selfStats.durationMillis))
                StatPill("точки", state.selfStats.pointCount.toString())
            }
            SyncBadge(state)
        }
    }
}

@Composable
private fun SyncBadge(state: MapUiState) {
    val (icon, text, color) = when {
        state.authIssue -> Triple(
            Icons.Filled.CloudOff,
            "Нужна повторная авторизация облака",
            MaterialTheme.colorScheme.error
        )
        state.unsyncedCount > 0 -> Triple(
            Icons.Filled.Sync,
            "Ожидает синхронизации: ${state.unsyncedCount}",
            MaterialTheme.colorScheme.secondary
        )
        state.lastSyncAt > 0 -> Triple(
            Icons.Filled.Sync,
            "Синхронизировано в ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(state.lastSyncAt))}",
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        else -> Triple(
            Icons.Filled.CloudOff,
            "Облако не подключено",
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(
            text,
            modifier = Modifier.padding(start = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}

@Composable
private fun DayChipsRow(selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val days = (0..6).map { today.minusDays(6L - it) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(days) { day ->
            FilterChipCompact(
                label = dayLabel(day),
                selected = day == selected,
                onClick = { onSelect(day) }
            )
        }
    }
}

@Composable
private fun FilterChipCompact(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        onClick = onClick
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun dayLabel(day: LocalDate): String = when (day) {
    LocalDate.now() -> "Сегодня"
    LocalDate.now().minusDays(1) -> "Вчера"
    else -> day.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", Locale("ru")))
}

private fun rangeLabel(filter: RangeFilter): String = when (filter) {
    RangeFilter.ALL -> "Весь день"
    RangeFilter.H6 -> "6 ч"
    RangeFilter.H3 -> "3 ч"
    RangeFilter.H1 -> "1 ч"
}

@Composable
private fun ImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var link by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    label = { Text(stringResource(R.string.import_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = {
                    clipboard.getText()?.text?.let { link = it.trim() }
                }) {
                    Text(stringResource(R.string.import_paste))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onImport(link.trim()) },
                enabled = link.isNotBlank()
            ) {
                Text(stringResource(R.string.import_do))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.import_cancel))
            }
        }
    )
}

@Composable
private fun OsmMap(state: MapUiState) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(11.0)
            controller.setCenter(GeoPoint(55.7558, 37.6173))
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }
    var lastFitKey by remember { mutableStateOf<String?>(null) }
    AndroidView(
        factory = { mapView },
        update = { map ->
            map.overlays.removeAll { it is Polyline || it is Marker }
            state.polylines.forEach { line ->
                val geoPoints = line.points.map { GeoPoint(it.lat, it.lon) }
                if (geoPoints.isNotEmpty()) {
                    val polyline = Polyline(map)
                    polyline.outlinePaint.color = line.colorArgb.toInt()
                    polyline.outlinePaint.strokeWidth = 7f
                    polyline.setPoints(geoPoints)
                    map.overlays.add(polyline)
                    geoPoints.firstOrNull()?.let { start ->
                        val marker = Marker(map)
                        marker.position = start
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        marker.title = "Старт · ${line.title}"
                        map.overlays.add(marker)
                    }
                    if (geoPoints.size > 1) {
                        val marker = Marker(map)
                        marker.position = geoPoints.last()
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        marker.title = "Финиш · ${line.title}"
                        map.overlays.add(marker)
                    }
                }
            }
            map.invalidate()
            val fitKey = state.polylines.joinToString("|") {
                "${it.trackId}:${it.points.size}"
            }
            val hasPoints = state.polylines.any { it.points.isNotEmpty() }
            if (hasPoints && lastFitKey != fitKey) {
                lastFitKey = fitKey
                val all = state.polylines.flatMap { it.points }
                val box = BoundingBox(
                    all.maxOf { it.lat },
                    all.maxOf { it.lon },
                    all.minOf { it.lat },
                    all.minOf { it.lon }
                )
                map.post { map.zoomToBoundingBox(box, false) }
            }
        }
    )
}

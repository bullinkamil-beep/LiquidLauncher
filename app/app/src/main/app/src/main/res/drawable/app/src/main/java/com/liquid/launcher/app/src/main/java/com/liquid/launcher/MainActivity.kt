package com.liquid.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                LiquidLauncherRoot()
            }
        }
    }
}

@Composable
fun LiquidLauncherRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsStore = remember { SettingsStore(context) }

    var settings by remember { mutableStateOf(settingsStore.loadSettings()) }
    var wallpaper by remember { mutableStateOf<ImageBitmap?>(null) }
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var dockPackages by remember { mutableStateOf(settingsStore.loadPinnedDock()) }

    var batteryPercent by remember { mutableIntStateOf(100) }
    var isCharging by remember { mutableStateOf(false) }

    var showSearch by remember { mutableStateOf(false) }
    var showCustomize by remember { mutableStateOf(false) }
    var showLargeFolder by remember { mutableStateOf(false) }
    var selectedAppMenu by remember { mutableStateOf<AppInfo?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bmp = WallpaperStorage.saveCustomWallpaper(context, uri)
                if (bmp != null) {
                    wallpaper = bmp
                    settings = settings.copy(wallpaperPreset = -1)
                    settingsStore.saveSettings(settings)
                }
            }
        }
    }

    LaunchedEffect(settings.wallpaperPreset) {
        wallpaper = WallpaperStorage.loadWallpaper(context, settings.wallpaperPreset)
    }

    LaunchedEffect(Unit) {
        allApps = AppRepository.loadInstalledApps(context)
    }

    DisposableEffect(context) {
        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                intent ?: return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (level >= 0 && scale > 0) {
                    batteryPercent = (level * 100) / scale
                }
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        val pkgReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                scope.launch { allApps = AppRepository.loadInstalledApps(context) }
            }
        }

        ContextCompat.registerReceiver(
            context,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_EXPORTED
        )
        val pkgFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            context,
            pkgReceiver,
            pkgFilter,
            ContextCompat.RECEIVER_EXPORTED
        )

        onDispose {
            runCatching { context.unregisterReceiver(batteryReceiver) }
            runCatching { context.unregisterReceiver(pkgReceiver) }
        }
    }

    val dockApps = remember(allApps, dockPackages) {
        val pinned = dockPackages.mapNotNull { pkg -> allApps.find { it.packageName == pkg } }
        if (pinned.isNotEmpty()) pinned else allApps.take(4)
    }

    val folderApps = remember(allApps) { allApps.take(9) }
    val homeBottomApps = remember(allApps, settings.gridColumns) {
        allApps.drop(4).take(settings.gridColumns)
    }
    val remainingApps = remember(allApps, settings.gridColumns) {
        allApps.drop(4 + settings.gridColumns)
    }
    val appsPerPage = settings.gridColumns * 5
    val extraPages = if (remainingApps.isEmpty()) 0 else ceil(remainingApps.size / appsPerPage.toFloat()).toInt()
    val totalPages = 1 + extraPages

    val pagerState = rememberPagerState(pageCount = { totalPages })

    BackHandler {
        when {
            selectedAppMenu != null -> selectedAppMenu = null
            showSearch -> showSearch = false
            showCustomize -> showCustomize = false
            showLargeFolder -> showLargeFolder = false
            pagerState.currentPage != 0 -> scope.launch { pagerState.animateScrollToPage(0) }
        }
    }

    fun updateSettings(newSettings: LauncherSettings) {
        settings = newSettings
        settingsStore.saveSettings(newSettings)
    }

    fun toggleDockPin(pkg: String) {
        val current = dockApps.map { it.packageName }.toMutableList()
        if (current.contains(pkg)) {
            if (current.size > 1) current.remove(pkg)
        } else {
            if (current.size >= 5) current.removeAt(current.lastIndex)
            current.add(pkg)
        }
        dockPackages = current
        settingsStore.savePinnedDock(current)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenW = constraints.maxWidth
        val screenH = constraints.maxHeight

        val backdropState = remember(wallpaper, screenW, screenH, settings) {
            WallpaperBackdrop(
                bitmap = wallpaper,
                screenWidthPx = screenW,
                screenHeightPx = screenH,
                settings = settings
            )
        }

        CompositionLocalProvider(LocalWallpaperBackdrop provides backdropState) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0A1224))
                    .pointerInput(Unit) {
                        var totalDrag = 0f
                        detectVerticalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onVerticalDrag = { _, dragAmount -> totalDrag += dragAmount },
                            onDragEnd = {
                                if (totalDrag > 90f || totalDrag < -110f) {
                                    showSearch = true
                                }
                            }
                        )
                    }
            ) {
                wallpaper?.let { wp ->
                    Image(
                        bitmap = wp,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(6.dp))

                    LiveAlertCapsule(
                        batteryPercent = batteryPercent,
                        isCharging = isCharging,
                        onOpenSearch = { showSearch = true },
                        onOpenCustomize = { showCustomize = true },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(Modifier.height(10.dp))

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) { page ->
                        if (page == 0) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 18.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                LiquidClockWidget(
                                    modifier = Modifier.fillMaxWidth(),
                                    onTapCustomize = { showCustomize = true }
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    ColorOsLargeFolder(
                                        title = "Быстрые",
                                        apps = folderApps,
                                        onAppClick = { AppRepository.launchApp(context, it.packageName) },
                                        onExpandFolder = { showLargeFolder = true },
                                        modifier = Modifier.weight(1f)
                                    )
                                    LiquidBatteryWidget(
                                        batteryPercent = batteryPercent,
                                        isCharging = isCharging,
                                        onOpenCustomize = { showCustomize = true },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(Modifier.weight(1f))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    homeBottomApps.forEach { app ->
                                        LiquidAppTile(
                                            app = app,
                                            onClick = { AppRepository.launchApp(context, app.packageName) },
                                            onLongClick = { selectedAppMenu = app }
                                        )
                                    }
                                }
                            }
                        } else {
                            val pageIndex = page - 1
                            val start = pageIndex * appsPerPage
                            val pageApps = remainingApps.drop(start).take(appsPerPage)

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(settings.gridColumns),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(18.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(pageApps, key = { it.packageName }) { app ->
                                    LiquidAppTile(
                                        app = app,
                                        onClick = { AppRepository.launchApp(context, app.packageName) },
                                        onLongClick = { selectedAppMenu = app }
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LiquidGlassSurface(
                            cornerRadius = 16.dp,
                            onClick = { showSearch = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = "Поиск",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                repeat(totalPages.coerceAtMost(8)) { idx ->
                                    val selected = pagerState.currentPage == idx
                                    Box(
                                        modifier = Modifier
                                            .size(width = if (selected) 14.dp else 6.dp, height = 6.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (selected) Color.White else Color.White.copy(alpha = 0.4f)
                                            )
                                    )
                                }
                            }
                        }
                    }

                    LiquidGlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 12.dp)
                            .height(86.dp),
                        cornerRadius = 32.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            dockApps.forEach { app ->
                                LiquidAppTile(
                                    app = app,
                                    showLabel = false,
                                    iconSize = 58.dp,
                                    onClick = { AppRepository.launchApp(context, app.packageName) },
                                    onLongClick = { selectedAppMenu = app }
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = showSearch,
                    enter = fadeIn() + slideInVertically { -it / 5 },
                    exit = fadeOut() + slideOutVertically { -it / 5 }
                ) {
                    SpotlightSearchOverlay(
                        allApps = allApps,
                        onAppClick = {
                            showSearch = false
                            AppRepository.launchApp(context, it.packageName)
                        },
                        onAppLongClick = { selectedAppMenu = it },
                        onDismiss = { showSearch = false }
                    )
                }

                AnimatedVisibility(
                    visible = showLargeFolder,
                    enter = fadeIn() + scaleIn(initialScale = 0.85f),
                    exit = fadeOut() + scaleOut(targetScale = 0.85f)
                ) {
                    ExpandedFolderOverlay(
                        title = "Быстрые приложения",
                        apps = folderApps,
                        onAppClick = {
                            showLargeFolder = false
                            AppRepository.launchApp(context, it.packageName)
                        },
                        onDismiss = { showLargeFolder = false }
                    )
                }

                AnimatedVisibility(
                    visible = showCustomize,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 }
                ) {
                    LiquidCustomizeOverlay(
                        settings = settings,
                        onUpdateSettings = { updateSettings(it) },
                        onPickGalleryWallpaper = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onDismiss = { showCustomize = false }
                    )
                }

                AnimatedVisibility(
                    visible = selectedAppMenu != null,
                    enter = fadeIn() + scaleIn(initialScale = 0.9f),
                    exit = fadeOut() + scaleOut(targetScale = 0.9f)
                ) {
                    selectedAppMenu?.let { app ->
                        val isPinned = dockApps.any { it.packageName == app.packageName }
                        AppContextMenuOverlay(
                            app = app,
                            isPinnedToDock = isPinned,
                            onPinToggle = {
                                toggleDockPin(app.packageName)
                                selectedAppMenu = null
                            },
                            onAppInfo = {
                                selectedAppMenu = null
                                AppRepository.openAppInfo(context, app.packageName)
                            },
                            onUninstall = {
                                selectedAppMenu = null
                                AppRepository.uninstallApp(context, app.packageName)
                            },
                            onDismiss = { selectedAppMenu = null }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveAlertCapsule(
    batteryPercent: Int,
    isCharging: Boolean,
    onOpenSearch: () -> Unit,
    onOpenCustomize: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }

    fun toggleFlashlight() {
        runCatching {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val camId = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            torchOn = !torchOn
            cm.setTorchMode(camId, torchOn)
        }
    }

    LiquidGlassSurface(
        modifier = modifier
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
        cornerRadius = if (expanded) 30.dp else 24.dp,
        extraTint = Color.Black.copy(alpha = 0.28f),
        onClick = { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = if (expanded) 18.dp else 16.dp,
                vertical = if (expanded) 14.dp else 8.dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isCharging) Color(0xFF34D399) else Color(0xFF38BDF8))
                )
                Text(
                    text = if (isCharging) "Зарядка $batteryPercent%" else "LiquidOS • $batteryPercent%",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = if (isCharging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
                    contentDescription = null,
                    tint = if (isCharging) Color(0xFF34D399) else Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CapsuleActionBtn(
                        icon = if (torchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                        label = "Фонарь",
                        active = torchOn,
                        onClick = { toggleFlashlight() }
                    )
                    CapsuleActionBtn(
                        icon = Icons.Rounded.Search,
                        label = "Поиск",
                        onClick = {
                            expanded = false
                            onOpenSearch()
                        }
                    )
                    CapsuleActionBtn(
                        icon = Icons.Rounded.Palette,
                        label = "Стекло",
                        onClick = {
                            expanded = false
                            onOpenCustomize()
                        }
                    )
                    CapsuleActionBtn(
                        icon = Icons.Rounded.Settings,
                        label = "Система",
                        onClick = {
                            expanded = false
                            AppRepository.openSystemSettings(context)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CapsuleActionBtn(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (active) Color.White else Color.White.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) Color.Black else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun LiquidClockWidget(
    modifier: Modifier = Modifier,
    onTapCustomize: () -> Unit
) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1000L)
        }
    }

    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("EEEE, d MMMM", Locale.forLanguageTag("ru")) }

    LiquidGlassSurface(
        modifier = modifier.height(152.dp),
        cornerRadius = 32.dp,
        onLongClick = onTapCustomize
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = dateFmt.format(now).replaceFirstChar { it.uppercase() },
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = timeFmt.format(now),
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-2).sp
            )
        }
    }
}

@Composable
private fun ColorOsLargeFolder(
    title: String,
    apps: List<AppInfo>,
    onAppClick: (AppInfo) -> Unit,
    onExpandFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LiquidGlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            cornerRadius = 30.dp,
            onClick = onExpandFolder
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FolderMiniSlot(apps.getOrNull(0), onAppClick)
                    FolderMiniSlot(apps.getOrNull(1), onAppClick)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FolderMiniSlot(apps.getOrNull(2), onAppClick)
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.18f))
                            .clickable(onClick = onExpandFolder),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GridView,
                            contentDescription = "Открыть папку",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun FolderMiniSlot(app: AppInfo?, onAppClick: (AppInfo) -> Unit) {
    if (app != null) {
        LiquidAppIconGraphic(
            app = app,
            size = 56.dp,
            onClick = { onAppClick(app) }
        )
    } else {
        Spacer(Modifier.size(56.dp))
    }
}

@Composable
private fun LiquidBatteryWidget(
    batteryPercent: Int,
    isCharging: Boolean,
    onOpenCustomize: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LiquidGlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            cornerRadius = 30.dp,
            onClick = onOpenCustomize
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isCharging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
                        contentDescription = null,
                        tint = if (isCharging) Color(0xFF34D399) else Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                    Icon(
                        imageVector = Icons.Rounded.Palette,
                        contentDescription = "Стиль",
                        tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = "$batteryPercent%",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = (batteryPercent / 100f).coerceIn(0.05f, 1f))
                                .height(10.dp)
                                .clip(CircleShape)
                                .background(
                                    if (batteryPercent > 20) Color(0xFF34D399) else Color(0xFFFB7185)
                                )
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Батарея • Тема",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun LiquidAppTile(
    app: AppInfo,
    showLabel: Boolean = true,
    iconSize: Dp = 58.dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LiquidAppIconGraphic(
            app = app,
            size = iconSize,
            onClick = onClick,
            onLongClick = onLongClick
        )
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = app.label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(74.dp)
            )
        }
    }
}

@Composable
private fun LiquidAppIconGraphic(
    app: AppInfo,
    size: Dp = 58.dp,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val style = LocalWallpaperBackdrop.current.settings.iconStyle
    val corner = (size.value * 0.28f).dp

    val colorFilter = remember(style) {
        when (style) {
            IconStyle.COLOR -> null
            IconStyle.LIQUID_CLEAR -> {
                val matrix = ColorMatrix().apply { setToSaturation(0.12f) }
                ColorFilter.colorMatrix(matrix)
            }
            IconStyle.TINTED -> {
                ColorFilter.tint(Color(0xFF93C5FD), BlendMode.Modulate)
            }
        }
    }

    if (style == IconStyle.COLOR) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(corner))
                .pointerInput(app.packageName) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { onLongClick?.invoke() }
                    )
                }
        ) {
            Image(
                bitmap = app.icon,
                contentDescription = app.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        LiquidGlassSurface(
            modifier = Modifier.size(size),
            cornerRadius = corner,
            blurMultiplier = 0.7f,
            refractionMultiplier = 0.75f,
            extraTint = if (style == IconStyle.TINTED) Color(0xFF38BDF8).copy(alpha = 0.16f) else Color.White.copy(alpha = 0.08f),
            onClick = onClick,
            onLongClick = onLongClick
        ) {
            Image(
                bitmap = app.icon,
                contentDescription = app.label,
                colorFilter = colorFilter,
                alpha = 0.88f,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(7.dp)
                    .clip(RoundedCornerShape((size.value * 0.18f).dp))
            )
        }
    }
}

@Composable
private fun SpotlightSearchOverlay(
    allApps: List<AppInfo>,
    onAppClick: (AppInfo) -> Unit,
    onAppLongClick: (AppInfo) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val calcResult = remember(query) { AppRepository.tryQuickCalculate(query) }
    val filtered = remember(query, allApps) {
        if (query.isBlank()) allApps
        else allApps.filter { it.label.contains(query, ignoreCase = true) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LiquidGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                cornerRadius = 26.dp,
                extraTint = Color.Black.copy(alpha = 0.25f),
                bounceOnPress = false,
                onClick = {}
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.width(12.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = "Поиск или пример (12*8)...",
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 15.sp
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                            cursorBrush = SolidColor(Color.White),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Закрыть",
                        tint = Color.White,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable {
                                if (query.isNotEmpty()) query = "" else onDismiss()
                            }
                    )
                }
            }

            if (calcResult != null) {
                Spacer(Modifier.height(12.dp))
                LiquidGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 24.dp,
                    extraTint = Color(0xFF10B981).copy(alpha = 0.18f),
                    bounceOnPress = false,
                    onClick = {}
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Calculate,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "$query = $calcResult",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            LiquidGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                cornerRadius = 30.dp,
                extraTint = Color.Black.copy(alpha = 0.22f),
                bounceOnPress = false,
                onClick = {}
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        LiquidAppTile(
                            app = app,
                            onClick = { onAppClick(app) },
                            onLongClick = { onAppLongClick(app) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandedFolderOverlay(
    title: String,
    apps: List<AppInfo>,
    onAppClick: (AppInfo) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(18.dp))
            LiquidGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                cornerRadius = 36.dp,
                extraTint = Color.Black.copy(alpha = 0.18f),
                bounceOnPress = false,
                onClick = {}
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        LiquidAppTile(
                            app = app,
                            onClick = { onAppClick(app) },
                            onLongClick = {}
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiquidCustomizeOverlay(
    settings: LauncherSettings,
    onUpdateSettings: (LauncherSettings) -> Unit,
    onPickGalleryWallpaper: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        LiquidGlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            cornerRadius = 34.dp,
            extraTint = Color.Black.copy(alpha = 0.36f),
            bounceOnPress = false,
            onClick = {}
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Настройка Liquid Glass",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Закрыть",
                        tint = Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(onClick = onDismiss)
                    )
                }

                Text("Стиль иконок", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconStyle.entries.forEach { style ->
                        val active = settings.iconStyle == style
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (active) Color.White else Color.White.copy(alpha = 0.14f))
                                .clickable { onUpdateSettings(settings.copy(iconStyle = style)) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = style.label,
                                color = if (active) Color.Black else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Text("Обои (Flux / Галерея)", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val presetColors = listOf(
                        Color(0xFF00D4FF),
                        Color(0xFF10B981),
                        Color(0xFFFF6B6B),
                        Color(0xFF64748B)
                    )
                    presetColors.forEachIndexed { idx, col ->
                        val active = settings.wallpaperPreset == idx
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(col)
                                .clickable { onUpdateSettings(settings.copy(wallpaperPreset = idx)) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (active) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .clickable(onClick = onPickGalleryWallpaper)
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Image,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("Из галереи", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }

                GlassSlider(
                    title = "Размытие стекла (${settings.blurRadius.toInt()})",
                    value = settings.blurRadius,
                    range = 6f..45f,
                    onChange = { onUpdateSettings(settings.copy(blurRadius = it)) }
                )
                GlassSlider(
                    title = "Преломление линзы (${settings.refraction.toInt()})",
                    value = settings.refraction,
                    range = 0f..45f,
                    onChange = { onUpdateSettings(settings.copy(refraction = it)) }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Столбцов в сетке", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(4, 5).forEach { cols ->
                            val active = settings.gridColumns == cols
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (active) Color.White else Color.White.copy(alpha = 0.16f))
                                    .clickable { onUpdateSettings(settings.copy(gridColumns = cols)) }
                                    .padding(horizontal = 18.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "$cols",
                                    color = if (active) Color.Black else Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column {
        Text(title, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
            )
        )
    }
}

@Composable
private fun AppContextMenuOverlay(
    app: AppInfo,
    isPinnedToDock: Boolean,
    onPinToggle: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        LiquidGlassSurface(
            modifier = Modifier.width(270.dp),
            cornerRadius = 28.dp,
            extraTint = Color.Black.copy(alpha = 0.35f),
            bounceOnPress = false,
            onClick = {}
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        bitmap = app.icon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = app.label,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(14.dp))
                MenuActionRow(
                    icon = Icons.Rounded.PushPin,
                    text = if (isPinnedToDock) "Убрать из дока" else "Закрепить в док",
                    onClick = onPinToggle
                )
                MenuActionRow(
                    icon = Icons.Rounded.Info,
                    text = "О приложении",
                    onClick = onAppInfo
                )
                MenuActionRow(
                    icon = Icons.Rounded.Delete,
                    text = "Удалить",
                    tint = Color(0xFFFB7185),
                    onClick = onUninstall
                )
            }
        }
    }
}

@Composable
private fun MenuActionRow(
    icon: ImageVector,
    text: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text = text, color = tint, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

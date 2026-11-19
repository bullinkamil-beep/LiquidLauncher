package com.liquid.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

private const val LIQUID_GLASS_AGSL = """
    uniform shader content;
    uniform float2 resolution;
    uniform float cornerRadius;
    uniform float refractionStrength;
    uniform float tintAlpha;

    float sdRoundedBox(float2 p, float2 b, float r) {
        float2 q = abs(p) - b + float2(r);
        return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - r;
    }

    float2 calcNormal(float2 p, float2 b, float r) {
        float eps = 1.5;
        float dx = sdRoundedBox(p + float2(eps, 0.0), b, r) - sdRoundedBox(p - float2(eps, 0.0), b, r);
        float dy = sdRoundedBox(p + float2(0.0, eps), b, r) - sdRoundedBox(p - float2(0.0, eps), b, r);
        float2 n = float2(dx, dy);
        float len = length(n);
        return len > 0.0001 ? n / len : float2(0.0, -1.0);
    }

    half4 main(float2 fragCoord) {
        float2 safeRes = max(resolution, float2(4.0));
        float2 halfSize = safeRes * 0.5;
        float2 p = fragCoord - halfSize;
        float r = min(cornerRadius, min(halfSize.x, halfSize.y));
        float dist = sdRoundedBox(p, halfSize, r);

        float rimWidth = min(38.0, min(halfSize.x, halfSize.y) * 0.55);
        float edgeFactor = smoothstep(-rimWidth, 0.0, dist);
        float2 normal = calcNormal(p, halfSize, r);

        float bend = edgeFactor * edgeFactor * refractionStrength;
        float2 refracted = fragCoord - normal * bend;

        float disp = edgeFactor * edgeFactor * (refractionStrength * 0.14);
        float2 maxCoord = safeRes - float2(1.0);

        float rCol = content.eval(clamp(refracted + normal * disp, float2(0.0), maxCoord)).r;
        float gCol = content.eval(clamp(refracted, float2(0.0), maxCoord)).g;
        float bCol = content.eval(clamp(refracted - normal * disp, float2(0.0), maxCoord)).b;

        float3 baseColor = float3(rCol, gCol, bCol);

        float lightTopLeft = max(dot(normal, normalize(float2(-0.65, -0.75))), 0.0);
        float lightBottomRight = max(dot(normal, normalize(float2(0.65, 0.75))), 0.0);

        float rimLine = smoothstep(-3.0, -0.4, dist) * (1.0 - smoothstep(-0.4, 1.0, dist));
        float innerBevel = smoothstep(-rimWidth * 0.65, 0.0, dist);

        float spec = rimLine * (0.28 + lightTopLeft * 0.62 + lightBottomRight * 0.28)
                   + innerBevel * (lightTopLeft * 0.14 + lightBottomRight * 0.06);

        float3 glassTint = float3(0.96, 0.98, 1.0);
        float3 finalRgb = mix(baseColor, glassTint, tintAlpha) + float3(spec);

        return half4(clamp(finalRgb, float3(0.0), float3(1.0)), 1.0);
    }
"""

enum class IconStyle(val label: String) {
    COLOR("Обычные"),
    LIQUID_CLEAR("iOS 26 Стекло"),
    TINTED("Тонированные")
}

@Immutable
data class LauncherSettings(
    val blurRadius: Float = 24f,
    val refraction: Float = 22f,
    val glassOpacity: Float = 0.16f,
    val gridColumns: Int = 4,
    val iconStyle: IconStyle = IconStyle.LIQUID_CLEAR,
    val wallpaperPreset: Int = 0
)

@Immutable
data class WallpaperBackdrop(
    val bitmap: ImageBitmap? = null,
    val screenWidthPx: Int = 1080,
    val screenHeightPx: Int = 2400,
    val settings: LauncherSettings = LauncherSettings()
)

val LocalWallpaperBackdrop = compositionLocalOf { WallpaperBackdrop() }

@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    blurMultiplier: Float = 1f,
    refractionMultiplier: Float = 1f,
    extraTint: Color = Color.Transparent,
    bounceOnPress: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val backdrop = LocalWallpaperBackdrop.current
    val density = LocalDensity.current
    val cornerPx = with(density) { cornerRadius.toPx() }
    val shape = RoundedCornerShape(cornerRadius)

    var posInRoot by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var isPressed by remember { mutableStateOf(false) }

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && bounceOnPress) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "glassPressScale"
    )

    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RuntimeShader(LIQUID_GLASS_AGSL)
        } else null
    }

    val blurPx = (backdrop.settings.blurRadius * blurMultiplier).coerceAtLeast(1f)
    val refractionPx = (backdrop.settings.refraction * refractionMultiplier).coerceAtLeast(0f)
    val tintAlpha = backdrop.settings.glassOpacity.coerceIn(0.05f, 0.45f)

    val interactiveModifier = if (onClick != null || onLongClick != null) {
        Modifier.pointerInput(onClick, onLongClick, bounceOnPress) {
            detectTapGestures(
                onPress = {
                    if (bounceOnPress) isPressed = true
                    tryAwaitRelease()
                    isPressed = false
                },
                onTap = { onClick?.invoke() },
                onLongPress = { onLongClick?.invoke() }
            )
        }
    } else Modifier

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .onGloballyPositioned { coords ->
                posInRoot = coords.positionInRoot()
                boxSize = coords.size
            }
            .clip(shape)
            .then(interactiveModifier)
    ) {
        val wp = backdrop.bitmap
        if (wp != null && boxSize.width > 4 && boxSize.height > 4) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null) {
                            shader.setFloatUniform(
                                "resolution",
                                boxSize.width.toFloat(),
                                boxSize.height.toFloat()
                            )
                            shader.setFloatUniform("cornerRadius", cornerPx)
                            shader.setFloatUniform("refractionStrength", refractionPx)
                            shader.setFloatUniform("tintAlpha", tintAlpha)

                            val blurEffect = RenderEffect.createBlurEffect(
                                blurPx,
                                blurPx,
                                Shader.TileMode.CLAMP
                            )
                            val glassEffect = RenderEffect.createRuntimeShaderEffect(
                                shader,
                                "content"
                            )
                            renderEffect = RenderEffect
                                .createChainEffect(glassEffect, blurEffect)
                                .asComposeRenderEffect()
                        }
                    }
            ) {
                translate(left = -posInRoot.x, top = -posInRoot.y) {
                    drawImage(
                        image = wp,
                        dstSize = IntSize(backdrop.screenWidthPx, backdrop.screenHeightPx)
                    )
                }
                if (extraTint != Color.Transparent) {
                    drawRect(color = extraTint)
                }
            }
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.55f),
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.30f)
                        )
                    ),
                    shape = shape
                ),
            content = content
        )
    }
}

object WallpaperStorage {
    private const val CUSTOM_WP_FILE = "custom_wallpaper.jpg"

    suspend fun loadWallpaper(context: Context, preset: Int): ImageBitmap =
        withContext(Dispatchers.Default) {
            if (preset == -1) {
                val file = File(context.filesDir, CUSTOM_WP_FILE)
                if (file.exists()) {
                    val bmp = BitmapFactory.decodeFile(file.absolutePath)
                    if (bmp != null) return@withContext bmp.asImageBitmap()
                }
            }
            generateFluxWallpaper(preset).asImageBitmap()
        }

    suspend fun saveCustomWallpaper(context: Context, uri: Uri): ImageBitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
                val raw = BitmapFactory.decodeStream(input)
                input.close()
                if (raw == null) return@withContext null

                val targetW = 720
                val targetH = 1600
                val scaled = centerCropBitmap(raw, targetW, targetH)
                if (scaled != raw) raw.recycle()

                val file = File(context.filesDir, CUSTOM_WP_FILE)
                FileOutputStream(file).use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
                scaled.asImageBitmap()
            }.getOrNull()
        }

    private fun centerCropBitmap(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val scale = maxOf(targetW.toFloat() / src.width, targetH.toFloat() / src.height)
        val scaledW = (src.width * scale).toInt().coerceAtLeast(targetW)
        val scaledH = (src.height * scale).toInt().coerceAtLeast(targetH)
        val resized = Bitmap.createScaledBitmap(src, scaledW, scaledH, true)
        val x = ((scaledW - targetW) / 2).coerceAtLeast(0)
        val y = ((scaledH - targetH) / 2).coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(resized, x, y, targetW, targetH)
        if (resized != cropped) resized.recycle()
        return cropped
    }

    private fun generateFluxWallpaper(preset: Int): Bitmap {
        val w = 540
        val h = 1200
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val palettes = listOf(
            intArrayOf(0xFF0A1636.toInt(), 0xFF153B75.toInt(), 0xFF00D4FF.toInt(), 0xFF8B5CF6.toInt(), 0xFFEC4899.toInt()),
            intArrayOf(0xFF051A14.toInt(), 0xFF0C3B2E.toInt(), 0xFF10B981.toInt(), 0xFF38BDF8.toInt(), 0xFFF59E0B.toInt()),
            intArrayOf(0xFF1D0E26.toInt(), 0xFF4C1D42.toInt(), 0xFFFF6B6B.toInt(), 0xFFFFB347.toInt(), 0xFF6366F1.toInt()),
            intArrayOf(0xFF090D16.toInt(), 0xFF1E293B.toInt(), 0xFF64748B.toInt(), 0xFF38BDF8.toInt(), 0xFFC084FC.toInt())
        )
        val p = palettes[preset.coerceIn(0, palettes.lastIndex)]

        paint.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), p[0], p[1], Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

        fun drawOrb(cx: Float, cy: Float, radius: Float, color: Int) {
            val transparent = color and 0x00FFFFFF
            paint.shader = RadialGradient(cx, cy, radius, color, transparent, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, radius, paint)
        }

        drawOrb(w * 0.2f, h * 0.22f, w * 0.85f, p[2])
        drawOrb(w * 0.88f, h * 0.45f, w * 0.90f, p[3])
        drawOrb(w * 0.32f, h * 0.82f, w * 0.95f, p[4])
        drawOrb(w * 0.80f, h * 0.92f, w * 0.65f, p[2])

        return bmp
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("liquid_launcher_prefs", Context.MODE_PRIVATE)

    fun loadSettings(): LauncherSettings {
        val styleName = prefs.getString("icon_style", IconStyle.LIQUID_CLEAR.name)
        val style = runCatching { IconStyle.valueOf(styleName ?: "") }.getOrDefault(IconStyle.LIQUID_CLEAR)
        return LauncherSettings(
            blurRadius = prefs.getFloat("blur_radius", 24f),
            refraction = prefs.getFloat("refraction", 22f),
            glassOpacity = prefs.getFloat("glass_opacity", 0.16f),
            gridColumns = prefs.getInt("grid_cols", 4),
            iconStyle = style,
            wallpaperPreset = prefs.getInt("wp_preset", 0)
        )
    }

    fun saveSettings(s: LauncherSettings) {
        prefs.edit()
            .putFloat("blur_radius", s.blurRadius)
            .putFloat("refraction", s.refraction)
            .putFloat("glass_opacity", s.glassOpacity)
            .putInt("grid_cols", s.gridColumns)
            .putString("icon_style", s.iconStyle.name)
            .putInt("wp_preset", s.wallpaperPreset)
            .apply()
    }

    fun loadPinnedDock(): List<String> {
        val raw = prefs.getString("dock_packages", "") ?: ""
        return raw.split(",").filter { it.isNotBlank() }
    }

    fun savePinnedDock(packages: List<String>) {
        prefs.edit().putString("dock_packages", packages.distinct().take(5).joinToString(",")).apply()
    }
}

@Immutable
data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap
)

object AppRepository {
    suspend fun loadInstalledApps(context: Context): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        val selfPkg = context.packageName

        resolveInfos
            .asSequence()
            .filter { it.activityInfo.packageName != selfPkg }
            .distinctBy { it.activityInfo.packageName }
            .mapNotNull { info ->
                runCatching {
                    val label = info.loadLabel(pm).toString()
                    val pkg = info.activityInfo.packageName
                    val drawable = info.loadIcon(pm)
                    val bitmap = drawable.toBitmap(width = 144, height = 144).asImageBitmap()
                    AppInfo(label = label, packageName = pkg, icon = bitmap)
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun launchApp(context: Context, packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openAppInfo(context: Context, packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun uninstallApp(context: Context, packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openSystemSettings(context: Context) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun tryQuickCalculate(query: String): String? {
        val clean = query.replace(" ", "").replace(",", ".")
        val match = Regex("^(-?\\d+(?:\\.\\d+)?)([+\\-*/])(-?\\d+(?:\\.\\d+)?)$").find(clean)
            ?: return null
        val a = match.groupValues[1].toDoubleOrNull() ?: return null
        val op = match.groupValues[2]
        val b = match.groupValues[3].toDoubleOrNull() ?: return null
        val result = when (op) {
            "+" -> a + b
            "-" -> a - b
            "*" -> a * b
            "/" -> if (b != 0.0) a / b else return null
            else -> return null
        }
        return if (result % 1.0 == 0.0) result.toLong().toString()
        else String.format(Locale.US, "%.2f", result)
    }
}

package com.bomo.colorcompletion

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import com.bomo.colorcompletion.AppListProvider
import com.bomo.colorcompletion.ModuleApplication
import com.bomo.colorcompletion.ui.SelectionRepository
import com.bomo.colorcompletion.ui.theme.ColorCompletionTheme
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Switch
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.LocalContentColor
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** COUI 卡片圆角，与 ONextBox AppUiTokens.CardCornerRadius 一致（squircle 视觉扩张 1.2819 倍）。@author bomo */
private val CardCornerRadius = 20.dp

/** 应用条目；两个名单共用一份已安装应用枚举。@author bomo */
private data class AppEntry(val label: String, val pkg: String) {
    /** 名称或包名包含查询串时命中；查询为空全部命中。@author bomo */
    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        return label.lowercase().contains(q) || pkg.lowercase().contains(q)
    }
}

/** 应用图标内存缓存；避免列表滚动时重复解码。@author bomo */
private val iconCache = HashMap<String, ImageBitmap>()

/** 枚举可启动应用并按标签排序；在 IO 线程执行。@author bomo */
private suspend fun loadApps(context: android.content.Context): List<AppEntry> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    pm.getInstalledApplications(0)
        .filter { pm.getLaunchIntentForPackage(it.packageName) != null && it.packageName != context.packageName }
        .map { AppEntry(pm.getApplicationLabel(it).toString(), it.packageName) }
        .sortedBy { it.label.lowercase() }
}

class MainActivity : ComponentActivity() {
    /** 创建界面；边缘到edge、状态栏图标随深浅色、挂载 COUI 主题与设置页。@author bomo */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        setContent {
            ColorCompletionTheme {
                CompletionApp()
            }
        }
    }
}

/** 设置页主体：状态卡 + 三个折叠分组，色彩与横屏各维护一份自选名单。@author bomo */
@Composable
private fun CompletionApp() {
    val context = LocalContext.current
    val repo = remember { SelectionRepository(context) }
    var apps by remember { mutableStateOf(emptyList<AppEntry>()) }
    LaunchedEffect(Unit) { apps = loadApps(context) }

    var colorSet by remember { mutableStateOf(repo.load(AppListProvider.KEY)) }
    var landscapeSet by remember { mutableStateOf(repo.load(ModuleApplication.LANDSCAPE_KEY)) }
    var srExpanded by remember { mutableStateOf(false) }
    var colorExpanded by remember { mutableStateOf(false) }
    var landscapeExpanded by remember { mutableStateOf(false) }
    var colorQuery by remember { mutableStateOf("") }
    var landscapeQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = { SmallTopAppBar(title = "功能补全", subtitle = "视频画质 · 色彩 · 横屏布局") },
        containerColor = COUITheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item(key = "status") {
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 8.dp), cornerRadius = CardCornerRadius) {
                    Text(
                        text = "开发版：色彩名单并入厂商名单后立即生效；横屏名单需重新打开系统设置的「应用横屏显示」页；厂商原有行为不受关闭影响。",
                        style = COUITheme.textStyles.footnote1,
                        color = COUITheme.colorScheme.onSurfaceContainerVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            // 画质超清增强：本平台无 filter/vpp 管线，仅保留结论说明。
            item(key = "sr-head") {
                JoinedRow(0, if (srExpanded) 2 else 1) {
                    GroupHeader("画质超清增强", "平台不支持", srExpanded) { srExpanded = !srExpanded }
                }
            }
            if (srExpanded) {
                item(key = "sr-note") {
                    JoinedRow(1, 2) {
                        Text(
                            text = "本平台编解码组件库未注册任何 filter/vpp 组件，超清所依附的滤镜不存在，无法实现。\n（2026-11-04 跨机型移植实验结论，详见项目文档）",
                            style = COUITheme.textStyles.footnote1,
                            color = COUITheme.colorScheme.onSurfaceContainerVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }

            // 视频色彩增强：媒体进程名单钩子，动态生效。
            val colorFiltered = apps.filter { it.matches(colorQuery) }
            val colorCount = 1 + if (colorExpanded) (if (colorFiltered.isEmpty()) 2 else 1 + colorFiltered.size) else 0
            item(key = "color-head") {
                JoinedRow(0, colorCount) {
                    GroupHeader("视频色彩增强", "自选应用名单 · 立即生效", colorExpanded) { colorExpanded = !colorExpanded }
                }
            }
            if (colorExpanded) {
                item(key = "color-search") { JoinedRow(1, colorCount) { SearchField(colorQuery) { colorQuery = it } } }
                if (colorFiltered.isEmpty()) {
                    item(key = "color-empty") { JoinedRow(2, colorCount) { EmptyRow("没有匹配的应用") } }
                } else {
                    itemsIndexed(colorFiltered, key = { _, entry -> "color-${entry.pkg}" }) { index, entry ->
                        JoinedRow(2 + index, colorCount) {
                            AppRow(entry, entry.pkg in colorSet) { on ->
                                colorSet = toggleApp(repo, AppListProvider.KEY, colorSet, entry, on)
                            }
                        }
                    }
                }
            }

            // 应用横屏布局：设置进程钩子，需重新打开设置页生效。
            val landFiltered = apps.filter { it.matches(landscapeQuery) }
            val landCount = 1 + if (landscapeExpanded) (if (landFiltered.isEmpty()) 2 else 1 + landFiltered.size) else 0
            item(key = "land-head") {
                JoinedRow(0, landCount) {
                    GroupHeader("应用横屏布局", "自选应用获得四种布局 · 重开设置页生效", landscapeExpanded) { landscapeExpanded = !landscapeExpanded }
                }
            }
            if (landscapeExpanded) {
                item(key = "land-note") {
                    JoinedRow(1, landCount) {
                        Text(
                            text = "开启后，该应用在 系统设置 → 应用横屏显示 中可用 自适应 / 双窗 / 居中 / 原始 四种布局；修改名单后需重新打开该设置页。",
                            style = COUITheme.textStyles.footnote1,
                            color = COUITheme.colorScheme.onSurfaceContainerVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                item(key = "land-search") { JoinedRow(2, landCount) { SearchField(landscapeQuery) { landscapeQuery = it } } }
                if (landFiltered.isEmpty()) {
                    item(key = "land-empty") { JoinedRow(3, landCount) { EmptyRow("没有匹配的应用") } }
                } else {
                    itemsIndexed(landFiltered, key = { _, entry -> "land-${entry.pkg}" }) { index, entry ->
                        JoinedRow(3 + index, landCount) {
                            AppRow(entry, entry.pkg in landscapeSet) { on ->
                                landscapeSet = toggleApp(repo, ModuleApplication.LANDSCAPE_KEY, landscapeSet, entry, on)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 切换应用选中态并持久化；保存失败时保持原集合。@author bomo */
private fun toggleApp(
    repo: SelectionRepository,
    key: String,
    selected: Set<String>,
    entry: AppEntry,
    on: Boolean,
): Set<String> {
    val next = LinkedHashSet(selected)
    if (on) next.add(entry.pkg) else next.remove(entry.pkg)
    return if (repo.save(key, next)) next else selected
}

/** 拼接列表行：首行上圆角、末行下圆角，行间 1dp 分隔线，末行带组间距。@author bomo */
@Composable
private fun JoinedRow(index: Int, count: Int, content: @Composable ColumnScope.() -> Unit) {
    val colors = CardDefaults.defaultColors()
    val top = if (index == 0) CardCornerRadius else 0.dp
    val bottom = if (index == count - 1) CardCornerRadius else 0.dp
    val last = index == count - 1
    CompositionLocalProvider(LocalContentColor provides colors.contentColor) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = if (last) 8.dp else 0.dp)
                .squircleSurface(colors.color, top, top, bottom, bottom)
        ) {
            content()
            if (!last) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(COUITheme.colorScheme.dividerLine)
                )
            }
        }
    }
}

/** 分组标题行：标题 + 副标题 + 折叠箭头，点击展开或收起。@author bomo */
@Composable
private fun GroupHeader(title: String, subtitle: String, expanded: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "arrow")
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp, 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = COUITheme.textStyles.body1,
                color = COUITheme.colorScheme.onSurfaceContainer,
            )
            Text(
                text = subtitle,
                style = COUITheme.textStyles.footnote2,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = COUITheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.size(22.dp).rotate(rotation),
        )
    }
}

/** 应用开关行：图标 + 名称/包名 + COUI 开关。@author bomo */
@Composable
private fun AppRow(entry: AppEntry, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp, 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(entry.pkg, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.label,
                style = COUITheme.textStyles.body1,
                color = COUITheme.colorScheme.onSurfaceContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.pkg,
                style = COUITheme.textStyles.footnote2,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 应用图标；异步解码并写入内存缓存。@author bomo */
@Composable
private fun AppIcon(pkg: String, size: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    var bitmap by remember(pkg) { mutableStateOf(iconCache[pkg]) }
    LaunchedEffect(pkg) {
        if (bitmap == null) {
            val loaded = withContext(Dispatchers.IO) {
                try {
                    val drawable = context.packageManager.getApplicationIcon(pkg)
                    val bmp = if (drawable is android.graphics.drawable.BitmapDrawable && drawable.bitmap != null) {
                        drawable.bitmap
                    } else {
                        val w = drawable.intrinsicWidth.coerceAtLeast(1)
                        val h = drawable.intrinsicHeight.coerceAtLeast(1)
                        val created = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(created)
                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                        drawable.draw(canvas)
                        created
                    }
                    bmp.asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            }
            if (loaded != null) {
                synchronized(iconCache) { iconCache[pkg] = loaded }
                bitmap = loaded
            }
        }
    }
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        bitmap?.let {
            Image(bitmap = it, contentDescription = null, modifier = Modifier.size(size))
        }
    }
}

/** 搜索框：放大镜图标 + 单行输入 + 清除。@author bomo */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp, 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = COUITheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = COUITheme.textStyles.body1.copy(color = COUITheme.colorScheme.onSurfaceContainer),
                cursorBrush = SolidColor(COUITheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
            if (query.isEmpty()) {
                Text(
                    text = "搜索应用名称或包名",
                    style = COUITheme.textStyles.body1,
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        if (query.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "清除",
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.primary,
                modifier = Modifier.clickable { onQueryChange("") },
            )
        }
    }
}

/** 空结果提示行。@author bomo */
@Composable
private fun EmptyRow(hint: String) {
    Text(
        text = hint,
        style = COUITheme.textStyles.footnote1,
        color = COUITheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.fillMaxWidth().padding(16.dp, 20.dp),
    )
}

package com.bomo.colorcompletion.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.suqi8.coui.kmp.theme.darkColorScheme
import io.github.suqi8.coui.kmp.theme.lightColorScheme
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 功能补全界面主题：COUI 原生配色（OPPO 蓝，亮 #0066FF / 暗 #247CFF），随系统深浅色切换。@author bomo */
@Composable
fun ColorCompletionTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (dark) {
        // COUI 深色方案本身就是纯黑页面 + 半透明白卡片，层次已足够。
        darkColorScheme()
    } else {
        // COUI 亮色方案默认 background 与 surfaceContainer 同为纯白，卡片没有视觉边界；
        // 页面底色沿用 build.py 版的 #F4F6FB，卡片保持白色形成拼接感。
        lightColorScheme(background = Color(0xFFF4F6FB))
    }
    COUITheme(colors = colors, content = content)
}

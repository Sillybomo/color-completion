package com.bomo.colorcompletion.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import io.github.suqi8.coui.kmp.theme.darkColorScheme
import io.github.suqi8.coui.kmp.theme.lightColorScheme
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 功能补全界面主题：COUI 原生配色（OPPO 蓝，亮 #0066FF / 暗 #247CFF），随系统深浅色切换。@author bomo */
@Composable
fun ColorCompletionTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    COUITheme(colors = if (dark) darkColorScheme() else lightColorScheme(), content = content)
}

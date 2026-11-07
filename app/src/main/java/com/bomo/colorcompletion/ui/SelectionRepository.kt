package com.bomo.colorcompletion.ui

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.bomo.colorcompletion.AppListProvider
import com.bomo.colorcompletion.ModuleApplication

/** 名单仓库：读写本地两份名单并同步到框架远程配置。@author bomo */
class SelectionRepository(context: Context) {
    private val app = context.applicationContext
    private val prefs: SharedPreferences = app.getSharedPreferences(AppListProvider.PREFS, Context.MODE_PRIVATE)

    /** 读取指定键的名单；非法条目忽略。@author bomo */
    fun load(key: String): Set<String> {
        val raw = prefs.getString(key, "") ?: return emptySet()
        val out = LinkedHashSet<String>()
        for (pkg in raw.split("\n")) if (PACKAGE_PATTERN.matches(pkg)) out.add(pkg)
        return out
    }

    /** 保存名单；写本地、通知提供方、同步框架，提交失败返回 false。@author bomo */
    fun save(key: String, value: Set<String>): Boolean {
        if (!prefs.edit().putString(key, value.sorted().joinToString("\n")).commit()) return false
        app.contentResolver.notifyChange(Uri.parse(AppListProvider.URI), null)
        (app as? ModuleApplication)?.synchronizeSelection()
        return true
    }

    private companion object {
        val PACKAGE_PATTERN = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")
    }
}

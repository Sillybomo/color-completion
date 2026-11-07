package com.bomo.colorcompletion;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/** 管理模块应用与 LSPosed 远程配置之间的连接。@author bomo */
public class ModuleApplication extends Application {
    /** 横屏布局名单在本地与远程配置中的键名。@author bomo */
    public static final String LANDSCAPE_KEY = "landscape";

    private volatile XposedService service;

    /** 监听框架服务连接；配置在服务可用后同步。@author bomo */
    @Override public void onCreate() {
        super.onCreate();
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            /** 服务连接后同步本地名单；参数为框架服务。@author bomo */
            @Override public void onServiceBind(XposedService value) { service = value; synchronizeSelection(); }
            /** 服务断开时保留本地名单，等待下次连接。@author bomo */
            @Override public void onServiceDied(XposedService value) { if (service == value) service = null; }
        });
    }
    /** 将本地两份名单同步到框架；服务未连接或任一同步失败时返回 false，不丢失本地选择。@author bomo */
    public boolean synchronizeSelection() {
        XposedService current = service;
        SharedPreferences local = getSharedPreferences(AppListProvider.PREFS, 0);
        if (current == null) return false;
        boolean ok = true;
        try {
            SharedPreferences remote = current.getRemotePreferences("selection");
            ok = sync(remote, local, AppListProvider.KEY, "packages") & sync(remote, local, LANDSCAPE_KEY, LANDSCAPE_KEY);
        } catch (RuntimeException error) { Log.e("功能补全", "框架配置同步失败", error); return false; }
        return ok;
    }
    /** 同步单个键；本地无该键视为成功，提交失败记录日志并返回 false。@author bomo */
    private boolean sync(SharedPreferences remote, SharedPreferences local, String localKey, String remoteKey) {
        if (!local.contains(localKey)) return true;
        String selection = local.getString(localKey, "");
        if (selection.equals(remote.getString(remoteKey, null))) return true;
        boolean ok = remote.edit().putString(remoteKey, selection).commit();
        if (!ok) Log.e("功能补全", "框架配置保存失败：" + remoteKey);
        return ok;
    }
}

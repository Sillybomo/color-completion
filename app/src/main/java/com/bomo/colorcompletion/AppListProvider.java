package com.bomo.colorcompletion;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Process;

/** 向本应用及系统 UID 提供只读名单，不对普通应用公开数据。@author bomo */
public class AppListProvider extends ContentProvider {
    public static final String URI = "content://com.bomo.colorcompletion.apps/list";
    public static final String PREFS = "selection";
    public static final String KEY = "packages";

    /** 初始化接口；无参数，始终返回成功。@author bomo */
    @Override public boolean onCreate() { return true; }
    /** 查询名单；仅接受固定路径及系统或本应用调用，其他调用抛出安全异常。@author bomo */
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        int uid = Binder.getCallingUid();
        if (uid != Process.SYSTEM_UID && uid != Process.myUid()) throw new SecurityException("不允许读取应用名单");
        if (!URI.equals(uri.toString())) throw new IllegalArgumentException("未知路径");
        MatrixCursor cursor = new MatrixCursor(new String[]{"packages"});
        cursor.addRow(new Object[]{getContext().getSharedPreferences(PREFS, 0).getString(KEY, "")});
        return cursor;
    }
    /** 返回固定 MIME 类型；未知路径返回空。@author bomo */
    @Override public String getType(Uri uri) { return URI.equals(uri.toString()) ? "vnd.android.cursor.item/vnd.bomo.applist" : null; }
    /** 禁止通过接口写入；始终抛出异常。@author bomo */
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("只读接口"); }
    /** 禁止通过接口删除；始终抛出异常。@author bomo */
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException("只读接口"); }
    /** 禁止通过接口更新；始终抛出异常。@author bomo */
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException("只读接口"); }
}

package com.bomo.colorcompletion;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 功能补全应用选择界面。@author bomo */
public class MainActivity extends Activity {
    private boolean reverting;
    private LinearLayout list;
    private TextView status;
    private final Set<String> selected = new HashSet<>();

    /** 创建界面；参数为系统恢复状态，界面从系统设置读取已选应用。现版本已改为读取模块私有存储。 @author bomo */
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        initializeDefaults();
        ((ModuleApplication)getApplication()).synchronizeSelection();
        String saved = getSharedPreferences(AppListProvider.PREFS, 0).getString(AppListProvider.KEY, "");
        if (saved != null) Collections.addAll(selected, saved.split("\\n"));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(244,246,251));
        TextView header = text("功能补全\n视频画质与色彩", 23, Color.WHITE); header.setTypeface(null, Typeface.BOLD); header.setPadding(dp(22), dp(28), dp(22), dp(25));
        header.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xff304f9c, 0xff7294da})); root.addView(header);
        ScrollView scroll = new ScrollView(this); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(16), dp(16), dp(16), dp(20)); scroll.addView(body);
        status = text("", 14, 0xff43516c); status.setPadding(dp(16), dp(14), dp(16), dp(14)); card(status); body.addView(status);
        TextView resolution = text("画质超清增强\n正在验证系统支持，暂不可用", 16, 0xff59677e); resolution.setPadding(dp(16), dp(16), dp(16), dp(16)); card(resolution); addSpace(body); body.addView(resolution);
        TextView title = text("视频色彩增强 · 额外应用", 18, 0xff18233d); title.setTypeface(null, Typeface.BOLD); title.setPadding(0, dp(18), 0, dp(10)); body.addView(title);
        EditText search = new EditText(this); search.setSingleLine(true); search.setTextSize(15); search.setHint("搜索应用名称或包名"); search.setPadding(dp(16), dp(8), dp(16), dp(8)); card(search); body.addView(search);
        addSpace(body);
        list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); card(list); body.addView(list);
        setContentView(root); populate();
        search.addTextChangedListener(new TextWatcher() {
            /** 输入变化前不做处理；参数由系统提供。@author bomo */
            public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            /** 根据名称或包名过滤已加载的应用；参数由系统提供。@author bomo */
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                String query = value.toString().trim().toLowerCase(Locale.ROOT);
                for (int i = 0; i < list.getChildCount(); i++) {
                    View row = list.getChildAt(i);
                    row.setVisibility(((String) row.getTag()).contains(query) ? View.VISIBLE : View.GONE);
                }
            }
            /** 输入变化后不做处理；参数由系统提供。@author bomo */
            public void afterTextChanged(Editable value) { }
        });
    }

    /** 首次启动时选择已安装的视频应用；后续保留用户修改，保存失败则不标记完成。@author bomo */
    private void initializeDefaults() {
        android.content.SharedPreferences preferences = getSharedPreferences(AppListProvider.PREFS, 0);
        if (preferences.contains(AppListProvider.KEY)) return;
        String[] candidates = {"com.phoenix.read", "com.ss.android.ugc.aweme", "tv.danmaku.bili", "com.tencent.qqlive", "com.youku.phone", "com.qiyi.video"};
        List<String> installed = new ArrayList<>();
        PackageManager pm = getPackageManager();
        for (String pkg : candidates) {
            try { pm.getPackageInfo(pkg, 0); installed.add(pkg); }
            catch (PackageManager.NameNotFoundException ignored) { /* 未安装的应用不预选。 */ }
        }
        if (preferences.edit().putString(AppListProvider.KEY, String.join("\n", installed)).commit()) {
            getContentResolver().notifyChange(Uri.parse(AppListProvider.URI), null);
        }
    }

    /** 恢复界面时刷新权限说明；无参数及返回值。 @author bomo */
    @Override protected void onResume() {
        super.onResume();
        if (status != null) status.setText("开发版：预选应用仅代表加入名单，实际效果尚未验证；厂商原有名单不受此处开关控制。");
    }

    /** 枚举可启动应用并显示选择项；无法写入设置时保持原选择。现版本的设置保存在模块私有存储。 @author bomo */
    private void populate() {
        PackageManager pm = getPackageManager(); List<ApplicationInfo> apps = new ArrayList<>();
        for (ApplicationInfo app : pm.getInstalledApplications(0)) if (pm.getLaunchIntentForPackage(app.packageName) != null && !app.packageName.equals(getPackageName())) apps.add(app);
        Collections.sort(apps, Comparator.comparing(a -> pm.getApplicationLabel(a).toString(), String.CASE_INSENSITIVE_ORDER));
        for (ApplicationInfo app : apps) {
            Switch row = new Switch(this); row.setText(pm.getApplicationLabel(app) + "\n" + app.packageName); row.setTextSize(14); row.setTextColor(0xff202b42); row.setPadding(dp(16), dp(12), dp(16), dp(12)); row.setGravity(Gravity.CENTER_VERTICAL); row.setChecked(selected.contains(app.packageName));
            row.setTag((pm.getApplicationLabel(app).toString() + " " + app.packageName).toLowerCase(Locale.ROOT));
            row.setOnCheckedChangeListener((button, checked) -> {
                if (reverting) return;
                if (checked) selected.add(app.packageName); else selected.remove(app.packageName);
                List<String> values = new ArrayList<>(selected); Collections.sort(values);
                if (!getSharedPreferences(AppListProvider.PREFS, 0).edit().putString(AppListProvider.KEY, String.join("\n", values)).commit()) {
                    if (checked) selected.remove(app.packageName); else selected.add(app.packageName);
                    reverting = true; button.setChecked(!checked); reverting = false;
                    android.widget.Toast.makeText(this, "保存失败，请重试", android.widget.Toast.LENGTH_LONG).show();
                } else { getContentResolver().notifyChange(Uri.parse(AppListProvider.URI), null); ((ModuleApplication)getApplication()).synchronizeSelection(); }
            }); list.addView(row);
        }
    }

    /** 创建文本控件；参数为文本、字号和颜色，返回已配置控件。 @author bomo */
    private TextView text(String value, int size, int color) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); return t; }
    /** 将控件设为白色圆角卡片；参数为目标控件。 @author bomo */
    private void card(View view) { GradientDrawable d = new GradientDrawable(); d.setColor(Color.WHITE); d.setCornerRadius(dp(16)); view.setBackground(d); }
    /** 增加卡片间距；参数为父容器。 @author bomo */
    private void addSpace(LinearLayout parent) { View spacer = new View(this); parent.addView(spacer, new LinearLayout.LayoutParams(1, dp(10))); }
    /** 转换设备无关像素；参数为整数像素，返回屏幕实际像素。 @author bomo */
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }
}

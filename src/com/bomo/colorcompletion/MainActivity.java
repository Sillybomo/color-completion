package com.bomo.colorcompletion;

import android.app.Activity;
import android.content.SharedPreferences;
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
import android.widget.EditText;
import android.widget.LinearLayout;
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

/** 功能补全界面：超清、色彩、横屏三个折叠分组，色彩与横屏各维护一份自选名单。@author bomo */
public class MainActivity extends Activity {
    private boolean reverting;
    private TextView status;
    private final Set<String> colorSelected = new HashSet<>();
    private final Set<String> landscapeSelected = new HashSet<>();
    private final List<ApplicationInfo> apps = new ArrayList<>();

    /** 已安装应用条目缓存；两个名单共用，避免重复枚举。@author bomo */
    private void loadApps() {
        PackageManager pm = getPackageManager();
        for (ApplicationInfo app : pm.getInstalledApplications(0)) {
            if (pm.getLaunchIntentForPackage(app.packageName) != null && !app.packageName.equals(getPackageName())) apps.add(app);
        }
        Collections.sort(apps, Comparator.comparing(a -> pm.getApplicationLabel(a).toString(), String.CASE_INSENSITIVE_ORDER));
    }

    /** 创建界面；从私有存储读取两份名单，构建三个折叠分组。@author bomo */
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        initializeDefaults();
        ((ModuleApplication) getApplication()).synchronizeSelection();
        SharedPreferences prefs = getSharedPreferences(AppListProvider.PREFS, 0);
        loadSet(prefs.getString(AppListProvider.KEY, ""), colorSelected);
        loadSet(prefs.getString(ModuleApplication.LANDSCAPE_KEY, ""), landscapeSelected);
        loadApps();

        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(244, 246, 251));
        TextView header = text("功能补全\n视频画质 · 色彩 · 横屏布局", 23, Color.WHITE); header.setTypeface(null, Typeface.BOLD); header.setPadding(dp(22), dp(28), dp(22), dp(25));
        header.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xff304f9c, 0xff7294da})); root.addView(header);
        ScrollView scroll = new ScrollView(this); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(16), dp(16), dp(16), dp(20)); scroll.addView(body);

        status = text("", 14, 0xff43516c); status.setPadding(dp(16), dp(14), dp(16), dp(14)); card(status); body.addView(status);
        addSpace(body);

        // 画质超清增强：本平台无对应管线，只保留结论说明。
        LinearLayout srContent = section(body, "画质超清增强", "平台不支持");
        TextView srNote = text("本平台编解码组件库未注册任何 filter/vpp 组件，超清所依附的滤镜不存在，无法实现。\n（2026-11-04 跨机型移植实验结论，详见项目文档）", 14, 0xff59677e);
        srNote.setPadding(dp(16), dp(14), dp(16), dp(14)); srContent.addView(srNote);
        addSpace(body);

        // 视频色彩增强：媒体进程名单钩子，动态生效。
        LinearLayout colorContent = section(body, "视频色彩增强", "自选应用名单 · 立即生效");
        colorContent.addView(searchBox(null));
        LinearLayout colorList = new LinearLayout(this); colorList.setOrientation(LinearLayout.VERTICAL); card(colorList);
        colorContent.addView(colorList);
        attachSearch(colorContent, colorList);
        addSpace(body);

        // 应用横屏布局：设置进程钩子，需重新打开设置页生效。
        LinearLayout landContent = section(body, "应用横屏布局", "自选应用获得四种布局 · 重开设置页生效");
        TextView landHint = text("开启后，该应用在 系统设置 → 应用横屏显示 中可用 自适应 / 双窗 / 居中 / 原始 四种布局；修改名单后需重新打开该设置页。", 13, 0xff59677e);
        landHint.setPadding(dp(16), dp(10), dp(16), dp(10)); landContent.addView(landHint);
        landContent.addView(searchBox(null));
        LinearLayout landList = new LinearLayout(this); landList.setOrientation(LinearLayout.VERTICAL); card(landList);
        landContent.addView(landList);
        attachSearch(landContent, landList);

        setContentView(root);
        populate(colorList, colorSelected, AppListProvider.KEY);
        populate(landList, landscapeSelected, ModuleApplication.LANDSCAPE_KEY);
    }

    /** 按换行解析包名集合；非法条目忽略。@author bomo */
    private static void loadSet(String saved, Set<String> target) {
        if (saved == null) return;
        for (String pkg : saved.split("\\n")) if (pkg.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) target.add(pkg);
    }

    /** 构建折叠分组：返回内容容器；点标题行展开或收起。@author bomo */
    private LinearLayout section(LinearLayout body, String title, String subtitle) {
        LinearLayout wrapper = new LinearLayout(this); wrapper.setOrientation(LinearLayout.VERTICAL); card(wrapper); body.addView(wrapper);
        LinearLayout head = new LinearLayout(this); head.setOrientation(LinearLayout.HORIZONTAL); head.setGravity(Gravity.CENTER_VERTICAL); head.setPadding(dp(16), dp(14), dp(16), dp(14)); wrapper.addView(head);
        LinearLayout texts = new LinearLayout(this); texts.setOrientation(LinearLayout.VERTICAL); head.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        TextView name = text(title, 17, 0xff18233d); name.setTypeface(null, Typeface.BOLD); texts.addView(name);
        TextView sub = text(subtitle, 12, 0xff8a94a8); texts.addView(sub);
        TextView arrow = text("▶", 15, 0xff8a94a8); arrow.setPadding(dp(8), 0, 0, 0); head.addView(arrow);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setVisibility(View.GONE); wrapper.addView(content);
        head.setOnClickListener(v -> {
            boolean open = content.getVisibility() == View.VISIBLE;
            content.setVisibility(open ? View.GONE : View.VISIBLE);
            arrow.setText(open ? "▶" : "▼");
        });
        return content;
    }

    /** 创建搜索框；参数保留为空以便统一外观。@author bomo */
    private EditText searchBox(Void unused) {
        EditText search = new EditText(this); search.setSingleLine(true); search.setTextSize(15); search.setHint("搜索应用名称或包名"); search.setPadding(dp(16), dp(8), dp(16), dp(8)); card(search);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.setMargins(0, dp(4), 0, dp(10)); search.setLayoutParams(params);
        return search;
    }

    /** 将分组内最后一个搜索框绑定到目标列表的过滤。@author bomo */
    private void attachSearch(LinearLayout content, LinearLayout list) {
        EditText search = null;
        for (int i = 0; i < content.getChildCount(); i++) {
            View child = content.getChildAt(i);
            if (child instanceof EditText) search = (EditText) child;
        }
        if (search == null) return;
        search.addTextChangedListener(new TextWatcher() {
            /** 输入变化前不做处理；参数由系统提供。@author bomo */
            public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            /** 根据名称或包名过滤目标列表；参数由系统提供。@author bomo */
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

    /** 首次启动时为色彩名单预选已安装的视频应用；后续保留用户修改。@author bomo */
    private void initializeDefaults() {
        SharedPreferences preferences = getSharedPreferences(AppListProvider.PREFS, 0);
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

    /** 恢复界面时刷新说明；无参数及返回值。@author bomo */
    @Override protected void onResume() {
        super.onResume();
        if (status != null) status.setText("开发版：色彩名单并入厂商名单后立即生效；横屏名单需重新打开系统设置的「应用横屏显示」页；厂商原有行为不受关闭影响。");
    }

    /** 为目标名单生成应用开关行；保存失败时回滚界面状态。@author bomo */
    private void populate(LinearLayout list, Set<String> selected, String key) {
        PackageManager pm = getPackageManager();
        for (ApplicationInfo app : apps) {
            Switch row = new Switch(this); row.setText(pm.getApplicationLabel(app) + "\n" + app.packageName); row.setTextSize(14); row.setTextColor(0xff202b42); row.setPadding(dp(16), dp(12), dp(16), dp(12)); row.setGravity(Gravity.CENTER_VERTICAL); row.setChecked(selected.contains(app.packageName));
            row.setTag((pm.getApplicationLabel(app).toString() + " " + app.packageName).toLowerCase(Locale.ROOT));
            row.setOnCheckedChangeListener((button, checked) -> {
                if (reverting) return;
                if (checked) selected.add(app.packageName); else selected.remove(app.packageName);
                List<String> values = new ArrayList<>(selected); Collections.sort(values);
                if (!getSharedPreferences(AppListProvider.PREFS, 0).edit().putString(key, String.join("\n", values)).commit()) {
                    if (checked) selected.remove(app.packageName); else selected.add(app.packageName);
                    reverting = true; button.setChecked(!checked); reverting = false;
                    android.widget.Toast.makeText(this, "保存失败，请重试", android.widget.Toast.LENGTH_LONG).show();
                } else {
                    getContentResolver().notifyChange(Uri.parse(AppListProvider.URI), null);
                    ((ModuleApplication) getApplication()).synchronizeSelection();
                }
            });
            list.addView(row);
        }
    }

    /** 创建文本控件；参数为文本、字号和颜色，返回已配置控件。@author bomo */
    private TextView text(String value, int size, int color) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); return t; }
    /** 将控件设为白色圆角卡片；参数为目标控件。@author bomo */
    private void card(View view) { GradientDrawable d = new GradientDrawable(); d.setColor(Color.WHITE); d.setCornerRadius(dp(16)); view.setBackground(d); }
    /** 增加卡片间距；参数为父容器。@author bomo */
    private void addSpace(LinearLayout parent) { View spacer = new View(this); parent.addView(spacer, new LinearLayout.LayoutParams(1, dp(10))); }
    /** 转换设备无关像素；参数为整数像素，返回屏幕实际像素。@author bomo */
    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + 0.5f); }
}

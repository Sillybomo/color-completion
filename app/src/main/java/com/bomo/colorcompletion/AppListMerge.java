package com.bomo.colorcompletion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 构造厂商名单与用户额外名单的并集，不修改原列表。@author bomo */
public final class AppListMerge {
    /** 参数为厂商原始列表和换行分隔的包名；返回新列表，空配置保留原名单。@author bomo */
    public static List<String> merge(List<String> base, String saved) {
        if (base == null) throw new IllegalArgumentException("base is null");
        List<String> result = new ArrayList<>(base);
        Set<String> seen = new HashSet<>(base);
        if (saved != null) for (String pkg : saved.split("\\n")) {
            if (pkg.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+") && seen.add(pkg)) result.add(pkg);
        }
        return result;
    }
}

package zzw.utils;

import arc.util.Log;
import mindustry.Vars;
import mindustry.type.Item;

/**
 * JS 和 Java 桥接工具类
 * 提供 Java 和 JS 之间的互操作
 * 当前架构：Java 加载内容，JS 引用内容
 */
public class JSBridge {
    
    /**
     * 从已加载的内容中获取物品（Java 和 JS 通用）
     * @param name 物品名称
     * @return 物品对象，如果不存在返回 null
     */
    public static Item getItem(String name) {
        try {
            if (Vars.content == null) {
                return null;
            }
            return Vars.content.item(name);
        } catch (Exception e) {
            Log.err("[ZZW] Error getting item " + name + ": " + e);
            return null;
        }
    }
    
    /**
     * 检查脚本环境是否可用
     * @return 脚本是否可用
     */
    public static boolean isScriptEnabled() {
        try {
            return Vars.mods != null && Vars.mods.getScripts() != null;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 在 JS 环境中执行代码
     * @param code 要执行的 JavaScript 代码
     */
    public static void evalInJS(String code) {
        try {
            if (isScriptEnabled()) {
                Vars.mods.getScripts().runConsole(code);
            }
        } catch (Exception e) {
            Log.err("[ZZW] Error evaluating JS: " + e);
        }
    }
    
    /**
     * 从 JS 全局作用域获取对象
     * @param name 变量名
     * @return 对象，如果不存在返回 null
     */
    public static Object getJSGlobal(String name) {
        try {
            if (!isScriptEnabled()) {
                return null;
            }
            return Vars.mods.getScripts().scope.get(name, Vars.mods.getScripts().scope);
        } catch (Exception e) {
            Log.err("[ZZW] Error getting JS global " + name + ": " + e);
            return null;
        }
    }
}


package zzw.content.units;

import arc.func.Prov;
import arc.struct.ObjectIntMap;
import arc.util.Strings;
import mindustry.gen.EntityMapping;
import mindustry.gen.Entityc;

/**
 * 自定义 Entity 注册工具 (模仿 PU132 的 UnityEntityMapping)
 *
 * v154.3 要求每个 Entity 子类有唯一 classId, 否则 UnitType.init() 会抛异常.
 * arc 的 EntityMapping 没有暴露 "注册并返回 id" 的 API, 所以我们自己实现:
 *
 * 1. 遍历 EntityMapping.idMap 找一个空 slot
 * 2. 把 prov 放进去
 * 3. 在 ids map 里记录 type → slot (作为 classId)
 * 4. 同时注册到 nameMap (按类名和 kebab-case 名)
 *
 * 子类重写 classId() 调用 classId(getClass()) 返回这个 id
 */
public class ZEntityRegister {
    /** type → classId 映射 */
    private static final ObjectIntMap<Class<? extends Entityc>> ids = new ObjectIntMap<>();
    /** 下一个待扫描的 slot (避免每次从头扫) */
    private static int cursor = 0;

    /**
     * 注册一个 Entity 类, 返回它的 classId
     * 同一个类重复注册会直接返回已分配的 id
     */
    public static synchronized <T extends Entityc> int register(Class<T> type, Prov<T> prov) {
        if (ids.containsKey(type)) return ids.get(type, -1);

        // 找一个空 slot
        for (; cursor < EntityMapping.idMap.length; cursor++) {
            if (EntityMapping.idMap[cursor] == null) {
                EntityMapping.idMap[cursor] = prov;
                ids.put(type, cursor);

                // 同时注册到 nameMap (UnitType 构造时会用 EntityMapping.map(name) 查找)
                EntityMapping.nameMap.put(type.getSimpleName(), prov);
                EntityMapping.nameMap.put(Strings.camelToKebab(type.getSimpleName()), prov);

                return cursor;
            }
        }

        // 没有空 slot 了 (一般不会发生, arc 默认留很多空位)
        throw new RuntimeException("No free entity id slot for " + type.getSimpleName());
    }

    /** 获取已注册的 classId, 没注册过返回 -1 */
    public static int classId(Class<? extends Entityc> type) {
        return ids.get(type, -1);
    }
}

package zzw.util;

import mindustry.gen.Building;

/**
 * 建筑防作弊守卫 —— 兼容旧调用点 (EndGameTurret 仍引用本类)。
 *
 * <p>建筑防作弊逻辑现已<b>合并</b>进统一的 {@link AntiCheat} 管理器
 * (PU132 里 building 与 unit 本就同属一个 {@code Unity.antiCheat})。
 * 本类保留为薄封装, 把 {@link #add(Building)} / {@link #remove(Building)}
 * 直接转交给 {@link AntiCheat#addBuilding} / {@link AntiCheat#removeBuilding},
 * 调用行为与之前完全一致:</p>
 *
 * <ul>
 *   <li>登记后每 15 帧检查: 被从地图抹掉 → 写回地块; 被从实体组剔除 → 重新 add;</li>
 *   <li>同队正在拆除 (地块上是本队 ConstructBuild 脚手架) → 尊重玩家操作, 注销;</li>
 *   <li>血量归零 (真死) → 注销。</li>
 * </ul>
 *
 * @deprecated 新代码请直接使用 {@link AntiCheat}。
 */
@Deprecated
public class AntiCheatBuildings{
    /** 登记一个需要保护的建筑 (幂等)。 */
    public static void add(Building build){
        AntiCheat.setup();
        AntiCheat.addBuilding(build);
    }

    /** 注销建筑 (真正被摧毁 / 被同队拆除时调用)。 */
    public static void remove(Building build){
        AntiCheat.removeBuilding(build);
    }
}
package zzw.content.units.ai;

import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Interval;
import mindustry.core.World;
import mindustry.gen.Healthc;
import mindustry.entities.Units;
import mindustry.entities.units.UnitController;
import mindustry.gen.Building;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.world.Tile;
import zzw.content.units.entities.MonolithSoulUnit;
import zzw.content.units.soul.MonolithWorld;
import zzw.content.units.soul.MonolithWorld.Chunk;
import zzw.content.units.soul.Soul;

/**
 * 灵魂单位 AI (PU132 unity.ai.MonolithSoulAI 的纯 Java 移植)。
 *
 * <p>AI 直觉逻辑：</p>
 * <ol>
 *   <li>周期性 {@link #contemplate()}：若处于幽灵态且当前没有正在 join/成形的目标，
 *       则在"觅活范围"内寻找可加入的友方容器 (单位或建筑)，找不到就寻找最近的
 *       巨石地块分块作为成形目标；</li>
 *   <li>有 join 目标 → 飞向它并持续加入；</li>
 *   <li>有 form 目标 → 飞向它并周期性拾取巨石地块回血，血量回满即可实体化。</li>
 * </ol>
 *
 * @author GlennFolker (原作), 移植: zzw
 */
public class MonolithSoulAI implements UnitController {
    private static final Vec2 vec = new Vec2();
    protected MonolithSoulUnit unit;

    protected Teamc joinTarget;
    protected Chunk formTarget;
    protected Interval timer = new Interval(2);

    @Override
    public void unit(Unit unit) {
        this.unit = (MonolithSoulUnit) unit;
    }

    @Override
    public Unit unit() {
        return unit;
    }

    @Override
    public void updateUnit() {
        // 定期寻找"活路" (避免持续掉血而亡)
        if (timer.get(0, 12f)) contemplate();

        if (joinTarget != null) {
            // 飞向容器：停在距其 0.8×range 处
            addLength(vec
                .set(joinTarget)
                .add(Mathf.randomSeedRange(unit.id, 24f), Mathf.randomSeedRange(unit.id + 1, 24f))
                .sub(unit),
                -unit.type.range * 0.8f
            ).limit(unit.type.speed);
            unit.moveAt(vec);
            unit.lookAt(unit.prefRotation());
            unit.join(joinTarget);
        } else if (formTarget != null) {
            // 飞向成形分块并拾取地块
            addLength(vec
                .set(formTarget.centerX, formTarget.centerY)
                .add(Mathf.randomSeedRange(unit.id, 24f), Mathf.randomSeedRange(unit.id + 1, 24f))
                .sub(unit),
                -unit.type.range * 0.8f
            ).limit(unit.type.speed);
            unit.moveAt(vec);
            unit.lookAt(unit.prefRotation());

            if (timer.get(1, 5f)) {
                Chunk in = formTarget.within(unit) ? formTarget
                    : MonolithWorld.get().getChunk(World.toTile(unit.x), World.toTile(unit.y));
                if (in != null) {
                    for (int i = 0; i < 3; i++) { // 最多尝试 3 次
                        Tile tile = in.monolithTiles.random();
                        if (tile != null && !unit.forms().contains(tile) && unit.forms().size < 5) {
                            unit.form(tile);
                            break;
                        }
                    }
                }
            }
        }
    }

    /** 只有幽灵态才需要考虑"活路"。 */
    public void contemplate() {
        if (unit.corporeal()) return;

        float delta = unit.lifeDelta();
        // 已经在 join，或已通过成形获得正收益时，不重新决策
        if (!unit.joining() && !(unit.forming() && delta > 0f)) {
            // 依据剩余血量估算还能飞多远
            float range = unit.type.speed * (unit.health / -delta) / 2f;

            Unit vesselUnit = Units.closest(unit.team, unit.x, unit.y, range, this::accept);
            Building vesselBuild = Units.findAllyTile(unit.team, unit.x, unit.y, range, this::accept);
            joinTarget = (vesselUnit != null || vesselBuild != null)
                ? (vesselUnit == null ? vesselBuild : vesselBuild == null ? vesselUnit :
                    Math.max(unit.dst(vesselUnit) - vesselUnit.hitSize / 2f, 0f) <=
                    Math.max(unit.dst(vesselBuild) - vesselBuild.hitSize() / 2f, 0f)
                    ? vesselUnit : vesselBuild)
                : null;

            if (joinTarget == null) {
                // 无容器可加入 → 找最近的巨石分块 (地块越多、越近越优先)
                float r = range * range;
                formTarget = MonolithWorld.get().nearest(unit.x, unit.y, range,
                    c -> Math.min(c.monolithTiles.size, 5) * (r / unit.dst2(c.centerX, c.centerY)));
            } else {
                formTarget = null;
            }
        }
    }

    /** 判断某实体是否是可加入的容器 (PU132 MonolithSoulAI.accept)。 */
    public <T extends Teamc & mindustry.gen.Healthc> boolean accept(T other) {
        Soul soul = Soul.toSoul(other);
        return soul != null && other.isValid() && soul.acceptSoul(1) >= 1;
    }

    /** PU132 MathU.addLength: 把向量长度加上给定值 (可为负，用于停在目标前方)。 */
    private static Vec2 addLength(Vec2 v, float length) {
        return v.setLength(v.len() + length);
    }
}
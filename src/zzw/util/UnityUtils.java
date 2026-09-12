package zzw.util;

import arc.math.geom.Geometry;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.struct.IntSet;
import mindustry.Vars;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Unit;

/**
 * PU132 unity.util.Utils 移植版 (仅收录 Scar 系列所需方法)
 *
 * <p>v158.1 中原版 Mindustry 的 {@link mindustry.entities.Damage} 没有纯伤害直线判定,
 * 因此把 PU132 的 collideLineDamageOnly / getBulletDamage 一并移植到此工具类。</p>
 *
 * @author PU132 原作, 移植: zzw
 */
public class UnityUtils{
    /** 复用四元数 (PU132 Utils.q1/q2), 3D 透视圆环旋转用 —— 每帧重复利用避免分配。 */
    public static final Quat q1 = new Quat(), q2 = new Quat();
    /** 复用临时向量 (避免每帧分配) */
    private static final Vec2 tV = new Vec2();
    /** 复用临时矩形 */
    private static final Rect rect = new Rect(), hitRect = new Rect();
    /** 已判定建筑去重集合 (key = tile.pos()) */
    private static final IntSet collidedBlocks = new IntSet();

    /**
     * 计算子弹的综合伤害值 (直接伤害 + 溅射伤害 + 闪电伤害折半)。
     *
     * <p>PU132 Utils.getBulletDamage: 方向护盾用它在护盾扣血前评估一发子弹的真实威力,
     * 只统计直接伤害部分, 溅射/闪电按比例折算。</p>
     *
     * @param type 子弹类型
     * @return 综合伤害值
     */
    public static float getBulletDamage(BulletType type){
        return type.damage + type.splashDamage + (Math.max(type.lightningDamage / 2f, 0f) * type.lightning * type.lightningLength);
    }

    /**
     * Damage.collideLine 的纯伤害版本 (PU132 Utils.collideLineDamageOnly)。
     *
     * <p>与原版 collideLine 的区别: 不产生命中特效/击退/状态, 只对路径上的
     * 建筑与单位施加固定 damage —— Saber 连续激光的 swipe 伤害累积机制依赖它
     * 来把"甩刀"造成的角度变化转化为额外伤害, 而不重复触发完整碰撞流程。</p>
     *
     * <p>执行步骤:</p>
     * <ol>
     *   <li>按 angle/length 计算直线终点 tV;</li>
     *   <li>若子弹 collidesGround, 沿直线逐格 raycast, 对每个敌方建筑扣血
     *       (用 IntSet 按 tile.pos() 去重, 防止多格建筑重复受伤);</li>
     *   <li>把整条直线规范化为一个覆盖矩形 (负宽高翻正), 外扩 3 格容差;</li>
     *   <li>对矩形内敌方单位, 先用 checkTarget 过滤空/地属性, 再用
     *       raycastRect 判断单位碰撞盒与直线是否相交, 相交则扣血。</li>
     * </ol>
     *
     * @param team 伤害来源阵营 (跳过友方)
     * @param damage 施加的固定伤害值
     * @param x,y   直线起点
     * @param angle 直线角度 (度)
     * @param length 直线长度 (世界单位)
     * @param hitter 发起判定的子弹 (取其 collidesAir/collidesGround 过滤属性)
     */
    public static void collideLineDamageOnly(Team team, float damage, float x, float y, float angle, float length, Bullet hitter){
        collidedBlocks.clear();
        tV.trns(angle, length);

        // 第2步: 逐格 raycast 伤害路径上的敌方建筑
        if(hitter.type.collidesGround){
            Vars.world.raycastEachWorld(x, y, x + tV.x, y + tV.y, (cx, cy) -> {
                Building tile = Vars.world.build(cx, cy);

                if(tile != null && !collidedBlocks.contains(tile.pos()) && tile.team != team){
                    tile.damage(damage);
                    collidedBlocks.add(tile.pos());
                }

                return false;
            });
        }

        // 第3步: 构造覆盖整条直线的矩形 (从起点到终点, 负宽高翻正)
        rect.setPosition(x, y).setSize(tV.x, tV.y);
        float x2 = tV.x + x, y2 = tV.y + y;

        if(rect.width < 0){
            rect.x += rect.width;
            rect.width *= -1;
        }
        if(rect.height < 0){
            rect.y += rect.height;
            rect.height *= -1;
        }

        // 外扩 3 格, 保证贴边单位也能被扫到
        float expand = 3f;

        rect.y -= expand;
        rect.x -= expand;
        rect.width += expand * 2;
        rect.height += expand * 2;

        // 第4步: 对矩形内敌方单位做直线-矩形相交判定
        Units.nearbyEnemies(team, rect, unit -> {
            if(!unit.checkTarget(hitter.type.collidesAir, hitter.type.collidesGround)) return;
            unit.hitbox(hitRect);

            Vec2 vec = Geometry.raycastRect(x, y, x2, y2, hitRect.grow(expand * 2));

            if(vec != null) unit.damage(damage);
        });
    }
}

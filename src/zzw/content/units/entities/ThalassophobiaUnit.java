package zzw.content.units.entities;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.content.Blocks;
import mindustry.entities.EntityCollisions;
import mindustry.gen.WaterMovec;
import mindustry.graphics.Trail;
import mindustry.world.Tile;
import zzw.content.units.ZEntityRegister;

/**
 * 深海恐惧单位实体 (PU132 WaterMovec 组件移植版)
 *
 * <p>PU132 的 thalassophobia 带 WaterMovec 组件 (水中移动单位),
 * v158 原版 UnitType.init() 检测到实体实现 WaterMovec 时自动设置:</p>
 * <p>- naval = true (判定为海军单位, 不再像陆军一样在地面爬行);
 * <br>- canDrown = false / emitWalkSound = false;
* <br>- omniMovement = false (船体式移动, 只能朝面向方向前进, 转向有惯性);
 * <br>- immunities += 潮湿。</p>
 *
 * <p>本类同时移植 WaterMoveComp 的船尾水波拖尾:</p>
 * <p>- update(): 左右两侧 (waveTrailX/Y 偏移) 的拖尾按"是否在液面上"更新;
 * <br>- draw(): 在 debris 图层 (单位身体之下) 用地面颜色渐变的拖尾绘制水波;
 * <br>- solidity()/onSolid(): 碰撞改用水体判定 (waterSolid), 单位无法离开液面;
 * <br>- floorSpeedMultiplier(): 深水 1.3 倍加速 (PU132 原版数值)。</p>
 */
public class ThalassophobiaUnit extends DecorationUnitEntity implements WaterMovec{
    /** 左右两条水波拖尾 (WaterMoveComp.tleft/tright) */
    private final transient Trail tleft = new Trail(1), tright = new Trail(1);
    /** 水波颜色 (跟随脚下液体的地图色渐变) */
    private final transient Color waveTrailColor = Blocks.water.mapColor.cpy().mul(1.5f);

    // ===== End 防作弊 (PU132 EndComp): 血量双轨 + 死亡拒绝复活 =====
    /** 防作弊参数 (UnitType.antiCheatType) */
    private zzw.content.units.anticheat.EndCheatVars ac;
    /** 真实血量台账 (按防作弊上限缓慢扣减) */
    private float trueHealth;

    /** 实体工厂 (UnitType.constructor 用) */
    public static ThalassophobiaUnit create() {
        return new ThalassophobiaUnit();
    }

    /** 返回注册的 classId (v155.4+ 要求显式实体注册) */
    @Override
    public int classId() {
        return ZEntityRegister.classId(ThalassophobiaUnit.class);
    }

    @Override
    public void add() {
        if (added) return;
        super.add();
        tleft.clear();
        tright.clear();
        // 读取 End 防作弊参数 (thalassophobia: 8000/16000/h÷520/h÷120)
        if (type instanceof zzw.content.type.UnityUnitType u && u.antiCheatType != null) {
            ac = u.antiCheatType;
        }
        trueHealth = type.health;
    }

    /**
     * 血量双轨伤害 (PU132 EndComp):
     * 台账 (trueHealth) 按防作弊单次上限扣减 (慢), 显示血量由原版路径扣减 (快);
     * 显示血量先归零 → kill → 台账未耗尽 → 拒绝死亡+复活。
     */
    @Override
    public void damage(float amount) {
        if (ac != null) {
            trueHealth -= Math.min(amount, ac.maxDamageTaken);
            if (trueHealth < 0f) trueHealth = 0f;
        }
        super.damage(amount);
    }

    @Override
    public void heal(float amount) {
        super.heal(amount);
        // 治疗同步回台账
        if (ac != null) trueHealth = Math.max(trueHealth, Math.min(health, maxHealth));
    }

    /** 死亡拒绝+复活: 台账未耗尽时播放红色蓄力特效并复活 */
    private boolean denyDeath() {
        if (ac != null && trueHealth > 0f) {
            health = Math.max(health, Math.min(trueHealth, maxHealth));
            hitTime = 1f;
            zzw.content.units.effects.SpecialFx.endDeny.at(x, y, rotation, this);
            return true;
        }
        return false;
    }

    @Override
    public void destroy() {
        if (denyDeath()) return;
        super.destroy();
    }

    @Override
    public void kill() {
        if (denyDeath()) return;
        super.kill();
    }

    /**
     * 每帧更新水波拖尾 (WaterMoveComp.update 移植)。
     *
     * <p>两条拖尾分别位于机身左右两侧 (waveTrailX 左右对称, waveTrailY 前后偏移),
     * 宽度值 = 1 (在液面上) 或 0 (离水/飞行), 拖尾淡出模拟入水/出水波纹。</p>
     */
    @Override
    public void update() {
        super.update();

        boolean flying = isFlying();
        for(int i = 0; i < 2; i++){
            Trail t = i == 0 ? tleft : tright;
            t.length = type.trailLength;

            int sign = i == 0 ? -1 : 1;
            float cx = Angles.trnsx(rotation - 90, type.waveTrailX * sign, type.waveTrailY) + x,
            cy = Angles.trnsy(rotation - 90, type.waveTrailX * sign, type.waveTrailY) + y;
            t.update(cx, cy, mindustry.Vars.world.floorWorld(cx, cy).isLiquid && !flying ? 1 : 0);
        }
    }

    /**
     * 水波拖尾绘制 (WaterMoveComp.draw 移植)。
     *
     * <p>先画身体 (super.draw), 再在 debris 图层 (单位层之下) 画水波;
     * 水波颜色向脚下液体地图色缓慢渐变 (0.04/tick)。</p>
     */
    @Override
    public void draw() {
        super.draw();

        float z = Draw.z();

        Draw.z(mindustry.graphics.Layer.debris);

        Tile tile = tileOn();
        Color color = Tmp.c1.set(tile == null || tile.floor().mapColor.equals(Color.black)
            ? Blocks.water.mapColor : tile.floor().mapColor).mul(1.5f);
        waveTrailColor.lerp(color, Mathf.clamp(Time.delta * 0.04f));

        tleft.draw(waveTrailColor, type.trailScl);
        tright.draw(waveTrailColor, type.trailScl);

        Draw.z(z);
    }

    /** 碰撞判定: 飞行/穿墙时无碰撞, 否则用水体碰撞 (无法上岸) (WaterMoveComp.solidity) */
    @Override
    public EntityCollisions.SolidPred solidity() {
        return isFlying() || ignoreSolids() ? null : EntityCollisions::waterSolid;
    }

    /** 是否在实体方块上 (水体判定版) (WaterMoveComp.onSolid) */
    @Override
    public boolean onSolid() {
        return EntityCollisions.waterSolid(tileX(), tileY());
    }

    /** 地形速度倍率: 深水 1.3 倍 / 浅水 1 倍 (WaterMoveComp.floorSpeedMultiplier) */
    @Override
    public float floorSpeedMultiplier() {
        if(isFlying()) return speedMultiplier;
        return (floorOn().shallow ? 1f : 1.3f) * speedMultiplier;
    }

    /** 是否在液面上 (WaterMoveComp.onLiquid) */
    @Override
    public boolean onLiquid() {
        Tile tile = tileOn();
        return tile != null && tile.floor().isLiquid;
    }
}

package zzw.content.units.weapons;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.ui.layout.Table;
import arc.util.Tmp;
import arc.util.Time;
import arc.Core;
import mindustry.Vars;
import mindustry.audio.SoundLoop;
import mindustry.content.StatusEffects;
import mindustry.entities.Units;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.Building;
import mindustry.gen.Healthc;
import mindustry.gen.Sounds;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.type.Weapon;

/**
 * 牵引光束武器 (PU132 unity.type.weapons.TractorBeamWeapon 完整移植)
 *
 * <p>anomaly 单位使用的拖拽光束: 命中敌人后将其持续拉向自身,
 * 并根据距离缩放吸力 (scaledForce)。带发光光束渲染。</p>
 *
 * <p><b>核心算法 (逐步解释):</b>
 * <br>1. update(): 命中目标时用 impulseNet 施加朝自身方向的冲量 (int), 强度 = pullStrength
 *     + (距离越近越大的 scaledForce 附加力), 每 5 帧造成一次武器伤害并施加状态;
 * <br>2. 未命中或超范围时, 受击单位的吸力比例 scl 平滑衰减到 0;
 * <br>3. draw(): 用 Drawf.laser 在武器口和吸附目标之间画两层光束 (底光 + 高光),
 *     宽度随 scl 缩放, 渲染层级设为 flyingUnit + 1 保证盖在单位之上。</p>
 *
 * <p>★ v155 适配: PU132 用自定义 Sounds.tractorbeam, 本 mod 无该资源,
 * 改用原版 Sounds.beam 保证音效可播放。</p>
 */
public class TractorBeamWeapon extends Weapon{
    /** 基础拉力强度 */
    public float pullStrength = 10f, scaledForce = 1f;
    /** 光束总宽度 */
    public float beamWidth = 0.75f;
    /** 是否可吸附已死亡单位 */
    public boolean includeDead = false;

    public TextureRegion laser, laserEnd, laserTop, laserTopEnd;

    public Color laserColor = Pal.lancerLaser, laserTopColor = Color.white;

    /**
     * @param name 武器贴图名 (需带完全名, 含 mod 前缀)
     */
    public TractorBeamWeapon(String name){
        super(name);
        reload = 1f;
        predictTarget = false;
        autoTarget = true;
        controllable = false;
        rotate = true;
        // v155.4 Weapon 无 useAmmo 字段, 牵引光束不吃弹药
        recoil = -3f;
        // PU132 用 Sounds.tractorbeam (无资源), 改原版光束音效
        shootSound = Sounds.beamParallax;
        alternate = false;
        mountType = TractorBeamMount::new;
    }

    /**
     * 加载光束贴图 (PU132 原版)。
     */
    @Override
    public void load(){
        super.load();

        laser = Core.atlas.find("laser-white");
        laserEnd = Core.atlas.find("laser-white-end");
        laserTop = Core.atlas.find("laser-top");
        laserTopEnd = Core.atlas.find("laser-top-end");
    }

    /**
     * 显示拉力和伤害状态统计 (PU132 原版)。
     */
    @Override
    public void addStats(UnitType u, Table t){
        t.row();
        // 拉力范围: 无 scaledForce 时单值, 否则显示区间
        String n = scaledForce != 0f ? pullStrength + "-" + (pullStrength + scaledForce) : String.valueOf(pullStrength);
        t.add("[lightgray]" + Core.bundle.get("stat.unity.pullstrength") + "[white]" + n);
        if(bullet.damage > 0f){
            t.row();
            t.add(Core.bundle.format("bullet.damage", bullet.damage));
        }
        if(bullet.status != null && bullet.status != StatusEffects.none){
            t.row();
            t.add((bullet.minfo.mod == null ? bullet.status.emoji() : "") + "[stat]" + bullet.status.localizedName);
        }
    }

    /**
     * 每帧更新拖拽逻辑 (PU132 原版)。
     */
    @Override
    public void update(Unit unit, WeaponMount mount){
        super.update(unit, mount);
        TractorBeamMount tm = (TractorBeamMount)mount;

        float weaponRotation = unit.rotation - 90,
        wx = unit.x + Angles.trnsx(weaponRotation, x, y),
        wy = unit.y + Angles.trnsy(weaponRotation, x, y);
        // 指向目标且角度在范围内时开始吸附
        if(mount.target != null && Angles.within(unit.rotation + mount.rotation, mount.target.angleTo(wx, wy) + 180f, shootCone)){
            tm.targetP.set(mount.target);
            if(mount.target instanceof Unit u){
                // 吸力比例平滑升到 1
                tm.scl = Mathf.lerpDelta(tm.scl, 1f, 0.07f);
                // 基础拉力 + 距离越近越大的附加拉力 (scaledForce)
                float scl = tm.scl * (pullStrength + (Mathf.clamp(1f - (Mathf.dst(wx, wy, tm.targetP.x, tm.targetP.y) / bullet.range)) * scaledForce)),
                ang = mount.target.angleTo(wx, wy);

                // 拉被害目标朝自己, 反作用力推自己
                u.impulseNet(Tmp.v1.trns(ang, scl));
                unit.impulseNet(Tmp.v1.scl(-1f));
                // 每 5 帧造成一次武器伤害
                if((tm.timer += Time.delta) >= 5f){
                    u.damage(bullet.damage);
                    tm.timer = 0f;
                }
                u.apply(bullet.status, bullet.statusDuration);
            }
        }else{
            // 未命中则吸力衰减
            tm.scl = Mathf.lerpDelta(tm.scl, 0f, 0.07f);
        }
        // 吸力 ≥ 0.01 且非 headless 时循环播放光束音效
        if(tm.scl > 0.01f && !Vars.headless){
            if(mount.sound == null) mount.sound = new SoundLoop(shootSound, 1f);
            mount.sound.update(wx, wy, true);
        }else{
            if(mount.sound != null) mount.sound.update(wx, wy, false);
        }
        mount.reload = tm.scl * reload;
    }

    /**
     * 索敌 (PU132 原版): 找范围内最近敌人, 不含建筑。
     */
    @Override
    protected Teamc findTarget(Unit unit, float x, float y, float range, boolean air, boolean ground){
        return Units.closestTarget(unit.team, x, y, range + Math.abs(shootY), u -> u.checkTarget(air, ground), t -> false);
    }

    /**
     * 目标有效性检查 (PU132 原版)。
     */
    @Override
    protected boolean checkTarget(Unit unit, Teamc target, float x, float y, float range){
        return (super.checkTarget(unit, target, x, y, range) && (!(target instanceof Healthc h) || !target.isAdded() || (h.dead() && !includeDead))) || target instanceof Building;
    }

    /**
     * 牵引光束不开火只拖拽, 重写为空 (PU132 原版, v155 签名适配)。
     */
    @Override
    protected void shoot(Unit unit, WeaponMount mount, float shootX, float shootY, float rotation){

    }

    /**
     * 绘制牵引光束 (PU132 原版): 双层光束, 覆盖到吸附目标。
     */
    @Override
    public void draw(Unit unit, WeaponMount mount){
        super.draw(unit, mount);
        TractorBeamMount tm = (TractorBeamMount)mount;

        if(tm.scl > 0.01f){
            float z = Draw.z(),
            weaponRotation = unit.rotation - 90,
            wx = unit.x + Angles.trnsx(weaponRotation, x, y),
            wy = unit.y + Angles.trnsy(weaponRotation, x, y),
            ox = wx + Angles.trnsx(unit.rotation + mount.rotation, shootY),
            oy = wy + Angles.trnsy(unit.rotation + mount.rotation, shootY);

            // 盖在飞行单位之上的底层光束
            Draw.z(Layer.flyingUnit + 1f);
            Draw.color(laserColor);
            Drawf.laser(laser, laserEnd, ox, oy, tm.targetP.x, tm.targetP.y, tm.scl * beamWidth);
            // 更高一层的高光光束
            Draw.z(Layer.flyingUnit + 1.1f);
            Draw.color(laserTopColor);
            Drawf.laser(laserTop, laserTopEnd, ox, oy, tm.targetP.x, tm.targetP.y, tm.scl * beamWidth);
            Draw.z(z);
        }
    }

    /**
     * 牵引光束挂载状态 (PU132 原版)。
     */
    public static class TractorBeamMount extends WeaponMount{
        /** 当前吸附目标位置 */
        public Vec2 targetP = new Vec2();
        /** 吸力动画比例 (0~1, 平滑过渡) */
        public float scl, timer;

        /**
         * @param weapon 所属武器
         */
        public TractorBeamMount(Weapon weapon){
            super(weapon);
        }
    }
}
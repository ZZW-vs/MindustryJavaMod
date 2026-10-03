package zzw.content.blocks.turrets;

import arc.Core;
import arc.audio.Sound;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Nullable;
import arc.util.Time;
import mindustry.content.UnitTypes;
import mindustry.core.World;
import mindustry.entities.Units;
import mindustry.gen.BlockUnitc;
import mindustry.gen.Posc;
import mindustry.gen.Sounds;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.logic.LAccess;
import mindustry.logic.Senseable;
import mindustry.world.blocks.ControlBlock;
import mindustry.world.blocks.defense.turrets.BaseTurret;
import mindustry.world.blocks.defense.turrets.Turret;
import zzw.content.graphics.UnityPal;

import static mindustry.Vars.*;

/**
 * 通用牵引光束炮台基类 (PU132 unity/world/blocks/defense/turrets/GenericTractorBeamTurret 完整移植)
 *
 * <p>与 Mindustry 自带的 {@link mindustry.world.blocks.defense.turrets.TractorBeamTurret} 的区别:
 * <br>1. 目标类型由泛型参数 T 指定 (可以是 {@link mindustry.gen.Bullet} / {@link mindustry.gen.Unit} /
 *    {@link mindustry.gen.Building} 等任意 {@link Teamc}), 而原版只认 {@link mindustry.gen.Unit};
 * <br>2. 自带逻辑控制 (ControlBlock) 与传感器 (Senseable) 支持;
 * <br>3. 激光贴图使用全局 laser / laser-end (原版要求方块专属 @-laser)。</p>
 *
 * <p>★ v160 适配说明 (对照 PU132 v132 API):
 * <br>- 原版构造里的 consumes.powerCond(powerUse, b -&gt; b.target != null) 在 v160 不存在,
 *   改为 consumePower(powerUse) + 重写 shouldConsume() (只有存在目标时才耗电);
 * <br>- efiiciency()/edelta() 方法改为 v160 的 efficiency 字段与 delta();
 * <br>- updateClipRadius 取代原版直接改 clipSize。</p>
 *
 * @param <T> 目标类型 (Teamc 子类)
 * @author GlennFolker (PU132 原作), 移植: zzw
 */
@SuppressWarnings("unchecked")
public abstract class GenericTractorBeamTurret<T extends Teamc> extends BaseTurret{
    /** 重新选定目标的计时器编号 */
    public final int timerTarget = timers++;
    /** 重新选定目标的间隔 (tick) */
    public float retargetTime = 5f;

    /** 底盘贴图 */
    public TextureRegion baseRegion;
    /** 开火/命中判定角度 (度) */
    public float shootCone = 6f;
    /** 激光起点距方块中心的距离 (小于 0 时取 size*tilesize/2) */
    public float shootLength = -1f;

    /** 电力消耗 (有目标时) */
    public float powerUse = 1f;
    /** 可开火的效率下限 */
    public float powerUseThreshold = 0f;

    /** ★ v160 无 Sounds.tractorbeam, 沿用原版 TractorBeamTurret 的 beamParallax */
    public Sound shootSound = Sounds.beamParallax;
    public float shootSoundVolume = 0.9f;

    /** 激光颜色 (PU132 默认 monolith 蓝) */
    public Color laserColor = UnityPal.monolith;
    /** 激光粗细 */
    public float laserWidth = 0.4f;
    public TextureRegion laser;
    public TextureRegion laserEnd;

    protected Vec2 tr = new Vec2();
    protected Vec2 drawTargetPos = new Vec2();

    protected GenericTractorBeamTurret(String name){
        super(name);

        rotateSpeed = 20f;
        hasItems = hasLiquids = false;
        hasPower = true;
        // ★ v160 无 acceptCoolant 字段, 不添加 ConsumeCoolant 即等价于不接受冷却液
        // PU132: consumes.powerCond(powerUse, build -> build.target != null)
        consumePower(powerUse);
    }

    @Override
    public void init(){
        // 与原版一致: 把激光射程计入裁剪范围
        updateClipRadius(range + tilesize);

        super.init();
        if(shootLength < 0f) shootLength = size * tilesize / 2f;
    }

    @Override
    public void load(){
        super.load();
        // 原版用全局 laser / laser-end 贴图 (不是方块专属 @-laser, 否则会找不到贴图)
        TextureRegion br = Core.atlas.find(name + "-base");
        baseRegion = br.found() ? br : Core.atlas.find("block-" + size);
        laser = Core.atlas.find("laser");
        laserEnd = Core.atlas.find("laser-end");
    }

    @Override
    protected TextureRegion[] icons(){
        return new TextureRegion[]{baseRegion, region};
    }

    public abstract class GenericTractorBeamTurretBuild extends BaseTurretBuild implements ControlBlock, Senseable{
        /** 逻辑/玩家控制的代理单位 */
        public @Nullable BlockUnitc unit;

        /** 当前目标 (可为子弹/单位/建筑) */
        public T target;
        /** 供激光绘制/逻辑读取的目标位置 */
        public Vec2 targetPos = new Vec2();
        /** 激光强度 (0~1) */
        public float strength;

        public float logicControlTime = -1f;
        public boolean logicShooting = false;

        @Override
        public void created(){
            super.created();

            unit = UnitTypes.block.create(team).as();
            unit.tile(this);
        }

        @Override
        public Unit unit(){
            return unit.as();
        }

        @Override
        public void control(LAccess type, double p1, double p2, double p3, double p4){
            if(type == LAccess.shoot && !unit.isPlayer()){
                targetPos.set(World.unconv((float)p1), World.unconv((float)p2));
                logicControlTime = Turret.logicControlCooldown;
                logicShooting = !Mathf.zero(p3);
            }

            super.control(type, p1, p2, p3, p4);
        }

        @Override
        public void control(LAccess type, Object p1, double p2, double p3, double p4){
            if(type == LAccess.shootp && !unit.isPlayer()){
                logicControlTime = Turret.logicControlCooldown;
                logicShooting = !Mathf.zero(p2);

                if(p1 instanceof Posc pos){
                    targetPos.set(pos.x(), pos.y());
                }
            }

            super.control(type, p1, p2, p3, p4);
        }

        @Override
        public double sense(LAccess sensor){
            return switch(sensor){
                case rotation -> rotation;
                case shootX -> World.conv(targetPos.x);
                case shootY -> World.conv(targetPos.y);
                case shooting -> isShooting() ? 1 : 0;
                default -> super.sense(sensor);
            };
        }

        public boolean isShooting(){
            return (isControlled() ? unit.isShooting() : logicControlled() ? logicShooting : (canShoot() && target != null));
        }

        @Override
        public void updateTile(){
            if(!validateTarget()) target = null;
            if(target != null && !(target.x() == 0f && target.y() == 0f)) drawTargetPos.set(target.x(), target.y());

            unit.health(health);
            unit.rotation(rotation);
            unit.team(team);
            unit.set(x, y);

            if(logicControlTime > 0f){
                logicControlTime -= Time.delta;
            }

            boolean shot = false;

            if(canShoot()){
                if(!logicControlled() && !isControlled() && timer(timerTarget, retargetTime)){
                    findTarget();
                }

                if(validateTarget()){
                    float targetRot = angleTo(targetPos.x, targetPos.y);
                    turnToTarget(targetRot);

                    boolean shoot = true;
                    if(isControlled()){
                        targetPos.set(unit.aimX(), unit.aimY());
                        shoot = unit.isShooting();
                    }else if(logicControlled()){
                        shoot = logicShooting;
                    }else{
                        targetPos.set(target.x(), target.y());

                        if(Float.isNaN(rotation)) rotation = 0f;
                    }

                    targetPos.sub(x, y).limit(range).add(x, y);

                    if(shoot && Angles.angleDist(rotation, targetRot) < shootCone){
                        shot = true;
                        updateShooting();
                    }
                }
            }

            if(shot){
                strength = Mathf.lerpDelta(strength, Mathf.clamp(efficiency), 0.1f);
                if(strength > 0.1f && !headless){
                    control.sound.loop(shootSound, this, shootSoundVolume);
                }
            }else{
                strength = Mathf.lerpDelta(strength, 0f, 0.1f);
            }
        }

        protected void updateShooting(){
            if(logicControlled() || isControlled()){
                findTarget(targetPos);
            }

            if(target != null && strength > 0.1f){
                apply();
            }
        }

        protected void turnToTarget(float targetRot){
            rotation = Angles.moveToward(rotation, targetRot, rotateSpeed * efficiency * delta());
        }

        protected boolean validateTarget(){
            return !Units.invalidateTarget(target, team, x, y) || isControlled() || logicControlled();
        }

        public boolean logicControlled(){
            return logicControlTime > 0f;
        }

        public boolean canShoot(){
            return efficiency > powerUseThreshold;
        }

        /** 只有存在目标时才消耗电力 (对应 PU132 consumes.powerCond(powerUse, 有目标)) */
        @Override
        public boolean shouldConsume(){
            return super.shouldConsume() && target != null;
        }

        protected abstract void findTarget();

        protected abstract void findTarget(Vec2 pos);

        protected abstract void apply();

        @Override
        public void draw(){
            Draw.rect(baseRegion, x, y);
            Drawf.shadow(region, x - (size / 2f), y - (size / 2f), rotation - 90);
            Draw.rect(region, x, y, rotation - 90);

            if(strength > 0.1f){
                tr.trns(rotation, shootLength);

                Draw.z(Layer.bullet);

                Draw.mixcol(laserColor, Mathf.absin(4f, 0.6f));
                Draw.alpha(laserAlpha());

                Drawf.laser(laser, laserEnd,
                x + tr.x, y + tr.y,
                drawTargetPos.x, drawTargetPos.y,
                strength * laserWidth);

                Draw.mixcol();
            }
        }

        /** 激光透明度 (原版默认 = efficiency, 子类可覆写) */
        public float laserAlpha(){
            return efficiency;
        }
    }
}

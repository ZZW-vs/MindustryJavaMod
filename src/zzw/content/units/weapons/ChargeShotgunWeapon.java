package zzw.content.units.weapons;

import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.audio.SoundLoop;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.type.Weapon;
import zzw.content.units.util.UnityUtils;

import static mindustry.Vars.headless;
import static mindustry.Vars.state;

/**
 * 蓄力霰弹武器 (PU132 unity.type.weapons.monolith.ChargeShotgunWeapon 移植)
 *
 * <p>核心机制: 武器随时间 "装填" 若干发子弹 (每发间隔 {@link #addSequenceTime}),
 * 装填的子弹以编织 (weave) 轨迹绕炮口盘旋; 开火信号一到,
 * 所有已装填子弹依次从各自盘旋位置射出。
 * pedestal 的 "环绕装填弹幕" 效果即由本类实现。</p>
 *
 * <p>★ v155.4 适配 (相对 PU132):</p>
 * <ul>
 *   <li>shots/shotDelay/firstShotDelay 从武器字段移入 shoot ShootPattern;</li>
 *   <li>PU 的 Trns (跟随武器挂点的特效父对象) 未移植 — 特效父对象直接用 unit
 *       (特效跟随单位整体而非挂点, 视觉差异微小);</li>
 *   <li>PU bullet(unit, x, y, angle, velocityScl) → BulletType.create(...) 直接
 *       在任意位置生成子弹; scaleVelocity 字段 v155.4 不存在, 速度系数恒为 1
 *       (TODO: scaleVelocity — pedestal 子弹未用该字段, 无实际影响);</li>
 *   <li>update() 为 v155.4 Weapon.update 全量覆写 + 装填状态机 +
 *       额外开火条件 (loaded() > 0 且非 releasing);</li>
 *   <li>mount.recoil 语义变化: PU 直接存像素值, v155.4 存 0~1 归一值。</li>
 * </ul>
 *
 * @author GlennFolker (原作), 移植: zzw
 */
public class ChargeShotgunWeapon extends Weapon{
    private static final Vec2 tmp = new Vec2();

    /** 每发子弹的装填间隔 (tick)。 */
    public float addSequenceTime = 38f;
    /** 编织轨迹的正弦周期缩放。 */
    public float weaveScale = 24f;
    /** 编织轨迹的摆动幅度 (度)。 */
    public float weaveAmount = 30f;
    /** 出射角向炮口方向收拢的比例分母。 */
    public float angleStrideScale = 10f;

    /** 每装填一发子弹时的特效。 */
    public Effect addEffect = Fx.lancerLaserCharge;
    /** 一发子弹装填完成时的特效。 */
    public Effect addedEffect = Fx.lightningShoot;
    /** 开火 (开始释放全部子弹) 时的特效。 */
    public Effect releaseEffect = Fx.none;

    public ChargeShotgunWeapon(String name){
        super(name);
        mountType = ChargeShotgunMount::new;
    }

    public ChargeShotgunWeapon(){
        this("");
    }

    /**
     * 计算某发已装填子弹当前的世界坐标。
     *
     * @param local 子弹的局部坐标 (相对武器挂点, 随编织动画变化)
     */
    protected Vec2 chargePos(Vec2 local, Unit unit, ChargeShotgunMount mount){
        float
            weaponRotation = unit.rotation - 90f + (rotate ? mount.rotation : baseRotation),
            mountX = unit.x + Angles.trnsx(unit.rotation - 90f, x, y),
            mountY = unit.y + Angles.trnsy(unit.rotation - 90f, x, y);

        return tmp.trns(weaponRotation, local.x, local.y).add(mountX, mountY);
    }

    /**
     * 绘制: 原版武器贴图 + 已装填子弹的蓄力绘制钩子。
     */
    @Override
    public void draw(Unit unit, WeaponMount mount){
        super.draw(unit, mount);

        ChargeShotgunMount m = (ChargeShotgunMount)mount;
        float
            weaponRotation = unit.rotation - 90f + (rotate ? m.rotation : baseRotation),
            mountX = unit.x + Angles.trnsx(unit.rotation - 90f, x, y),
            mountY = unit.y + Angles.trnsy(unit.rotation - 90f, x, y),
            bulletX = mountX + Angles.trnsx(weaponRotation, shootX, shootY),
            bulletY = mountY + Angles.trnsy(weaponRotation, shootX, shootY),
            shootAngle = rotate ? weaponRotation + 90f : Angles.angle(bulletX, bulletY, m.aimX, m.aimY) + (unit.rotation - unit.angleTo(m.aimX, m.aimY));

        for(int i = 0; i < m.added.size - 1; i += 2){
            Vec2 current = m.added.get(i);
            if(!Float.isNaN(current.x) && !Float.isNaN(current.y)){
                Vec2 pos = chargePos(current, unit, m);
                // 出射角向炮口方向收拢: 与炮口连线的偏差除以 angleStrideScale
                float rot = shootAngle + UnityUtils.angleDistSigned(shootAngle, Angles.angle(mountX, mountY, pos.x, pos.y)) / angleStrideScale;

                drawCharge(pos.x, pos.y, weaponRotation, rot, unit, m);
            }
        }
    }

    /**
     * 蓄力子弹绘制钩子 (子类覆写以绘制贴图)。
     */
    public void drawCharge(float x, float y, float rotation, float shootAngle, Unit unit, ChargeShotgunMount mount){}

    /**
     * 单发子弹的释放 (PU132 内联逻辑提取为方法)。
     *
     * <p>步骤: 生成子弹 (从盘旋位置射出, 角度带随机散布) →
     * 播放射击音效 → 施加后坐力 → 抖屏 → 射击/烟雾特效。</p>
     */
    protected void releaseShot(Unit unit, float x, float y, float rot){
        BulletType ammo = bullet;
        boolean parentize = ammo.keepVelocity || parentizeEffects;

        // TODO: v155.4 无 scaleVelocity 字段 (PU 按目标距离缩放初速), 此处恒为 1
        ammo.create(unit, unit.team, x, y, rot + Mathf.range(inaccuracy), 1f, 1f, 1f, null, null);

        shootSound.at(x, y, Mathf.random(soundPitchMin, soundPitchMax), shootSoundVolume);

        unit.vel.add(Tmp.v1.trns(rot + 180f, ammo.recoil));
        Effect.shake(shake, shake, x, y);
        ammo.shootEffect.at(x, y, rot, parentize ? unit : null);
        ammo.smokeEffect.at(x, y, rot, parentize ? unit : null);
    }

    /**
     * 开火: 依次释放所有已装填子弹。
     *
     * <p>有 firstShotDelay/shotDelay 时: 进入 releasing 状态逐发延迟释放,
     * 期间每发子弹从当前盘旋位置射出后置 NaN 不再绘制;
     * 全部释放完毕后清空装填列表。无延迟时: 一次性全部射出。</p>
     */
    @Override
    protected void shoot(Unit unit, WeaponMount mount, float shootX, float shootY, float rotation){
        ChargeShotgunMount m = (ChargeShotgunMount)mount;

        float
            mountX = unit.x + Angles.trnsx(unit.rotation - 90f, x, y),
            mountY = unit.y + Angles.trnsy(unit.rotation - 90f, x, y);

        boolean delay = shoot.firstShotDelay + shoot.shotDelay > 0f;

        BulletType ammo = bullet;
        boolean parentize = ammo.keepVelocity || parentizeEffects;

        if(delay){
            m.releasing = true;
            if(m.adding){
                // 尚未装填完成的那一发直接丢弃
                m.added.removeRange(m.added.size - 2, m.added.size - 1);
                m.adding = false;
            }

            for(int i = 0; i < m.added.size - 1; i += 2){
                Vec2 current = m.added.get(i);
                Time.run(i / 2f * shoot.shotDelay + shoot.firstShotDelay, () -> {
                    if(!unit.isAdded()) return;

                    Vec2 pos = chargePos(current, unit, m);
                    float rot = rotation + UnityUtils.angleDistSigned(rotation, Angles.angle(mountX, mountY, pos.x, pos.y)) / angleStrideScale;

                    releaseShot(unit, pos.x, pos.y, rot);

                    mount.recoil = 1f;
                    mount.heat = 1f;

                    current.x = current.y = Float.NaN;
                });

                Vec2 pos = chargePos(current, unit, m);
                float rot = rotation + UnityUtils.angleDistSigned(rotation, Angles.angle(mountX, mountY, pos.x, pos.y)) / angleStrideScale;

                releaseEffect.at(pos.x, pos.y, rot, parentize ? unit : null);
            }

            Time.run((m.loaded() - 1) * shoot.shotDelay + shoot.firstShotDelay, () -> {
                m.releasing = false;
                m.added.clear();
            });

            Time.run(shoot.firstShotDelay, () -> {
                if(!unit.isAdded()) return;
                ammo.chargeEffect.at(shootX, shootY, rotation, parentize ? unit : null);
            });
        }else{
            for(int i = 0; i < m.added.size - 1; i += 2){
                Vec2 current = m.added.get(i);
                if(!Float.isNaN(current.x) && !Float.isNaN(current.y)){
                    Vec2 pos = chargePos(current, unit, m);
                    float rot = rotation + UnityUtils.angleDistSigned(rotation, Angles.angle(mountX, mountY, pos.x, pos.y)) / angleStrideScale;

                    releaseShot(unit, pos.x, pos.y, rot);
                }
            }

            unit.vel.add(Tmp.v1.trns(rotation + 180f, ammo.recoil));
            mount.recoil = 1f;
            mount.heat = 1f;

            m.added.clear();
        }

        ejectEffect.at(mountX, mountY, rotation * Mathf.sign(this.x));
        unit.apply(shootStatus, shootStatusDuration);
    }

    /**
     * 更新: v155.4 原版流程 + PU132 装填状态机。
     *
     * <p>装填状态机 (PU132 原样移植):</p>
     * <ol>
     *   <li>非释放中且未在装填: add 累计到 (reload - addSequenceTime) 时
     *       开始装填一发 (added 加入 NaN 占位 + 目标点), 触发 addEffect;</li>
     *   <li>装填中: addSequence 累计到 addSequenceTime 时完成该发
     *       (占位替换为实际位置), 触发 addedEffect;</li>
     *   <li>释放中: 重置状态;</li>
     *   <li>目标点按 sin 编织轨迹摆动, 占位点 slerp 平滑追踪目标点。</li>
     * </ol>
     */
    @Override
    public void update(Unit unit, WeaponMount mount){
        ChargeShotgunMount m = (ChargeShotgunMount)mount;

        // ===== 装填状态机 (PU132) =====
        float
            chargeWeaponRotation = unit.rotation - 90f + (rotate ? m.rotation : baseRotation),
            chargeMountX = unit.x + Angles.trnsx(unit.rotation - 90f, x, y),
            chargeMountY = unit.y + Angles.trnsy(unit.rotation - 90f, x, y),
            chargeBulletX = chargeMountX + Angles.trnsx(chargeWeaponRotation, shootX, shootY),
            chargeBulletY = chargeMountY + Angles.trnsy(chargeWeaponRotation, shootX, shootY),

            addTime = Math.max(reload - addSequenceTime, 0f);

        if(!m.releasing){
            if(!m.adding){
                if(m.loaded() < shoot.shots && (m.add += Time.delta * unit.reloadMultiplier) >= addTime){
                    m.adding = true;

                    m.added.add(new Vec2(Float.NaN, Float.NaN), new Vec2(shootX, shootY));
                    m.addSequence = m.add % addTime;
                    m.add = 0f;

                    addEffect.at(chargeBulletX, chargeBulletY, chargeWeaponRotation, parentizeEffects ? unit : null);
                }
            }else if((m.addSequence += Time.delta) >= addSequenceTime && m.added.any()){
                m.adding = false;

                m.added.get(m.added.size - 2).set(shootX, shootY);
                m.add = m.addSequence % addSequenceTime;
                m.addSequence = 0f;

                addedEffect.at(chargeBulletX, chargeBulletY, chargeWeaponRotation, parentizeEffects ? unit : null);
            }
        }else{
            m.adding = false;
            m.add = m.addSequence = 0f;
        }

        // ===== 编织动画: 目标点摆动, 占位点平滑追踪 =====
        for(int i = 0; i < m.added.size - 1; i += 2){
            Vec2 current = m.added.get(i), target = m.added.get(i + 1);
            if(!m.releasing){
                target.setAngle(Mathf.sin(Time.time + Mathf.randomSeed(unit.id, Mathf.pi * 2f * weaveScale) + (Mathf.pi * 2f * weaveScale) * ((float)i / m.added.size), weaveScale, weaveAmount / 2f * m.loaded()) + 90f);
            }

            if(!Float.isNaN(current.x) && !Float.isNaN(current.y)){
                current.setAngle(Mathf.slerpDelta(current.angle(), target.angle(), 0.08f));
            }
        }

        // ===== 以下为 v155.4 原版 Weapon.update 流程 =====
        boolean can = unit.canShoot();
        float lastReload = mount.reload;
        mount.reload = Math.max(mount.reload - Time.delta * unit.reloadMultiplier, 0);
        mount.recoil = Mathf.approachDelta(mount.recoil, 0, unit.reloadMultiplier / recoilTime);
        if(recoils > 0){
            if(mount.recoils == null) mount.recoils = new float[recoils];
            for(int i = 0; i < recoils; i++){
                mount.recoils[i] = Mathf.approachDelta(mount.recoils[i], 0, unit.reloadMultiplier / recoilTime);
            }
        }
        mount.smoothReload = Mathf.lerpDelta(mount.smoothReload, mount.reload / reload, smoothReloadSpeed);
        mount.charge = mount.charging && shoot.firstShotDelay > 0 ? Mathf.approachDelta(mount.charge, 1, 1 / shoot.firstShotDelay) : 0;

        float warmupTarget = (can && mount.shoot) || (continuous && mount.bullet != null) || mount.charging ? 1f : 0f;
        if(linearWarmup){
            mount.warmup = Mathf.approachDelta(mount.warmup, warmupTarget, shootWarmupSpeed);
        }else{
            mount.warmup = Mathf.lerpDelta(mount.warmup, warmupTarget, shootWarmupSpeed);
        }

        float
        mountX = unit.x + Angles.trnsx(unit.rotation - 90, x, y),
        mountY = unit.y + Angles.trnsy(unit.rotation - 90, x, y);

        //find a new target
        if(!controllable && autoTarget){
            if((mount.retarget -= Time.delta) <= 0f){
                mount.target = findTarget(unit, mountX, mountY, bullet.range, bullet.collidesAir, bullet.collidesGround);
                mount.retarget = mount.target == null ? targetInterval : targetSwitchInterval;
            }

            if(mount.target != null && checkTarget(unit, mount.target, mountX, mountY, bullet.range)){
                mount.target = null;
            }

            boolean shoot = false;

            if(mount.target != null){
                shoot = mount.target.within(mountX, mountY, bullet.range + Math.abs(shootY) + (mount.target instanceof mindustry.entities.Sized s ? s.hitSize() / 2f : 0f)) && can;

                if(predictTarget){
                    Vec2 to = mindustry.entities.Predict.intercept(unit, mount.target, bullet);
                    mount.aimX = to.x;
                    mount.aimY = to.y;
                }else{
                    mount.aimX = mount.target.x();
                    mount.aimY = mount.target.y();
                }
            }

            mount.shoot = mount.rotate = shoot;
        }

        //rotate if applicable
        if(rotate && (mount.rotate || mount.shoot) && can){
            float axisX = unit.x + Angles.trnsx(unit.rotation - 90, x, y),
            axisY = unit.y + Angles.trnsy(unit.rotation - 90, x, y);

            mount.targetRotation = Angles.angle(axisX, axisY, mount.aimX, mount.aimY) - unit.rotation;
            mount.rotation = Angles.moveToward(mount.rotation, mount.targetRotation, rotateSpeed * Time.delta);
            if(rotationLimit < 360){
                float dst = Angles.angleDist(mount.rotation, baseRotation);
                if(dst > rotationLimit / 2f){
                    mount.rotation = Angles.moveToward(mount.rotation, baseRotation, dst - rotationLimit / 2f);
                }
            }
        }else if(!rotate){
            mount.rotation = baseRotation;
            mount.targetRotation = unit.angleTo(mount.aimX, mount.aimY);
        }

        float
        weaponRotation = unit.rotation - 90 + (rotate ? mount.rotation : baseRotation),
        bulletX = mountX + Angles.trnsx(weaponRotation, this.shootX, this.shootY),
        bulletY = mountY + Angles.trnsy(weaponRotation, this.shootX, this.shootY),
        shootAngle = bulletRotation(unit, mount, bulletX, bulletY);

        if(alwaysShooting) mount.shoot = true;

        //update continuous state
        if(continuous && mount.bullet != null){
            if(!mount.bullet.isAdded() || mount.bullet.time >= mount.bullet.lifetime || mount.bullet.type != bullet){
                mount.bullet = null;
            }else{
                mount.bullet.rotation(weaponRotation + 90);
                mount.bullet.set(bulletX, bulletY);
                mount.reload = reload;
                mount.recoil = 1f;
                unit.vel.add(Tmp.v1.trns(mount.bullet.rotation() + 180f, mount.bullet.type.recoil * Time.delta));
                if(shootSound != Sounds.none && !headless){
                    if(mount.sound == null) mount.sound = new SoundLoop(shootSound, 1f);
                    mount.sound.update(bulletX, bulletY, true);
                }

                //target length of laser
                float shootLength = Math.min(Mathf.dst(bulletX, bulletY, mount.aimX, mount.aimY), range());
                //current length of laser
                float curLength = Mathf.dst(bulletX, bulletY, mount.bullet.aimX, mount.bullet.aimY);
                //resulting length of the bullet (smoothed)
                float resultLength = Mathf.approachDelta(curLength, shootLength, aimChangeSpeed);
                //actual aim end point based on length
                Tmp.v1.trns(shootAngle, mount.lastLength = resultLength).add(bulletX, bulletY);

                mount.bullet.aimX = Tmp.v1.x;
                mount.bullet.aimY = Tmp.v1.y;

                if(alwaysContinuous && mount.shoot){
                    mount.bullet.time = mount.bullet.lifetime * mount.bullet.type.optimalLifeFract * mount.warmup;
                    mount.bullet.keepAlive = true;

                    unit.apply(shootStatus, shootStatusDuration);
                }
            }
        }else{
            //heat decreases when not firing
            mount.heat = Math.max(mount.heat - Time.delta * unit.reloadMultiplier / cooldownTime, 0);

            if(mount.sound != null){
                mount.sound.update(bulletX, bulletY, false);
            }
        }

        //flip weapon shoot side for alternating weapons
        boolean wasFlipped = mount.side;
        if(otherSide >= 0 && alternate && mount.side == flipSprite && otherSide < unit.mounts.length && mount.reload <= reload / 2f && lastReload > reload / 2f){
            unit.mounts[otherSide].side = !unit.mounts[otherSide].side;
            mount.side = !mount.side;
        }

        // ===== 开火判定 (v155.4 原版条件 + PU132 的 loaded/releasing 条件) =====
        if(mount.shoot && //must be shooting
        can && //must be able to shoot
        !(bullet.killShooter && mount.totalShots > 0) &&
        // ★ v158 已移除单位弹药系统: 删除 (!useAmmo || unit.ammo > 0 || !state.rules.unitAmmo || ...) 弹药检查
        (!alternate || wasFlipped == flipSprite) &&
        mount.warmup >= minWarmup && //must be warmed up
        unit.deltaLen() / Time.delta >= minShootVelocity && //check velocity requirements
        m.loaded() > 0 && //must have loaded bullets (PU132)
        !m.releasing && //must not be releasing (PU132)
        (mount.reload <= 0.0001f || (alwaysContinuous && mount.bullet == null)) && //reload has to be 0
        (alwaysShooting || Angles.within(rotate ? mount.rotation : unit.rotation + baseRotation, mount.targetRotation, shootCone)) //has to be within the cone
        ){
            shoot(unit, mount, bulletX, bulletY, shootAngle);

            mount.reload = reload;
        }
    }

    /**
     * 蓄力霰弹挂载点: 记录已装填子弹 (成对存 [占位NaN, 目标点]) 与状态。
     */
    public static class ChargeShotgunMount extends WeaponMount{
        /** 是否正在装填一发子弹 (占位已加入但未完成)。 */
        public boolean adding;

        /** 已装填子弹列表, 每发两个 Vec2: [当前显示位置(可为NaN), 编织目标点]。 */
        public Seq<Vec2> added = new Seq<>();
        /** 距离开始装填下一发的累计时间。 */
        public float add;
        /** 当前这发的装填进度。 */
        public float addSequence;

        /** 是否正在逐发释放子弹 (开火后的延迟序列期间)。 */
        public boolean releasing;

        public ChargeShotgunMount(Weapon weapon){
            super(weapon);
        }

        /** 已完成装填、可立即发射的子弹数。 */
        public int loaded(){
            return added.size / 2 - (adding ? 1 : 0);
        }
    }
}

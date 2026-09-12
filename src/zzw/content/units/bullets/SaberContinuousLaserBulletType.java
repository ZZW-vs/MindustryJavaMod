package zzw.content.units.bullets;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Log;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.content.Fx;
import mindustry.entities.Damage;
import mindustry.entities.bullet.ContinuousLaserBulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Velc;
import mindustry.graphics.Drawf;
import zzw.content.units.SaberData;
import zzw.content.units.effects.ScarFx;
import zzw.content.graphics.UnityPal;
import zzw.util.UnityUtils;

/**
 * Saber 连续激光子弹类型
 * PU132 unity.entities.bullet.laser.SaberContinuousLaserBulletType 移植版
 * 
 * <p>具有以下特性：</p>
 * <ul>
 *   <li>支持 swipe 模式（根据速度和角度变化调整长度）</li>
 *   <li>collideLineDamageOnly 伤害累积机制</li>
 *   <li>闪电效果和假闪电特效</li>
 *   <li>动态长度调整</li>
 * </ul>
 */
public class SaberContinuousLaserBulletType extends ContinuousLaserBulletType {
    /** 是否启用 swipe 模式 */
    protected boolean swipe;
    /** swipe 模式持续时间 */
    protected float swipeTime = 40f;
    /** swipe 模式伤害倍数 */
    protected float swipeDamageMultiplier = 1f;
    
    /** 激光长度缩放数组 */
    protected float[] lenscales = {1f, 1.1f, 1.2f, 1.3f, 1.4f, 1.5f};
    /** 拖尾缩放数组 */
    protected float[] tscales = {1f, 1.05f, 1.1f, 1.15f, 1.2f};
    /** 描边宽度数组 */
    protected float[] strokes = {0.5f, 0.7f, 0.9f, 1.1f, 1.3f};
    /** 振荡参数 */
    protected float oscScl = 2f;
    protected float oscMag = 1f;
    protected float spaceMag = 4f;

    /** 数组长度不一致警告只输出一次 (避免刷屏), 记录已警告的实例 */
    private static final java.util.Set<SaberContinuousLaserBulletType> warnedMismatch =
        java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    /** 缺少 SaberData 警告只输出一次 */
    private static final java.util.Set<SaberContinuousLaserBulletType> warnedData =
        java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    public SaberContinuousLaserBulletType(float damage) {
        super(damage);
    }

    public SaberContinuousLaserBulletType() {
        this(0f);
    }

    /**
     * 计算 swipe 模式下的充能伤害
     * @param b 子弹实例
     * @param val 基础伤害值
     * @return 充能伤害值
     */
    protected float chargedDamage(Bullet b, float val) {
        return b.time < swipeTime ? swipeDamageMultiplier * val * (swipeTime - b.time) : 0f;
    }

    @Override
    public void update(Bullet b) {
        // 初始化 SaberData
        if (!(b.data instanceof SaberData)) {
            b.data = new SaberData(0f, 3, b.rotation(), 10);
        }
        SaberData temp = (SaberData) b.data;

        if (swipe) {
            // swipe 模式逻辑
            float angDst = Angles.angleDist(b.rotation(), temp.rot) / Time.delta;
            temp.mean.add(angDst);
            angDst = temp.mean.rawMean();
            
            // 根据速度和角度变化调整长度
            if (b.owner instanceof Velc v) {
                temp.f = Mathf.clamp(temp.f + v.vel().len() / 2f + angDst, 0f, length + angDst * 7f);
            }

            // 计算充能伤害
            float damageC = chargedDamage(b, angDst);
            float realLength = Damage.findLaserLength(b, temp.f);
            float fout = Mathf.clamp(b.time > b.lifetime - fadeTime ? 
                1f - (b.time - (lifetime - fadeTime)) / fadeTime : 1f);
            float baseLen = realLength * fout;

            // 每 5tick 执行一次碰撞检测 (v158.1 的 collideLine 无 Effect 参数)
            if (b.timer(1, 5f)) {
                Damage.collideLine(b, b.team, b.x, b.y, b.rotation(), temp.f, largeHit);
                // collideLineDamageOnly 伤害累积机制 (PU132 Utils, 移植至 UnityUtils)
                if (angDst > 0.0001f) {
                    UnityUtils.collideLineDamageOnly(b.team, (angDst + damageC) * 2f, b.x, b.y, b.rotation(), temp.f, b);
                }
            }

            // 前 25tick 生成闪电
            if (b.time < 25f) {
                float c = (25f - b.time) * (angDst / 25f) / 25f;
                for (int i = 0; i < 3; i++) {
                    float lenRangedB = baseLen + Mathf.range(16f);
                    if (Mathf.chanceDelta(c) && lenRangedB >= 8f) {
                        mindustry.entities.Lightning.create(b, UnityPal.scarColor, 3 + damageC / 2f, 
                            b.x, b.y, b.rotation(), Mathf.round(lenRangedB / 8f));
                    }
                }
            }

            // 随机生成闪电 (b.fout() 在 v158.1 中移除, 等价于 1 - fin)
            float lenRanged = baseLen + Mathf.range(16f);
            if (Mathf.chanceDelta((0.1f + Mathf.clamp(angDst / 25f)) * (1f - b.fin())) && 
                Mathf.round(lenRanged / 8f) >= 1) {
                mindustry.entities.Lightning.create(b, UnityPal.scarColor, 
                    6f + angDst * 1.7f + damageC * 2f, b.x, b.y, b.rotation(), Mathf.round(lenRanged / 8f));
            }

            // 生成假闪电特效
            if (Mathf.chanceDelta(0.12f * (1f - b.fin()))) {
                ScarFx.falseLightning.at(b.x, b.y, b.rotation(), UnityPal.scarColor, baseLen);
            }

            // 更新数据
            temp.rot = b.rotation();
            Tmp.v1.trns(b.rotation(), (baseLen * lenscales[lenscales.length - 1]) / 2f);
            temp.fT.update(b.x + Tmp.v1.x, b.y + Tmp.v1.y, b.rotation() + 90f);
        } else {
            // 普通模式逻辑
            temp.f = length;
            if (b.owner instanceof Velc v) {
                temp.f = Mathf.clamp(v.vel().len() * 19f, 0f, length);
            }
            if (b.timer(1, 5f)) {
                Damage.collideLine(b, b.team, b.x, b.y, b.rotation(), temp.f, largeHit);
            }
        }
    }

    @Override
    public void draw(Bullet b) {
        if (!(b.data instanceof SaberData temp)) {
            // ★ 诊断日志: data 不是 SaberData 说明 update 未先初始化 (激光将不可见), 便于用户汇报
            if (warnedData.add(this)) {
                Log.warn("SaberContinuousLaserBulletType.draw: b.data=@ (期望 SaberData), 子弹类型=@",
                    b.data, b.type);
            }
            return;
        }
        
        float realLength = Damage.findLaserLength(b, temp.f);
        float fout = Mathf.clamp(b.time > b.lifetime - fadeTime ? 
            1f - (b.time - (lifetime - fadeTime)) / fadeTime : 1f);
        float baseLen = realLength * fout;

        // 绘制拖尾效果
        temp.fT.draw(UnityPal.scarColor, baseLen * lenscales[lenscales.length - 1] * 0.5f);
        
        // 绘制激光主体
        Lines.lineAngle(b.x, b.y, b.rotation(), baseLen);
        
        // ★ 防御性检查: colors/strokes 与 tscales/lenscales 由外部配置, 长度可能不一致
        //   (jetstream 配置 lenscales=4 而 tscales=5 曾导致 ArrayIndexOutOfBoundsException)
        //   遍历上限取各数组最小长度, 并输出警告日志方便定位配置错误
        if (strokes.length < colors.length || lenscales.length < tscales.length) {
            if (warnedMismatch.add(this)) {
                Log.warn("SaberContinuousLaserBulletType 数组长度不一致: colors=@ strokes=@ tscales=@ lenscales=@ (单位: @)",
                    colors.length, strokes.length, tscales.length, lenscales.length,
                    b.owner instanceof mindustry.gen.Unit u ? u.type.name : b.owner);
            }
        }

        // 绘制激光效果层
        int layers = Math.min(colors.length, strokes.length);
        int inner = Math.min(tscales.length, lenscales.length);
        for (int s = 0; s < layers; s++) {
            Draw.color(Tmp.c1.set(colors[s]).mul(1f + Mathf.absin(1f, 0.1f)));
            for (int i = 0; i < inner; i++) {
                Tmp.v1.trns(b.rotation() + 180f, (lenscales[i] - 1f) * spaceMag);
                Lines.stroke((width + Mathf.absin(oscScl, oscMag)) * fout * strokes[s] * tscales[i]);
                Lines.lineAngle(b.x + Tmp.v1.x, b.y + Tmp.v1.y, b.rotation(), baseLen * lenscales[i], false);
            }
        }

        // 绘制光照效果 (v158.1 的 Drawf.light 无 team 重载)
        Tmp.v1.trns(b.rotation(), baseLen * 1.1f);
        Drawf.light(b.x, b.y, b.x + Tmp.v1.x, b.y + Tmp.v1.y, lightStroke, lightColor, 0.7f);
        
        Draw.reset();
    }
}
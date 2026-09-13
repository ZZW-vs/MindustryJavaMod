package zzw.content.units.types;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.entities.units.WeaponMount;
import zzw.content.units.entities.EndInvisibleUnit;
import zzw.content.type.UnityUnitType;

/**
 * 隐形单位类型基类 (移植自 PU132 type/InvisibleUnitType 完整版)
 *
 * <p>隐身渲染原理 (对照 PU132 InvisibleUnitType):</p>
 * <p>1. 实体侧 (ApocalypseUnit) 维护 alphaLerp: 血量高于 50% 且未开火/未被发现时渐隐到 1 (隐身),
 *     受击/靠近敌人/开火时渐显到 0;
 * <br>2. 类型侧 (本类) 在所有绘制入口用 fade(unit) = 1 - alphaLerp 计算可见度,
 *     把身体轮廓/武器/阴影/引擎全部乘以该系数;
 * <br>3. 淡出过程中轮廓向 tint (红色) 过渡, 模拟"红雾消散"效果。</p>
 *
 * <p>子类可覆写 drawEngines 追加专属引擎 (如 apocalypse 的双小喷口)。</p>
 */
public class InvisibleUnitType extends UnityUnitType{
    /** 隐身时轮廓的过渡色 (PU132 InvisibleUnitType.tint, 默认红色) */
    public Color tint = Color.red;
    /** 视觉悬浮高度 (PU132 UnitType.visualElevation, v155.4 已移除该字段; 影子绘制用) */
    public float visualElevation = 0f;
    public InvisibleUnitType(String name){
        super(name);
    }

    /**
     * 可见度系数 (PU132 InvisibleUnitType.fade L28-31)。
     *
     * <p>PU132 原版己方最低可见 0.1; 按用户要求改为 0.25 (半透明可辨认),
     * 敌方保持 PU132 原版 0.01 (几乎全隐)。</p>
     */
    protected float fade(EndInvisibleUnit unit){
        float minimum = Vars.player.team() == unit.team() ? 0.25f : 0.01f;
        return Mathf.clamp(1f - unit.getAlphaLerp(), minimum, 1f);
    }

    @Override
    public void drawOutline(Unit unit){
        if(!(unit instanceof EndInvisibleUnit e)){
            super.drawOutline(unit);
            return;
        }

        // 轮廓颜色: 白色随隐身程度向 tint (红) 过渡, 透明度随之降低
        Tmp.c1.set(Color.white).lerp(tint, Mathf.lerp(0f, 0.5f, e.getAlphaLerp()));
        Draw.color(Tmp.c1);
        Draw.alpha(1f - e.getAlphaLerp());

        if(Core.atlas.isFound(outlineRegion)){
            Draw.rect(outlineRegion, unit.x, unit.y, unit.rotation - 90);
        }
    }

    @Override
    public Color cellColor(Unit unit){
        if(unit instanceof EndInvisibleUnit e) return super.cellColor(unit).a(fade(e));
        return super.cellColor(unit);
    }

    @Override
    public void drawSoftShadow(Unit unit){
        if(!(unit instanceof EndInvisibleUnit e)){
            super.drawSoftShadow(unit);
            return;
        }
        Draw.color(0, 0, 0, 0.4f * fade(e));
        float rad = 1.6f;
        float size = Math.max(region.width, region.height) * Draw.scl;
        Draw.rect(softShadowRegion, unit, size * rad, size * rad);
        Draw.color();
    }

    @Override
    public void drawShadow(Unit unit){
        if(!(unit instanceof EndInvisibleUnit e)){
            super.drawShadow(unit);
            return;
        }

        Draw.color(Pal.shadow);
        Draw.alpha(Pal.shadow.a * fade(e));
        float el = Math.max(unit.elevation, visualElevation);
        Draw.rect(shadowRegion, unit.x + shadowTX * el, unit.y + shadowTY * el, unit.rotation - 90);
        Draw.color();
    }

    @Override
    public void drawLight(Unit unit){
        if(!(unit instanceof EndInvisibleUnit e)){
            super.drawLight(unit);
            return;
        }
        if(lightRadius > 0){
            // v155.4 无 light(Team,...) 重载, 用坐标版光照
            Drawf.light(unit.x, unit.y, lightRadius, lightColor, lightOpacity * (1f - e.getAlphaLerp()));
        }
    }

    /**
     * 武器绘制 (PU132 InvisibleUnitType.drawWeapons 完整移植)。
     *
     * <p>与原版 drawWeapons 的差异: 所有武器贴图先画轮廓再画本体,
     * 隐身时武器整体跟随 fade 变透明, 阴影透明度同步。</p>
     */
    @Override
    public void drawWeapons(Unit unit){
        float z = Draw.z();

        for(WeaponMount mount : unit.mounts){
            Weapon weapon = mount.weapon;
            boolean found = bottomWeapons.contains(weapon);

            float rotation = unit.rotation - 90;
            float weaponRotation = rotation + (weapon.rotate ? mount.rotation : 0);
            float recoil = -((mount.reload) / weapon.reload * weapon.recoil);
            float wx = unit.x + Angles.trnsx(rotation, weapon.x, weapon.y) + Angles.trnsx(weaponRotation, 0, recoil),
            wy = unit.y + Angles.trnsy(rotation, weapon.x, weapon.y) + Angles.trnsy(weaponRotation, 0, recoil);

            float zC = Draw.z();
            if(found) Draw.z(zC - 0.005f);

            if(weapon.shadow > 0){
                float shadowFade = 1f;
                if(unit instanceof EndInvisibleUnit e) shadowFade = fade(e);
                Drawf.shadow(wx, wy, weapon.shadow, shadowFade);
            }

            boolean outlineFound = weapon.outlineRegion.found();
            applyColor(unit);
            if(outlineFound){
                float zB = Draw.z();
                if(!weapon.top || found) Draw.z(zB);

                Draw.rect(weapon.outlineRegion,
                wx, wy,
                weapon.outlineRegion.width * Draw.scl * -Mathf.sign(weapon.flipSprite),
                weapon.region.height * Draw.scl,
                weaponRotation);

                Draw.z(zB);
            }

            // 本体: 隐身时透明度 = 1 - alphaLerp (轮廓颜色保持, 本体渐隐)
            // ★ found() 守卫: PU132 原版无条件绘制, 但 opticaecus 的无贴图匿名武器
            //   (中间激光炮) 会画出错误贴图 — 原版 v158 Weapon.draw 有此判断
            if(unit instanceof EndInvisibleUnit e && outlineFound) Draw.alpha(1f - e.getAlphaLerp());
            if(weapon.region.found()) Draw.rect(weapon.region,
            wx, wy,
            weapon.region.width * Draw.scl * -Mathf.sign(weapon.flipSprite),
            weapon.region.height * Draw.scl,
            weaponRotation);

            if(weapon.heatRegion.found() && mount.heat > 0){
                Draw.color(weapon.heatColor, mount.heat);
                Draw.blend(Blending.additive);
                Draw.rect(weapon.heatRegion,
                wx, wy,
                weapon.heatRegion.width * Draw.scl * -Mathf.sign(weapon.flipSprite),
                weapon.region.height * Draw.scl,
                weaponRotation);
                Draw.blend();
                Draw.color();
            }
            Draw.z(zC);
        }

        Draw.reset();
        Draw.z(z);
    }

    @Override
    public void applyColor(Unit unit){
        if(!(unit instanceof EndInvisibleUnit e)){
            super.applyColor(unit);
            return;
        }

        // 隐身时整体颜色向 tint 过渡 + 透明度降低 (PU132 applyColor L250-262)
        float lerp = fade(e);
        Tmp.c1.set(Color.white).lerp(tint, Mathf.lerp(0f, 0.5f, e.getAlphaLerp()));
        Draw.color(Tmp.c1);
        Draw.alpha(lerp);
        Draw.mixcol(Color.white, unit.hitTime);
        if(unit.drownTime > 0 && unit.floorOn().isDeep()){
            Draw.mixcol(unit.floorOn().mapColor, unit.drownTime * 0.8f);
        }
    }
}

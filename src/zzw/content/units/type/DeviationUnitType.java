package zzw.content.units.type;

import arc.graphics.Color;
import arc.math.Interp;
import arc.util.Log;
import mindustry.content.StatusEffects;
import mindustry.graphics.Pal;
import mindustry.type.Weapon;
import zzw.content.units.bullet.LightningTurretBulletType;
import zzw.content.units.entities.DecorationUnitEntity;
import zzw.content.units.type.decal.WingDecorationType;
import zzw.content.units.type.decal.WingDecorationType.Wing;
import zzw.content.type.UnityUnitType;

/**
 * deviation 单位类型 (PU132 UnityUnitTypes.java L3610-3653 完整移植)
 *
 * <p>T6 飞行单位: 闪电炮弹武器 + 4 片翅膀扇动装饰。</p>
 *
 * <p>★ 与 PU132 的差异 (v155.4 适配):
 * <br>1. PU132 用注解 @EntityDef({Unitc, Decorationc}) 生成装饰实体,
 *     这里显式指定 constructor = DecorationUnitEntity::create;
 * <br>2. 武器名需带 "create-" 前缀 (mod 贴图在 atlas 中自动加前缀,
 *     武器贴图名不会被 ContentTransformer 转换, 必须手动写全)。</p>
 */
public class DeviationUnitType extends UnityUnitType{

    /**
     * @param name 单位注册名 (如 "deviation", ContentTransformer 会自动加 mod 前缀)
     */
    public DeviationUnitType(String name){
        super(name);

        // ★ 装饰实体: 持有 decors[] 驱动翅膀动画, 没有它翅膀不会显示!
        constructor = DecorationUnitEntity::create;

        health = 9500f;
        speed = 2.7f;
        accel = 0.07f;
        drag = 0.04f;
        hitSize = 96f;
        engineOffset = 38f;
        engineSize = 4.75f;
        flying = true;
        lowAltitude = true;
        outlineColor = Color.valueOf("464a61");

        // 武器贴图名必须带 "create-" 前缀 (atlas 中是 create-deviation-mount)
        weapons.add(new Weapon("create-" + name + "-mount"){{
            x = 28f;
            y = -17.5f;
            shootY = 10.25f;
            rotate = true;
            rotateSpeed = 5f;
            reload = 80f;
            inaccuracy = 1f;

            // 闪电炮弹: 发射一枚光球, 命中后驻留原地持续闪电攻击周围敌人
            bullet = new LightningTurretBulletType(8f, 32f){{
                range = 120f;
                trailLength = 15;
                trailColor = color = lightningColor = Pal.lancerLaser;
                lightningDamage = 20f;
                lightning = 8;
                splashDamage = 25f;
                splashDamageRadius = 35f;
                status = StatusEffects.shocked;
                reload = 85f;
                duration = 5f * 60f;
            }};
        }});

        // 翅膀装饰 (贴图 load 时自动尝试 create- 前缀)
        decorations.add(new WingDecorationType(name + "-wing", 4){{
            flapScl = 120f;
            flapAnimation = new Interp.ExpOut(2, 2.5f);
            wings.add(new Wing(0, 19f, -35.25f, 0.75f, 19f),
                new Wing(1, 24.75f, -28.75f, 0.5f, 18f),
                new Wing(2, 24.25f, -8f, 0.25f, 17f),
                new Wing(3, 18f, 0.25f, 0f, 16f));
        }});

        Log.info("[deco-debug] DeviationUnitType '@' 构造完成, 装饰: @, 武器: @",
            name, decorations.size, weapons.size);
    }
}

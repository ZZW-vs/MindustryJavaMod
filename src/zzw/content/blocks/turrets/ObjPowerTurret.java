package zzw.content.blocks.turrets;

import arc.func.Cons;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.geom.Vec3;
import arc.util.Time;
import mindustry.content.Fx;
import mindustry.entities.bullet.LaserBoltBulletType;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.draw.DrawTurret;
import zzw.util.WavefrontObject;

/**
 * PU132 ObjPowerTurret 移植版 (cube 炮台) - v155.4 适配
 *
 * 机制:
 * - 使用 WavefrontObject (.obj 文件) 渲染伪 3D 立方体
 * - 受击时产生形变效果 (distortionTime)
 * - 旋转动画随 reload 进度变化
 *
 * v155.4 适配:
 * - reloadTime → reload (Block 字段)
 * - efficiency() → efficiency (字段而非方法)
 * - baseRegion 需自行声明
 *
 * 参考: PU_V8 main/src/unity/world/blocks/defense/turrets/ObjPowerTurret.java
 */
public class ObjPowerTurret extends PowerTurret {
    public WavefrontObject object;
    public TextureRegion baseRegion;
    
    // 死星激光攻击 - 蓝色系
    public LaserBoltBulletType laserShootType = new LaserBoltBulletType(5.2f, 13){{
        lifetime = 30f;
        healPercent = 0f;  // 改为攻击而非治疗
        collidesTeam = false;
        backColor = Color.valueOf("6586b0");  // 使用立方体炮台原本的蓝色
        frontColor = Color.valueOf("87ceeb");  // 使用立方体炮台原本的浅蓝色
        width = 2f;
        height = 7f;
        shootEffect = Fx.shootBig;
        hitEffect = Fx.hitLaser;
        despawnEffect = Fx.hitLaser;
        hittable = false;
        reflectable = false;
        lightColor = Color.valueOf("6586b0");
        lightOpacity = 0.6f;
    }};

    public ObjPowerTurret(String name) {
        super(name);
        shootType = laserShootType;
    }

    @Override
    public void load() {
        super.load();
        baseRegion = region;
        // 修复 UI 图标显示问题: DrawTurret.icons() 返回 {base, region},
        // 若 base 找不到 (create-the-cube-base 不存在) 会导致图标只显示一个角.
        // 让 DrawTurret.base 也用 region, 使 UI 图标正确显示.
        if (drawer instanceof DrawTurret dt) {
            dt.base = region;
        }
    }

    public class ObjPowerTurretBuild extends PowerTurretBuild {
        // 初始旋转时间随机化, 使多个炮台旋转方向/相位不同 (原版 effect: 旋转方向不固定)
        float time = Mathf.random(0f, 360f * Mathf.degRad);
        float distortionTime = 0f;
        float chargeTime = 0f;  // 蓄力时间
        boolean isCharging = false;  // 是否正在蓄力

        @Override
        public void updateTile() {
            super.updateTile();
            if (Float.isNaN(time)) time = Mathf.random(0f, 360f * Mathf.degRad);
            
            // 蓄力逻辑 - 模仿死星的蓄力机制
            if (target != null && efficiency > 0) {
                if (!isCharging) {
                    isCharging = true;
                    chargeTime = 0f;
                }
                chargeTime += Time.delta;
                
                // 蓄满后射击
                if (chargeTime >= 2.5f) {  // 2.5秒蓄力时间
                    shoot(laserShootType);
                    chargeTime = 0f;
                    isCharging = false;
                }
            } else {
                isCharging = false;
                chargeTime = 0f;
            }
            
            // 立方体旋转速度随 reload 进度变化
            // PU_V8: efficiency() * (1f + ((reload * 2.5f) / reloadTime)) * Time.delta
            // v155.4: efficiency() → efficiency (字段), reloadTime → reload (Block 字段),
            //         PU_V8 reload (Build 计数器) → reloadCounter
            // ★ 修复: 即使 efficiency=0 (无电/无弹药), 仍保持基础旋转 (与原版一致)
            float speed = efficiency * (1f + ((reloadCounter * 2.5f) / reload));
            if (speed < 0.1f) speed = 0.1f;  // 最小旋转速度, 避免蓄力完后静止
            time += speed * Time.delta;
            distortionTime = Math.max(0f, distortionTime - (Time.delta * 0.2f));
        }

        @Override
        public void damage(float damage) {
            distortionTime = Mathf.clamp(Mathf.sqrt(Math.max(0f, damage / 20f)), 0f, 3f);
            super.damage(damage);
        }

        protected float getDistortion() {
            return ((Mathf.clamp(1f - (healthf() * 2f)) * 2f) + distortionTime) / 16f;
        }

        @Override
        public void draw() {
            Draw.rect(baseRegion, x, y);
            Draw.color();

            // 蓄力特效 - 模仿死星的蓄力视觉效果
            if (isCharging) {
                float chargeProgress = chargeTime / 2.5f;
                Draw.color(Color.valueOf("6586b00"));  // 半透明的蓝色
                Draw.alpha(chargeProgress * 0.8f);  // 透明度随蓄力进度增加
                
                // 绘制蓄力光环
                for (int i = 0; i < 3; i++) {
                    float radius = 20f + chargeProgress * 10f + i * 8f;
                    float alpha = (1f - chargeProgress) * (1f - i * 0.3f);
                    Draw.alpha(alpha);
                    Lines.circle(x, y, radius);
                }
                
                Draw.color();
            }

            Cons<Vec3> distort = v -> {
                if (getDistortion() >= 0.001f) {
                    v.add(Mathf.range(getDistortion()), Mathf.range(getDistortion()), Mathf.range(getDistortion()));
                }
            };

            // ★ GPU渲染器: Mat3D.rotate(Vec3.Z, +deg) = 逆时针 (标准OpenGL), 取反旧公式的 rotation
            object.draw(x, y, Mathf.cos(time, 76f, 120f), Mathf.sin(time, 76f, 120f), rotation, distort);
        }
    }
}

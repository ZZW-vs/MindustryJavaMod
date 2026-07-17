package zzw.content.blocks.turrets;

import arc.func.Cons;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.geom.Vec3;
import arc.util.Time;
import mindustry.world.blocks.defense.turrets.PowerTurret;
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

    public ObjPowerTurret(String name) {
        super(name);
    }

    @Override
    public void load() {
        super.load();
        baseRegion = region;
    }

    public class ObjPowerTurretBuild extends PowerTurretBuild {
        float time = 0f;
        float distortionTime = 0f;

        @Override
        public void updateTile() {
            super.updateTile();
            if (Float.isNaN(time)) time = 0f;
            // 立方体旋转速度随 reload 进度变化 (reload 是当前计数器, block.reload 是最大值)
            // 原 PU_V8 公式: efficiency() * (1f + ((reload * 2.5f) / reloadTime)) * Time.delta
            // v155.4: efficiency() → efficiency (字段), reloadTime → block.reload
            time += efficiency * (1f + ((reload * 2.5f) / reload)) * Time.delta;
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

            Cons<Vec3> distort = v -> {
                if (getDistortion() >= 0.001f) {
                    v.add(Mathf.range(getDistortion()), Mathf.range(getDistortion()), Mathf.range(getDistortion()));
                }
            };

            object.draw(x, y, Mathf.cos(time, 76f, 120f), Mathf.sin(time, 76f, 120f), -rotation, distort);
        }
    }
}

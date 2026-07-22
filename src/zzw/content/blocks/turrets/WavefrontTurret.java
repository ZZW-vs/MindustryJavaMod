package zzw.content.blocks.turrets;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import mindustry.graphics.Drawf;
import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.graphics.Layer;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import zzw.util.WavefrontObject;

/**
 * PU132 WavefrontTurret 移植版 (wavefront 炮台) - v155.4 适配
 *
 * 原版机制:
 * - 使用 ModelInstance (真 3D) 渲染 wavefront.g3dj 模型
 * - 模型随炮台朝向旋转 (rotation - 90°)
 * - 射击时产生间隙 (gap) 动画
 *
 * v155.4 简化 (因 arc 库无 arc.graphics.g3d 包):
 * - 改用 WavefrontObject (伪 3D) 渲染 wavefront.obj
 * - 保留: 旋转动画、射击时间隙动画
 * - 简化: 移除展开/折叠动画 (AnimControl)
 *
 * 修复:
 * - reload/reloadCounter 字段混淆导致 angle 计算恒为常数
 * - object.size 不再在 draw 中被覆盖 (由 ZObjs 统一配置为 4f)
 * - 旋转用 rotation - 90f (匹配原版 Z 轴朝向)
 *
 * 参考: PU_V8 main/src/unity/world/blocks/defense/turrets/WavefrontTurret.java
 */
public class WavefrontTurret extends PowerTurret {
    public WavefrontObject object;
    public float objectRotationSpeed = 7f;
    public TextureRegion baseRegion;

    public WavefrontTurret(String name) {
        super(name);
        recoil = 6f;  // v155.4: recoilAmount → recoil
    }

    @Override
    public void load() {
        super.load();
        baseRegion = Core.atlas.find(name + "-base");
    }

    /** wavefront.png 不存在 (3D 炮台用 WavefrontObject 渲染), UI 图标只显示底座 */
    @Override
    public TextureRegion[] icons() {
        return new TextureRegion[]{baseRegion};
    }

    public class WavefrontTurretBuild extends PowerTurretBuild {
        float gap = 0f;
        float angle = 0f;
        float waitTime = 0f;
        float animTime = 0f;

        @Override
        public void updateTile() {
            super.updateTile();
            if (isShooting() && canConsume()) {  // v155.4: consValid() → canConsume()
                gap = Math.min(0.5f, gap + (0.005f * Time.delta));
                // PU_V8: angle += (reload / reloadTime) * objectRotationSpeed;
                // v155.4: reloadTime → reload (Block 字段); reload (Build 计数器) → reloadCounter
                angle += (reloadCounter / reload) * objectRotationSpeed;
                animTime = Mathf.approach(animTime, 40f, Time.delta);
            } else {
                angle = Mathf.slerp(angle, Mathf.round(angle / 90f) * 90f, 0.1f);
                if (resetAvailable()) {
                    gap = Math.max(0f, gap - (0.005f * Time.delta));
                }
                animTime = Mathf.approach(animTime, 0f, Time.delta);
            }

            if (waitTime > 0f) {
                waitTime -= Time.delta;
            }
        }

        @Override
        public boolean shouldTurn() {
            return super.shouldTurn() && waitTime <= 0f;
        }

        @Override
        protected void shoot(mindustry.entities.bullet.BulletType type) {
            super.shoot(type);
            waitTime = 60f;
        }

        private boolean resetAvailable() {
            return Angles.within(angle, Mathf.round(angle / 90f) * 90f, 3f);
        }

        @Override
        public void draw() {
            if (baseRegion.found()) {
                Draw.rect(baseRegion, x, y);
            }
            Draw.color();

            // 阴影层 (在模型之下, 与 DrawTurret.shadowLayer 一致)
            Draw.z(Layer.blockBuilding - 1f);
            // 3D 模型无 2D 贴图, 使用圆形阴影代替
            Drawf.shadow(x, y, size * 12f);

            if (object != null && object.faces != null && object.faces.size > 0) {
                // 模型绕 Z 轴旋转 (跟随炮台朝向)
                // ★ 修正: Vec3.mul(Mat) 是行向量乘矩阵, rotate(Vec3.Z, +deg) = 顺时针 (非逆时针)
                // 所以 rotation 系数必须取负: rZ = 90f - rotation
                float rZ = 90f - rotation;
                // gap 作为轻微 X 轴倾斜 (间隙效果)
                float rX = gap * 30f;
                // angle 旋转转为 Y 轴摆动
                float rY = Mathf.sin(angle * Mathf.degRad) * 5f;

                object.draw(x, y, rX, rY, rZ);
            }
        }
    }
}

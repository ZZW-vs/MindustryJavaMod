package zzw.content.blocks.turrets;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Angles;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.math.Rand;
import arc.util.Time;
import mindustry.Vars;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Bullet;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.world.blocks.defense.turrets.PowerTurret;

/**
 * 浮游球炮台 (PU_V8 OrbTurret 移植版)
 * orb-turret: 多层环绕浮游球, 充能完成后发射带尾迹的子弹
 * 简化: 移除 TexturedTrail 依赖 (v158 无此类), 用简单 Fill.circle 绘制球体;
 *       保留多层环绕运动 + 充能装填机制
 * 参考: PU_V8 main/src/unity/world/blocks/defense/turrets/OrbTurret.java
 */
public class OrbTurret extends PowerTurret {
    public int orbsPerLayer = 3;
    public int layers = 2;

    public float bulletWidth = 2f;

    public Color bulletHeadColor = Color.white;
    public Color bulletTrailColor = Pal.accent;

    public float layerSpeedMultiplier = 1f;
    public float layerDamageMultiplier = 1f;

    public OrbTurret(String name) {
        super(name);

        solid = true;
        update = true;
    }

    public class OrbTurretBuild extends PowerTurretBuild {
        public Seq<Float> orbX = new Seq<>();
        public Seq<Float> orbY = new Seq<>();
        public Seq<Float> offsets = new Seq<>();
        public float loader = 0f;
        public int activeOrbs = 0;
        public Rand rand = new Rand();

        public float getX(int i) {
            return x + Mathf.cosDeg((360f / orbsPerLayer) * (int) (i % orbsPerLayer) + Time.time * 5f + offsets.get(i / orbsPerLayer)) * (bulletWidth * 3f + bulletWidth * 2f) * (int) (1 + i / orbsPerLayer) * Mathf.cosDeg(90f + Time.time * 5f + offsets.get(i / orbsPerLayer));
        }

        public float getY(int i) {
            return y + Mathf.sinDeg((360f / orbsPerLayer) * (int) (i % orbsPerLayer) + Time.time * 5f + offsets.get(i / orbsPerLayer)) * (bulletWidth * 3f + bulletWidth * 2f) * (int) (1 + i / orbsPerLayer);
        }

        @Override
        public void placed() {
            super.placed();
            for (int i = 0; i < layers; i++) {
                rand.setSeed(pos() + i * 69);
                offsets.add(rand.nextFloat() * 420f);
            }
        }

        @Override
        protected boolean validateTarget() {
            return super.validateTarget() && activeOrbs > 0;
        }

        @Override
        protected void bullet(BulletType type, float xOffset, float yOffset, float angleOffset, mindustry.entities.Mover mover) {
            int l = (int) Math.ceil((float) activeOrbs / (float) layers);
            float xP = getX(activeOrbs - 1);
            float yP = getY(activeOrbs - 1);
            Bullet bullet = type.create(this, team, xP, yP, Angles.angle(xP, yP, targetPos.x, targetPos.y), (1f + Mathf.range(0f)) * (1f + l * layerSpeedMultiplier), 1f);
            bullet.damage = bullet.damage * (1f + l * layerDamageMultiplier);
            activeOrbs--;
        }

        @Override
        public void update() {
            super.update();

            if (offsets.size == 0) {
                for (int i = 0; i < layers; i++) {
                    rand.setSeed(pos() + i * 69);
                    offsets.add(rand.nextFloat() * 420f);
                }
            }

            if (activeOrbs < layers * orbsPerLayer) {
                if (loader >= 1f) {
                    activeOrbs++;
                    loader = 0f;
                }
                loader += 3f / 60f;
            } else {
                loader = 0f;
            }
        }

        @Override
        public void draw() {
            Draw.rect(((mindustry.world.draw.DrawTurret)drawer).base, x, y);

            Draw.z(Layer.effect);

            for (int i = 0; i < activeOrbs; i++) {
                float ox = getX(i);
                float oy = getY(i);
                Draw.color(bulletTrailColor);
                Fill.circle(ox, oy, bulletWidth * 1.5f);
                Draw.color(bulletHeadColor);
                Fill.circle(ox, oy, bulletWidth);
            }

            Draw.color();
        }
    }
}

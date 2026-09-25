package zzw.content.blocks.turrets;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.content.Fx;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Building;
import mindustry.gen.Posc;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.consumers.Consume;
import mindustry.world.consumers.ConsumeLiquidFilter;
import zzw.content.Z_Sounds;

/**
 * EndGameTurret 移植自 PU_V8
 */
public class EndGameTurret extends PowerTurret {
    public TextureRegion baseRegion, baseLightsRegion, bottomLightsRegion, eyeMainRegion;

    public EndGameTurret(String name) {
        super(name);
        health = 68000;
        consumePower(320f);
        reload = 300f;
        shootShake = 2.2f;
        outlineIcon = false;
        noUpdateDisabled = false;
        loopSound = Z_Sounds.endgameActive;
        shootSound = Z_Sounds.endgameShoot;
    }

    @Override
    public void load() {
        super.load();
        baseRegion = Core.atlas.find(name + "-base");
        baseLightsRegion = Core.atlas.find(name + "-base-lights");
        bottomLightsRegion = Core.atlas.find(name + "-bottom-lights");
        eyeMainRegion = Core.atlas.find(name + "-eye");
    }

    public class EndGameTurretBuild extends PowerTurretBuild {
        protected float charge = 0f;
        protected float resist = 1f;
        protected float resistTime = 10f;
        protected float threatLevel = 1f;
        protected float lastHealth = 0f;
        protected float eyeResetTime = 0f;
        protected float eyesAlpha = 0f;
        protected float lightsAlpha = 0f;

        @Override
        public void updateTile() {
            enabled = true;
            lastHealth = health;
            
            // 只要有电就亮着
            if (efficiency > 0.0001f) {
                eyeResetTime = 0f;
                float value = lightsAlpha > efficiency ? 1f : efficiency;
                lightsAlpha = Mathf.lerpDelta(lightsAlpha, efficiency, 0.07f * value);
                eyesAlpha = Mathf.lerpDelta(eyesAlpha, efficiency, 0.06f * value);
            } else {
                if (eyeResetTime >= 60f) {
                    lightsAlpha = Mathf.lerpDelta(lightsAlpha, 0f, 0.07f);
                    eyesAlpha = Mathf.lerpDelta(eyesAlpha, 0f, 0.06f);
                } else {
                    eyeResetTime += Time.delta;
                }
            }

            // 反子弹和主攻击需要 canConsume
            if (canConsume()) {
                super.updateTile();
            }
        }

        @Override
        protected void shoot(BulletType type) {
            consume();
            super.shoot(type);
            shootSound.at(x, y, 1f, 1.5f);
        }
    }
}
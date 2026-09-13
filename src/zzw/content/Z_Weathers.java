package zzw.content;

import arc.graphics.Color;
import arc.math.Mathf;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.content.Fx;
import mindustry.entities.Damage;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.graphics.Pal;
import mindustry.type.weather.ParticleWeather;
import mindustry.gen.WeatherState;
import mindustry.type.Weather;


import static mindustry.Vars.tilesize;
import static mindustry.Vars.world;

/**
 * PU132 天气移植版 (unity.content.UnityWeathers)。
 *
 * <p>包含 imber 派系的时空异常雨 timeStorm 与 end 派系的碎屑风暴 debrisStorm。</p>
 *
 * <p>★ v158 适配: 原生 {@link ParticleWeather} 在 v158.1 仍存在, timeStorm 的
 * noise 参数全部兼容; 唯一差异是 timeStorm 的 update 会在场上生成
 * {@code UnityBullets.distField}(空间扭曲力场弹) —— 该弹型属于未移植的
 * exp 力场系统, 暂以注释保留, 待 DistFieldBulletType 移植后恢复。</p>
 *
 * @author PU132 原作, 移植: zzw
 */
public class Z_Weathers{
    public static Weather timeStorm, debrisStorm;

    public static void load(){
        // timeStorm — 时空异常 (imber): 紫色雾气 + 随机空间扭曲 (PU132 ParticleWeather)
        timeStorm = new ParticleWeather("time-anomoly"){ // PU132 原文即拼错 (anomoly)
            public final float spawnChance = 0.02f;
            public final float minDistSize = 2f * tilesize;
            public final float maxDistSize = 6f * tilesize;

            {
                duration = 1.5f * Time.toMinutes;
                noiseLayerSclM = 0.6f;
                noiseLayerAlphaM = 0.7f;
                noiseLayerSpeedM = 2f;
                baseSpeed = 0.05f;
                color = noiseColor = Pal.lancerLaser;
                noiseScale = 1100f;
                noisePath = "fog";
                drawParticles = false;
                drawNoise = true;
                useWindVector = false;
                xspeed = 2f;
                yspeed = -0.5f;
                opacityMultiplier = 0.47f;
            }

            @Override
            public void update(WeatherState state){
                // TODO PU132: 每 tick 以 spawnChance 概率在场上随机位置生成
                // UnityBullets.distField 空间扭曲力场 (依赖未移植的 DistFieldBulletType,
                // new Float[]{Mathf.random(minDistSize, maxDistSize), 2f} 为力场参数)
                super.update(state);
            }
        };

        // debrisStorm — 碎屑风暴 (end): 全场随机爆炸伤害 + 单位击退 (自研 DebrisWeather 移植)
        debrisStorm = new DebrisWeather("debris-storm"){{
            sizeMax = 32f;
            sizeMin = 10f;
            density = 100000f;
            xspeed = 18f;
            yspeed = -12f;
            color = Color.darkGray;
            opacityMultiplier = 2f;

            sound = mindustry.gen.Sounds.wind2;  // PU132 Sounds.windhowl, v158 用 wind2 替代
            soundVol = 0f;
            soundVolOscMag = 1.5f;
            soundVolOscScl = 1100f;
            soundVolMin = 0.02f;
        }};
    }

    /**
     * 碎屑风暴 (PU132 unity.type.DebrisWeather 完整移植)。
     *
     * <p> ParticleWeather 子类: 除粒子视觉外, 每 tick 有概率在场上任意位置
     * 造成小范围爆炸伤害 (50~300), 并有概率对全场单位施加风暴方向的
     * 击退冲量 + 100~500 伤害 —— 是 end 派系的"末日环境"。</p>
     */
    public static class DebrisWeather extends ParticleWeather{
        public float spawnChance = 0.5f, minSplashRadius = 2f, maxSplashRadius = 10f, minDamage = 50f, maxDamage = 300f;
        public float knockbackChance = 0.005f, minKnockback = 5f, maxKnockback = 15f, knockbackDamageMin = 100f, knockbackDamageMax = 500f;

        public DebrisWeather(String name){
            super(name);
        }

        @Override
        public void update(WeatherState state){
            if(Mathf.chanceDelta(state.intensity * spawnChance)){
                float x = Mathf.random(world.unitWidth()), y = Mathf.random(world.unitHeight());
                Fx.smoke.at(x, y);
                Fx.blastExplosion.at(x, y);
                Damage.damage(x, y, Mathf.random(minSplashRadius, maxSplashRadius), Mathf.random(minDamage, maxDamage));
            }
            Groups.unit.each(u -> {
                if(Mathf.chanceDelta(state.intensity * knockbackChance)){
                    u.impulse(Tmp.v1.trns(Mathf.angle(xspeed, yspeed), Mathf.random(minKnockback, maxKnockback) * 80f));
                    u.damage(Mathf.random(knockbackDamageMin, knockbackDamageMax));
                }
            });
            super.update(state);
        }
    }
}

package zzw.content.blocks;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.Strings;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import zzw.content.exp.EField;
import zzw.content.exp.UnityPal;

import static arc.Core.atlas;
import static mindustry.Vars.*;

/**
 * LevelLimitWall (移植自 PU_V8 unity.world.blocks.defense.LevelLimitWall)
 *
 * 继承 ExpLimitWall, 额外添加:
 * - 多级贴图 (levelRegions): 根据等级显示不同贴图
 * - damageExp: 受击时将伤害转换为经验
 * - edgeRegion/edgeMaxRegion: 边缘贴图 (under 层)
 * - updateEffect: 最高等级时随机播放的特效
 *
 * 注意: PU_V8 用 @Dupe(base=ExpTurret, parent=LimitWall) 自动生成 ExpLimitWall,
 * 此处手写继承 ExpLimitWall (已手动实现等价逻辑)
 */
public class LevelLimitWall extends ExpLimitWall {
    public TextureRegion[] levelRegions;
    public TextureRegion edgeRegion, edgeMaxRegion;
    public float damageExp = 1 / 20f;

    public Effect updateEffect = Fx.none;
    public float updateChance = 0.01f;

    public LevelLimitWall(String name) {
        super(name);
        maxLevel = 6;
        passive = true;
        updateExpFields = false;
        upgradeEffect = Fx.none;
    }

    @Override
    public void init() {
        // 与 PU_V8 一致: damageReduction = EExpoZero(0.1, 8^(1/maxLevel), clamp=true)
        damageReduction = new EField.EExpoZero(f -> {}, 0.1f, Mathf.pow(8f, 1f / maxLevel), true, null,
            v -> Strings.autoFixed(Mathf.roundPositive(v * 10000) / 100f, 2) + "%");
        super.init();
    }

    @Override
    public void load() {
        super.load();
        // 大型变体贴图不存在时回退到基础贴图 (如 shielded-wall-large 缺失时用 shielded-wall)
        if (!region.found()) {
            String baseName = name.replace("-large", "");
            region = atlas.find(baseName);
        }
        edgeRegion = atlas.find(name + "-under");
        edgeMaxRegion = atlas.find(name + "-under-max", name + "-under");
        int n = 1;
        while (n <= 100) {
            TextureRegion t = atlas.find(name + n);
            if (!t.found()) break;
            n++;
        }
        if (n > 1) {
            levelRegions = new TextureRegion[n];
            levelRegions[0] = region;
            for (int i = 1; i < n; i++) {
                levelRegions[i] = atlas.find(name + i);
            }
        }
    }

    public class LevelLimitWallBuild extends ExpLimitWallBuild {
        public TextureRegion levelRegion() {
            if (levelRegions == null) return region;
            return levelRegions[Math.min((int)(levelf() * levelRegions.length), levelRegions.length - 1)];
        }

        @Override
        public void draw() {
            TextureRegion top = levelRegion();
            Draw.z(Layer.block);
            Draw.rect(top, x, y);
            if (top != region) {
                Draw.z(Layer.blockUnder - 0.01f);
                if (edgeRegion.found()) {
                    Draw.rect(top == levelRegions[levelRegions.length - 1] ? edgeMaxRegion : edgeRegion, x, y);
                }
                if (!state.isPaused() && updateEffect != Fx.none
                    && top == levelRegions[levelRegions.length - 1]
                    && Mathf.chanceDelta(updateChance)) {
                    updateEffect.at(x + Mathf.range(size * 4f), y + Mathf.range(size * 4f), UnityPal.exp);
                }
            }

            if (flashHit && hit > 0.0001f) {
                Draw.z(Layer.block);
                Draw.color(flashColor);
                Draw.alpha(hit * 0.5f);
                Draw.blend(arc.graphics.Blending.additive);
                Fill.rect(x, y, tilesize * size, tilesize * size);
                if (top != region) {
                    Draw.z(Layer.blockUnder - 0.01f);
                    Draw.mixcol(Color.white, 1f);
                    Draw.rect(edgeRegion, x, y);
                    Draw.mixcol();
                }
                Draw.blend();
                Draw.reset();

                if (!state.isPaused()) {
                    hit = Mathf.clamp(hit - arc.util.Time.delta / 10f);
                }
            }
        }

        @Override
        public float handleDamage(float amount) {
            float a = amount * damageExp;
            if (a >= 1f) handleExp((int) a);
            else if (a > 0f && Mathf.chance(a)) handleExp(1);
            setEFields(level());
            return super.handleDamage(amount);
        }

        @Override
        public void levelup() {
            upgradeSound.at(this);
            upgradeEffect.at(this);
            if (upgradeBlockEffect != Fx.none) {
                upgradeBlockEffect.at(x, y, 0, Color.white, levelRegion());
            }
        }
    }
}

package zzw.content.blocks;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.entities.Effect;
import mindustry.gen.Bullet;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.ui.Bar;
import mindustry.world.meta.Stat;

import static arc.Core.atlas;
import static arc.Core.settings;
import static mindustry.Vars.tilesize;

/**
 * ShieldWall (移植自 PU_V8 unity.world.blocks.defense.ShieldWall)
 *
 * 继承 LevelLimitWall, 额外添加护盾机制:
 * - shieldHealth: 护盾血量, 受击先扣护盾
 * - repair: 护盾恢复速度
 * - topRegion: 受击时叠加显示的贴图 (name + "-top")
 * - shieldBroke: 护盾是否破碎
 * - 护盾渲染 (animatedshields 或线条框)
 *
 * 贴图命名: shielded-wall.png + shielded-wall-top.png (PU132/PU_V8 原版贴图)
 */
public class ShieldWall extends LevelLimitWall {
    public float shieldHealth;
    public float repair = 50f;
    public TextureRegion topRegion;

    private final int timerHeal = timers++;

    public Effect shieldGen = new Effect(20, e -> {
        Draw.color(e.color, e.fin());
        if (settings.getBool("animatedshields")) {
            Fill.rect(e.x, e.y, e.fin() * size * 8, e.fin() * size * 8);
        } else {
            Lines.stroke(1.5f);
            Draw.alpha(0.09f);
            Fill.rect(e.x - e.fin() * size * 4, e.y - e.fin() * size * 4, e.fin() * size * 8, e.fin() * size * 8);
            Draw.alpha(1f);
            Lines.rect(e.x, e.y, e.fin() * size * 8, e.fin() * size * 8);
        }
    }).layer(Layer.shields);

    public Effect shieldBreak = new Effect(40, e -> {
        Draw.color(e.color);
        Lines.stroke(3f * e.fout());
        Lines.rect(e.x, e.y, e.fin() * size * 8, e.fin() * size * 8);
    }).followParent(true);

    public Effect shieldShrink = new Effect(20, e -> {
        Draw.color(e.color, e.fout());
        if (settings.getBool("animatedshields")) {
            Fill.rect(e.x, e.y, e.fout() * size * 8, e.fout() * size * 8);
        } else {
            Lines.stroke(1.5f);
            Draw.alpha(0.2f);
            Fill.rect(e.x, e.y, e.fout() * size * 8, e.fout() * size * 8);
            Draw.alpha(1f);
            Lines.rect(e.x, e.y, e.fout() * size * 8, e.fout() * size * 8);
        }
    }).layer(Layer.shields);

    public ShieldWall(String name) {
        super(name);
        update = true;
        flashHit = false;
    }

    @Override
    public void load() {
        super.load();
        // 大型盾墙贴图不存在时回退到普通盾墙贴图
        TextureRegion baseTop = atlas.find(name + "-top");
        if (!baseTop.found()) {
            topRegion = atlas.find("create-shielded-wall-top");
        } else {
            topRegion = baseTop;
        }
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(Stat.shieldHealth, shieldHealth);
    }

    @Override
    public void setBars() {
        super.setBars();
        addBar("shield", (ShieldWallBuild e) -> new Bar("stat.shieldhealth", Pal.accent, () -> e.shieldBroke ? 0f : 1f - e.gotDamage / shieldHealth));
    }

    public class ShieldWallBuild extends LevelLimitWallBuild {
        public boolean shieldBroke = true;
        public float gotDamage, warmup, scl = 0;

        @Override
        public void created() {
            super.created();
            shieldGen.at(x, y);
        }

        @Override
        public void updateTile() {
            super.updateTile();

            warmup = Mathf.lerpDelta(warmup, 1f, 0.05f);
            scl = Mathf.lerpDelta(scl, shieldBroke ? 0f : 1f, 0.05f);

            if (timer(timerHeal, 60f) && shieldBroke && gotDamage > 0) {
                gotDamage -= repair * delta();
            }

            if (gotDamage >= shieldHealth && !shieldBroke) {
                shieldBroke = true;
                gotDamage = shieldHealth;
                shieldBreak.at(x, y);
            }

            if (shieldBroke && gotDamage <= 0) {
                shieldBroke = false;
                gotDamage = 0;
            }

            if (gotDamage < 0) gotDamage = 0;

            if (this.hit > 0) this.hit -= 0.2f * Time.delta;
        }

        @Override
        public void draw() {
            super.draw();

            if (gotDamage > 0f) {
                Draw.alpha(gotDamage / shieldHealth * 0.75f);
                Draw.blend(arc.graphics.Blending.additive);
                Draw.rect(topRegion, x, y);
                Draw.blend();
                Draw.reset();
            }

            drawShield();
        }

        @Override
        public boolean collide(Bullet b) {
            if (b.team != team && b.type.speed > 0.001f && b.type.absorbable) {
                b.hit = true;
                b.type.despawnEffect.at(x, y, b.rotation(), b.type.hitColor);

                if (shieldBroke) {
                    damage(b.damage);
                } else {
                    handleExp((int) (b.damage * damageExp));
                    setEFields(level());
                    gotDamage += b.damage;
                }
                hit = 1f;

                b.remove();
                return false;
            }
            return super.collide(b);
        }

        @Override
        public void onRemoved() {
            super.onRemoved();
            if (!shieldBroke) shieldShrink.at(x, y);
        }

        public void drawShield() {
            if (!shieldBroke) {
                Draw.z(Layer.shields);
                Draw.color(team.color, Color.white, Mathf.clamp(hit));

                float radius = this.block.size * tilesize * warmup * scl;

                if (settings.getBool("animatedshields")) {
                    Fill.rect(x, y, radius, radius);
                } else {
                    Lines.stroke(1.5f);
                    Draw.alpha(0.09f + Mathf.clamp(0.08f * hit));
                    Fill.rect(x, y, radius, radius);
                    Draw.alpha(1f);
                    Lines.rect(x - radius / 2, y - radius - 2, radius, radius);
                    Draw.reset();
                }
            }

            Draw.reset();
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.bool(shieldBroke);
            write.f(gotDamage);
            write.f(warmup);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            shieldBroke = read.bool();
            gotDamage = read.f();
            warmup = read.f();
        }
    }
}

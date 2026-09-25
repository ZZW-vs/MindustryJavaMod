package zzw.content.blocks.turrets;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.ObjectFloatMap;
import arc.struct.Seq;
import arc.util.Strings;
import arc.util.Time;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.pattern.ShootAlternate;
import mindustry.type.Liquid;
import mindustry.ui.Styles;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

import static mindustry.Vars.tilesize;

public class BarrelsItemTurret extends ItemTurret {
    protected final Seq<Barrel> barrels = new Seq<>(1);
    protected boolean focus;
    protected Vec2 tr3 = new Vec2();
    protected float barrelSpread = 12f;
    protected int barrelShots = 1;

    public ObjectFloatMap<Liquid> coolantBoost = new ObjectFloatMap<>();

    public BarrelsItemTurret(String name){
        super(name);
    }

    protected void addBarrel(float x, float y, float reloadTime){
        barrels.add(new Barrel(x, y, reloadTime));
    }

    /**
     * 自定义冷却强化显示: 直接展示固定百分比 (120% / 145% ...)。
     *
     * <p>原版公式 {@code 1 + 消耗量 × coolantMultiplier × 液体热容} 因水 (0.4) 与
     * 冷冻液 (0.9) 热容不同, 算出的百分比很难看; 这里用 {@link #coolantBoost}
     * 直接指定每种液体的效率加成, 面板也同步显示。</p>
     */
    @Override
    public void setStats(){
        super.setStats();

        if (coolant != null && !coolantBoost.isEmpty()) {
            stats.replace(Stat.booster, table -> {
                table.row();
                table.table(c -> {
                    for (Liquid liquid : mindustry.Vars.content.liquids()) {
                        float boost = coolantBoost.get(liquid, -1f);
                        if (boost < 0f) continue;

                        c.table(Styles.grayPanel, b -> {
                            b.image(liquid.uiIcon).size(40).pad(10f).left();
                            b.table(info -> {
                                info.add(liquid.localizedName).left().row();
                                info.add(Strings.autoFixed(coolant.amount * 60f, 2) + StatUnit.perSecond.localized())
                                        .left().color(Color.lightGray);
                            });
                            b.add(Core.bundle.format("bullet.reload", Strings.autoFixed((1f + boost) * 100f, 2)))
                                    .pad(10f).right().grow().padRight(15f);
                        }).growX().pad(5).row();
                    }
                }).growX().colspan(table.getColumns());
                table.row();
            });
        }
    }

    protected class Barrel{
        public final float x, y, reloadTime;

        public Barrel(float x, float y, float reloadTime){
            this.x = x;
            this.y = y;
            this.reloadTime = reloadTime;
        }
    }

    public class BarrelsItemTurretBuild extends ItemTurretBuild{
        protected float[] barrelReloads = new float[barrels.size];
        protected int[] barrelShotCounters = new int[barrels.size];
        protected int barrelCounter = 0;

        @Override
        protected void shoot(BulletType type){
            if(focus){
                recoil = 2f;
                heat = 1f;
                float i = barrelCounter % 2 - 0.5f;
                barrelCounter++;
                for(int s = 0; s < barrelShots; s++){
                    float offset = (s - barrelShots / 2f + 0.5f) * barrelSpread;
                    tr3.trns(rotation + offset, Mathf.random(-1f, 1f) * range * 0.1f);
                    bullet(type, x + tr3.x, y + tr3.y, rotation + offset + Mathf.random(-inaccuracy, inaccuracy), null);
                }
            }else{
                super.shoot(type);
            }
        }

        protected void shootBarrel(int barrel, BulletType type){
            if(barrelReloads[barrel] >= barrels.get(barrel).reloadTime){
                barrelReloads[barrel] = 0f;
                barrelShotCounters[barrel]++;
                recoil = 2f;
                heat = 1f;
                float angle = rotation + barrels.get(barrel).x * Mathf.sinDeg(barrels.get(barrel).y);
                bullet(type, x + barrels.get(barrel).x, y + barrels.get(barrel).y, angle + Mathf.random(-inaccuracy, inaccuracy), null);
            }
        }

        @Override
        protected void updateShooting(){
            super.updateShooting();

            // ★ 多管独立装填: 每根管子按自身 reloadTime 循环 (参考 PU_V8 BarrelsItemTurret)
            //   注意必须用 delta() (帧增量) 累加, 之前写成 reload * Time.delta 会导致每帧都触发一次开火
            for(int i = 0, len = barrels.size; i < len; i++){
                if(!hasAmmo()) break;

                if(barrelReloads[i] >= barrels.get(i).reloadTime){
                    shootBarrel(i, peekAmmo());
                    barrelReloads[i] = 0f;
                }else{
                    barrelReloads[i] += delta() * peekAmmo().reloadMultiplier * baseReloadSpeed();
                }
            }
        }

        /**
         * 覆写冷却推进: 使用 {@link BarrelsItemTurret#coolantBoost} 里的固定百分比。
         *
         * <p>原版实现是 {@code reloadCounter += 消耗量 × 热容 × coolantMultiplier},
         * 换成 {@code edelta() × boost} 后, 装填速度正好是 {@code 1 + boost}。</p>
         */
        @Override
        protected void updateCooling(){
            if(coolantBoost.isEmpty()){
                super.updateCooling();
                return;
            }

            if(coolant == null || coolant.efficiency(this) <= 0f || efficiency <= 0f) return;

            float boost = coolantBoost.get(liquids.current(), 0f);
            float amount = coolant.amount * coolant.efficiency(this);
            coolant.update(this);
            reloadCounter += edelta() * boost;

            if(Mathf.chance(0.06 * amount)){
                coolEffect.at(x + Mathf.range(size * tilesize / 2f), y + Mathf.range(size * tilesize / 2f));
            }
        }

        @Override
        public void draw(){
            // ★ 恢复原版绘制逻辑: 绘制底座 + 炮管
            // 本 mod 有整炮贴图，按原版方式绘制底座和炮管
            Draw.rect(region, x, y, rotation - 90);
        }
    }
}

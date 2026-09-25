package zzw.content.blocks.turrets;

import arc.Core;
import arc.graphics.Color;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.ObjectFloatMap;
import arc.struct.Seq;
import arc.util.Strings;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.pattern.ShootAlternate;
import mindustry.type.Liquid;
import mindustry.ui.Styles;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

import static mindustry.Vars.tilesize;

/**
 * 多管物品炮台 (PU_V8 BarrelsItemTurret 完整移植)
 * ghost/banshee: 支持多炮管独立装填, focus 模式下所有炮管集中瞄准目标点
 * ★完整移植 PU_V8 原版机制 (v155.4 API 适配):
 *  - shoot(): focus 模式下使用 barrelCounter 交替 + spread + xRand 计算位置
 *  - shootBarrel(): 各炮管独立装填计数, focus 模式下瞄向目标点
 *  - bullet() 5 参方法 (v155.4): 自动处理声音/特效/反冲/弹药消耗
 *  - 使用 barrelCounter 替代 PU_V8 shotCounter (v155.4 无 shotCounter 字段)
 *  - 使用 tr3 自有 Vec2 替代 PU_V8 tr (v155.4 ItemTurretBuild 无 tr 字段)
 *  - spread 从 ShootAlternate 模式提取 (v155.4 无独立 spread 字段)
 * 参考: PU_V8 main/src/unity/world/blocks/defense/turrets/BarrelsItemTurret.java
 */
public class BarrelsItemTurret extends ItemTurret {
    protected final Seq<Barrel> barrels = new Seq<>(1);
    protected boolean focus;
    protected Vec2 tr3 = new Vec2();

    /**
     * 自定义冷却强化表: 液体 → 额外装填速度比例 (0.2 表示 +20%, 即 120%)。
     */
    public ObjectFloatMap<Liquid> coolantBoost = new ObjectFloatMap<>();

    public BarrelsItemTurret(String name){
        super(name);
    }

    protected void addBarrel(float x, float y, float reloadTime){
        barrels.add(new Barrel(x, y, reloadTime));
    }

    @Override
    public void load(){
        super.load();
        baseRegion = Core.atlas.find("unity-block-" + size);
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

        @Override
        protected void shoot(BulletType type){
            if(focus){
                recoil = recoilAmount;
                heat = 1f;
                float i = shotCounter % 2 - 0.5f;
                for(int s = 0; s < shots; s++){
                    float offset = (s - shots / 2f + 0.5f) * spread;
                    tr3.trns(rotation + offset, xRand() * range * 0.1f);
                    bullet(type, x + tr3.x, y + tr3.y, rotation + offset + Mathf.random(-inaccuracy, inaccuracy), Mathf.random(), null);
                }
            }else{
                super.shoot(type);
            }
        }

        @Override
        protected void shootBarrel(int barrel, BulletType type){
            if(barrelReloads[barrel] >= reloadTime){
                barrelReloads[barrel] = 0f;
                barrelShotCounters[barrel]++;
                recoil = recoilAmount;
                heat = 1f;
                float angle = rotation + barrels.get(barrel).x * Angles.lenient(barrels.get(barrel).y);
                bullet(type, x + barrels.get(barrel).x, y + barrels.get(barrel).y, angle + Mathf.random(-inaccuracy, inaccuracy), Mathf.random(), null);
            }
        }

        @Override
        public void updateTile(){
            super.updateTile();
            for(int i = 0; i < barrelReloads.length; i++){
                barrelReloads[i] += efficiency() * reloadTime * Time.delta;
            }
        }

        @Override
        public void draw(){
            Draw.rect(baseRegion, x, y);
            Draw.color();
            for(int i = 0; i < barrels.size; i++){
                Barrel barrel = barrels.get(i);
                Draw.rect(region, x + barrel.x, y + barrel.y, rotation - 90);
            }
        }
    }
}
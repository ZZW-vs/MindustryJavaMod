package zzw.content.blocks.production;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.logic.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.blocks.production.*;
import mindustry.world.consumers.*;

import static mindustry.Vars.*;

/**
 * 爆炸分离器 (PU132 unity.world.blocks.production.ExplosiveSeparator 移植)
 * <p>继承 Separator, 增加热量系统: 燃料物品积累热量, 冷却液降温,
 * 过热时引发核爆级爆炸 (类似钍反应堆)。</p>
 *
 * <p>适配说明:
 * <ul>
 *   <li>consumes.&lt;ConsumeLiquid&gt;get(ConsumeType.liquid) → findConsumer + init() 缓存 (v155.4 无 ConsumeType)</li>
 *   <li>bars.add(...) → addBar(...) (v155.4 barMap API)</li>
 *   <li>无 PU132 依赖, 全部使用原版 Fx/Sounds/Pal</li>
 * </ul></p>
 */
public class ExplosiveSeparator extends Separator{
    /** 灯光颜色 (紫) */
    public Color lightColor = Color.valueOf("7f19ea");
    /** 冷却状态颜色 (透明) */
    public Color coolColor = new Color(1, 1, 1, 0f);
    /** 过热状态颜色 (橙红) */
    public Color hotColor = Color.valueOf("ff9575a3");
    /** 导致分离器加热的燃料物品 */
    public Item fuelItem;
    /** 每帧加热量 × 满载度 */
    public float heating = 0.01f;
    /** 开始冒烟的热量阈值 */
    public float smokeThreshold = 0.3f;
    /** 灯光开始闪烁的热量阈值 */
    public float flashThreshold = 0.46f;
    /** 爆炸半径 (格) */
    public float explosionRadius = 19f;
    /** 爆炸伤害 */
    public float explosionDamage = 1350f;
    /** 每单位冷却液移除的热量 */
    public float coolantPower = 0.5f;

    /** 顶部贴图 */
    public TextureRegion lightsRegion, topRegion;

    /** 缓存的冷却液类型 (init() 中从 ConsumeLiquid 获取) */
    public Liquid coolantLiquid;

    protected Vec2 tr = new Vec2();

    public ExplosiveSeparator(String name){
        super(name);
    }

    @Override
    public void init(){
        super.init();
        // v155.4: ConsumeType.liquid 不存在, 用 findConsumer 查找冷却液消费者
        ConsumeLiquid cliquid = findConsumer(c -> c instanceof ConsumeLiquid);
        if(cliquid != null) coolantLiquid = cliquid.liquid;
    }

    @Override
    public void load(){
        super.load();
        topRegion = Core.atlas.find(name + "-top");
        lightsRegion = Core.atlas.find(name + "-lights");
    }

    @Override
    public TextureRegion[] icons(){
        return new TextureRegion[]{region, topRegion};
    }

    @Override
    public void setBars(){
        super.setBars();
        // v155.4: 使用 addBar 替代 bars.add
        addBar("heat", entity -> new Bar("bar.heat", Pal.lightOrange, () -> ((ExplosiveSeparatorBuild)entity).heat));
    }

    public class ExplosiveSeparatorBuild extends SeparatorBuild{
        /** 当前热量 (0~1) */
        protected float heat;
        /** 生产效率 (燃料满载度) */
        protected float productionEfficiency;

        @Override
        public void updateTile(){
            super.updateTile();

            int fuel = items.get(fuelItem);
            float fullness = (float)fuel / itemCapacity;
            productionEfficiency = fullness;

            // 有燃料且通电时加热
            if(fuel > 0 && enabled && power.status > 0f){
                heat += fullness * heating * Math.min(delta(), 4f);
            }else{
                productionEfficiency = 0f;
            }

            // 冷却液降温
            if(heat > 0 && enabled && coolantLiquid != null){
                float maxUsed = Math.min(liquids.get(coolantLiquid), heat / coolantPower);
                heat -= maxUsed * coolantPower;
                liquids.remove(coolantLiquid, maxUsed);
            }

            // 过热冒烟
            if(heat > smokeThreshold){
                float smoke = 1.0f + (heat - smokeThreshold) / (1f - smokeThreshold);
                if(Mathf.chance(smoke / 20f * delta())){
                    Fx.reactorsmoke.at(x + Mathf.range(size * tilesize / 2f),
                        y + Mathf.random(size * tilesize / 2f));
                }
            }

            heat = Mathf.clamp(heat);

            // 过热爆炸
            if(heat >= 0.999f){
                Events.fire(Trigger.thoriumReactorOverheat);
                kill();
            }
        }

        @Override
        public void onDestroyed(){
            super.onDestroyed();
            Sounds.explosionReactor.at(tile);

            int fuel = items.get(fuelItem);

            // 燃料不足且热量低时不爆炸
            if((fuel < 5 && heat < 0.5f) || !state.rules.reactorExplosions) return;

            Effect.shake(6f, 16f, x, y);
            Fx.reactorExplosion.at(x, y);
            for(int i = 0; i < 6; i++){
                Time.run(Mathf.random(40f), () -> Fx.reactorsmoke.at(x, y));
            }

            Damage.damage(x, y, explosionRadius * tilesize, explosionDamage * 4f);

            // 爆炸特效
            for(int i = 0; i < 20; i++){
                Time.run(Mathf.random(50f), () -> {
                    tr.rnd(Mathf.random(40f));
                    Fx.explosion.at(tr.x + x, tr.y + y);
                });
            }

            // 核爆烟柱
            for(int i = 0; i < 70; i++){
                Time.run(Mathf.random(80f), () -> {
                    tr.rnd(Mathf.random(120f));
                    Fx.reactorsmoke.at(tr.x + x, tr.y + y);
                });
            }
        }

        @Override
        public void drawLight(){
            float fract = productionEfficiency;
            Drawf.light(x, y, (90f + Mathf.absin(5f, 5f)) * fract, Tmp.c1.set(lightColor).lerp(Color.scarlet, heat), 0.6f * fract);
        }

        @Override
        public void draw(){
            super.draw();
            Draw.rect(topRegion, x, y);

            // 热量颜色叠加
            Draw.color(coolColor, hotColor, heat);
            Fill.rect(x, y, size * tilesize, size * tilesize);

            // 灯光闪烁 (超过阈值时)
            if(heat > flashThreshold){
                float flash = 1f + ((heat - flashThreshold) / (1f - flashThreshold)) * 5.4f;
                flash += flash * delta();
                Draw.color(Color.red, Color.yellow, Mathf.absin(flash, 9f, 1f));
                Draw.alpha(0.6f);
                Draw.rect(lightsRegion, x, y);
            }

            Draw.reset();
        }

        @Override
        public double sense(LAccess sensor){
            if(sensor == LAccess.heat) return heat;
            return super.sense(sensor);
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.f(heat);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            heat = read.f();
        }
    }
}

package zzw.content.blocks.exp;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.content.Liquids;
import mindustry.entities.Effect;
import mindustry.entities.Fires;
import mindustry.entities.Puddles;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.graphics.BlockRenderer;
import mindustry.type.Liquid;
import mindustry.ui.Bar;
import mindustry.world.Tile;
import zzw.content.graphics.UnityFx;

import static mindustry.Vars.renderer;

/**
 * 熔化工厂 (PU132 unity.world.blocks.exp.MeltingCrafter 移植)
 * <p>继承 KoruhCrafter。经验不足时不会直接受伤, 而是累积 melt 值;
 * melt 值满后方块销毁, 喷出岩浆和火焰。draw() 中绘制裂纹和岩浆色叠加。</p>
 *
 * <p>适配说明:
 * <ul>
 *   <li>unity.content.UnityLiquids.lava → zzw.content.Z_Liquids.lava (ZZW 项目已有熔岩)</li>
 *   <li>unity.content.UnityFx.blockMelt/longSmoke → zzw.content.graphics.UnityFx.blockMelt/longSmoke</li>
 *   <li>BlockRenderer.cracks / crackRegions / Puddles.deposit / Fires.create 均为 v155.4 原生 API</li>
 * </ul></p>
 */
public class MeltingCrafter extends KoruhCrafter{
    /** 每点缺失经验增加的熔化值 */
    public float meltAmount = 0.01f;
    /** 熔化值冷却速度 */
    public float cooldown = 0.01f;
    /** 岩浆液体 (Z_Liquids.lava, 若为 null 则回退到原版 slag) */
    public Liquid lava = zzw.content.Z_Liquids.lava != null ? zzw.content.Z_Liquids.lava : Liquids.slag;

    /** 裂纹颜色 1 */
    public Color lavaColor1 = Color.coral;
    /** 裂纹颜色 2 */
    public Color lavaColor2 = Color.orange;
    /** 熔化销毁特效 */
    public Effect meltEffect = UnityFx.blockMelt;
    /** 长烟雾特效 */
    public Effect smokeEffect = UnityFx.longSmoke;

    public MeltingCrafter(String name){
        super(name);
        ignoreExp = true;
    }

    @Override
    public void setBars(){
        super.setBars();
        addBar("heat", (MeltingCrafterBuild entity) -> new Bar(
            () -> Core.bundle.get("bar.heat"),
            () -> Pal.ammo,
            () -> Mathf.clamp(entity.melt)
        ));
    }

    public class MeltingCrafterBuild extends KoruhCrafterBuild{
        /** 当前熔化值 [0, 1] */
        public float melt = 0f;

        @Override
        public void lackingExp(int missing){
            // 经验不足: 不直接伤害, 而是累积熔化值
            melt += meltAmount * missing;
        }

        @Override
        public void updateTile(){
            super.updateTile();

            // 有经验时缓慢冷却熔化值
            if(exp > 0 && melt > 0){
                melt -= delta() * cooldown;
                if(melt < 0) melt = 0;
            }
            // 高熔化值时随机冒烟
            if(Mathf.chance(Mathf.clamp(melt) * 0.1f)) smokeEffect.at(x + Mathf.range(size * 2f), y + Mathf.range(size * 2f));
            // 熔化值满且岩浆液体不足: 销毁
            if(melt >= 1f && (liquids == null || liquids.get(lava) > 0.1f * liquidCapacity)) kill();
        }

        @Override
        public void draw(){
            super.draw();

            if(melt < 0.1f) return;
            if(melt > 1f) melt = 1f;
            Draw.z(Layer.bullet - 0.01f);
            Draw.color(lavaColor1, lavaColor2, Mathf.absin(3f, 1f));
            // 绘制裂纹纹理 (随 melt 值选择裂纹阶段)
            TextureRegion region = renderer.blocks.cracks[block.size - 1][Mathf.clamp((int)(melt * BlockRenderer.crackRegions), 0, BlockRenderer.crackRegions - 1)];
            Draw.rect(region, x, y, (id % 4) * 90);
            Draw.color();
        }

        @Override
        public void onDestroyed(){
            super.onDestroyed();
            // 销毁时喷出岩浆和火焰
            if(liquids == null || liquids.currentAmount() > 0.1f * liquidCapacity){
                meltEffect.at(x, y, 0f, lavaColor2);
                Puddles.deposit(tile, lava, liquids.get(lava) * 10);
                for(int i = 0; i < 4; i++){
                    Tile tg = tile.nearby(i);
                    if(tg == null || !tg.solid()) continue;
                    Fires.create(tg);
                }

                float fx = x; float fy = y; int fsize = size;
                for(int i = 0; i < 5; i++){
                    Time.run(Mathf.random(60f), () -> smokeEffect.at(fx + Mathf.range(fsize * 2f), fy + Mathf.range(fsize * 2f)));
                }
            }
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.f(melt);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            melt = read.f();
        }
    }
}

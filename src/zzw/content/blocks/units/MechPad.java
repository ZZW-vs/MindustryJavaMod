package zzw.content.blocks.units;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.*;
import mindustry.world.blocks.storage.*;

import static mindustry.Vars.*;

/**
 * MechPad 机甲台 (PU132 unity.world.blocks.units.MechPad 完整移植)
 *
 * 功能: 玩家踩上台子后点击, 经过 craftTime 时间建造指定单位并切换控制
 * - 玩家在 2.5*tilesize 范围内可点击激活
 * - 建造过程中显示单位图标和进度条
 * - 再次点击可还原为核心单位 (revert)
 * - 支持被 ConversionPad 继承
 *
 * v155.4 适配: consValid() 改为本地方法 (原版 @Override 已移除)
 */
public class MechPad extends Block{
    public UnitType unitType = UnitTypes.dagger;
    public float craftTime = 100f;
    public float cooldown = 0.1f;
    public float spawnRot = 90f;
    public float spawnForce = 3f;

    protected TextureRegion arrowRegion;

    public MechPad(String name){
        super(name);
        update = configurable = true;
        hasItems = solid = false;
        ambientSound = Sounds.unitCreate;  // v155.4: respawn -> unitCreate
        ambientSoundVolume = 0.08f;
    }

    @Override
    public void setStats(){
        super.setStats();
    }

    @Override
    public void load(){
        super.load();
        arrowRegion = Core.atlas.find("transfer-arrow");
    }

    @Override
    public boolean canReplace(Block other){
        return other.alwaysReplace;
    }

    public class MechPadBuild extends Building implements ControlBlock{
        protected @Nullable BlockUnitc thisU;
        protected float time;
        protected float heat;
        protected boolean revert;

        @Override
        public boolean canControl(){
            return false;
        }

        public boolean inRange(Player player){
            return player.unit() != null && !player.unit().dead && Math.abs(player.unit().x - x) <= 2.5f * tilesize && Math.abs(player.unit().y - y) <= 2.5f * tilesize;
        }

        @Override
        public void drawSelect(){
            Draw.color(consValid() ? (inRange(player) ? Color.orange : Pal.accent) : Pal.darkMetal);
            float length = tilesize * size / 2f + 3f + Mathf.absin(Time.time, 5f, 2f);

            Draw.rect(arrowRegion, x + length, y, (0f + 2f) * 90f);
            Draw.rect(arrowRegion, x, y + length, (1f + 2f) * 90f);
            Draw.rect(arrowRegion, x + -1 * length, y, (2f + 2f) * 90f);
            Draw.rect(arrowRegion, x, y + -1 * length, (3f + 2f) * 90f);

            Draw.color();
        }

        @Override
        public boolean shouldShowConfigure(Player player){
            return consValid() && inRange(player);
        }

        @Override
        public Unit unit(){
            if(thisU == null){
                thisU = (BlockUnitc)UnitTypes.block.create(team);
                thisU.tile(self());
            }
            return (Unit)thisU;
        }

        @Override
        public boolean configTapped(){
            if(!consValid() || !inRange(player)) return false;
            configure(null);
            return false;
        }

        @Override
        public void configured(@Nullable Unit unit, @Nullable Object value){
            if(unit != null && unit.isPlayer() && !(unit instanceof BlockUnitc)){
                time = 0;
                revert = unit.type == unitType;
                if(!net.client()){
                    unit.getPlayer().unit(unit());
                }
            }
        }

        @Override
        public boolean shouldAmbientSound(){
            return inProgress();
        }

        @Override
        public void updateTile(){
            if(inProgress()){
                time += edelta() * (consValid() ? 1 : 0) * state.rules.unitBuildSpeedMultiplier;
                if(time >= craftTime) finishUnit();
            }
            heat = Mathf.lerpDelta(heat, inProgress() ? 1 : 0, cooldown);
        }

        public UnitType getResultUnit(){
            return revert ? bestCoreUnit() : unitType;
        }

        public UnitType bestCoreUnit(){
            return ((CoreBlock)thisU.getPlayer().bestCore().block).unitType;
        }

        public boolean inProgress(){
            return thisU != null && isControlled();
        }

        public void finishUnit(){
            Player thisP = thisU.getPlayer();
            if(thisP == null) return;
            Fx.spawn.at(self());

            if(!net.client()){
                Unit unit = getResultUnit().create(team);
                unit.set(self());
                unit.rotation = spawnRot;
                unit.impulse(0, spawnForce);
                unit.set(getResultUnit(), thisP);
                unit.spawnedByCore = true;
                unit.add();
            }

            if(state.isCampaign() && thisP == player) getResultUnit().unlock();

            consume();
            time = 0;
            revert = false;
        }

        @Override
        public void draw(){
            super.draw();
            if(!inProgress()) return;
            float progress = Mathf.clamp(time / craftTime);

            Draw.color(Pal.darkMetal);
            Lines.stroke(2f * heat);
            Fill.poly(x, y, 4, 10f * heat);
            Draw.reset();
            TextureRegion region = getResultUnit().fullIcon;

            Draw.color(0, 0, 0, 0.4f * progress);
            Draw.rect("circle-shadow", x, y, region.width / 3f, region.width / 3f);
            Draw.color();
            Draw.draw(Layer.blockOver, () -> {
                try{
                    Drawf.construct(x, y, region, 0f, progress, state.rules.unitBuildSpeedMultiplier, time);
                    Lines.stroke(heat, Pal.accentBack);
                    float pos = Mathf.sin(time, 6f, 8f);
                    Lines.lineAngleCenter(x + pos, y, 90f, 16f - Math.abs(pos) * 2f);
                    Draw.color();
                }
                catch(Throwable bruh){
                    //why.
                }
            });

            Lines.stroke(1.5f * heat);
            Draw.color(Pal.accentBack);
            Lines.poly(x, y, 4, 8f * heat);

            float oy = -7f;
            float len = 6f * heat;
            Lines.stroke(5f);
            Draw.color(Pal.darkMetal);
            Lines.line(x - len, y + oy, x + len, y + oy, false);

            Fill.tri(x + len, y + oy - Lines.getStroke() / 2f, x + len, y + oy + Lines.getStroke() / 2f, x + (len + Lines.getStroke() * heat), y + oy);
            Fill.tri(x + len * -1, y + oy - Lines.getStroke() / 2f, x + len * -1, y + oy + Lines.getStroke() / 2f, x + (len + Lines.getStroke() * heat) * -1f, y + oy);

            Lines.stroke(3);
            Draw.color(Pal.accent);
            Lines.line(x - len, y + oy, x - len+ len * 2f * progress, y + oy, false);

            Fill.tri(x + len, y + oy - Lines.getStroke() / 2f, x + len, y + oy + Lines.getStroke() / 2f, x + (len + Lines.getStroke() * heat), y + oy);
            Fill.tri(x + len * -1f, y + oy - Lines.getStroke() / 2f, x + len * -1f, y + oy + Lines.getStroke() / 2f, x + (len + Lines.getStroke() * heat) * -1f, y + oy);

            Draw.reset();
        }

        // v155.4 适配: consValid() 已移除, 改为本地方法
        public boolean consValid(){
            return power.status > 0.98f;
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            time = read.f();
            revert = read.bool();
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.f(time);
            write.bool(revert);
        }
    }
}

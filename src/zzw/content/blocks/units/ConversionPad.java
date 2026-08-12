package zzw.content.blocks.units;

import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.type.*;

import static mindustry.Vars.*;

/**
 * ConversionPad 转换台 (PU132 unity.world.blocks.units.ConversionPad 完整移植)
 *
 * 功能: 沙盒专用, 玩家踩上后根据 upgrades 列表将当前单位转换为另一种单位
 * - 继承 MechPad, 复用建造/切换逻辑
 * - 在 inRange() 中检查玩家当前单位是否在 upgrades 列表中
 * - configured() 中根据 upgrades 匹配设置 resultUnit
 * - finishUnit() 中使用 coreSpawn 保持核心生成状态
 */
public class ConversionPad extends MechPad{
    public Seq<UnitType[]> upgrades = new Seq<>();

    public ConversionPad(String name){
        super(name);
    }

    public class ConversionPadBuild extends MechPadBuild{
        UnitType resultUnit;
        boolean coreSpawn;

        @Override
        public boolean inRange(Player player){
            boolean isValid = false;
            for(UnitType[] unitTypes : upgrades){
                if(player.unit().type == unitTypes[0]) isValid = true;
            }
            return super.inRange(player) && isValid;
        }

        @Override
        public void configured(@Nullable Unit unit, @Nullable Object value){
            if(unit != null && unit.isPlayer() && !(unit instanceof BlockUnitc)){
                time = 0;
                for(UnitType[] unitTypes : upgrades){
                    if(unit.type == unitTypes[0]) resultUnit = unitTypes[1];
                }
                coreSpawn = unit.spawnedByCore;
                unit.spawnedByCore = true;
                if(!net.client()){
                    unit.getPlayer().unit(unit());
                }
            }
        }

        @Override
        public UnitType getResultUnit(){
            return resultUnit;
        }

        @Override
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
                unit.spawnedByCore = coreSpawn;
                unit.add();
            }

            if(state.isCampaign() && thisP == player) getResultUnit().unlock();

            consume();
            time = 0;
        }
    }
}

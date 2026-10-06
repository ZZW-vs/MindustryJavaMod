package zzw.content.units.soul;

import arc.math.Mathf;
import mindustry.Vars;
import mindustry.type.UnitType;
import zzw.content.Z_StatusEffects;
import zzw.content.type.UnityUnitType;
import zzw.content.units.ZEntityRegister;
import zzw.content.units.entities.DecorationUnitEntity;
import zzw.content.units.entities.MonolithSoulUnit;

/**
 * 携带灵魂的装饰单位实体 (PU132 unity.entities.comp.MonolithComp 的纯 Java 移植)。
 *
 * <p>tendence / liminality 这类"能量环单位"在原版是
 * {@link DecorationUnitEntity} (承载顶层装饰贴图)。要同时具备灵魂承载能力，
 * 必须继承 {@link DecorationUnitEntity} 而非普通 {@code UnitEntity}，
 * 否则会丢失装饰贴图 —— 这是单纯的实体子类化下唯一可行的"多继承"折衷。</p>
 *
 * <p>核心机制与 {@link SoulMechUnit} 一致，差别在于 {@code add()} / {@code update()}
 * 仍需先调用 {@link DecorationUnitEntity} 的实现来驱动装饰：</p>
 * <ol>
 *   <li>{@link #add()}：先 super.add() 初始化装饰实例 (decors[])，再处理灵魂容量；</li>
 *   <li>{@link #update()}：先执行瘫痪判定，再 super.update() 驱动装饰动画。</li>
 * </ol>
 *
 * @author GlennFolker (原作), 移植: zzw
 */
public class SoulDecorationUnit extends DecorationUnitEntity implements Soul{
    /** 当前灵魂数量 */
    private int souls;
    /** 灵魂容量 (0 = 不承载灵魂) */
    private int maxSouls;

    public static SoulDecorationUnit create(){
        return new SoulDecorationUnit();
    }

    @Override
    public int classId(){
        return ZEntityRegister.classId(SoulDecorationUnit.class);
    }

    @Override
    public void setType(UnitType type){
        super.setType(type);
        // 步骤 1: 非核心召唤的单位读取类型上的灵魂容量, 核心单位则为 0
        if(!spawnedByCore && type instanceof UnityUnitType u){
            maxSouls = u.maxSouls;
        }else{
            maxSouls = 0;
            souls = 0;
        }
    }

    @Override
    public void add(){
        // 先执行装饰初始化 (DecorationUnitEntity.add -> 创建 decors[])
        super.add();
        // 核心直接召唤的单位不携带灵魂
        if(spawnedByCore){
            maxSouls = 0;
            souls = 0;
        }
    }

    @Override
    public void update(){
        // 步骤 2: 瘫痪判定
        if(maxSouls > 0){
            if(!spawnedByCore && !hasSouls()){
                if(!hasEffect(Z_StatusEffects.disabled)){
                    apply(Z_StatusEffects.disabled, Float.MAX_VALUE);
                }
            }else{
                unapply(Z_StatusEffects.disabled);
            }
        }
        // 再驱动装饰动画
        super.update();
    }

    @Override
    public void killed(){
        // 步骤 3: 死亡拆魂
        if(Vars.net.server() || !Vars.net.active()){
            spreadSouls();
        }
        super.killed();
    }

    @Override
    public boolean apply(MonolithSoulUnit soul, int index, boolean transferred){
        // 步骤 4: 玩家操控的单位拆魂时, 有概率把一只灵魂交给玩家
        if(isPlayer() && !transferred && (Mathf.chance(1f / souls) || index == souls - 1)){
            soul.controller(getPlayer());
            transferred = true;
        }
        return transferred;
    }

    @Override
    public int souls(){
        return souls;
    }

    @Override
    public int maxSouls(){
        return maxSouls;
    }

    @Override
    public void join(){
        if(canJoin()) souls++;
    }

    @Override
    public void unjoin(){
        if(souls > 0) souls--;
    }
}

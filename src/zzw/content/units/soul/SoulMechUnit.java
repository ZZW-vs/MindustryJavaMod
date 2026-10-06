package zzw.content.units.soul;

import arc.math.Mathf;
import mindustry.Vars;
import mindustry.gen.MechUnit;
import mindustry.type.UnitType;
import zzw.content.Z_StatusEffects;
import zzw.content.type.UnityUnitType;
import zzw.content.units.ZEntityRegister;
import zzw.content.units.entities.MonolithSoulUnit;

/**
 * 携带灵魂的机甲单位实体 (PU132 unity.entities.comp.MonolithComp 的纯 Java 移植)。
 *
 * <p>PU132 用 {@code @EntityComponent} 把灵魂承载逻辑织入 Mechc 单位；
 * 本模组改为直接继承 {@link MechUnit} 并实现 {@link Soul} 接口。
 * pedestal / pilaster 等巨石机甲使用本类作为 {@code constructor}。</p>
 *
 * <p>核心机制 (逐步说明):</p>
 * <ol>
 *   <li><b>容量来源</b>：{@link #setType(UnitType)} 时从
 *       {@link UnityUnitType#maxSouls} 读取容量；被核心直接召唤
 *       (spawnedByCore) 的单位容量强制为 0，与 PU132 一致；</li>
 *   <li><b>瘫痪状态</b>：非核心单位在没有任何灵魂时会被施加
 *       {@link Z_StatusEffects#disabled} (移速/装填归零 + 缴械)，
 *       一旦有灵魂加入立即解除 —— 这是"巨石单位必须供魂"的阵营机制；</li>
 *   <li><b>死亡拆魂</b>：单位死亡时调用 {@link Soul#spreadSouls()}，
 *       把体内灵魂拆成一团团灵魂单位飞出，供玩家指挥或自动加入其他容器；</li>
 *   <li><b>操控转移</b>：拆魂时按 {@link #apply(MonolithSoulUnit, int, boolean)}
 *       的概率把其中一只灵魂的操控权交给玩家。</li>
 * </ol>
 *
 * @author GlennFolker (原作), 移植: zzw
 */
public class SoulMechUnit extends MechUnit implements Soul{
    /** 当前灵魂数量 */
    private int souls;
    /** 灵魂容量 (0 = 不承载灵魂) */
    private int maxSouls;

    public static SoulMechUnit create(){
        return new SoulMechUnit();
    }

    @Override
    public int classId(){
        return ZEntityRegister.classId(SoulMechUnit.class);
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
        super.add();
        // 核心直接召唤的单位不携带灵魂 (PU132 MonolithComp.add)
        if(spawnedByCore){
            maxSouls = 0;
            souls = 0;
        }
    }

    @Override
    public void update(){
        // 步骤 2: 瘫痪判定 —— 只有在单位确实能承载灵魂 (maxSouls > 0) 时才生效
        if(maxSouls > 0){
            if(!spawnedByCore && !hasSouls()){
                if(!hasEffect(Z_StatusEffects.disabled)){
                    apply(Z_StatusEffects.disabled, Float.MAX_VALUE);
                }
            }else{
                unapply(Z_StatusEffects.disabled);
            }
        }
        super.update();
    }

    @Override
    public void killed(){
        // 步骤 3: 死亡拆魂 (仅服务端 / 单机执行, 避免联机双端重复生成)
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
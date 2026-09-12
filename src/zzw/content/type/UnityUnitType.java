package zzw.content.type;

import arc.Core;
import arc.func.Func;
import arc.graphics.g2d.*;
import arc.struct.*;
import arc.util.*;
import mindustry.ctype.*;
import mindustry.entities.abilities.*;
import mindustry.entities.units.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.blocks.environment.*;
import zzw.content.units.AbilityTextures;
import zzw.content.units.entities.DecorationUnitEntity;
import zzw.content.units.type.decal.UnitDecorationType;
import zzw.content.units.type.decal.UnitDecorationType.UnitDecoration;
import zzw.content.units.types.Engine;

import static arc.Core.*;
import static mindustry.Vars.content;

/**
 * UnityUnitType (PU132 unity.type.UnityUnitType 移植)
 *
 * 这是简化版，仅保留 buffer/omega/cache 等基本单位所需功能:
 * - weaponXs: 武器初始 x 坐标列表, 供 ShootArmorAbility 偏移镜像武器
 * - sortSegWeapons: 段身武器镜像复制排序
 * - bottomWeapons: 底层武器列表
 *
 * 移除的高级功能 (依赖链过深, buffer/omega/cache 不需要):
 * - Worm (虫子单位): Wormc/WormDefaultUnit/WormDecal/segmentWeapons 等
 * - Copter (直升机): Copterc/Rotor/drawRotors 等
 * - Tentacle (触手): Tentaclec/TentacleType 等
 * - Decoration (装饰): Decorationc/UnitDecorationType 等
 * - CLeg/TriJointLegs: CLegc/CLegGroupType/TriJointLegsc 等
 * - Monolith/World/End/Imber: 自定义实体接口
 * - AbilityTextures: Ability 贴图枚举
 */
@SuppressWarnings("unchecked")
public class UnityUnitType extends UnitType{
    public final Seq<Weapon> segWeapSeq = new Seq<>();
    public Seq<Weapon> bottomWeapons = new Seq<>();

    // For shoot armor ability
    public FloatSeq weaponXs = new FloatSeq();
    
    // For decorations system
    public Seq<UnitDecorationType> decorations = new Seq<>();

    // For ability system (PU132)
    /** 能力贴图数组 */
    public TextureRegion[] abilityRegions = new TextureRegion[AbilityTextures.values().length];
    
    /** 自定义后腿贴图 */
    public TextureRegion customBackLegs;

    /** 对象化引擎 (PU132): 非 null 时 drawEngine 走 Engine.draw 而非原版标量字段。 */
    public Engine engine;
    /** 拖尾工厂 (PU132 trailType): 替代原版 new Trail(trailLength), Monolith 用 TexturedTrail/MultiTrail。 */
    public Func<Unit, Trail> trailType = unit -> new Trail(trailLength);

    /** 调试用: 装饰绘制日志计数 (只打印前 3 次, 避免刷屏) */
    private int decorDrawLogs = 0;

    /**
     * 最大容纳灵魂数 (PU132 UnitType.maxSouls)。
     *
     * <p>TODO: 灵魂机制 (MonolithSoul 聚合形成单位 / 单位死亡拆解为灵魂)
     * 未移植, 此字段仅作为数据占位保留, 与 PU132 数值一致。</p>
     */
    public int maxSouls = 0;

    public UnityUnitType(String name){
        super(name);
        outlines = false;
        // v155.4 适配: PU132 通过注解处理器自动设置, 简化版需手动指定默认构造器
        constructor = mindustry.gen.UnitEntity::create;
    }

    /**
     * 加载阶段: 加载装饰贴图 (PU132 原版 load() 的 decorations.each 段)。
     *
     * <p>★ 调试: 打印装饰数量, 方便确认装饰系统已接入。</p>
     */
    @Override
    public void load(){
        super.load();
        
        // 加载能力贴图 (PU132)
        for(AbilityTextures type : AbilityTextures.values()){
            String regionName = name + "-" + type.name();
            abilityRegions[type.ordinal()] = Core.atlas.find(regionName, (arc.graphics.g2d.TextureRegion)null);
        }
        
        // 加载自定义后腿贴图
        if(customBackLegs == null){
            customBackLegs = Core.atlas.find(name + "-backlegs", (arc.graphics.g2d.TextureRegion)null);
        }
        
        if(decorations.size > 0){
            boolean decorEntity = constructor != null && constructor.get() instanceof DecorationUnitEntity;
            Log.info("[deco-debug] 单位 @ 开始加载装饰, 数量: @ (实体构造器: @)",
                name, decorations.size,
                decorEntity ? "DecorationUnitEntity OK" : "非装饰实体 (装饰不会工作!)");
        }
        decorations.each(UnitDecorationType::load);
    }

    /**
     * 绘制身体下方的装饰 (PU132 原版 drawOutline: top=false 的装饰, 如翅膀)。
     *
     * <p>★ 调试: 前 3 次绘制打印日志, 确认绘制链路接通。</p>
     */
    @Override
    public void drawOutline(Unit unit){
        if(unit instanceof DecorationUnitEntity d){
            for(UnitDecoration decor : d.decors){
                if(!decor.type.top) decor.type.draw(unit, decor);
            }
            if(decorDrawLogs < 3){
                decorDrawLogs++;
                Log.info("[deco-debug] @ drawOutline 绘制下方装饰, decors: @", name, d.decors.length);
            }
        }
        super.drawOutline(unit);
    }

    /**
     * 绘制身体上方的装饰 (PU132 原版 drawBody: top=true 的装饰)。
     */
    @Override
    public void drawBody(Unit unit){
        super.drawBody(unit);
        if(unit instanceof DecorationUnitEntity d){
            for(UnitDecoration decor : d.decors){
                if(decor.type.top) decor.type.draw(unit, decor);
            }
        }
    }

    @Override
    public void init(){
        super.init();

        weapons.each(w -> weaponXs.add(w.x));

        //worm segment weapons sorting (保留用于段身单位)
        sortSegWeapons(segWeapSeq);

        Seq<Weapon> addBottoms = new Seq<>();
        for(Weapon w : weapons){
            if(bottomWeapons.contains(w) && w.otherSide != -1){
                addBottoms.add(weapons.get(w.otherSide));
            }
        }

        bottomWeapons.addAll(addBottoms.distinct());
    }

    public void sortSegWeapons(Seq<Weapon> weaponSeq){
        Seq<Weapon> mapped = new Seq<>();
        for(int i = 0, len = weaponSeq.size; i < len; i++){
            Weapon w = weaponSeq.get(i);
            if(w.recoilTime < 0f){
                w.recoilTime = w.reload;
            }
            mapped.add(w);

            if(w.mirror){
                Weapon copy = w.copy();
                copy.x *= -1;
                copy.shootX *= -1;
                copy.flipSprite = !copy.flipSprite;
                mapped.add(copy);

                w.reload *= 2;
                copy.reload *= 2;
                w.recoilTime *= 2;
                copy.recoilTime *= 2;
                w.otherSide = mapped.size - 1;
                copy.otherSide = mapped.size - 2;
            }
        }

        weaponSeq.set(mapped);
    }

    @Override
    public void drawWeapons(Unit unit){
        float z = Draw.z();

        applyColor(unit);
        for(WeaponMount mount : unit.mounts){
            Weapon weapon = mount.weapon;
            if(bottomWeapons.contains(weapon)) Draw.z(z - 0.0001f);

            weapon.draw(unit, mount);
            Draw.z(z);
        }

        Draw.reset();
    }

    /**
     * 引擎绘制 (PU132 UnityUnitType.drawEngine 移植, v158 签名为 drawEngines)。
     *
     * <p>engine 字段非 null 时走对象化引擎 (支持多引擎/自定义颜色),
     * 否则回退到原版 engines/标量字段渲染。</p>
     */
    @Override
    public void drawEngines(Unit unit){
        if(!unit.isFlying()) return;
        if(engine != null){
            engine.draw(unit);
        }else{
            super.drawEngines(unit);
        }
    }
}

package zzw.content.units.entities;

import arc.func.Floatc;
import arc.graphics.Color;
import arc.graphics.Draw;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Interp;
import arc.math.Mathf;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.core.World;
import mindustry.entities.Units;
import mindustry.entities.units.UnitController;
import mindustry.gen.*;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;
import zzw.content.graphics.MultiTrail;
import zzw.content.graphics.Trails;
import zzw.content.units.ai.MonolithSoulAI;

import static mindustry.Vars.*;

/**
 * MonolithSoul — 巨石灵魂实体 (PU132 移植)
 *
 * <p>PU132: unity.entities.comp.MonolithSoul 简化移植。</p>
 *
 * <p>★ v132 → v155.4 适配要点:</p>
 * <ul>
 *   <li>抽象类 → 普通类, 继承 UnitEntity;</li>
 *   <li>PU132 的 @EntityComponent 注解处理器依赖移除;</li>
 *   <li>PU132 的 MathU.addLength → 手动实现 (Vec2.addLength);</li>
 *   <li>PU132 的 monolithWorld → 本项目暂无此概念, 临时留 TODO;</li>
 *   <li>PU132 的 Soul.toSoul → 本项目暂无 Soul 系统, 临时留 TODO。</li>
 * </ul>
 *
 * @author GlennFolker (原作), 移植: zzw
 */
public class MonolithSoulEntity extends UnitEntity {
    // ===== 状态字段 =====
    /** 是否已实体化 (聚合为完整单位) */
    public boolean corporeal = false;
    /** 正在加入目标单位/建筑 */
    public boolean joining = false;
    /** 正在拾取方块 */
    public boolean forming = false;
    /** 加入目标单位/建筑 */
    public Teamc joinTarget;
    /** 目标方块 (forming) */
    public Tile formTarget;
    /** 已拾取的方块列表 */
    public Seq<Tile> forms = new Seq<>();
    /** 加入进度 (0-1) */
    public float joinProgress;
    /** 加入时间 (用于动画插值) */
    public float joinTime;
    /** 形成进度 (0-1) */
    public float formProgress;

    // ===== 临时变量 (PU132 原版为 transient) =====
    private static final Vec2 vec = new Vec2();
    private Interval timer = new Interval(2);

    @Override
    public UnitController controller() {
        return (MonolithSoulAI) super.controller();
    }

    @Override
    public void update() {
        super.update();

        // 避免死亡
        if(timer.get(0, 12f)) contemplate();

        // 如果找到合适的容器，加入
        if(joinTarget != null){
            vec.set(joinTarget)
                .add(Mathf.randomSeedRange(id, 24f), Mathf.randomSeedRange(id + 1, 24f))
                .sub(this);
            vec.addLength(-type.range * 0.8f).limit(type.speed);
            moveAt(vec);
            lookAt(prefRotation());
            join(joinTarget);
        } else if(formTarget != null) { // 否则，寻找合适的形成位置并拾取方块
            vec.set(World.toTile(formTarget.x), World.toTile(formTarget.y))
                .add(Mathf.randomSeedRange(id, 24f), Mathf.randomSeedRange(id + 1, 24f))
                .sub(this);
            vec.addLength(-type.range * 0.8f).limit(type.speed);
            moveAt(vec);
            lookAt(prefRotation());

            if(timer.get(1, 5f)){
                // TODO: PU132 的 monolithWorld.getChunk → 本项目暂无此概念
                // Tile in = formTarget.within(this) ? formTarget : monolithWorld.getChunk(World.toTile(x), World.toTile(y));
                Tile in = formTarget;
                if(in != null){
                    for(int i = 0; i < 3; i++){ // 尝试3次
                        Tile tile = in.block() instanceof CoreBlock.CoreBuild ? in : null; // 简化：只接受核心方块
                        if(tile != null && !forms.contains(tile) && forms.size < 5){
                            form(tile);
                            break;
                        }
                    }
                }
            }
        }
    }

    public void contemplate(){
        // 如果已经实体化，无需寻找
        if(corporeal) return;

        // 如果正在加入或形成且生命值在恢复，不寻找
        float delta = lifeDelta();
        if(joining || (forming && delta > 0f)){
            return;
        }

        float range = type.speed * (health / -delta) / 2f;

        // 寻找最近的单位/建筑作为加入目标
        Unit vesselUnit = Units.closest(team, x, y, range, this::accept);
        Building vesselBuild = Units.findAllyTile(team, x, y, range, this::accept);
        joinTarget = (vesselUnit != null || vesselBuild != null)
            ? (vesselUnit == null ? vesselBuild : vesselBuild == null ? vesselUnit :
                Math.max(dst(vesselUnit) - vesselUnit.hitSize / 2f, 0f) <=
                Math.max(dst(vesselBuild) - vesselBuild.hitSize() / 2f, 0f)
                ? vesselUnit : vesselBuild)
            : null;

        // 如果找不到容器，寻找形成位置
        if(joinTarget == null){
            // TODO: PU132 的 monolithWorld.nearest → 本项目暂无此概念
            // formTarget = monolithWorld.nearest(x, y, range, c -> Math.min(c.monolithTiles.size, 5) * (range * range / dst2(c.centerX, c.centerY)));
            formTarget = null; // 临时简化
        } else {
            formTarget = null;
        }
    }

    public <T extends Teamc & Healthc> boolean accept(T other){
        // TODO: PU132 的 Soul.toSoul → 本项目暂无 Soul 系统
        // Soul soul = Soul.toSoul(other);
        // return soul != null && other.isValid() && soul.acceptSoul(1) >= 1;
        return other.isValid() && other instanceof Unit; // 简化：只接受单位
    }

    public void join(Teamc target){
        if(joining) return;
        joining = true;
        joinTarget = target;
        joinProgress = 0f;
    }

    public void form(Tile tile){
        if(forms.size >= 5) return;
        forms.add(tile);
        forming = true;
        formProgress = 1f;
    }

    public boolean corporeal(){
        return corporeal;
    }

    public boolean joining(){
        return joining;
    }

    public boolean forming(){
        return forming;
    }

    public Seq<Tile> forms(){
        return forms;
    }

    public float formProgress(){
        return formProgress;
    }

    public float joinTime(){
        return joinTime;
    }

    public float ringRotation(){
        return rotation;
    }

    @Override
    public void write(Writes write){
        super.write(write);
        write.bool(corporeal);
        write.bool(joining);
        write.bool(forming);
        write.f(joinProgress);
        write.f(joinTime);
        write.f(formProgress);
        write.i(forms.size);
        for(Tile tile : forms){
            write.i(tile.pos());
        }
    }

    @Override
    public void read(Reads read, byte revision){
        super.read(read, revision);
        corporeal = read.bool();
        joining = read.bool();
        forming = read.bool();
        joinProgress = read.f();
        joinTime = read.f();
        formProgress = read.f();
        int count = read.i();
        forms.clear();
        for(int i = 0; i < count; i++){
            Tile tile = world.tile(read.i());
            if(tile != null) forms.add(tile);
        }
    }
}
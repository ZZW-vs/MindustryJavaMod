package zzw.content.blocks.turrets;

import arc.Core;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Strings;
import arc.util.Time;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.graphics.Pal;
import mindustry.type.StatusEffect;
import mindustry.ui.Bar;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

/**
 * 吸收者炮台 (PU132 unity/world/blocks/defense/turrets/AbsorberTurret 完整移植)
 *
 * <p>牵引光束可吸收敌方子弹 / 单位 / 建筑三类目标:
 * <br>- 子弹: 按帧削减速度与伤害, 速度归零或伤害耗尽即销毁;
 * <br>- 单位: 叠加速度/伤害状态并造成伤害;
 * <br>- 建筑: 直接造成伤害。
 * <br>被吸收的目标会依据其伤害与速度转化为电力产出。</p>
 *
 * <p>★ 完整还原要点 (相对旧版):
 * <br>1. 继承 {@link GenericTractorBeamTurret GenericTractorBeamTurret&lt;Teamc&gt;}, 三类目标原生支持,
 *   不再需要旧版把 Teamc 强转成 Unit 的折衷做法;
 * <br>2. apply() 伤害改用原版 unit.damage(damage) (旧版误用 damageContinuousPierce, 差了约 60 倍);
 * <br>3. 去掉原版不存在的 impulseNet 牵引效果;
 * <br>4. 激光颜色/粗细沿用基类 (monolith 蓝 / 0.4), 不再继承 TractorBeamTurret 的默认值。</p>
 *
 * @author GlennFolker (PU132 原作), ThePythonGuy3 (PU132 原作), 移植: zzw
 */
public class AbsorberTurret extends GenericTractorBeamTurret<Teamc>{
    /** 满效率时的电力产出系数 */
    public float powerProduction = 2.5f;
    /** 每帧对子弹速度的削减系数 */
    public float resistance = 0.4f;
    /** 计算电力产出时对伤害的归一化除数 */
    public float damageScale = 18f;
    /** 对单位/建筑每帧造成的伤害 (0 表示纯吸收不伤害) */
    public float damage = 0f;
    /** 计算电力产出时对速度的归一化除数 */
    public float speedScale = 3.5f;

    /** 吸收单位时附加的状态 */
    public StatusEffect status;

    /** 目标类型开关 */
    public boolean targetBullets, targetUnits, targetBuildings = false;

    /** 建筑查找的临时缓存 (避免每帧分配) */
    private Seq<Building> buildings = new Seq<>();

    public AbsorberTurret(String name){
        super(name);
        outputsPower = true;
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(Stat.basePowerGeneration, powerProduction * 60f, StatUnit.powerSecond);
    }

    @Override
    public void setBars(){
        super.setBars();

        addBar("power", (AbsorberTurretBuild entity) -> new Bar(() ->
            Core.bundle.format("bar.poweroutput",
            Strings.fixed(entity.getPowerProduction() * 60f * entity.timeScale(), 1)),
            () -> Pal.powerBar,
            () -> entity.getPowerProduction() / powerProduction)
        );
    }

    public class AbsorberTurretBuild extends GenericTractorBeamTurretBuild{
        /** 常规索敌: 以自身位置为中心, 搜索范围为 range */
        @Override
        protected void findTarget(){
            findTarget(x, y, range);
        }

        /** 逻辑/玩家控制索敌: 以目标点为圆心, 仅搜索激光末端附近 (原版 laserWidth/2) */
        @Override
        protected void findTarget(Vec2 pos){
            findTarget(pos.x, pos.y, laserWidth / 2f);
        }

        /**
         * 三类目标统一索敌, 取距离最近者。
         *
         * @param x 搜索圆心 X
         * @param y 搜索圆心 Y
         * @param r 搜索半径
         */
        protected void findTarget(float x, float y, float r){
            Teamc tempTarget = null;
            target = null;
            float distance = Float.MAX_VALUE;

            // 1) 子弹: 敌对且可被拦截
            if(targetBullets){
                tempTarget = Groups.bullet
                .intersect(x - r, y - r, r * 2f, r * 2f)
                .min(b -> b.team != team && b.type != null && b.type.hittable, b -> b.dst2(x, y));

                if(tempTarget != null){
                    target = tempTarget;
                    distance = Mathf.dst(x, y, tempTarget.x(), tempTarget.y());
                }
            }

            // 2) 单位: 敌对且未死亡
            if(targetUnits){
                tempTarget = Groups.unit
                .intersect(x - r, y - r, r * 2f, r * 2f)
                .min(b -> b.team != team && !b.dead, b -> b.dst2(x, y));

                if(tempTarget != null){
                    float d = Mathf.dst(x, y, tempTarget.x(), tempTarget.y());
                    if(d < distance){
                        distance = d;
                        target = tempTarget;
                    }
                }
            }

            // 3) 建筑: 敌对且未摧毁
            if(targetBuildings){
                buildings.clear();

                Vars.indexer.eachBlock(null, x, y, r, b -> b.team != team && !b.dead, buildings::add);

                tempTarget = buildings.min(b -> b.dst2(x, y));

                if(tempTarget != null){
                    float d = Mathf.dst(x, y, tempTarget.x(), tempTarget.y());
                    if(d < distance) target = tempTarget;
                }
            }
        }

        /** 逐帧处理被吸收的目标 (原版逻辑, 无额外牵引) */
        @Override
        protected void apply(){
            // 子弹: 削减速度与伤害, 归零即销毁
            if(target instanceof Bullet bullet){
                bullet.vel.setLength(Math.max(bullet.vel.len() - resistance * strength, 0f));
                bullet.damage = Math.max(bullet.damage - (resistance / 2f) * strength * Time.delta, 0f);

                if(bullet.vel.isZero(0.01f) || bullet.damage <= 0f){
                    bullet.remove();
                }
            }

            // 单位: 附加状态并造成伤害 (原版为 damage(damage), 非 damageContinuousPierce)
            if(target instanceof Unit unit && damage > 0f){
                if(status != null) unit.apply(status);
                unit.damage(damage);
            }

            // 建筑: 直接造成伤害
            if(target instanceof Building building && damage > 0f){
                building.damage(damage);
            }
        }

        /** 依目标伤害与速度换算电力产出 (原版公式) */
        @Override
        public float getPowerProduction(){
            if(target == null) return 0f;

            if(target instanceof Bullet bullet){
                if(bullet.type == null) return 0f;

                return (bullet.type.damage / damageScale) * (bullet.vel.len() / speedScale) * powerProduction;
            }

            if(target instanceof Unit unit){
                if(unit.type == null) return 0f;

                return (unit.type.dpsEstimate / damageScale) * (unit.vel.len() / speedScale) * powerProduction;
            }

            return 0f;
        }
    }
}

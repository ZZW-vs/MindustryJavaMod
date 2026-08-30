package zzw.content.mechanics.torque.blocks.power;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import zzw.content.mechanics.torque.blocks.GraphBlock;
import zzw.content.mechanics.torque.modules.GraphFluxModule;

import static mindustry.Vars.*;

/**
 * 磁体 (PU132 unity.world.blocks.power.Magnet 完整移植)
 *
 * <p>磁力系统的核心: 按朝向输出基础磁通量 (电力满意度调谐, 电磁铁才有电力消费),
 * 永磁体 (定子) 无电力时也输出全额磁通。磁通在网络中按对数权重合成 (FluxGraph)。</p>
 *
 * <p>updatePost 副作用: 磁通范围内偏转子弹 (含经验球, 转子发电机的物理玩法核心),
 * 偏转力与 DPS 成反比 (重弹难偏), 经验球受到 5 倍偏转力。</p>
 */
public class Magnet extends GraphBlock{
    /** 四个朝向的贴图 (name+1..4) */
    public final TextureRegion[] regions = new TextureRegion[4];

    public Magnet(String name){
        super(name);

        rotate = solid = true;
    }

    @Override
    public void load(){
        super.load();

        for(int i = 0; i < 4; i++) regions[i] = Core.atlas.find(name + (i + 1));
    }

    public class MagnetBuild extends GraphBuild{
        @Override
        public void updatePre(){
            // 电力满意度调谐磁通 (PU132 原版; 无电力消费的永磁体不调谐, 保持基础磁通)
            // ★ v155.4 适配: 未接入电网时 power.graph 为 null, 直接调聚会 NPE
            //   (NPE 会让建筑 update 中止 → 磁体完全失效, 子弹偏转也停)
            if(hasPower && power != null && power.graph != null){
                flux().mulFlux(power.graph.getSatisfaction());
            }
        }

        @Override
        public void draw(){
            Draw.rect(regions[rotation], x, y);

            drawTeamTop();
        }

        @Override
        public void updatePost(){
            // 磁通范围内偏转子弹 (PU132 原版物理)
            float f = flux().flux();

            Groups.bullet.intersect(x - f * 2f, y - f * 2f, f * 4f, f * 4f, bullet -> {
                if(bullet.type == null) return;

                // 经验球判定 (PU132 ExpOrb): 本项目经验球为直接分配无子弹实体, 恒 false
                boolean isOrb = false;

                if(bullet.type.hittable || isOrb){
                    float dx = bullet.x - x;
                    float dy = bullet.y - y;
                    float dis = Mathf.sqrt(dx * dx + dy * dy);

                    if(dis < f * 2f){
                        // invmass * forcemag (PU132 原版公式)
                        float mul = 1f / Math.max(1f, bullet.type.estimateDPS() / 10f) * (isOrb ? 5f : 1f) * Time.delta * 0.1f * f / (8f + dis);

                        bullet.vel.x += mul * arc.math.geom.Geometry.d4x(rotation);
                        bullet.vel.y += mul * arc.math.geom.Geometry.d4y(rotation);
                    }
                }
            });
        }

        /** 当前磁通模块 */
        protected GraphFluxModule flux(){
            return gms().flux();
        }
    }
}
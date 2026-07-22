package zzw.content.exp;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.meta.*;

import static arc.Core.atlas;
import static mindustry.Vars.*;

/**
 * 经验卸载器 - 从相邻的经验储罐/经验塔主动抽取经验, 沿朝向发射经验球到传送带
 *
 * 参考 vanilla v158.1 Unloader 的"从相邻方块抽取"机制
 * 与 exp-output (ExpHub) 的区别:
 *   - exp-output: 链接经验炮塔, 抽取炮塔击杀经验的 30% 分成 (原版 PU_V8 行为)
 *   - exp-unloader: 从相邻经验储罐/塔直接抽取经验, 不需要链接配置, 朝向决定发射方向
 */
public class ExpUnloader extends ExpTank {
    public float reloadTime = 30f;
    public Effect transferEffect = UnityFx.expLaser;
    public TextureRegion laser, laserEnd;
    private final Seq<Building> tmp = new Seq<>();

    public ExpUnloader(String name){
        super(name);
        rotate = true;
        solid = false;
        schematicPriority = -16;
    }

    @Override
    public void init(){
        super.init();
        clipSize = Math.max(clipSize, 60f);
    }

    @Override
    public void load(){
        super.load();
        laser = atlas.find("create-exp-laser");
        laserEnd = atlas.find("create-exp-laser-end");
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(Stat.speed, 60f / reloadTime, StatUnit.itemsSecond);
    }

    public class ExpUnloaderBuild extends ExpTankBuild {
        public float reload = reloadTime;

        @Override
        public void updateTile(){
            reload += edelta();

            // 主动从相邻的 ExpHolder (储罐/经验塔) 抽取经验
            // 不从炮台 (ExpTurret) 抽取 - 炮台需要经验升级, 由其 incExp 主动 push
            if(exp < expCapacity){
                int need = expCapacity - exp;
                for(int i = 0; i < proximity.size; i++){
                    Building b = proximity.get(i);
                    // 跳过炮台 (ExpTurretBuild), 只从储罐/塔抽取
                    if(b instanceof ExpHolder e && !(b instanceof ExpTurret.ExpTurretBuild)){
                        int got = e.unloadExp(need);
                        if(got > 0){
                            exp += got;
                            need -= got;
                            transferEffect.at(x, y, 0f, Color.white, b);
                            if(need <= 0) break;
                        }
                    }
                }
            }

            // 每 reloadTime tick 发射一个经验球 (朝向方向)
            if(reload >= reloadTime && ExpOrbs.orbs(exp) > 0){
                int a = handleExp(-ExpOrbs.oneOrb(exp));
                if(a < 0) ExpOrbs.dropExp(x, y, rotation * 90f, 4f, -a);
                reload = 0f;
            }
        }

        @Override
        public void draw(){
            Draw.rect(region, x, y);
            Draw.color(UnityPal.exp, Color.white, Mathf.absin(20, 0.6f));
            Draw.alpha(expf() * 0.6f);
            Draw.rect(expRegion, x, y);
            Draw.color();
            Draw.rect(topRegion, x, y, rotdeg());

            // 绘制抽取激光 (到正在被抽取的相邻方块)
            drawExtractLasers();
        }

        protected void drawExtractLasers(){
            if(Mathf.zero(Renderer.laserOpacity)) return;
            Draw.z(Layer.power + 1f);
            Draw.alpha(Renderer.laserOpacity * (Mathf.absin(5f, 0.3f) + 0.1f));
            for(int i = 0; i < proximity.size; i++){
                Building b = proximity.get(i);
                if(b instanceof ExpHolder e && !(b instanceof ExpTurret.ExpTurretBuild) && e.getExp() > 0){
                    Tmp.v2.set(b);
                    Tmp.v1.set(Tmp.v2).sub(this).nor().scl(size * tilesize / 2f);
                    Tmp.v2.sub(Tmp.v1);
                    Tmp.v1.add(this);
                    Drawf.laser(laser, laserEnd, Tmp.v1.x, Tmp.v1.y, Tmp.v2.x, Tmp.v2.y, 0.3f);
                }
            }
            Draw.reset();
        }

        @Override
        public void drawLight(){
            super.drawLight();
            Drawf.light(x, y, lightRadius * expf(), UnityPal.exp, 0.5f);
        }

        @Override
        public boolean acceptOrb(){
            return false;  // 不接受经验球 (只发射)
        }

        @Override
        public int handleTower(int amount, float angle){
            return 0;  // 不接受经验塔传输
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.f(reload);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            reload = read.f();
        }
    }
}

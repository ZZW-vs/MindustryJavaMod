package zzw.content.mechanics.torque.blocks.power;

import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import zzw.content.mechanics.torque.blocks.*;

import static arc.Core.*;

/**
 * 蒸汽活塞 (PU160 unity.world.blocks.power.SteamPiston 移植)
 * <p>
 * 热量网络驱动的直线做功部件: 当内部温度超过 {@code minTemp} 时,
 * 消耗水, 把热量转换为推力, 推动贴在前方的 {@link FlyWheel 飞轮} 旋转。
 * 活塞只向正前方 ({@code front()}) 的飞轮挂接。
 * <p>
 * 移植说明: 原版用 {@code heatNode().getTemp()/addHeatEnergy()} 与
 * {@code flywheel.torqueNode().baseForce}, 这里分别改为本项目的
 * {@code heat().getTemp() / heat().heat} 以及直接累加到飞轮扭矩力。
 * 参数取自 参考/PU160反编译/.../YoungchaBlocks.java L485-497。
 */
public class SteamPiston extends GraphBlock{
    /** 开始做功温度 (K, 100°C) */
    public float minTemp = 373.15f;
    /** 达到最大效率温度 (K, 400°C) */
    public float maxTemp = 673.15f;

    /** 4 方向本体贴图 (rot1~rot4) */
    final TextureRegion[] sprite = new TextureRegion[4];
    TextureRegion base, liquid, arm, pivot;
    /** 排气粒子计时器槽位 */
    final int smokeTimer = timers++;

    public SteamPiston(String name){
        super(name);

        rotate = true;
        solid = true;
    }

    @Override
    public void load(){
        super.load();

        base = atlas.find(name + "-base");
        liquid = atlas.find(name + "-liquid");
        arm = atlas.find(name + "-arm");
        pivot = atlas.find(name + "-pivot");
        for(int i = 0; i < 4; i++) sprite[i] = atlas.find(name + "-rot" + (i + 1));
    }

    public class SteamPistonBuild extends GraphBuild{
        /** 当前挂接的飞轮 */
        FlyWheel.FlyWheelBuild flywheel;
        /** 活塞指向飞轮的单位向量 */
        final Vec2 flywheeldir = new Vec2();
        boolean initConnect;
        /** 输出推力 (0~1), 供飞轮读取 */
        public float pushForce;
        /** 剩余做功时间 */
        float rwater;

        @Override
        public boolean shouldConsume(){
            return super.shouldConsume() && heat().getTemp() > minTemp;
        }

        @Override
        public boolean acceptLiquid(Building source, Liquid liquid){
            return liquids.get(liquid) < liquidCapacity - 0.001f;
        }

        @Override
        public void updateTile(){
            super.updateTile();

            if(!initConnect){
                tryConnect();
                initConnect = true;
            }
            // ★ 未连接时每帧重试: 建造顺序 / 旋转变化可能导致首次连接失败 (原版仅依赖 proximity 更新)
            if(flywheel == null){
                tryConnect();
            }
            if(flywheel == null){
                pushForce = 0f;
                return;
            }

            float temp = heat().getTemp();
            if(temp <= minTemp){
                pushForce = 0f;
                return;
            }

            float eff = Mathf.clamp(Mathf.map(temp, minTemp, maxTemp, 0f, 1f));
            // 挂点相对活塞处于"回程"时不做功 (只有推程输出)
            boolean pulling = flywheeldir.dot(flywheel.attachY - y, -(flywheel.attachX - x)) > 0f;
            if(pulling){
                pushForce = 0f;
                if(timer(smokeTimer, 5f) && liquids.currentAmount() > 1f){
                    float rand = Mathf.random() > 0.5f ? -1f : 1f;
                    Fx.fuelburn.at(x + flywheeldir.y * 8f * rand, y - flywheeldir.x * 8f * rand);
                }
            }else{
                if(rwater <= 0f && canConsume()){
                    consume();
                    heat().heat -= eff * 150f;
                    rwater += 10f;
                }
                if(rwater > 0f){
                    rwater -= eff * delta();
                    // ★ 对齐原版: 目标推力用 timeScale() 而非 delta()
                    pushForce += (timeScale() * eff - pushForce) * 0.1f * delta();
                }else{
                    pushForce = 0f;
                }
            }
        }

        @Override
        public void onDelete(){
            if(flywheel != null){
                flywheel.connected.remove(this);
                setFlywheel(null);
            }
        }

        @Override
        public void onDestroyed(){
            onDelete();

            super.onDestroyed();
        }

        @Override
        public void proxUpdate(){
            tryConnect();
        }

        /** 尝试挂接到正前方的飞轮 (只接受同列/同行的飞轮) */
        public void tryConnect(){
            Building fb = front();
            if(fb instanceof FlyWheel.FlyWheelBuild fwb && (fb.x == x || fb.y == y)){
                if(flywheel == fwb) return;
                if(flywheel != null) flywheel.connected.remove(this);
                setFlywheel(fwb);
                flywheel.connected.add(this);
                return;
            }

            if(flywheel == null) return;

            flywheel.connected.remove(this);
            setFlywheel(null);
        }

        public void setFlywheel(FlyWheel.FlyWheelBuild fw){
            flywheel = fw;
            if(fw != null) flywheeldir.set(fw.x - x, fw.y - y).nor();
        }

        @Override
        public void draw(){
            Draw.rect(base, x, y, 0f);
            Drawf.liquid(liquid, x, y, liquids.currentAmount() / liquidCapacity, liquids.current().color);
            Draw.rect(sprite[rotation], x, y, 0f);
            drawTeamTop();

            if(flywheel != null){
                float r = 8f;
                float yd = flywheeldir.dot(flywheel.attachX - x, flywheel.attachY - y);
                float xd = Math.abs(flywheeldir.dot(flywheel.attachY - y, -(flywheel.attachX - x)));
                float d = Math.max(0f, yd - Mathf.sqrt(Math.max(0f, r * r - xd * xd)));
                float px = x + flywheeldir.x * d;
                float py = y + flywheeldir.y * d;

                Draw.z(35.1f);
                Lines.stroke(4f);
                Lines.line(arm, x + flywheeldir.x * 10f, y + flywheeldir.y * 10f, px, py, false);
                Lines.stroke(3f);
                Lines.line(arm, flywheel.attachX, flywheel.attachY, px, py, false);
                Draw.rect(pivot, px, py, 0f);
                Draw.rect(pivot, flywheel.attachX, flywheel.attachY, 0f);
            }
        }
    }
}

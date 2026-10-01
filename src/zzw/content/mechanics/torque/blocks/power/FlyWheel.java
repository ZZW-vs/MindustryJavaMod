package zzw.content.mechanics.torque.blocks.power;

import arc.graphics.g2d.*;
import arc.math.*;
import arc.struct.*;
import mindustry.graphics.*;
import zzw.content.mechanics.torque.blocks.*;

import static arc.Core.*;

/**
 * 飞轮 (PU160 unity.world.blocks.power.FlyWheel 移植)
 * <p>
 * 大型储能传动部件: 本身是一个高惯量的扭矩节点, 由贴在它前方的
 * {@link SteamPiston 蒸汽活塞} 推动旋转。活塞每帧把推力累加到飞轮的
 * 扭矩力上, 飞轮再把力注入扭矩网络, 从而驱动整个传动系统。
 * <p>
 * 移植说明: 原版使用旧一代图框架 (GenericGraphBlock + TorqueGraphNode.baseForce),
 * 这里改写为本项目架构 —— 直接给 {@code torque().force} 赋值,
 * 由 {@code TorqueGraph.updateDirect()} 汇总。参数取自
 * 参考/PU160反编译/unity/content/blocks/YoungchaBlocks.java L473-484。
 */
public class FlyWheel extends GraphBlock{
    /** 底面 */
    TextureRegion base;
    /** 传动轴 (随方块朝向固定) */
    TextureRegion shaft;
    /** 轮盘 (随转速旋转) */
    TextureRegion wheel;
    /** 顶面覆盖层 */
    TextureRegion top;

    public FlyWheel(String name){
        super(name);

        rotate = true;
        solid = true;
    }

    @Override
    public void load(){
        super.load();

        base = atlas.find(name + "-base");
        shaft = atlas.find(name + "-shaft");
        wheel = atlas.find(name + "-wheel");
        top = atlas.find(name + "-top");
    }

    public class FlyWheelBuild extends GraphBuild{
        /** 已挂接的蒸汽活塞 (由活塞在连接/断开时增删) */
        public final OrderedSet<SteamPiston.SteamPistonBuild> connected = new OrderedSet<>();
        /** 连杆挂点 (随飞轮旋转而绕圈, 供活塞画连杆) */
        public float attachX, attachY;

        @Override
        public void created(){
            super.created();

            attachX = x;
            attachY = y;
        }

        @Override
        public void updatePost(){
            // 原版: baseForce = Σ(活塞推力 * 0.5), getForce() = baseForce * maxTorque * speedRatio
            float bf = 0f;
            for(var spb : connected) bf += spb.pushForce * 0.5f;

            float speedRatio = Mathf.clamp(1f - torque().getNetwork().lastVelocity / 10f, 0f, 1f);
            torque().force = enabled ? bf * 30f * speedRatio : 0f;

            float rot = torque().getRotation();
            attachX = Mathf.sinDeg(-rot) * 4f + x;
            attachY = Mathf.cosDeg(rot) * 4f + y;
        }

        @Override
        public void onDelete(){
            for(var spb : connected) spb.setFlywheel(null);
            connected.clear();
        }

        @Override
        public void onDestroyed(){
            onDelete();

            super.onDestroyed();
        }

        @Override
        public void draw(){
            float graphRot = torque().getRotation();
            float fixedRot = (rotdeg() + 90f) % 180f - 90f;

            Draw.rect(base, x, y);
            Draw.rect(shaft, x, y, fixedRot);
            Drawf.spinSprite(wheel, x, y, graphRot);
            Draw.rect(top, x, y);

            drawTeamTop();
        }
    }
}

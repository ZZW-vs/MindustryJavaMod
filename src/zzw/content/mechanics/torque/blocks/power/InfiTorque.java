package zzw.content.mechanics.torque.blocks.power;

import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import arc.util.io.Reads;
import arc.util.io.Writes;
import zzw.content.mechanics.torque.modules.GraphTorqueModule;

/**
 * 无限扭矩源 (沙盒) —— 在 {@link TorqueGenerator 扭矩发生器} 基础上增加"目标转速"设置。
 * <p>
 * 默认 (未输入 / 输入为空 / 输入非正数) 时, 电机始终输出满力矩 → 转速无限加速。
 * 玩家点击方块打开配置框后, 可输入一个目标转速 (rps, 转/秒); 之后转速会被限制在目标值:
 * 低于目标时满力矩加速, 达到目标后停止施力并由 {@link zzw.content.mechanics.torque.graph.TorqueGraph}
 * 在积分后钳制转速, 从而稳定维持在目标附近。
 * <p>
 * 调速原理: 无限扭矩源的力矩极大 (maxSpeed=999999 → 每帧转速增量远超目标),
 * 单纯 bang-bang (到目标就断力) 会在单帧内把速度冲过目标, 之后摩擦 (0.001) 又拉不回来,
 * 目标形同虚设。因此在 {@link zzw.content.mechanics.torque.graph.TorqueGraph} 里对钳制做了支持:
 * 本方块把目标写入 {@code setSpeedLimit(...)}, 扭矩图积分后立即钳制, 保证本帧累计转角与显示转速一致。
 * <p>
 * 转速换算: 面板显示 rps = {@code lastVelocity / 6} (见 GraphTorqueModule.display 与转角累计),
 * 故 目标内部速度 = 目标 rps × 6。
 */
public class InfiTorque extends TorqueGenerator{
    public InfiTorque(String name){
        super(name);

        // 允许点击方块打开配置界面, 接收 Float 类型的转速配置
        configurable = true;
        config(Float.class, (InfiTorqueBuild build, Float value) -> build.targetRps = value);
    }

    public class InfiTorqueBuild extends TorqueGeneratorBuild{
        /** 目标转速 (rps); <= 0 表示不限制, 一直加速 */
        public float targetRps = -1f;

        @Override
        protected float generateTorque(){
            // 未设置目标时始终输出满力矩 (无限加速)
            return 1f;
        }

        @Override
        public void updatePre(){
            GraphTorqueModule<?> tGraph = torque();
            if(tGraph == null || tGraph.getNetwork() == null){
                return;
            }

            // 面板显示 rps = lastVelocity / 6, 故 目标内部速度 = 目标 rps × 6; 未设置时为负 → 不限制
            float target = targetRps > 0f ? targetRps * 6f : -1f;

            // 把目标写入扭矩图, 积分后立即钳制 (无限力矩下仅靠断力无法压住转速)
            tGraph.setSpeedLimit(target);

            // 已到达目标转速则停止施力, 否则满力矩加速; 未设置目标时一直加速
            boolean reached = target >= 0f && tGraph.getNetwork().lastVelocity >= target;
            tGraph.setMotorForceMult(reached ? 0f : 1f);
        }

        @Override
        public void buildConfiguration(Table table){
            TextField field = table.field("", text -> {}).width(120f).get();
            field.setMessageText("转速 (rps)");
            // 回填当前配置
            field.setText(targetRps > 0f ? trim(targetRps) : "");

            table.button("确定", () -> {
                String s = field.getText().trim();
                float v = -1f;
                if(!s.isEmpty()){
                    try{
                        v = Float.parseFloat(s);
                    }catch(NumberFormatException ignored){
                        v = -1f;
                    }
                }
                configure(v);
            }).size(64f, 34f).padLeft(4f);

            table.button("清除", () -> {
                field.clearText();
                configure(-1f);
            }).size(64f, 34f).padLeft(4f);

            table.row();
            table.label(() -> targetRps > 0f ? "目标: " + trim(targetRps) + " rps" : "无限加速")
                .padTop(4f).color(arc.graphics.Color.lightGray);
        }

        @Override
        public Float config(){
            return targetRps;
        }

        @Override
        public void configured(mindustry.gen.Unit builder, Object value){
            if(value instanceof Float f){
                targetRps = f;
            }
        }

        /** 去掉多余的尾随 0, 便于显示 */
        private String trim(float v){
            return arc.util.Strings.autoFixed(v, 2);
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.f(targetRps);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            targetRps = read.f();
        }
    }
}
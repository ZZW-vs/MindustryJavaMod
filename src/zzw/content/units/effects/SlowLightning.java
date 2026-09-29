package zzw.content.units.effects;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Position;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.game.Team;
import mindustry.graphics.Layer;
import zzw.util.UnityUtils;

/**
 * 慢速节点式闪电 (PU132 unity.entities.effects.SlowLightning 移植)。
 *
 * <p>PU132 的原实现是完整的 {@code Entityc} 实体 (由 ECS 注册表托管),
 * 本项目为降低耦合, 改为<b>自包含对象</b>: 由持有者 (如
 * {@code EndGameTurretBuild}) 放入 {@link Seq} 中, 每帧手动调用
 * {@link #update()} 与 {@link #draw()}, 生命周期结束后由持有者移除。</p>
 *
 * <p>结构: 一条闪电由若干 {@link Node} 组成, 节点像"树枝"一样从起点
 * 逐段生长 ({@code nodeTime} 控制生长速度, 值越大传播越慢), 生长完成
 * 后按 {@code splitChance} 概率分叉 (分叉会让子节点间距更大, 形成
 * "主枝 + 少量支叉" 的形状)。每个节点记录其父节点位置, 绘制与实际
 * 伤害都发生在 "父节点 → 本节点" 这一段上。</p>
 *
 * <p>伤害: {@code continuous} 为 true 时每帧沿所有可见线段施加线伤害
 * (PU132 为每 5 tick 结算一次; 本项目按需求改为每帧结算, 且总伤害
 * 固定为 {@code damage}, 由参与节点数均摊, 因此"整条闪电每帧约
 * {@code damage} 点" —— 默认 2000)。</p>
 *
 * <p>★ v132 → v158 适配要点:</p>
 * <ul>
 *   <li>{@code unity.util.Utils.collideLineRawEnemy} → 本项目的
 *       {@link UnityUtils#collideLineDamageOnly(Team, float, float, float, float, float)}
 *       (无子弹重载);</li>
 *   <li>去掉 ECS 相关 (Groups.add / Entityc / remove 回调), 改为
 *       {@code removed} 标记 + 持有者清理;</li>
 *   <li>颜色插值沿用 PU132 的 {@code colorFrom → colorTo}</li>
 * </ul>
 *
 * @author GlennFolker (原作), 移植: zzw
 */
public class SlowLightning{

    // ===== 可配置参数 (与 PU132 SlowLightningType 默认值对齐) =====

    /** 起始颜色。 */
    public Color colorFrom = Color.white;
    /** 终止颜色。 */
    public Color colorTo = Color.black;
    /** 颜色渐变耗时 (帧)。 */
    public float colorTime = 32f;
    /** 分叉概率 (0~1)。 */
    public float splitChance = 0.045f;
    /** 单个节点长度 (世界单位)。 */
    public float nodeLength = 50f;
    /** 单个节点生长耗时 (帧) —— 越大传播越慢。 */
    public float nodeTime = 5f;
    /** 最大延伸距离 (世界单位)。 */
    public float range = 810f;
    /** 普通节点随机偏转角 (度)。 */
    public float randSpacing = 20f;
    /** 分叉节点随机偏转角 (度)。 */
    public float splitRandSpacing = 60f;
    /** 线宽。 */
    public float lineWidth = 2f;
    /** 存活时长 (帧), 之后淡出移除。 */
    public float lifetime = 120f;
    /** 朝目标偏转的最大/最小角速度 (度/帧)。 */
    public float maxRotationSpeed = 22f, minRotationSpeed = 1.5f;
    /** 偏转生效距离 (超过则不再朝目标偏转)。 */
    public float rotationDistance = 600f;
    /** 是否开启连续帧伤。 */
    public boolean continuous = true;

    /**
     * 整条闪电每帧造成的总伤害 (由参与节点均摊)。
     *
     * <p>默认 2000 —— 与需求 "帧伤约 2000" 对齐。</p>
     */
    public float damage = 2000f;

    // ===== 运行时状态 =====

    /** 阵营 (跳过友方)。 */
    public Team team;
    /** 起点。 */
    public float x, y;
    /** 目标 (用于朝目标偏转, 可为 null)。 */
    public Position target;
    /** 已存活时间 (帧)。 */
    public float time = 0f;
    /** 是否已结束 (等待持有者移除)。 */
    public boolean removed = false;

    /** 全部节点。 */
    public final Seq<Node> nodes = new Seq<>();

    /** 单个节点: 记录自身位置与其父节点位置。 */
    public static class Node{
        /** 本节点终点。 */
        public float x, y;
        /** 父节点 (本段起点)。 */
        public float px, py;
        /** 本段方向 (度)。 */
        public float rotation;
        /** 距起点累计距离。 */
        public float dist;
        /** 生长进度 (0~1)。 */
        public float time = 0f;
        /** 颜色进度 (0~1)。 */
        public float colorProgress = 0f;
        /** 是否已生长完毕。 */
        public boolean grown = false;
    }

    /**
     * 创建并在内部初始化一条闪电。
     *
     * @param team   阵营
     * @param x,y    起点
     * @param rotation 初始方向 (度)
     * @param target 偏转目标 (可为 null)
     */
    public void create(Team team, float x, float y, float rotation, Position target){
        this.team = team;
        this.x = x;
        this.y = y;
        this.target = target;
        this.time = 0f;
        this.removed = false;
        nodes.clear();

        // 根节点: 就是起点, 已生长完毕 (作为第一段的父节点)
        Node root = new Node();
        root.x = root.px = x;
        root.y = root.py = y;
        root.rotation = rotation;
        root.dist = 0f;
        root.time = 1f;
        root.grown = true;
        nodes.add(root);

        // 立刻生长第一段
        end(root);
    }

    /** 每帧更新: 推进生长 / 颜色 / 帧伤 / 生命周期。 */
    public void update(){
        time += Time.delta;
        if(time >= lifetime){
            removed = true;
            return;
        }

        // 推进每个节点的颜色渐变与生长
        for(int i = 0; i < nodes.size; i++){
            Node n = nodes.get(i);
            n.colorProgress = Mathf.approachDelta(n.colorProgress, 1f, Time.delta / colorTime);

            if(!n.grown){
                n.time += Time.delta / nodeTime;
                if(n.time >= 1f){
                    n.time = 1f;
                    n.grown = true;
                    end(n);
                }
            }
        }

        // 连续帧伤: 每帧沿所有线段施加伤害, 总伤害 = damage (按节点数均摊)
        if(continuous && nodes.size > 0){
            float per = damage / nodes.size;
            for(int i = 0; i < nodes.size; i++){
                Node n = nodes.get(i);
                if(n.time <= 0f) continue;
                // 只对已显现的部分施加伤害
                float ex = Mathf.lerp(n.px, n.x, n.time), ey = Mathf.lerp(n.py, n.y, n.time);
                UnityUtils.collideLineDamageOnly(team, per, n.px, n.py, ex, ey);
            }
        }
    }

    /**
     * 让一个已生长完毕的节点继续生长出下一段 (可能分叉)。
     *
     * @param node 已生长完毕的父节点
     */
    protected void end(Node node){
        // 已达最大距离 → 不再延伸
        if(node.dist >= range) return;

        // 分叉概率: 分叉出 2 段, 否则 1 段
        boolean split = Mathf.chance(splitChance);
        int count = split ? 2 : 1;

        for(int i = 0; i < count; i++){
            float r = Mathf.range(split ? splitRandSpacing : randSpacing);
            float rot = node.rotation + r;

            // 朝目标偏转 (距离越近偏得越快)
            if(target != null){
                float tar = Angles.angle(node.x, node.y, target.getX(), target.getY());
                float spd = Mathf.lerp(minRotationSpeed, maxRotationSpeed,
                    Mathf.clamp(Mathf.dst(node.x, node.y, target.getX(), target.getY()) / rotationDistance));
                rot = Angles.moveToward(rot, tar, spd * nodeTime);
            }

            Node n = new Node();
            n.px = node.x;
            n.py = node.y;
            n.rotation = rot;
            n.x = node.x + Angles.trnsx(rot, nodeLength);
            n.y = node.y + Angles.trnsy(rot, nodeLength);
            n.dist = node.dist + nodeLength;
            n.time = 0f;
            nodes.add(n);
        }
    }

    /** 绘制闪电 (调用方需在其所在图层自行管理 Draw 状态)。 */
    public void draw(){
        float fin = 1f - Mathf.curve(time, lifetime * 0.6f, lifetime);
        float w = lineWidth * fin;
        if(w <= 0.01f) return;

        float z = Draw.z();
        Draw.z(Layer.effect);
        Lines.stroke(w);

        for(int i = 0; i < nodes.size; i++){
            Node n = nodes.get(i);
            if(n.time <= 0f) continue;

            float ex = Mathf.lerp(n.px, n.x, n.time), ey = Mathf.lerp(n.py, n.y, n.time);
            Draw.color(colorFrom, colorTo, n.colorProgress);
            Lines.line(n.px, n.py, ex, ey);
        }

        Draw.color();
        Draw.z(z);
    }
}
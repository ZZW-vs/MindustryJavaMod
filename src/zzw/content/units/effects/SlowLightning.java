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
import mindustry.graphics.Pal;
import zzw.util.UnityUtils;

/**
 * 慢速节点式闪电 (PU132 {@code unity.entities.effects.SlowLightning} 移植)。
 *
 * <p>PU132 的原实现是完整的 {@code Entityc} 实体 (由 ECS 注册表托管),
 * 本项目为降低耦合, 改为<b>自包含对象</b>: 由持有者 (如
 * {@code EndGameTurretBuild}) 放入 {@link Seq} 中, 每帧手动调用
 * {@link #update()} 与 {@link #draw()}, 生命周期结束后由持有者移除。</p>
 *
 * <p>结构: 一条闪电由若干 {@link Node} 组成, 节点像"树枝"一样从起点
 * 逐段生长 ({@code nodeTime} 控制生长速度, 值越大传播越慢), 生长完成
 * 后按 {@code splitChance} 概率分叉。每个节点记录其父节点位置, 绘制与
 * 实际伤害都发生在 "父节点 → 本节点" 这一段上。</p>
 *
 * <p><b>★ 形态关键 (为什么之前是笔直的):</b></p>
 * <p>PU132 的弯曲感来自两点, 二者缺一不可 ——</p>
 * <ol>
 *   <li><b>rotRand 反向相关抖动</b>: 子节点的基准角不是父节点的
 *       {@code rotation}, 而是 {@code parent.rotation + parent.rotRand},
 *       而 {@code rotRand} 又按 {@code -parent.rotRand + (-r + parent.rotRand) * rand()}
 *       递推。这个"负反馈"让折线在目标方向两侧来回摆且不会无限漂移,
 *       才是闪电那种锯齿状手感;</li>
 *   <li><b>朝目标偏转不能乘 nodeTime</b>: 原版每段最多纠偏
 *       {@code maxRotationSpeed} (22°) 一次; 若乘上 {@code nodeTime}
 *       (旧实现写成 {@code spd * nodeTime}, 最大 110°), 每段都会被强行
 *       拧回目标方向, 折线被"拉直"成一根光柱。</li>
 * </ol>
 *
 * <p>伤害: {@code continuous} 为 true 时每帧沿所有可见线段施加线伤害
 * (PU132 为每 5 tick 结算一次; 本项目按需求改为每帧结算, 且总伤害
 * 固定为 {@code damage}, 由参与节点数均摊 —— 默认 2000)。</p>
 *
 * <p>★ v132 → v160 适配要点:</p>
 * <ul>
 *   <li>{@code unity.util.Utils.collideLineRawEnemy} → 本项目的
 *       {@link UnityUtils#collideLineDamageOnly(Team, float, float, float, float, float)}
 *       (无子弹重载);</li>
 *   <li>{@code Utils.findLaserLength} (吸收激光的墙体截断) 未移植, 仅按
 *       {@code range} 截断;</li>
 *   <li>去掉 ECS 相关 (Groups.add / Entityc / remove 回调), 改为
 *       {@code removed} 标记 + 持有者清理;</li>
 *   <li>随机数沿用 PU132 的种子序列 ({@code Mathf.randomSeed}), 保证同一
 *       颗种子生成同一条闪电。</li>
 * </ul>
 *
 * @author GlennFolker (原作), 移植: zzw
 */
public class SlowLightning{

    /** PU132 {@code SlowLightningType.maxNodes}: 单条闪电的节点数上限。 */
    public static final int maxNodes = 60;

    /** 全局种子自增计数器 (PU132 {@code SlowLightningType.seed})。 */
    private static long seedCounter = 1L;

    // ===== 可配置参数 (与 PU132 SlowLightningType 默认值对齐) =====

    /** 起始颜色。 */
    public Color colorFrom = Color.white;
    /** 终止颜色。 */
    public Color colorTo = Pal.lancerLaser;
    /** 颜色渐变耗时 (帧)。 */
    public float colorTime = 32f;
    /** 淡出耗时 (帧)。 */
    public float fadeTime = 20f;
    /** 分叉概率 (0~1)。 */
    public float splitChance = 0.035f;
    /** 单个节点长度 (世界单位)。 */
    public float nodeLength = 50f;
    /** 单个节点生长耗时 (帧) —— 越大传播越慢。 */
    public float nodeTime = 3f;
    /** 最大延伸距离 (世界单位)。 */
    public float range = 150f;
    /** 普通节点随机偏转角 (度)。 */
    public float randSpacing = 20f;
    /** 分叉节点随机偏转角 (度)。 */
    public float splitRandSpacing = 60f;
    /** 线宽。 */
    public float lineWidth = 2f;
    /** 存活时长 (帧), 之后淡出移除。 */
    public float lifetime = 120f;
    /** 朝目标偏转的最大/最小角速度 (度/段)。 */
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
    /** 初始方向 (度)。 */
    public float rotation;
    /** 目标 (用于朝目标偏转, 可为 null)。 */
    public Position target;
    /** 已存活时间 (帧)。 */
    public float time = 0f;
    /** 已延伸的最远距离 (世界单位)。 */
    public float distance = 0f;
    /** 本次闪电的随机种子。 */
    public long seed = 1L;
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
        /**
         * 方向抖动余量 (PU132 {@code SlowLightningNode.rotRand})。
         * <p>子节点的基准角 = {@code parent.rotation + parent.rotRand},
         * 用负反馈保证折线在目标方向附近摆动而不漂移。</p>
         */
        public float rotRand;
        /** 距起点累计距离。 */
        public float dist;
        /** 生长进度 (0~1)。 */
        public float time = 0f;
        /** 颜色进度 (0~1)。 */
        public float colorProgress = 0f;
        /** 已生长完毕 (不再延伸)。 */
        public boolean ended = false;
    }

    /**
     * 创建并在内部初始化一条闪电。
     *
     * @param team     阵营
     * @param x,y      起点
     * @param rotation 初始方向 (度)
     * @param target   偏转目标 (可为 null)
     */
    public void create(Team team, float x, float y, float rotation, Position target){
        this.team = team;
        this.x = x;
        this.y = y;
        this.rotation = rotation;
        this.target = target;
        this.time = 0f;
        this.distance = 0f;
        this.removed = false;
        this.seed = seedCounter++;
        nodes.clear();

        // 原版 add() 里直接 end(null) —— 没有"根节点", 第一段的父是闪电起点本身
        end(null);
    }

    /** 每帧更新: 推进生长 / 颜色 / 帧伤 / 生命周期。 */
    public void update(){
        time += Time.delta;
        if(time >= lifetime){
            removed = true;
            return;
        }

        // 推进每个节点的颜色渐变与生长 (注意: end() 会向 nodes 追加, 新增节点本帧也会被推进)
        for(int i = 0; i < nodes.size; i++){
            Node n = nodes.get(i);
            n.colorProgress = Math.min(1f, n.colorProgress + Time.delta / colorTime);

            if(n.time < 1f){
                n.time = Math.min(1f, n.time + Time.delta / nodeTime);
                if(n.time >= 1f && !n.ended && distance < range && nodes.size < maxNodes){
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
     * 让一个节点 (或闪电起点) 继续生长出下一段, 可能分叉。
     *
     * <p>严格对照 PU132 {@code SlowLightningComp.end(SlowLightningNode)}:
     * 先取随机偏转角 {@code r}, 再由父节点推出基准角 {@code tr}
     * (根为初始 {@code rotation}, 其余为 {@code parent.rotation + parent.rotRand}),
     * 然后按距离做<b>单次</b>朝目标纠偏, 最终方向 {@code rr = tr + r}。
     * 起点坐标在 {@code x,y} 处创建子节点后立即完成。</p>
     *
     * @param node 已生长完毕的父节点; {@code null} 表示从闪电起点生成第一段
     */
    protected void end(Node node){
        boolean split = nextBoolean(splitChance);
        int count = split ? 2 : 1;

        float px = node == null ? x : node.x;
        float py = node == null ? y : node.y;
        float parentDist = node == null ? 0f : node.dist;
        // 基准角: 根用初始方向, 其余用父节点方向 + 父节点抖动余量
        float base = node == null ? rotation : node.rotation + node.rotRand;
        // 本段长度: min(nodeLength, range - nodeLength), 与原版一致
        float len = Math.min(nodeLength, range - nodeLength);

        for(int i = 0; i < count; i++){
            float r = nextRange(split ? splitRandSpacing : randSpacing);

            float tr = base;
            if(target != null){
                // ★ 注意: 纠偏量不能乘 nodeTime, 否则每段都会被拧回目标方向 -> 变成笔直光柱
                float scl = 1f - Mathf.clamp(Mathf.dst(x, y, target.getX(), target.getY()) / rotationDistance);
                tr = Angles.moveToward(tr, Angles.angle(x, y, target.getX(), target.getY()),
                    ((maxRotationSpeed - minRotationSpeed) * scl) + minRotationSpeed);
            }

            float rr = tr + r;
            float dist = parentDist + len;

            Node n = new Node();
            n.px = px;
            n.py = py;
            n.rotation = rr;
            n.x = px + Angles.trnsx(rr, len);
            n.y = py + Angles.trnsy(rr, len);
            n.dist = dist;
            // 抖动余量: 根为 -r, 其余为负反馈递推 (原版公式)
            n.rotRand = node == null ? -r : -node.rotRand + (-r + node.rotRand) * nextRand();
            n.time = 0f;
            n.ended = dist >= range;
            nodes.add(n);

            distance = Math.max(distance, dist);
        }
    }

    /** 绘制闪电 (调用方需在其所在图层自行管理 Draw 状态)。 */
    public void draw(){
        float fin = Math.min(lifetime - time, fadeTime) / fadeTime;
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

    // ===== 随机数 (沿用 PU132 的种子序列, 保证同种子同形状) =====

    protected boolean nextBoolean(float chance){
        boolean b = Mathf.randomSeed(seed, 1f) < chance;
        seed = Mathf.randomSeed(seed, 63, Integer.MAX_VALUE);
        return b;
    }

    protected float nextRange(float range){
        float r = Mathf.randomSeed(seed, -range, range);
        seed = Mathf.randomSeed(seed, 63, Integer.MAX_VALUE);
        return r;
    }

    protected float nextRand(){
        float r = Mathf.randomSeed(seed, 1f);
        seed = Mathf.randomSeed(seed, 63, Integer.MAX_VALUE);
        return r;
    }
}

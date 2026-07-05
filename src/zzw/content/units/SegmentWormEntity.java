package zzw.content.units;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Tmp;
import mindustry.gen.Unit;
import mindustry.gen.UnitEntity;
import mindustry.entities.Effect;
import mindustry.content.Fx;

/**
 * 分段虫子单位的头部 Entity
 *
 * 设计: 借鉴 PU132 WormDefaultUnit, 适配 154.3 原生 API
 * - 头部 (本类) 持有 segments[] 数组 (段身列表)
 * - 段身是 SegmentUnitEntity, 由头部直接控制位置
 * - 血量分布: 头部和所有段身共享血量
 *
 * 不使用 @EntityDef 注解, 不需要 Segmentc 接口
 *
 * ★ 重要: 重写 classId() 返回 UnitEntity 的 classId, 避开 v154.3 的 checkEntityMapping 检查 ★
 * 这让我们不需要 @EntityDef 注解处理器
 */
public class SegmentWormEntity extends UnitEntity {

    /** 工厂方法 (UnitType.constructor 用) */
    public static SegmentWormEntity create() {
        return new SegmentWormEntity();
    }

    /** 返回注册的 classId (绕过 v154.3 的 checkEntityMapping 检查) */
    @Override
    public int classId() {
        return ZEntityRegister.classId(SegmentWormEntity.class);
    }

    /** 段身列表 (顺序: 头部后方第一段到最后一段) */
    public SegmentUnitEntity[] segments = new SegmentUnitEntity[0];
    /** 段身位置缓存 (PU132 segments[], 用于物理模拟) */
    protected Vec2[] segPositions;
    /** 段身速度 (PU132 segmentVelocities[], 让段身有惯性) */
    protected Vec2[] segVelocities;
    /** 段身朝向 (PU132 segmentUnits[i].rotation, 每段独立朝向, 用于约束算法) */
    protected float[] segRotations;
    /** 段身间最大角度差 (度, PU132 angleLimit, 限制段身相对前一段的转弯角度) */
    public float angleLimit = 80f;
    /** 段身间距 (PU132 segmentOffset) */
    public float segmentSpacing = 23f;
    /** 段身阻力 (PU132 drag, 段身速度衰减) */
    public float segmentDrag = 0.007f;
    /** 血量分布速率 (PU132 healthDistribution) */
    public float healthDistributionRate = 0.1f;
    /** 血量分布效率 (受伤降低, 慢慢恢复, PU132 healthDistributionEfficiency) */
    protected float healthDistributionEfficiency = 1f;

    /** 段身配置 (在 Z_Units.load 中设置) */
    public static mindustry.type.UnitType defaultSegmentType = null;
    public static int defaultSegmentCount = 5;
    public static float defaultSegmentSpacing = 23f;

    /** 调试标志: 只打印一次, 避免刷屏 */
    private static boolean debugLogged = false;
    /** 调试标志: draw 贴图状态只打印一次 */
    private static boolean debugDrawLogged = false;
    /** 段身是否已创建 (兜底: add() 没触发就在 update() 里创建) */
    private boolean segmentsCreated = false;

    @Override
    public void update() {
        super.update();

        // 调试: 只打印一次 (用于确认 entity 在运行)
        if (!debugLogged) {
            debugLogged = true;
            System.out.println("[ARCNELIDIA-DEBUG] update() called, segments.length=" + segments.length
                + " segmentsCreated=" + segmentsCreated);
        }

        // ★ 兜底: 如果 add() 没创建段身, 在第一次 update() 时创建 ★
        if (!segmentsCreated && defaultSegmentType != null) {
            try {
                segmentSpacing = defaultSegmentSpacing;
                createSegments(defaultSegmentCount, defaultSegmentType);
                segmentsCreated = true;
                System.out.println("[ARCNELIDIA-DEBUG] update() created " + segments.length + " segments (fallback)");
            } catch (Throwable t) {
                System.out.println("[ARCNELIDIA-DEBUG] update() createSegments FAILED: " + t);
                t.printStackTrace();
                segmentsCreated = true;  // 避免反复失败
            }
        }

        // 初始化位置/速度/朝向数组
        if (segPositions == null || segPositions.length != segments.length) {
            segPositions = new Vec2[segments.length];
            segVelocities = new Vec2[segments.length];
            segRotations = new float[segments.length];
            for (int i = 0; i < segments.length; i++) {
                segPositions[i] = new Vec2(x - (i + 1) * segmentSpacing, y);
                segVelocities[i] = new Vec2();
                segRotations[i] = rotation;  // 初始朝向 = 头部朝向
            }
        }

        // ★ PU132 原版算法 (WormDefaultUnit.updateSegmentsLocal 第126-164行) ★
        // segmentOffset = segmentSpacing / 2 (半间距, 用于约束)
        float segmentOffset = segmentSpacing / 2f;

        // === 第 0 段: 跟随头部 ===
        if (segments.length > 0 && segments[0] != null && segments[0].isAdded()) {
            Vec2 seg0 = segPositions[0];
            Vec2 segV0 = segVelocities[0];

            // 1. 段身位置 += 速度 (PU132 第134行)
            seg0.add(segV0);

            // 2. 头部 rotation 调整: 向段身朝向转一点 (PU132 第136行)
            //    让头部稍微跟随段身, 产生更自然的扭动
            rotation -= angleDistSigned(rotation, segRotations[0], angleLimit) / 1.25f;

            // 3. 理想位置: 头部后方 segmentOffset 处 (PU132 第137行)
            Tmp.v1.trns(rotation + 180f, segmentOffset).add(x, y);

            // 4. 段身朝向 = 从段身指向理想位置, 限制在头部朝向 ± angleLimit 内 (PU132 第138行)
            segRotations[0] = clampedAngle(seg0.angleTo(Tmp.v1), rotation, angleLimit);

            // 5. ★ PU132 约束 (第139-140行):
            //    Tmp.v2 = 沿段身朝向走 segmentOffset, 从段身位置出发, 减去理想位置
            //    seg0 = seg0 - Tmp.v2 = 理想位置 - 沿段身朝向走 segmentOffset
            Tmp.v2.trns(segRotations[0], segmentOffset).add(seg0).sub(Tmp.v1);
            seg0.sub(Tmp.v2);

            // 6. 速度衰减 (PU132 第142行)
            segV0.scl(Mathf.clamp(1f - (segmentDrag * arc.util.Time.delta)));

            // 应用到段身实体 (位置 + 朝向)
            segments[0].syncToHead(seg0.x, seg0.y, segRotations[0]);
        }

        // === 后续段身 (i >= 1): 跟随前一段 ===
        for (int i = 1; i < segments.length; i++) {
            SegmentUnitEntity seg = segments[i];
            if (seg == null || !seg.isAdded()) continue;

            Vec2 segPos = segPositions[i];
            Vec2 segV = segVelocities[i];
            Vec2 segLast = segPositions[i - 1];
            float segLastRot = segRotations[i - 1];

            // 1. 段身位置 += 速度 (PU132 第152行)
            segPos.add(segV);

            // 2. 前一段朝向调整: 向当前段朝向转一点 (PU132 第154行)
            //    让前一段稍微跟随当前段, 产生更自然的扭动
            segRotations[i - 1] -= angleDistSigned(segRotations[i - 1], segRotations[i], angleLimit) / 1.25f;

            // 3. 理想位置: 前一段后方 segmentOffset 处 (PU132 第155行)
            //    用前一段的朝向 (不是 segLast.angleTo(segPos))
            Tmp.v1.trns(segRotations[i - 1] + 180f, segmentOffset).add(segLast);

            // 4. 段身朝向 = 从段身指向理想位置, 限制在前一段朝向 ± angleLimit 内 (PU132 第156行)
            segRotations[i] = clampedAngle(segPos.angleTo(Tmp.v1), segRotations[i - 1], angleLimit);

            // 5. ★ PU132 约束 (第157-158行):
            //    segPos = 理想位置 - 沿段身朝向走 segmentOffset
            Tmp.v2.trns(segRotations[i], segmentOffset).add(segPos).sub(Tmp.v1);
            segPos.sub(Tmp.v2);

            // 6. 速度衰减 (PU132 第160行)
            segV.scl(Mathf.clamp(1f - (segmentDrag * arc.util.Time.delta)));

            // 应用到段身实体
            seg.syncToHead(segPos.x, segPos.y, segRotations[i]);
        }

        // 血量分布效率恢复 (PU132 WormDefaultUnit.update L74)
        healthDistributionEfficiency = Mathf.clamp(healthDistributionEfficiency + (arc.util.Time.delta / 160f));

        // 血量分布: 每段单独计算 (3 邻居局部平均, PU132 WormDefaultUnit.distributeHealth L179-202)
        if (healthDistributionRate > 0) {
            for (int i = 0; i < segments.length; i++) {
                distributeHealth(i);
            }
        }
    }

    /** 受伤降低血量分布效率 (PU132 WormDefaultUnit.damage L64-67) */
    @Override
    public void damage(float amount) {
        super.damage(amount);
        healthDistributionEfficiency = Mathf.clamp(healthDistributionEfficiency - (amount / 15f));
    }

    /** 获取段身 (index=-1 表示头部自己, PU132 WormDefaultUnit.getSegment L204-208) */
    protected Unit getSegment(int index) {
        if (index < 0) return this;
        if (index >= segments.length) return null;
        return segments[index];
    }

    /**
     * 3 邻居局部血量平均 (PU132 WormDefaultUnit.distributeHealth L179-202)
     * 对 index 段身, 取 (index-1, index, index+1) 三段做平均
     * index=-1 时表示头部自己 (与第 0 段一起平均)
     */
    protected void distributeHealth(int index) {
        int idx = 0;
        float mHealth = 0f;
        float mMaxHealth = 0f;
        for (int i = -1; i <= 1; i++) {
            Unit seg = getSegment(index + i);
            if (seg == null) break;
            mHealth += seg.health;
            mMaxHealth += seg.maxHealth;
            idx++;
        }
        if (idx == 0) return;
        mMaxHealth /= idx;
        mHealth /= idx;
        for (int i = -1; i <= 1; i++) {
            Unit seg = getSegment(index + i);
            if (seg == null) break;
            if (!Mathf.equal(seg.health, mHealth, 0.001f)) {
                seg.health = Mathf.lerpDelta(seg.health, mHealth, healthDistributionRate * healthDistributionEfficiency);
            }
            if (!Mathf.equal(seg.maxHealth, mMaxHealth, 0.001f)) {
                seg.maxHealth = Mathf.lerpDelta(seg.maxHealth, mMaxHealth, healthDistributionRate * healthDistributionEfficiency);
            }
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        // 销毁所有段身 (借鉴 PU132 WormDefaultUnit.destroy L234-267, 简化版)
        for (SegmentUnitEntity seg : segments) {
            if (seg == null || !seg.isAdded()) continue;
            seg.head = null;  // 避免段身死亡时再通知头部
            // 段身位置爆炸特效 (简化版, 不做 item 爆炸/wreck decal)
            float shake = seg.hitSize / 3f;
            Fx.explosion.at(seg);
            Effect.shake(shake, shake, seg);
            type.deathSound.at(seg);
            seg.remove();
        }
    }

    /** 重写 remove(): 确保所有段身也被移除 (借鉴 PU132 WormDefaultUnit.remove L270-276) */
    @Override
    public void remove() {
        super.remove();
        for (SegmentUnitEntity seg : segments) {
            if (seg != null && seg.isAdded()) {
                seg.head = null;
                seg.remove();
            }
        }
    }

    /** 重写 clipSize(): 让镜头边缘能看到整条虫子 (借鉴 PU132 WormDefaultUnit.clipSize L216-218) */
    @Override
    public float clipSize() {
        if (segments.length == 0) return super.clipSize();
        return segments.length * segmentSpacing * 2f;
    }

    // 注: v154.3 中影子由 UnitType.draw() 内部自动调用 drawShadow(unit)
    //    段身 flying=true 会自动画影子, 不需要重写 drawShadow() (UnitEntity 也没有此方法)
    //    段身 type.region 在 SegmentUnitEntity.draw() 中切换为 segment/tail 贴图,
    //    影子会用切换后的贴图, 与头部影子一起由 UnitType 自动绘制

    /** 段身死亡通知 (由 SegmentUnitEntity.kill 调用) */
    public void onSegmentDied(SegmentUnitEntity seg) {
        // 从列表中移除死段, 数组重新压缩
        int newLen = 0;
        for (SegmentUnitEntity s : segments) {
            if (s != null && s != seg && s.isAdded()) newLen++;
        }
        SegmentUnitEntity[] newSegs = new SegmentUnitEntity[newLen];
        Vec2[] newPos = new Vec2[newLen];
        int idx = 0;
        for (int i = 0; i < segments.length; i++) {
            if (segments[i] != null && segments[i] != seg && segments[i].isAdded()) {
                newSegs[idx] = segments[i];
                newPos[idx] = segPositions[i];
                idx++;
            }
        }
        segments = newSegs;
        segPositions = newPos;
    }

    /** 创建段身 (在 add() 时调用一次) */
    public void createSegments(int count, mindustry.type.UnitType segmentType) {
        segments = new SegmentUnitEntity[count];
        segPositions = new Vec2[count];
        segVelocities = new Vec2[count];
        segRotations = new float[count];

        for (int i = 0; i < count; i++) {
            // 用 segmentType 创建段身 (SegmentUnitEntity 实例)
            SegmentUnitEntity seg = (SegmentUnitEntity) segmentType.create(team);
            seg.set(x - (i + 1) * segmentSpacing, y);
            seg.rotation = rotation;
            seg.head = this;
            seg.segmentIndex = i;  // 段身在数组中的索引, 用于 z 层级
            // 最后一节段身是 tail (用 tail 贴图而不是 segment 贴图)
            seg.isTail = (i == count - 1);
            seg.add();

            segments[i] = seg;
            segPositions[i] = new Vec2(seg.x, seg.y);
            segVelocities[i] = new Vec2();  // 初始速度为 0
            segRotations[i] = rotation;     // 初始朝向 = 头部朝向
        }
    }

    @Override
    public void add() {
        super.add();
        // 用 System.out 保证输出 (Log.info 可能被过滤)
        System.out.println("[ARCNELIDIA-DEBUG] add() called, segments=" + segments.length
            + " type=" + (defaultSegmentType == null ? "null" : "ok")
            + " count=" + defaultSegmentCount);
        // 在头部被添加到世界时创建段身
        if (!segmentsCreated && segments.length == 0 && defaultSegmentType != null) {
            segmentSpacing = defaultSegmentSpacing;
            try {
                createSegments(defaultSegmentCount, defaultSegmentType);
                segmentsCreated = true;
                System.out.println("[ARCNELIDIA-DEBUG] created " + segments.length + " segments");
            } catch (Throwable t) {
                System.out.println("[ARCNELIDIA-DEBUG] createSegments FAILED: " + t);
                t.printStackTrace();
                segmentsCreated = true;
            }
        }
    }

    /**
     * 重写 draw(): 只画头部, 段身自己画 (SegmentUnitEntity.draw)
     *
     * 段身的 draw 会临时切换 type.region 为 segment/tail 贴图, 然后调用 super.draw()
     * 这样段身用 UnitType 默认逻辑画, 贴图查找最可靠
     *
     * z 层级控制:
     * - 头部 z = Layer.flyingUnit (默认)
     * - 段身 0 z = 头部z - 1/10000 (头部覆盖第1节)
     * - 段身 1 z = 头部z - 2/10000 (第1节覆盖第2节)
     * - ...
     *
     * (借鉴 PU132 UnityUnitType.drawBody 第669行)
     */
    @Override
    public void draw() {
        // 只画头部, 段身会自己画
        super.draw();
    }

    // ==================== PU132 角度工具方法 (Utils.java) ====================

    /** 带符号角度差 (PU132 Utils.angleDistSigned 第177行)
     *  返回 a 相对 b 的角度差, 范围 -180~180 */
    private static float angleDistSigned(float a, float b) {
        a += 360f;
        a %= 360f;
        b += 360f;
        b %= 360f;
        float d = Math.abs(a - b) % 360f;
        int sign = (a - b >= 0f && a - b <= 180f) || (a - b <= -180f && a - b >= -360f) ? 1 : -1;
        return (d > 180f ? 360f - d : d) * sign;
    }

    /** 带符号角度差, 超过 start 才返回差值 (PU132 Utils.angleDistSigned 第187行)
     *  用于头部/前一段朝向调整: 超过 angleLimit 才转 */
    private static float angleDistSigned(float a, float b, float start) {
        float dst = angleDistSigned(a, b);
        if (Math.abs(dst) > start) {
            return dst > 0 ? dst - start : dst + start;
        }
        return 0f;
    }

    /** 限制角度差 (PU132 Utils.clampedAngle 第200行)
     *  将 angle 限制在 relative ± limit 范围内
     *  用于段身朝向: 不能相对前一段转超过 angleLimit */
    private static float clampedAngle(float angle, float relative, float limit) {
        if (limit >= 180f) return angle;
        if (limit <= 0f) return relative;
        float dst = angleDistSigned(angle, relative);
        if (Math.abs(dst) > limit) {
            float val = dst > 0 ? dst - limit : dst + limit;
            return (angle - val) % 360f;
        }
        return angle;
    }
}

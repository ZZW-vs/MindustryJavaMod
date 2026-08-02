package zzw.content.units;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.Rand;
import arc.math.geom.Vec2;
import arc.struct.FloatSeq;
import arc.struct.Seq;
import arc.util.Tmp;
import arc.util.Time;
import mindustry.entities.Units;
import mindustry.entities.units.UnitController;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;

import static zzw.content.Z_Bullets.kamiBullet2;
import static zzw.content.Z_Bullets.kamiBullet3;

/**
 * kami 弹幕 AI 控制器 (PU132 移植版)
 *
 * 还原 PU132 的 kami 弹幕模式:
 * - basicPattern1: 双层旋转弹环 (6-12 + 16-32 子弹)
 * - basicPattern2: 交替方向弹环 (8+ 子弹)
 * - expandPattern: 散弹 → 环形扩张 (两阶段)
 * - flowerPattern: 花瓣形弹幕 (3-8 瓣, 双向射击)
 * - barrier: 800 半径屏障, 阻止玩家逃离
 *
 * 简化:
 * - 使用标准 UnitController (不需要自定义 Entity)
 * - 移除 hyperSpeedPattern (需要自定义 laser entity, 过于复杂)
 * - 弹幕用 b.data (float[]) 存储 width/length/turn
 *
 * 参考: PU132 unity/ai/kami/KamiAI.java + KamiPatterns.java
 */
public class KamiAI implements UnitController {
    public static final float minRange = 350f;
    public static final float barrierRange = 800f;

    private static final Vec2 vec = new Vec2();

    public Unit unit;
    public Teamc target;
    public int currentPattern = 0;
    public float[] reloads = new float[16];
    public int difficulty = 2;  // 难度等级 (0-5)
    public int stages = 0;
    public float x, y;
    public float patternTime, waitTime = 2f * 60f;
    public float patternDuration = 20f * 60f;  // 每个模式持续 20 秒
    public float stateTimer = 0f;
    public Rand rand = new Rand();

    // 模式列表 (4 个核心模式循环)
    private static final int PATTERN_COUNT = 4;

    @Override
    public void updateUnit() {
        // 更新目标 — ★ PU132 原版: 从所有玩家中选最近的, 不依赖敌方单位
        // ★ 目标玩家死亡(invalidate)时直接自杀
        if (target != null && Units.invalidateTarget(target, unit.team, unit.x, unit.y)) {
            // 目标失效 (死亡/离开), kami 自杀
            unit.kill();
            return;
        }
        if (target == null) {
            // ★ 原版逻辑: 遍历 Groups.player 找最近玩家单位
            Player bestPlayer = null;
            float bestDst = Float.MAX_VALUE;
            for (Player p : Groups.player) {
                if (p.unit() != null && p.unit().isValid()) {
                    float dst = unit.dst(p.unit());
                    if (dst < bestDst) {
                        bestDst = dst;
                        bestPlayer = p;
                    }
                }
            }
            if (bestPlayer != null) {
                target = bestPlayer.unit();
            } else {
                // 没有玩家目标, 自杀
                unit.kill();
                return;
            }
        }

        // 更新位置 (保持在目标附近 minRange 距离)
        if (target != null) {
            float speed = patternTime <= 0f ? Mathf.clamp(waitTime / 40f) : 1f;
            float range = minRange;
            vec.trns(target.angleTo(unit), range).add(target).sub(unit).scl(0.05f * speed * Time.delta);
            unit.move(vec);
            // ★ 直接设置 rotation, 不用 lookAt — v158 lookAt 用 rotateSpeed 步进,
            // 而 kami rotateSpeed=0f 导致 lookAt 完全失效, 单位永远朝东不旋转
            unit.rotation = unit.angleTo(target);
            if (patternTime <= 0f) {
                vec.set(x, y).lerpDelta(target.x(), target.y(), 0.1f * speed);
                x = vec.x;
                y = vec.y;
            }
        }

        // 模式执行
        if (target != null && waitTime <= 0f) {
            // ★ 初始化新模式 (patternTime=0 时设置持续时间)
            if (patternTime <= 0f) {
                patternTime = patternDuration;
                // 重置 reloads 用于新模式
                reloads[0] = 1f;
                reloads[4] = 1f;
                reloads[2] = 0f;
                reloads[3] = 0f;
                stateTimer = 0f;
            }

            stateTimer += Time.delta;

            switch (currentPattern) {
                case 0: updateBasicPattern1(); break;
                case 1: updateBasicPattern2(); break;
                case 2: updateExpandPattern(); break;
                case 3: updateFlowerPattern(); break;
            }

            patternTime -= Time.delta;
            if (patternTime <= 0f) {
                waitTime = 3f * 60f;  // 3 秒间隔
                stages++;
                currentPattern = (currentPattern + 1) % PATTERN_COUNT;
                // 难度随阶段提升
                if (difficulty < 5 && stages % 3 == 0) difficulty++;
            }
        }

        waitTime = Math.max(0f, waitTime - Time.delta);
        updateBarrier();
    }

    /** 模式1: 双层旋转弹环 */
    private void updateBasicPattern1() {
        Unit u = unit;
        int diff = 6 + Mathf.clamp(difficulty / 2, 0, 6);
        int diff2 = 16 + Mathf.clamp(difficulty * 2, 0, 16);
        float turn = Mathf.sin(patternTime, 90f, 0.75f);

        // 内层弹环: 6-12 子弹, 旋转
        if (shoot(0, 15f)) {
            for (int i = 0; i < diff; i++) {
                float ang = (i * (360f / diff)) + reloads[1];
                Bullet b = kamiBullet2.create(u, u.team, u.x, u.y, ang);
                setBulletData(b, 4f, 4f, turn);
                b.lifetime = 5f * 60f;
                b.vel.scl(4f);
            }
            reloads[1] += 180f / diff;
        }

        // 外层弹环: 16-32 子弹
        if (shoot(2, 40f)) {
            for (int i = 0; i < diff2; i++) {
                float ang = (i * (360f / diff2)) + reloads[3];
                Bullet b = kamiBullet2.create(u, u.team, u.x, u.y, ang);
                setBulletData(b, 10f, 10f, 0f);
                b.lifetime = 5f * 60f;
                b.vel.scl(5f);
            }
            reloads[3] += 180f / diff2;
        }
    }

    /** 模式2: 交替方向弹环 */
    private void updateBasicPattern2() {
        Unit u = unit;
        int diff = 8 + difficulty / 2;

        if (reloads[3] < 2f * 60f && shoot(1, 5f)) {
            for (int i = 0; i < diff; i++) {
                float ang = (i * (360f / diff)) + reloads[2];
                Bullet b = kamiBullet3.create(u, u.team, u.x, u.y, ang);
                setBulletData(b, 6f, 6f, 0.25f * reloads[0]);
                b.lifetime = 6f * 60f;
                b.vel.scl(4f);
            }
            reloads[0] *= -1f;
            reloads[2] += (40f / diff) * reloads[4];
        }

        reloads[3] += Time.delta;
        if (reloads[3] > 3.5f * 60f) {
            reloads[2] = 0f;
            reloads[3] -= 3.5f * 60f;
            reloads[4] *= -1f;
        }
    }

    /** 模式3: 散弹 → 环形扩张 (两阶段) */
    private void updateExpandPattern() {
        Unit u = unit;
        // 阶段1: 朝目标散弹 (前 8 秒)
        if (stateTimer < 8f * 60f) {
            if (shoot(0, 10f)) {
                int shots = 5 + difficulty;
                float baseAng = u.angleTo(target);
                for (int i = 0; i < shots; i++) {
                    float ang = baseAng + (i - shots / 2f) * 12f;
                    Bullet b = kamiBullet2.create(u, u.team, u.x, u.y, ang);
                    setBulletData(b, 5f, 5f, 0f);
                    b.lifetime = 5f * 60f;
                    b.vel.scl(6f);
                }
            }
        }
        // 阶段2: 环形扩张弹幕 (8 秒后)
        else {
            if (shoot(1, 25f)) {
                int ringCount = 12 + difficulty * 2;
                for (int i = 0; i < ringCount; i++) {
                    float ang = (i * (360f / ringCount)) + reloads[2];
                    Bullet b = kamiBullet3.create(u, u.team, u.x, u.y, ang);
                    setBulletData(b, 8f, 8f, 0.08f);
                    b.lifetime = 6f * 60f;
                    b.vel.scl(3f);
                }
                reloads[2] += 15f;
            }
        }
    }

    /** 模式4: 花瓣形弹幕 (3-8 瓣, 双向旋转射击) */
    private void updateFlowerPattern() {
        Unit u = unit;
        int petals = 3 + Mathf.clamp(difficulty, 0, 5);

        if (shoot(0, 14f)) {
            for (int i = 0; i < petals; i++) {
                float baseAng = (i * (360f / petals)) + reloads[1];
                // 双向射击: 正向 + 反向
                for (int dir : Mathf.signs) {
                    float ang = baseAng + dir * reloads[2];
                    Bullet b = kamiBullet2.create(u, u.team, u.x, u.y, ang);
                    setBulletData(b, 6f, 6f, dir * 0.12f);
                    b.lifetime = 5f * 60f;
                    b.vel.scl(4f);
                }
            }
            reloads[1] += 8f;
            reloads[2] += 6f;
        }
    }

    /** 设置弹幕子弹的 width/length/turn 数据 */
    private void setBulletData(Bullet b, float width, float length, float turn) {
        b.data = new float[]{width, length, turn};
    }

    /** 屏障: 将离开范围的玩家拉回 */
    private void updateBarrier() {
        for (Player p : Groups.player) {
            Unit u = p.unit();
            if (u != null && u.isValid() && !Mathf.within(x, y, u.x, u.y, barrierRange)) {
                vec.set(u.x - x, u.y - y).setLength(barrierRange).add(x, y);
                u.set(vec.x, vec.y);
            }
        }
    }

    /** 射击计时器 (返回 true 表示可以射击) */
    public boolean shoot(int i, float time) {
        boolean s = reloads[i] <= 0f;
        if (s) reloads[i] += time;
        reloads[i] -= Time.delta;
        return s;
    }

    /** 绘制屏障和特效 */
    public void draw() {
        float z = Draw.z();
        Draw.z(Layer.flyingUnit);
        // ★ 屏障: 加法混合 + 红色 hue-shift + 脉动线宽 + 半径 800 圆环
        Lines.stroke(3f + Mathf.absin(12f, 1f));
        Draw.color(Tmp.c1.set(Color.red).shiftHue(Time.time));
        Draw.blend(Blending.additive);
        Lines.circle(x, y, barrierRange);
        Draw.blend();
        Draw.reset();
        Draw.z(z);
    }

    @Override
    public void unit(Unit unit) {
        this.unit = unit;
        x = unit.x;
        y = unit.y;
        // ★ 原版: rand.setSeed(unit.id * 9999L)
        rand.setSeed(unit.id * 9999L);
    }

    @Override
    public Unit unit() {
        return unit;
    }
}

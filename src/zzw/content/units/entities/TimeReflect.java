package zzw.content.units.entities;

import arc.struct.Seq;
import arc.util.Log;
import arc.util.Reflect;
import arc.util.Time;
import arc.util.Time.DelayRun;
import arc.util.pooling.Pools;

import java.lang.reflect.Field;

/**
 * Time.run() 延迟队列反射工具 (PU132 unity.util.TimeReflect 移植)
 *
 * 作用: 让世界单位 (WorldUnitEntity) 子世界中的建筑物调用 Time.run() 时,
 * 延迟任务进入单位自己的 runs 队列而非主世界队列, 由单位自己 updateDelays 推进.
 * 否则建筑物在子世界中 Time.run 的任务会被主世界的 Time.update 提前执行, 造成时序错乱.
 *
 * 原理 (arc.util.Time 内部结构, v155.4 源码已核实):
 * - Time 类有一个 private static Seq<DelayRun> runs 队列
 * - DelayRun 有 float delay 和 Runnable finish 两个字段
 * - swapRuns(): 用单位自己的 runs 队列替换 Time.runs (建筑物 Time.run 进入单位队列)
 * - updateDelays(): 手动推进单位队列中的延迟任务 (减 delta, 到期执行 finish)
 * - resetRuns(): 恢复 Time.runs 为原始队列
 *
 * 适配 v155.4 改动 (PU132 → 本项目):
 * - PU132 用 ReflectUtils.findField(Time.class, "runs", true), arc.util.Reflect 没有 findField,
 *   改用 Class.getDeclaredField() + setAccessible(true) (java 反射)
 * - arc.util.Reflect.get(Object, Field) 参数顺序: object 在前, field 在后
 *   (PU132 ReflectUtils.getField(obj, field) 也是这个顺序, 直接换用 arc.util.Reflect.get)
 * - Unity.print(e) → Log.err(e)
 * - 其余 Field.set / Field.getFloat / Field.setFloat 为 java 反射原生 API, 保持不变
 */
public class TimeReflect {
    /** Time.runs 静态字段 (private static Seq<DelayRun>) */
    static Field runs, delay, finish;
    /** Time.runs 的原始队列 (resetRuns 时恢复) */
    static Seq<DelayRun> trueRuns;
    /** updateDelays 中收集已完成的 DelayRun */
    static Seq<DelayRun> removes = new Seq<>();

    /**
     * 初始化反射字段, 必须在使用前调用一次 (在 Z_Units.load() 中调用).
     * 反射失败会打印错误但不抛异常 (降级为不使用 TimeReflect).
     */
    public static void init() {
        try {
            runs = Time.class.getDeclaredField("runs");
            runs.setAccessible(true);
            trueRuns = Reflect.get(null, runs);

            delay = DelayRun.class.getDeclaredField("delay");
            delay.setAccessible(true);

            finish = DelayRun.class.getDeclaredField("finish");
            finish.setAccessible(true);
        } catch (Exception e) {
            Log.err("TimeReflect init failed", e);
        }
    }

    /** 把 Time.runs 替换为 newRuns, 让后续 Time.run 进入单位自己的队列 */
    public static void swapRuns(Seq<DelayRun> newRuns) {
        try {
            runs.set(null, newRuns);
        } catch (Exception e) {
            Log.err(e);
        }
    }

    /** 恢复 Time.runs 为原始队列 */
    public static void resetRuns() {
        try {
            runs.set(null, trueRuns);
        } catch (Exception e) {
            Log.err(e);
        }
    }

    /** 推进单位队列中的所有延迟任务 (减 delta, 到期执行 finish 并移除) */
    public static void updateDelays(Seq<DelayRun> runSeq) {
        removes.clear();
        for (DelayRun r : runSeq) {
            updateDelay(r);
        }
        runSeq.removeAll(removes);
    }

    static void updateDelay(DelayRun run) {
        try {
            float time = delay.getFloat(run);
            time -= Time.delta;
            if (time <= 0f) {
                Runnable r = Reflect.get(run, finish);
                r.run();
                removes.add(run);
                Pools.free(run);
            } else {
                delay.setFloat(run, time);
            }
        } catch (Exception e) {
            Log.err(e);
        }
    }
}

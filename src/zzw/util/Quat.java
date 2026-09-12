package zzw.util;

import arc.math.Mathf;
import arc.math.geom.Vec3;

/**
 * 精简四元数 (PU132 arc.math.Quat 移植)。
 *
 * <p>★ 依赖说明: PU132 使用 arc g3d 模块的 Quat/Mat3D 做 3D 透视旋转;
 * 本项目不引入 g3d 依赖 (运行时保证性未知), 故按四元数标准公式自实现,
 * 只实现 {@link #set(Vec3, float)} (轴角构造)、{@link #mul(Quat)} (组合旋转)、
 * {@link #transform(Vec3)} (向量旋转) 三个 panningCircle 所需操作。</p>
 *
 * <p>数学原理 (逐步解释):</p>
 * <ul>
 *   <li>单位四元数 q = (x*sin(θ/2), y*sin(θ/2), z*sin(θ/2), cos(θ/2))
 *       表示绕轴 (x,y,z) 旋转 θ 角;</li>
 *   <li>组合旋转 R(q1 ⊗ q2) = R(q1) ∘ R(q2), 即<b>先应用 q2 的旋转再应用 q1</b>
 *       (世界坐标系) —— 与 PU132 "q1.set(轴A, a).mul(q2.set(轴B, b))"
 *       的语义一致 (先绕轴 B 旋转, 再绕世界轴 A 旋转);</li>
 *   <li>向量旋转用三重积展开式 v' = v + 2w(q×v) + 2q×(q×v),
 *       等价于矩阵变换但免构造 3x3 矩阵。</li>
 * </ul>
 *
 * @author PU132 原作 (GlennFolker), 移植: zzw
 */
public class Quat{
    /** 四元数分量 (x/y/z 为旋转轴 * sin(θ/2), w 为 cos(θ/2))。 */
    public float x, y, z, w = 1f;

    public Quat(){
    }

    public Quat(float x, float y, float z, float w){
        set(x, y, z, w);
    }

    public Quat set(float x, float y, float z, float w){
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
        return this;
    }

    /**
     * 轴角构造: 绕单位向量 axis 旋转 degrees 度。
     *
     * @param axis 旋转轴 (须为单位向量, 如 Vec3.X / setToRandomDirection 结果)
     * @param degrees 旋转角 (度)
     */
    public Quat set(Vec3 axis, float degrees){
        float halfRad = degrees * Mathf.degreesToRadians / 2f;
        float s = Mathf.sin(halfRad);
        return set(axis.x * s, axis.y * s, axis.z * s, Mathf.cos(halfRad));
    }

    /**
     * 哈密顿积 this = this ⊗ q (组合旋转: 先应用 q 的旋转, 再应用 this 的旋转)。
     */
    public Quat mul(Quat q){
        float tx = w * q.x + x * q.w + y * q.z - z * q.y;
        float ty = w * q.y - x * q.z + y * q.w + z * q.x;
        float tz = w * q.z + x * q.y - y * q.x + z * q.w;
        float tw = w * q.w - x * q.x - y * q.y - z * q.z;
        return set(tx, ty, tz, tw);
    }

    /**
     * 原地旋转向量 v: v' = v + 2w(q×v) + 2q×(q×v)。
     */
    public void transform(Vec3 v){
        // 步骤 1: t = 2 * (q.xyz × v)
        float tx = 2f * (y * v.z - z * v.y);
        float ty = 2f * (z * v.x - x * v.z);
        float tz = 2f * (x * v.y - y * v.x);
        // 步骤 2: v' = v + w*t + (q.xyz × t)
        v.x += w * tx + (y * tz - z * ty);
        v.y += w * ty + (z * tx - x * tz);
        v.z += w * tz + (x * ty - y * tx);
    }
}

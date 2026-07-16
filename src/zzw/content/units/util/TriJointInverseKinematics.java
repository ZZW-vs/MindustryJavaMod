package zzw.content.units.util;

import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Tmp;

/**
 * 三节腿 IK 解算器 (完整移植 PU_V8 unity.util.TriJointInverseKinematics)
 *
 * 算法说明:
 * - 输入: 腿根 (wx, wy), 关节数组 vecs[3], 每段长度 legLength, 目标点 target, 侧边 side, 插值系数 jointLerp
 * - vecs[2] (脚) 直接被限制到距离腿根 3*legLength 以内
 * - 通过对腿根到脚的连线计算垂直偏移, 得到中点 v2
 * - 中点附近沿腿方向取两点作为 vecs[0] 和 vecs[1]
 * - jointLerp 控制跨帧平滑: 0=不动, 1=立即跟随
 *
 * 关键公式:
 * - offset = sineOut(cosDeg(lenRatio * 90)) * legLength * sign(side)
 * - lenRatio = (dist - legLength) / (legLength * 2)
 * - 腿伸直时 (dist = 3*legLength): lenRatio = 1, cosDeg(90) = 0, offset = 0 (直腿)
 * - 腿收缩时 (dist < 3*legLength): offset > 0 (弯曲)
 */
public class TriJointInverseKinematics {

    /**
     * 解算三节腿 IK
     *
     * @param wx        腿根 X
     * @param wy        腿根 Y
     * @param vecs      关节数组, 长度 3: vecs[0]=第一关节, vecs[1]=中间关节, vecs[2]=脚
     * @param legLength 每段长度 (腿总长 = 3 * legLength)
     * @param target    脚的目标位置
     * @param side      侧边 (true/false 决定弯曲方向)
     * @param jointLerp 跨帧插值系数, 会被 clamp 到 [0, 1]
     */
    public static void solve(float wx, float wy, Vec2[] vecs, float legLength, Vec2 target, boolean side, float jointLerp) {
        jointLerp = Mathf.clamp(jointLerp);

        // 1. 限制脚距离腿根不超过 3 * legLength, 并写入 vecs[2]
        Tmp.v1.set(target).sub(wx, wy).limit(legLength * 3f);
        vecs[2].set(Tmp.v1).add(wx, wy);

        // 2. 计算腿根到脚的角度和垂直偏移 (使用 PU132 原版公式)
        float angle = Tmp.v1.angle();
        float offset = Interp.sineOut.apply(
                Mathf.cosDeg(Mathf.mod(((Tmp.v1.len() - legLength) / (legLength * 2f)) * 90f, 360f))
        ) * legLength * Mathf.sign(side);

        // 3. 中点 v2 = 腿根 + (沿腿方向 dist/2) + (垂直方向 offset)
        Tmp.v2.trns(angle, Tmp.v1.len() / 2f, offset).add(wx, wy);

        // 4. 在中点附近沿腿方向取两点作为 vecs[0] 和 vecs[1], 并做跨帧插值
        for (int i = 0; i < 2; i++) {
            Tmp.v3.trns(angle, (legLength / 2f) * (-1 + (i * 2))).add(Tmp.v2);
            Tmp.v1.set(vecs[i]).lerp(Tmp.v3, jointLerp);
            vecs[i].set(Tmp.v1);
        }
    }
}

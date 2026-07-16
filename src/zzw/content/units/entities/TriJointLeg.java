package zzw.content.units.entities;

import arc.math.geom.Vec2;

/**
 * 三节腿数据类 (完整移植 PU_V8 unity.entities.TriJointLeg)
 *
 * 每条腿包含 3 个关节点 joints[0..2]:
 * - joints[0]: 第一关节 (靠近腿根)
 * - joints[1]: 中间关节
 * - joints[2]: 脚 (末端)
 *
 * 字段说明:
 * - group: 当前所在步态分组, 用于判断是否轮到该腿移动
 * - moving: 当前是否正在移动 (抬起 + 落下)
 * - legScl: 腿伸缩比例 (基于腿根到脚距离与 legLength*maxStretch 的比值)
 * - jointLerp: 跨帧平滑插值系数 (移动时 = legSpeed/4, 静止时 lerp 到 1)
 * - stage: 步态阶段 [0, 1), 0=刚落地, 1=刚抬起
 */
public class TriJointLeg {
    public Vec2[] joints = new Vec2[3];
    public int group;
    public boolean moving;
    public float legScl = 1f, jointLerp = 1f;
    public float stage;

    public TriJointLeg() {
        for (int i = 0; i < joints.length; i++) {
            joints[i] = new Vec2();
        }
    }
}

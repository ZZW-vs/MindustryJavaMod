package zzw.content.units.kami;

import arc.math.geom.Position;
import arc.util.Time;
import mindustry.gen.Bullet;

/**
 * kami 弹幕子弹的动态数据 (PU132 {@code KamiBulletComp} 中除位置/速度等原版字段外的移植)。
 *
 * <p>PU132 用注解实体系统给子弹扩展了 {@code width/length/turn/fdata/fdata2/bdata}
 * 等字段。v160 移植时不引入自定义实体 (避免注册/存档复杂度), 而是把这些字段集中到本
 * 数据对象, 由 {@code KamiBulletType} 在 {@code init()} 时创建并挂到 {@code b.data} 上,
 * 每帧 {@code update()} 时读取。</p>
 *
 * <p>参考: PU132 {@code unity/entities/comp/KamiBulletComp.java} 与
 * {@code unity/ai/kami/KamiBulletDatas.java}。</p>
 */
public class KamiBulletData {
    /** 弹体显示宽度 (对应原 KamiBullet.width) */
    public float width = 6f;
    /** 弹体显示长度 (对应原 KamiBullet.length) */
    public float length = 6f;
    /** 每帧旋转量, 单位度 (对应原 KamiBullet.turn) */
    public float turn = 0f;
    /** 通用数据槽 1 (对应原 KamiBullet.fdata) */
    public float fdata = 0f;
    /** 通用数据槽 2 (对应原 KamiBullet.fdata2) */
    public float fdata2 = 0f;
    /** 特殊行为 (对应原 KamiBullet.bdata, 见下方静态实例) */
    public Behavior behavior;

    /** 子弹特殊行为接口 (对应 PU132 的 {@code KamiBulletData}) */
    public interface Behavior {
        /** 每帧调用一次 */
        void update(Bullet b, KamiBulletData d);
    }

    /** 延迟转向: 存活 1.8 秒后把 turn 设为 fdata (PU132 {@code turnDelay1}) */
    public static final Behavior turnDelay1 = (b, d) -> {
        if (b.time >= 1.8f * 60f) d.turn = d.fdata;
    };

    /** 瞬时转向: 存活 1.8 秒后一次性旋转 fdata 度 (PU132 {@code suddenTurnDelay1}) */
    public static final Behavior suddenTurnDelay1 = (b, d) -> {
        if (b.time >= 1.8f * 60f && d.fdata != 0f) {
            b.rotation(b.rotation() + d.fdata);
            d.fdata = 0f;
        }
    };

    /**
     * 减速后锁定方向 (PU132 {@code stopChangeDirection})。
     *
     * <p>存活 1.8 秒后开始逐渐减速; 当 {@code b.time >= fdata} 时, 把朝向对准
     * {@code fdata2} 并以固定速度 4 飞出, 随后把 fdata 归零作为"已触发"标记。</p>
     */
    public static final Behavior stopChangeDirection = (b, d) -> {
        if (b.time >= 1.8f * 60f && d.fdata > 0f) {
            b.vel.scl(1f - 0.15f * Time.delta);
            if (b.time >= d.fdata) {
                b.rotation(d.fdata2);
                b.vel.trns(b.rotation(), 4f);
                d.fdata = 0f;
            }
        }
    };

    /** 位置锁定: 每帧把自己移动到 owner 的位置 (PU132 {@code positionLock}) */
    public static final Behavior positionLock = (b, d) -> {
        if (b.owner instanceof Position p) b.set(p);
    };
}

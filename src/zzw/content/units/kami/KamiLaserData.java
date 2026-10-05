package zzw.content.units.kami;

import arc.math.Mathf;
import arc.math.geom.Position;
import arc.struct.IntSeq;
import arc.util.Tmp;
import mindustry.gen.Bullet;

/**
 * kami 激光的动态数据 (PU132 {@code KamiLaserComp} 的移植)。
 *
 * <p>PU132 用自定义实体 {@code KamiLaser} 表示一段两端点激光 ({@code x2/y2/width}),
 * v160 移植时改用普通子弹 + 本数据对象保存两端点与碰撞参数。</p>
 *
 * <p>参考: PU132 {@code unity/entities/comp/KamiLaserComp.java} 与
 * {@code unity/ai/kami/KamiBulletDatas.java}。</p>
 */
public class KamiLaserData {
    /** 第二端点 X (对应原 KamiLaser.x2) */
    public float x2;
    /** 第二端点 Y (对应原 KamiLaser.y2) */
    public float y2;
    /** 激光半宽 (对应原 KamiLaser.width) */
    public float width = 6f;
    /** 通用数据槽 (对应原 KamiLaser.fdata) */
    public float fdata = 0f;
    /** 是否按 5 帧间隔做碰撞检测 (对应原 intervalCollision) */
    public boolean intervalCollision = true;
    /** 是否使用椭圆碰撞 (对应原 ellipseCollision) */
    public boolean ellipseCollision = true;
    /** 关联实体, hyperSpeed 激光用于取 owner 位置 (对应原 KamiLaser.data) */
    public Object data;
    /** 特殊行为 (对应原 KamiLaser.bdata) */
    public Behavior behavior;
    /** 已命中的单位 id, 避免重复结算 */
    public final IntSeq collided = new IntSeq();

    /** 激光特殊行为接口 (对应 PU132 的 {@code KamiLaserData}) */
    public interface Behavior {
        void update(Bullet b, KamiLaserData d);
    }

    /**
     * hyperSpeed 冲刺激光 (PU132 {@code hyperSpeedLaser1})。
     *
     * <p>前 7 帧贴住 owner 并记录当前长度; 之后第二端点以固定速度收缩回第一端点,
     * 两端点间距小于 2 像素时移除。</p>
     */
    public static final Behavior hyperSpeedLaser1 = (b, d) -> {
        if (d.data instanceof Position p) {
            if (b.time <= 7f) {
                b.set(p);
                d.fdata = Mathf.dst(b.x, b.y, d.x2, d.y2);
            } else {
                Tmp.v1.set(d.x2, d.y2).approachDelta(Tmp.v2.set(b.x, b.y), d.fdata / (16f - 7f));
                d.x2 = Tmp.v1.x;
                d.y2 = Tmp.v1.y;
                if (Mathf.within(b.x, b.y, d.x2, d.y2, 2f)) b.remove();
            }
        }
    };
}

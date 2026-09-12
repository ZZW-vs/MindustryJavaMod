package zzw.content.units;

import arc.math.WindowedMean;
import zzw.content.units.graphics.FixedTrail;

/**
 * Saber 连续激光的数据类
 * PU132 unity.entities.SaberData 移植版
 * 
 * <p>用于存储激光的动态数据，包括：</p>
 * <ul>
 *   <li>f: 激光当前长度</li>
 *   <li>rot: 激光旋转角度</li>
 *   <li>fT: 激光拖尾效果</li>
 *   <li>mean: 角度变化的滑动平均值</li>
 * </ul>
 */
public class SaberData {
    /** 激光当前长度 */
    public float f;
    /** 激光旋转角度 */
    public float rot;
    /** 激光拖尾效果 */
    public FixedTrail fT;
    /** 角度变化的滑动平均值（用于平滑角度变化） */
    public WindowedMean mean;

    /**
     * 构造函数
     * @param f 初始长度
     * @param ft 拖尾长度
     * @param rot 初始旋转角度
     * @param mean 滑动窗口大小
     */
    public SaberData(float f, int ft, float rot, int mean) {
        this.f = f;
        this.fT = new FixedTrail(ft);
        this.rot = rot;
        this.mean = new WindowedMean(mean);
        this.mean.fill(0f);
    }
}
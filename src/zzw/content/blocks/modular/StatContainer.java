package zzw.content.blocks.modular;

import arc.struct.Seq;

/**
 * 蓝图统计容器 (PU132 younggamExperimental.StatContainer 移植)。
 *
 * <p>汇总蓝图内全部零件产生的伤害段、惯量与血量/射程增量。</p>
 */
public class StatContainer{
    public final Seq<Segment> segments = new Seq<>();
    public int inertia, hpinc, rangeInc;

    public void clear(){
        segments.clear();
        inertia = hpinc = rangeInc = 0;
    }
}
package zzw.content.blocks.modular;

/**
 * 零件伤害段 (PU132 younggamExperimental.Segment 移植)。
 *
 * <p>记录一段零件命中检测的起止位置与伤害值。</p>
 */
public class Segment{
    public int damage, end;
    public final int start;

    public Segment(int start, int end, int damage){
        this.start = start;
        this.end = end;
        this.damage = damage;
    }
}
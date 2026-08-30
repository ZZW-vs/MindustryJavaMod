package zzw.content.optics;

import arc.func.Cons2;

/**
 * 光接收槽类型 (PU132 unity.world.LightAcceptorType 移植)
 * <p>定义槽在建筑左上角坐标系中的位置/尺寸/需求光强, 以及 update/draw 回调。
 * 槽必须贴建筑边缘, 否则光无法指向。</p>
 */
@SuppressWarnings("unchecked")
public class LightAcceptorType {
    /** 槽 x 位置 (相对建筑左上角) */
    public int x;
    /** 槽 y 位置 (相对建筑左上角) */
    public int y;
    public int width;
    public int height;
    /** 需求光强 [0..1], <=0 表示不需要光 */
    public float required;

    public Cons2<LightHoldBlock.LightHoldBuild, LightAcceptor> update = (e, s) -> {};
    public Cons2<LightHoldBlock.LightHoldBuild, LightAcceptor> draw = (e, s) -> {};

    public LightAcceptorType() {
        this(0, 0, 1f);
    }

    public LightAcceptorType(int x, int y, float required) {
        this(x, y, 1, 1, required);
    }

    public LightAcceptorType(int x, int y, int width, int height, float required) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.required = required;
    }

    public <T extends LightHoldBlock.LightHoldBuild, V extends LightAcceptor> LightAcceptorType update(Cons2<T, V> update) {
        this.update = (Cons2<LightHoldBlock.LightHoldBuild, LightAcceptor>) update;
        return this;
    }

    public <T extends LightHoldBlock.LightHoldBuild, V extends LightAcceptor> LightAcceptorType draw(Cons2<T, V> draw) {
        this.draw = (Cons2<LightHoldBlock.LightHoldBuild, LightAcceptor>) draw;
        return this;
    }

    public LightAcceptor create(LightHoldBlock.LightHoldBuild hold) {
        return new LightAcceptor(this, hold);
    }
}
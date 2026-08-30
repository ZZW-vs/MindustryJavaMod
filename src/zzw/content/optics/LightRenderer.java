package zzw.content.optics;

import arc.graphics.g2d.Draw;

/**
 * 光束渲染器: 每 DrawLayer 绘制全部活跃光束。
 * <p>由 LightProcess.register() 注册到 Trigger.draw;
 * PU132 原版 Light 是 Drawc 实体自动渲染, 本移植为普通对象手动驱动。</p>
 */
public final class LightRenderer {
    private LightRenderer() {}

    static void draw() {
        for (Light l : Light.active) {
            if (!l.removed) l.draw();
        }
    }
}
package zzw.content.type;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Mat;
import arc.math.geom.Vec2;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Tmp;
import arc.Core;
import arc.Events;
import mindustry.Vars;
import mindustry.core.World;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.ui.Styles;
import zzw.content.units.entities.WorldUnitEntity;

/**
 * 世界单位类型 (PU132 unity.type.UnityUnitType 中 Worldc 渲染部分移植)
 *
 * <p>继承 UnityUnitType, 添加:
 * <ul>
 *   <li>worldWidth / worldHeight: 子世界尺寸 (tile 数)</li>
 *   <li>drawBody() 渲染 hack: 让子世界中的建筑物跟随单位移动和旋转</li>
 * </ul>
 *
 * <p>适配 v155.4 改动:
 * <ul>
 *   <li>去掉 altBatch (用普通 batch, 接受可能的半透明排序 bug)</li>
 *   <li>constructor 设为 WorldUnitEntity::create</li>
 *   <li>Draw.sort(false) 确保建筑和子弹在单位之上</li>
 * </ul>
 */
public class WorldUnitType extends UnityUnitType {

    // World units
    /** 子世界宽度 (tile 数) */
    public int worldWidth, worldHeight;

    public WorldUnitType(String name) {
        super(name);
        constructor = WorldUnitEntity::create;
    }

    /**
     * 重写 drawBody: 在正常单位渲染后, 绘制子世界中的建筑物
     * <p>渲染 hack 原理 (PU132 UnityUnitType.drawBody):
     * <ol>
     *   <li>保存当前 camera.position 和 Draw.proj()</li>
     *   <li>计算偏移让子世界中心对齐单位位置</li>
     *   <li>Draw.proj(camera) + Draw.proj().rotate(r) 旋转投影</li>
     *   <li>Draw.sort(false) 关闭 z 排序, 按 call order 绘制</li>
     *   <li>遍历 buildings 调用 b.draw() (建筑内部自定 z)</li>
     *   <li>遍历 Groups.bullet 绘制子世界炮台发射的子弹</li>
     *   <li>恢复 camera 和 proj</li>
     * </ol>
     *
     * <p>★ 关键修复: 用 Draw.sort(false) 替代原来的 Draw.sort(true),
     * 确保所有建筑和子弹都在单位 body 之上绘制, 不会被 z 排序到单位下方。</p>
     */
    @Override
    public void drawBody(Unit unit) {
        float z = Draw.z();

        // 先执行正常的单位 body 渲染
        super.drawBody(unit);

        // 世界单位: 渲染子世界建筑物
        if (unit instanceof WorldUnitEntity w) {
            Draw.draw(z + 0.0001f, () -> {
                Seq<Building> build = w.buildings;
                World world = w.unitWorld;
                if (world == null || build == null || build.isEmpty()) return;

                float cx = world.width() * Vars.tilesize / 2f, cy = world.height() * Vars.tilesize / 2f;
                float r = w.rotation - 90f;

                // 保存当前投影矩阵
                Mat proj = Tmp.m1.set(Draw.proj());
                Vec2 cam = Core.camera.position;
                float camX = cam.x, camY = cam.y;
                float cw = Core.camera.width / 2f, ch = Core.camera.height / 2f;
                Tmp.v2.set(-cx, -cy).rotate(r);
                Tmp.v1.set(unit).sub(camX, camY).add(cw, ch).add(Tmp.v2);
                cam.set(cw - Tmp.v1.x, ch - Tmp.v1.y);
                Core.camera.update();
                Draw.flush();

                Draw.proj(Core.camera);
                Draw.proj().rotate(r);

                // ★ 关闭 z 排序: 按 call order 绘制, 确保建筑和子弹都在单位之上
                Draw.sort(false);

                // 绘制建筑 (含传送带物品、炮台旋转等内部动画)
                for (int i = 0; i < build.size; i++) {
                    build.get(i).draw();
                }

                // 绘制子世界炮台发射的子弹 (在建筑之上)
                // 遍历所有子弹, 检查 owner 是否为子世界建筑
                for (Bullet bullet : Groups.bullet) {
                    Object owner = bullet.owner;
                    if (owner instanceof Building b && build.contains(b)) {
                        bullet.draw();
                    }
                }

                Draw.flush();
                Draw.sort(true);

                // 恢复 camera 和投影
                cam.set(camX, camY);
                Core.camera.update();
                Draw.proj(proj);
            });
        }

        Draw.z(z);
    }
}

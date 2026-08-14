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
import mindustry.Vars;
import mindustry.core.World;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.ui.Styles;
import zzw.content.units.entities.WorldUnitEntity;

/**
 * 世界单位类型 (PU132 unity.type.UnityUnitType 中 Worldc 渲染部分移植)
 *
 * 继承 UnityUnitType, 添加:
 * - worldWidth / worldHeight: 子世界尺寸 (tile 数)
 * - drawBody() 渲染 hack: 让子世界中的建筑物跟随单位移动和旋转
 *
 * 适配 v155.4 改动:
 * - 去掉 altBatch (用普通 batch, 接受可能的半透明排序 bug)
 * - constructor 设为 WorldUnitEntity::create
 * - 简化渲染 hack, 核心功能保留 (保存/恢复 camera + proj, 旋转投影, 遍历 buildings.draw())
 *
 * 渲染 hack 原理 (PU132 UnityUnitType.drawBody L683-729):
 * 1. 保存当前 camera.position 和 Draw.proj()
 * 2. 计算偏移让子世界中心对齐单位位置
 * 3. Draw.proj(camera) + Draw.proj().rotate(r) 旋转投影
 * 4. 遍历 buildings 调用 b.draw()
 * 5. 恢复 camera 和 proj
 */
public class WorldUnitType extends UnityUnitType {

    // World units
    /** 子世界宽度 (tile 数) */
    public int worldWidth, worldHeight;

    public WorldUnitType(String name) {
        super(name);
        // ★ 适配: 设为 WorldUnitEntity 工厂方法 (PU132 通过注解处理器自动设置)
        constructor = WorldUnitEntity::create;
    }

    /**
     * 重写 drawBody: 在正常单位渲染后, 绘制子世界中的建筑物
     * (原 PU132 UnityUnitType.drawBody 中 `if(unit instanceof Worldc)` 块)
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
                // 子世界未初始化时跳过
                if (world == null || build == null || build.isEmpty()) return;

                float cx = world.width() * Vars.tilesize / 2f, cy = world.height() * Vars.tilesize / 2f;
                float r = w.rotation - 90f;

                // 保存当前投影矩阵
                Mat proj = Tmp.m1.set(Draw.proj());
                Vec2 cam = Core.camera.position;
                float camX = cam.x, camY = cam.y;
                float cw = Core.camera.width / 2f, ch = Core.camera.height / 2f;
                // 子世界中心偏移 (旋转后)
                Tmp.v2.set(-cx, -cy).rotate(r);

                // 计算单位在屏幕坐标中的位置, 加上子世界中心偏移
                Tmp.v1.set(unit).sub(camX, camY).add(cw, ch).add(Tmp.v2);

                // 移动 camera 让子世界中心对齐单位位置
                cam.set(cw - Tmp.v1.x, ch - Tmp.v1.y);
                Core.camera.update();
                Draw.flush();

                // ★ 适配: 省略 altBatch, 用普通 batch (接受可能的半透明排序 bug)

                // 设置投影: 先用 camera 投影, 再旋转 r 度
                Draw.proj(Core.camera);
                Draw.proj().rotate(r);
                Draw.sort(true);

                // 遍历子世界建筑物, 逐个绘制
                // ★ 不设固定 Draw.z(Layer.block), 让每个建筑自己决定渲染层级
                // 这样传送带上的物品、炮台旋转等内部动画能正确渲染
                for (int i = 0; i < build.size; i++) {
                    build.get(i).draw();
                }

                // Should fix the blending bug.
                Draw.z(9999f);
                Draw.color(Color.clear);
                Fill.rect(0, 0, 0, 0);

                Draw.reset();
                Draw.flush();
                Draw.sort(false);

                // 恢复 camera 和投影
                cam.set(camX, camY);
                Core.camera.update();
                Draw.proj(proj);
            });
        }

        Draw.z(z);
    }

    /**
     * 鼠标悬停显示：单位状态 + 鼠标所在位置的建筑状态
     * <p>如果鼠标悬停在单位上的某个建筑位置，额外显示该建筑的信息。</p>
     */
    @Override
    public void display(Unit unit, Table table){
        super.display(unit, table);

        if(unit instanceof WorldUnitEntity w && w.unitWorld != null && !w.buildings.isEmpty()){
            table.row();
            table.table(Styles.grayPanel, t -> {
                t.add("[accent]单位上的建筑 (" + w.buildings.size + ")").left().row();
                // 检测鼠标在哪个建筑上
                float mx = Core.input.mouseWorldX(), my = Core.input.mouseWorldY();
                mindustry.gen.Building hovered = w.buildingAt(mx, my);
                if(hovered != null){
                    t.table(bt -> {
                        bt.left();
                        bt.image(hovered.block.uiIcon).size(24f);
                        bt.add(hovered.block.localizedName).left();
                        bt.row();
                        bt.add("[lightgray]血量: " + (int)hovered.health + "/" + (int)hovered.maxHealth).left().row();
                        if(hovered.items != null && hovered.items.total() > 0){
                            bt.add("[lightgray]物品: " + hovered.items.total()).left();
                        }
                        if(hovered.power != null){
                            bt.add("[lightgray]电力: " + (int)(hovered.power.status * 100) + "%").left().row();
                        }
                    }).pad(4).left();
                } else {
                    // 没有悬停在特定建筑上时，显示建筑列表摘要
                    t.table(bt -> {
                        bt.left().top();
                        int cols = 6;
                        int i = 0;
                        for(Building b : w.buildings){
                            if(i++ % cols == 0) bt.row();
                            bt.image(b.block.uiIcon).size(16f).pad(2);
                        }
                    }).pad(4).left();
                }
            }).growX().padTop(4);
        }
    }
}

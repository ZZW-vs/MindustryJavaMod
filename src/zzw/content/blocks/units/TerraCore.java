package zzw.content.blocks.units;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.scene.ui.layout.Table;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Icon;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.type.Item;
import mindustry.ui.Styles;
import mindustry.world.Block;
import zzw.content.type.WorldUnitType;
import zzw.content.units.entities.WorldUnitEntity;

/**
 * 大地核心方块 (PU132 unity.world.blocks.units.TerraCore 简化移植)
 *
 * 功能: 点击按钮召唤一个携带建筑物的世界单位 (terra)
 * - configurable=true, 点击按钮创建 terra 单位并调用 setup() 初始化子世界
 * - 创建后自动接收单位身上的物品
 *
 * 适配 v155.4:
 * - Worldc 接口 → WorldUnitEntity 直接类型转换
 * - 其余 API 兼容, 无需修改
 */
public class TerraCore extends Block {
    /** 召唤的单位类型 (由 Z_Blocks.load() 设置) */
    public WorldUnitType type;

    public TerraCore(String name) {
        super(name);
        update = true;
        configurable = true;
        hasItems = true;
        itemCapacity = 150;
        separateItemCapacity = true;
    }

    public class TerraCoreBuild extends Building {
        /** 当前绑定的世界单位 (null = 尚未召唤) */
        WorldUnitEntity unit;
        /** 读档时待绑定的单位 id (建筑先于单位加载, 延迟到 updateTile 中解析) */
        int pendingUnitId = -1;

        /** 数据版本 1: 存档额外写入绑定的单位 id (旧存档 revision=0 不会多读字节) */
        @Override
        public byte version() {
            return 1;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            // ★ 保存绑定的单位 id, 重进地图后恢复核心与 Terra 的绑定
            write.i(unit != null && unit.isAdded() ? unit.id : -1);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            if (revision >= 1) {
                pendingUnitId = read.i();
            }
        }

        @Override
        public void buildConfiguration(Table table) {
            table.button(Icon.units, Styles.cleari, () -> {
                if (type == null) return;
                Unit u = type.create(team);
                if (u instanceof WorldUnitEntity) {
                    u.x = x;
                    u.y = y;
                    u.rotation = 90f;
                    unit = (WorldUnitEntity) u;
                    pendingUnitId = -1;
                    u.add();
                    // ★ 初始化子世界 + 迁移附近建筑物
                    ((WorldUnitEntity) u).setup();
                }
            }).size(50f);
        }

        @Override
        public void draw() {
            if (unit == null) {
                // 未召唤时显示半透明单位预览
                float z = Draw.z();
                Draw.z(Layer.debris);
                Draw.color(Color.white, 0.2f);

                if (type != null) Draw.rect(type.fullIcon, x, y, 0f);

                Draw.z(z);
                Draw.reset();
            }
            super.draw();
        }

        @Override
        public void updateTile() {
            // ★ 读档后延迟绑定: 建筑先于单位加载 (map 区块在 entities 之前),
            // 等 unitId 对应的世界单位出现在 Groups.unit 后再恢复绑定
            if (unit == null && pendingUnitId != -1) {
                for (Unit u : Groups.unit) {
                    if (u.id == pendingUnitId && u instanceof WorldUnitEntity) {
                        unit = (WorldUnitEntity) u;
                        pendingUnitId = -1;
                        break;
                    }
                }
            }
            if (unit != null) {
                // 接收单位身上的物品到方块中
                Item item = unit.item();
                if (item != null && items.get(item) < itemCapacity) {
                    int amount = acceptStack(unit.item(), unit.stack().amount, unit);
                    if (amount > 0) {
                        handleStack(item, amount, unit);
                        unit.stack().amount -= amount;
                    }
                }
            }
        }
    }
}

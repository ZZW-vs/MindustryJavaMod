package zzw.content.mechanics;

import arc.math.Mathf;
import mindustry.content.Items;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import zzw.content.Z_Items;

/**
 * 机械系统方块定义
 * 
 * 主要功能:
 * 1. 动力系统: 提供机械动力的生成、传递、处理
 * 2. 传动系统: 支持动力的传递和分配
 * 3. 可视化: 通过齿轮旋转显示动力流动
 * 4. 配置系统: 支持动力参数的实时调整
 * 
 * 系统组件:
 * - 应力源 (StressSource): 动力生成设备，可配置转速(0-256)
 * - 传动箱 (TransmissionBox): 动力传递设备，为相邻工厂提供加速
 * - 齿轮 (Cogwheel): 可视化设备，旋转表示动力流动
 * 
 * 技术特点:
 * - 使用MechanicalBuilds系统实现机械逻辑
 * - 支持实时配置调整
 * - 集成视觉反馈系统
 * - 支持相邻设备加速效果
 * 
 * 工作原理:
 * - 应力源产生机械动力，转速可配置
 * - 传动箱将动力传递给相邻设备
 * - 齿轮通过旋转动画显示动力流动
 * - 系统支持动力加速和分配
 * 
 * 配置选项:
 * - 应力源: 转速配置 (0-256)
 * - 传动箱: 自动传递动力
 * - 齿轮: 视觉效果，无配置选项
 * 
 * 使用场景:
 * - 机械工厂系统
 * - 动力传输网络
 * - 自动化生产线
 * - 机械装置演示
 */
public class Z_Mechanics {
    public static Block stressSource;
    public static Block transmissionBox;
    public static Block cogwheel;

    public static void load() {
        // 应力源: 提供动力, 可配置转速 (0-256)
        stressSource = new Block("stress_source") {{
            requirements(Category.crafting, ItemStack.with(Items.lead, 100, Items.copper, 80));
            size = 1;
            health = 500;
            solid = true;
            update = true;
            configurable = true;
            config(Float.class, (MechanicalBuilds.StressSourceBuild build, Float value) ->
                    build.setTargetSpeed(Mathf.clamp(value, 0f, 256f)));
        }};
        stressSource.buildType = MechanicalBuilds.StressSourceBuild::new;

        // 传动箱: 传递机械动力, 也为相邻工厂提供加速
        transmissionBox = new Block("transmission_box") {{
            requirements(Category.crafting, ItemStack.with(Items.lead, 10, Z_Items.Iron, 5));
            size = 1;
            health = 80;
            solid = true;
            update = true;
        }};
        transmissionBox.buildType = MechanicalBuilds.TransmissionBoxBuild::new;

        // 齿轮: 视觉上旋转表示动力流动
        cogwheel = new Block("cogwheel-z") {{
            requirements(Category.crafting, ItemStack.with(Items.copper, 15, Z_Items.Iron, 10));
            size = 1;
            health = 120;
            solid = true;
            update = true;
        }};
        cogwheel.buildType = MechanicalBuilds.CogwheelBuild::new;
    }
}

---
name: "mindustry-mod-dev"
description: "Mindustry mod开发助手,提供API参考、模板和最佳实践。当用户需要创建方块、物品、机械组件或询问Mindustry mod开发问题时调用。"
---

# Mindustry Mod 开发助手

专门用于Mindustry Java模组开发的辅助工具,提供API参考、代码模板和最佳实践指导。

## 项目结构

当前项目采用标准Mindustry mod结构:
```
src/zzw/
├── TestMod.java              # 主类,加载所有内容
└── content/
    ├── Z_Items.java          # 物品定义
    ├── Z_Mine.java           # 矿物定义
    ├── Z_Factory.java        # 工厂方块
    ├── Z_Blocks.java         # 其他方块
    └── mechanics/
        ├── Z_Mechanics.java  # 机械系统定义
        ├── MechanicalComponentBuild.java  # 机械组件基类
        └── MechanicalBuilds.java         # 机械组件实现
```

## 核心概念

### 1. 方块类型
- **Block**: 基础方块类
- **Building**: 方块的实例类
- **继承体系**: 通过`buildType`指定Building类

### 2. 机械系统
项目实现了类似Create模组的机械传动系统:
- **MechanicalComponentBuild**: 所有机械组件的基类
- **StressSourceBuild**: 动力源,提供转速和应力
- **CogwheelBuild**: 齿轮,传递动力
- **TransmissionBoxBuild**: 传动箱,传递应力

### 3. 网络系统
机械组件通过`networkId`连接成网络,实现应力和转速的同步传输。

## 常用API

### 方块创建
```java
Block block = new Block("block_name") {{
    requirements(Category.crafting, ItemStack.with(Items.copper, 10));
    size = 1;
    health = 100;
    solid = true;
    update = true;
    configurable = true;
    buildVisibility = BuildVisibility.shown;
}};
block.buildType = CustomBuild::new;
```

### Building类基础方法
```java
public class CustomBuild extends Building {
    @Override
    public void update() {
        // 每帧调用
    }
    
    @Override
    public void draw() {
        // 绘制方块
    }
    
    @Override
    public void buildConfiguration(Table table) {
        // 配置UI
    }
}
```

### 物品定义
```java
public static Item ironIngot = new Item("iron_ingot"){{
    localizedName = "铁锭";
    description = "基础金属原料";
    hardness = 2;
    cost = 1.5f;
}};
```

## 项目特定模式

### 机械组件扩展
要添加新的机械组件:
1. 继承`MechanicalComponentBuild`
2. 在`Z_Mechanics`中定义方块
3. 设置`buildType`为新的Build类

### 纹理处理
- 默认纹理: `block.region`
- 自定义纹理: `Core.atlas.find("texture_name")`
- 项目使用: `mechanical/cogwheel-z`

### 常量定义
```java
private static final float SPEED_THRESHOLD = 0.01f;  // 转速阈值
private static final float EFFICIENCY_LOSS_PER_BLOCK = 0.05f;  // 效率损失
private static final float INFINITY_STRESS = 10000f;  // 无限应力
```

## 开发规范

### 代码风格
- 使用中文注释
- 常量使用`UPPER_CASE`
- 私有字段使用`camelCase`
- 公共方法使用`camelCase`

### 文件命名
- 方块定义: `Z_<Category>.java`
- Build类: 放在对应包中
- 基类: 使用描述性名称

### 加载顺序
在`TestMod.loadContent()`中按依赖顺序加载:
1. 物品 (Z_Items)
2. 矿物 (Z_Mine)
3. 工厂 (Z_Factory)
4. 机械系统 (Z_Mechanics)
5. 其他方块 (Z_Blocks)

## 常见任务

### 添加新方块
1. 在对应的`Z_*.java`中定义方块
2. 设置属性(requirements, size, health等)
3. 创建Build类(如果需要自定义行为)
4. 设置`buildType`
5. 在`TestMod.loadContent()`中调用加载方法

### 添加新物品
1. 在`Z_Items.java`中定义物品
2. 设置属性(localizedName, description等)
3. 如果是矿物,在`Z_Mine.java`中定义矿石

### 添加机械组件
1. 继承`MechanicalComponentBuild`
2. 实现`update()`, `draw()`, `display()`等方法
3. 在`Z_Mechanics`中定义方块
4. 设置`buildType`

## 构建和部署

### 构建命令
```bash
# Desktop版本
./gradlew jar

# Android版本
./gradlew jarAndroid

# 完整部署
./gradlew deploy
```

### 输出文件
- `build/libs/TestModDesktop.jar` - Desktop版本
- `build/libs/TestModAndroid.jar` - Android版本
- `build/libs/TestMod.jar` - 完整版本

## 调试技巧

### 日志输出
```java
import arc.util.Log;
Log.info("Debug message: " + value);
```

### 网络调试
检查机械网络连接:
```java
Log.info("Network ID: " + networkId);
Log.info("Rotation Speed: " + rotationSpeed);
Log.info("Stress: " + stress);
```

## 注意事项

1. **纹理路径**: 确保纹理文件在`assets/`目录下
2. **网络同步**: 机械组件修改后需要调用`markNetworkForUpdate()`
3. **性能优化**: 避免在`update()`中进行大量计算
4. **内存管理**: 及时清理不再使用的资源

## 当前项目特性

- 机械传动系统(齿轮、传动箱、应力源)
- 金属加工系统(铁锭、金锭、铜板等)
- 防御方块(铜块、铁块等)
- 物流设备(传送带)

## 版本兼容性

- 最低游戏版本: v150.1
- Java版本: 8 (兼容性)
- 构建工具: Gradle

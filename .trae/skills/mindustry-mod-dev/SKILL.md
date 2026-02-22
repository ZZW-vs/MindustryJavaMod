---
name: "mindustry-mod-dev"
description: "Mindustry mod开发助手,提供API参考、模板和最佳实践。当用户需要创建方块、物品、机械组件、单位、状态效果或询问Mindustry mod开发问题时调用。"
---

# Mindustry Mod 开发助手

专门用于Mindustry Java模组开发的辅助工具，提供API参考、代码模板和最佳实践指导。基于饱和火力和Extra Utilities等优秀mod的开发经验。

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

### 资源文件结构（推荐）
```
assets/
├── bundles/                 # 本地化文件
│   ├── bundle.properties
│   └── bundle_zh_CN.properties
├── sprites/                # 精灵图
│   ├── blocks/
│   ├── items/
│   ├── units/
│   └── ui/
├── scripts/                # 脚本文件（高级功能）
│   ├── block/
│   ├── unit/
│   └── other/
├── maps/                   # 地图文件
└── shaders/                # 自定义着色器
```

## 核心概念

### 1. 方块类型体系

#### 基础方块类型
- **Block**: 最基础的方块类
- **Building**: 方块的实例类
- **继承体系**: 通过`buildType`指定Building类

#### 生产方块
- **GenericCrafter**: 通用生产器
- **Separator**: 分离器
- **CryoFridge**: 冷冻机
- **HeatCrafter**: 热量生产器

#### 炮塔方块
- **Turret**: 基础炮塔
- **PowerTurret**: 电力炮塔
- **LaserTurret**: 激光炮塔
- **ContinuousLaserTurret**: 连续激光炮塔

#### 防御方块
- **Wall**: 墙壁
- **Door**: 门
- **ForceProjector**: 力场发生器

#### 物流方块
- **Conveyor**: 传送带
- **Router**: 路由器
- **Distributor**: 分配器
- **Unloader**: 卸载器

#### 电力方块
- **PowerNode**: 电力节点
- **PowerGenerator**: 发电机
- **Battery**: 电池

#### 钻头方块
- **Drill**: 钻头
- **WaterExtractor**: 抽水机
- **AirUnitFactory**: 空中单位工厂
- **GroundUnitFactory**: 地面单位工厂

### 2. 机械系统（Create风格）

项目实现了类似Create模组的机械传动系统:
- **MechanicalComponentBuild**: 所有机械组件的基类
- **StressSourceBuild**: 动力源,提供转速和应力
- **CogwheelBuild**: 齿轮,传递动力
- **TransmissionBoxBuild**: 传动箱,传递应力

### 3. 网络系统
机械组件通过`networkId`连接成网络,实现应力和转速的同步传输。

### 4. 高级特性

#### 状态效果系统
- 自定义状态效果（修复、腐蚀、强化等）
- 状态效果组合和互斥
- 粒子效果集成

#### 粒子系统
- **ParticleEffect**: 粒子效果
- **MultiEffect**: 多重效果
- **WaveEffect**: 波浪效果
- 自定义粒子参数

#### 单位系统
- 自定义单位类型
- 单位AI和行为
- 单位武器系统
- 单位能力

#### 自定义绘制器
- **DrawMulti**: 多重绘制
- **DrawTurret**: 炮塔绘制
- **DrawLiquidRegion**: 液体绘制
- 自定义RegionPart

## 常用API

### 方块创建模板

#### 基础方块
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

#### 生产方块
```java
Block furnace = new GenericCrafter("furnace") {{
    requirements(Category.crafting, ItemStack.with(Items.copper, 50));
    size = 2;
    health = 200;
    hasPower = true;
    hasLiquids = true;
    hasItems = true;
    craftTime = 60;
    itemCapacity = 20;
    liquidCapacity = 60;
    
    consumes.power(2);
    consumes.items(ItemStack.with(Items.scrap, 2));
    produces.liquids(Liquids.slag, 1.5f);
    
    updateEffect = Fx.smeltsmoke;
    ambientSound = Sounds.smelter;
}};
furnace.buildType = FurnaceBuild::new;
```

#### 炮塔方块
```java
Block turret = new PowerTurret("turret") {{
    requirements(Category.turret, ItemStack.with(Items.copper, 100));
    size = 2;
    health = 500;
    range = 180;
    reload = 30;
    shootSound = Sounds.shoot;
    
    consumes.power(2);
    ammo(Items.copper, Bullets.standardCopper);
    ammo(Items.pyratite, Bullets.standardIncendiary);
}};
turret.buildType = TurretBuild::new;
```

### Building类基础方法

#### 核心生命周期方法
```java
public class CustomBuild extends Building {
    @Override
    public void created() {
        // 创建时调用一次
    }
    
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
    
    @Override
    public void onRemoved() {
        // 移除时调用
    }
    
    @Override
    public void onProximityAdded() {
        // 附近有新方块时调用
    }
    
    @Override
    public void display(Table table) {
        // 显示信息面板
    }
}
```

#### 生产方块Build类
```java
public class FurnaceBuild extends GenericCrafterBuild {
    @Override
    public void updateTile() {
        super.updateTile();
        // 自定义更新逻辑
    }
    
    @Override
    public void draw() {
        super.draw();
        // 自定义绘制
    }
}
```

#### 炮塔Build类
```java
public class TurretBuild extends PowerTurretBuild {
    @Override
    public void updateTile() {
        super.updateTile();
        // 自定义炮塔逻辑
    }
    
    @Override
    protected void handleBullet(Bullet bullet, float offsetX, float offsetY, float angleOffset) {
        super.handleBullet(bullet, offsetX, offsetY, angleOffset);
        // 自定义子弹处理
    }
}
```

### 物品定义

#### 基础物品
```java
public static Item ironIngot = new Item("iron_ingot"){{
    localizedName = "铁锭";
    description = "基础金属原料";
    hardness = 2;
    cost = 1.5f;
    color = Color.valueOf("C0C0C0");
    alwaysUnlocked = false;
    researchCostMultiplier = 0.5f;
}};
```

#### 高级物品属性
```java
public static Item advancedItem = new Item("advanced_item"){{
    localizedName = "高级物品";
    description = "具有特殊属性的物品";
    hardness = 4;
    cost = 3.0f;
    healthScaling = 0.6f;
    charge = 0.45f;
    explosiveness = 0.2f;
    flammability = 0.1f;
    radioactivity = 0.0f;
    barColor = Color.valueOf("FF6B6B");
    alwaysUnlocked = false;
}};
```

### 液体定义

#### 基础液体
```java
public static Liquid nanoFluid = new Liquid("nano_fluid"){{
    localizedName = "纳米流体";
    description = "纳米机器人组成的流体";
    color = Color.valueOf("7CF389FF");
    lightColor = Color.valueOf("7CF38970");
    temperature = 0.3f;
    heatCapacity = 1.45f;
    viscosity = 0.3f;
    effect = StatusEffects.repairing;
    alwaysUnlocked = false;
}};
```

### 状态效果定义

#### 基础状态效果
```java
public static StatusEffect repairEffect = new StatusEffect("repair_effect"){{
    localizedName = "修复";
    damage = -4f;
    healthMultiplier = 1.5f;
    effectChance = 0.3f;
    
    effect = new ParticleEffect(){{
        particles = 1;
        baseLength = 30f;
        length = -30f;
        lifetime = 30f;
        spin = 6f;
        interp = Interp.pow3Out;
        sizeInterp = Interp.pow3In;
        region = "triangle";
        sizeFrom = 1f;
        sizeTo = 0f;
        colorFrom = Color.valueOf("97FFA8");
        colorTo = Color.valueOf("97FFA8");
    }};
}};
```

### 单位定义

#### 基础单位
```java
public static UnitType customUnit = new UnitType("custom_unit", UnitTypes.dagger){{
    localizedName = "自定义单位";
    description = "具有特殊能力的单位";
    speed = 0.8f;
    hitSize = 12f;
    health = 200f;
    armor = 2f;
    buildSpeed = 1.2f;
    mineSpeed = 1.5f;
    itemCapacity = 30;
    engineOffset = 5.5f;
    flying = false;
    
    weapons.add(new Weapon(){{
        name = "weapon_name";
        reload = 30f;
        x = 3f;
        y = 1f;
        shootSound = Sounds.shoot;
        bullet = Bullets.standardCopper;
    }});
}};
```

## 项目特定模式

### 机械组件扩展

要添加新的机械组件:
1. 继承`MechanicalComponentBuild`
2. 在`Z_Mechanics`中定义方块
3. 设置`buildType`为新的Build类

#### 示例：添加新齿轮
```java
public static class LargeCogwheelBuild extends MechanicalComponentBuild {
    private float rotation = 0f;
    
    @Override
    public void update() {
        super.update();
        if (rotationSpeed > SPEED_THRESHOLD) {
            rotation += rotationSpeed * FRAME_TIME;
        }
    }
    
    @Override
    public void draw() {
        Draw.rect(block.region, x, y, rotation);
        if (stress > STRESS_THRESHOLD) {
            Draw.color(STRESS_COLOR);
            Lines.stroke(2f * stress);
            Lines.circle(x, y, 8f + stress * 2f);
            Draw.color();
        }
    }
}
```

### 纹理处理

#### 默认纹理
```java
block.region = Core.atlas.find("block_name");
```

#### 自定义纹理
```java
block.region = Core.atlas.find("mechanical/cogwheel-z");
```

#### 多重纹理绘制
```java
@Override
public void draw() {
    Draw.rect(block.region, x, y);
    Draw.rect(block.topRegion, x, y);
    if (hasLiquids) {
        Draw.color(liquids.current().color);
        Draw.rect(block.liquidRegion, x, y);
        Draw.color();
    }
}
```

### 常量定义

#### 机械系统常量
```java
private static final float SPEED_THRESHOLD = 0.01f;  // 转速阈值
private static final float EFFICIENCY_LOSS_PER_BLOCK = 0.05f;  // 效率损失
private static final float INFINITY_STRESS = 10000f;  // 无限应力
private static final float STRESS_THRESHOLD = 0.5f;  // 高应力阈值
private static final int SNAP_INTERVAL = 32;  // 转速吸附间隔
```

#### 游戏常量
```java
private static final float FRAME_TIME = 0.016f;  // 假设60fps
private static final float WELCOME_DIALOG_DELAY = 10f;
private static final float CLOSE_BUTTON_DELAY = 100f;
```

## 高级功能

### 自定义粒子效果

#### 粒子效果创建
```java
Effect customEffect = new Effect(30f, e -> {
    Draw.color(Color.valueOf("97FFA8"));
    for (int i = 0; i < 5; i++) {
        Angles.randLenVectors(e.id, 5, 10f + e.fin() * 20f, (x, y) -> {
            Fill.circle(e.x + x, e.y + y, 2f * e.fout());
        });
    }
    Draw.color();
});
```

#### 多重效果
```java
Effect multiEffect = new Effect(60f, e -> {
    // 效果1：粒子
    Draw.color(Color.valueOf("BFFFDB"));
    Fill.circle(e.x, e.y, 10f * e.fout());
    
    // 效果2：波浪
    Draw.color(Color.valueOf("97FFA8"));
    Lines.stroke(3f * e.fout());
    Lines.circle(e.x, e.y, 20f * e.fin());
    Draw.color();
});
```

### 自定义子弹类型

#### 基础子弹
```java
BulletType customBullet = new BulletType(3f, 20f){{
    lifetime = 60f;
    damage = 20f;
    speed = 3f;
    hitEffect = Fx.hitBulletSmall;
    despawnEffect = Fx.hitBulletSmall;
    width = 7f;
    height = 9f;
    frontColor = Color.valueOf("FFFFFF");
    backColor = Color.valueOf("97FFA8");
}};
```

#### 高级子弹（导弹）
```java
BulletType missileBullet = new MissileBulletType(3f, 30f){{
    lifetime = 80f;
    damage = 30f;
    speed = 3f;
    homingPower = 0.05f;
    homingRange = 200f;
    splashDamage = 15f;
    splashDamageRadius = 30f;
    hitEffect = Fx.explosion;
    despawnEffect = Fx.explosion;
    frontColor = Color.valueOf("FFFFFF");
    backColor = Color.valueOf("97FFA8");
    trailColor = Color.valueOf("97FFA8");
    trailLength = 10f;
    trailWidth = 2f;
}};
```

### 自定义武器

#### 单位武器
```java
Weapon customWeapon = new Weapon(){{
    name = "custom_weapon";
    reload = 30f;
    x = 3f;
    y = 1f;
    shootSound = Sounds.shoot;
    recoil = 2f;
    shake = 1f;
    bullet = customBullet;
    shootY = 2f;
    mirror = true;
    top = false;
}};
```

### 自定义绘制器

#### 多重绘制
```java
@Override
public void draw() {
    // 基础绘制
    Draw.rect(block.region, x, y);
    
    // 液体绘制
    if (hasLiquids && liquids.currentAmount > 0.01f) {
        Draw.color(liquids.current().color);
        Draw.alpha(liquids.currentAmount / liquidCapacity);
        Draw.rect(block.liquidRegion, x, y);
        Draw.color();
    }
    
    // 热量绘制
    if (warmup > 0.01f) {
        Draw.color(Color.valueOf("FF4040"), warmup);
        Draw.rect(block.heatRegion, x, y);
        Draw.color();
    }
}
```

#### 自定义RegionPart
```java
@Override
public void draw() {
    Draw.rect(block.region, x, y);
    
    // 动态部件
    float progress = (Time.time % 60f) / 60f;
    float offsetX = Mathf.sin(progress * Mathf.pi2) * 5f;
    Draw.rect(block.partRegion, x + offsetX, y);
}
```

## 开发规范

### 代码风格

#### 命名规范
- 类名: `PascalCase` (如 `CustomBuild`)
- 方法名: `camelCase` (如 `updateTile`)
- 变量名: `camelCase` (如 `rotationSpeed`)
- 常量名: `UPPER_SNAKE_CASE` (如 `SPEED_THRESHOLD`)
- 包名: `lowercase` (如 `zzw.content.blocks`)

#### 注释规范
- 使用中文注释
- 类注释: 描述类的功能和用途
- 方法注释: 描述方法的功能、参数和返回值
- 行内注释: 解释复杂逻辑

```java
/**
 * 齿轮方块实现
 * 特性：
 * 1. 整个方块旋转动画，转速越大转得越快
 * 2. 机械动力传输
 */
public static class CogwheelBuild extends MechanicalComponentBuild {
    private float rotation = 0f;  // 当前旋转角度
    
    @Override
    public void update() {
        super.update();
        // 更新旋转角度 - 转速越大，旋转越快
        if (rotationSpeed > SPEED_THRESHOLD) {
            rotation += rotationSpeed * FRAME_TIME;
        }
    }
}
```

### 文件命名

#### 方块定义文件
- `Z_Blocks.java` - 通用方块
- `Z_Factory.java` - 工厂方块
- `Z_Turrets.java` - 炮塔方块
- `Z_Walls.java` - 墙壁方块

#### Build类
- 放在对应包中
- 使用描述性名称
- 继承对应的基类

### 加载顺序

在`TestMod.loadContent()`中按依赖顺序加载:
1. 物品 (Z_Items)
2. 液体 (Z_Liquids)
3. 状态效果 (Z_StatusEffects)
4. 矿物 (Z_Mine)
5. 工厂 (Z_Factory)
6. 机械系统 (Z_Mechanics)
7. 炮塔 (Z_Turrets)
8. 其他方块 (Z_Blocks)
9. 单位 (Z_Units)

## 常见任务

### 添加新方块

#### 步骤
1. 在对应的`Z_*.java`中定义方块
2. 设置属性(requirements, size, health等)
3. 创建Build类(如果需要自定义行为)
4. 设置`buildType`
5. 在`TestMod.loadContent()`中调用加载方法

#### 示例：添加生产方块
```java
public static Block advancedFurnace;

public static void load() {
    advancedFurnace = new GenericCrafter("advanced_furnace"){{
        requirements(Category.crafting, ItemStack.with(Items.copper, 100, Items.lead, 80));
        size = 3;
        health = 400;
        hasPower = true;
        hasLiquids = true;
        hasItems = true;
        craftTime = 45;
        itemCapacity = 30;
        liquidCapacity = 90;
        
        consumes.power(3);
        consumes.items(ItemStack.with(Items.scrap, 3));
        produces.liquids(Liquids.slag, 2.0f);
        
        updateEffect = Fx.smeltsmoke;
        ambientSound = Sounds.smelter;
    }};
    advancedFurnace.buildType = AdvancedFurnaceBuild::new;
}
```

### 添加新物品

#### 步骤
1. 在`Z_Items.java`中定义物品
2. 设置属性(localizedName, description等)
3. 如果是矿物,在`Z_Mine.java`中定义矿石

#### 示例：添加高级物品
```java
public static Item titaniumAlloy = new Item("titanium_alloy"){{
    localizedName = "钛合金";
    description = "高强度轻质合金，用于制造高级设备";
    hardness = 4;
    cost = 2.5f;
    color = Color.valueOf("A8D8E8");
    healthScaling = 0.7f;
    alwaysUnlocked = false;
}};
```

### 添加新液体

#### 步骤
1. 在`Z_Liquids.java`中定义液体
2. 设置属性(color, temperature等)
3. 关联状态效果(可选)

#### 示例：添加特殊液体
```java
public static Liquid healingFluid = new Liquid("healing_fluid"){{
    localizedName = "治疗液";
    description = "具有治疗效果的液体";
    color = Color.valueOf("97FFA8FF");
    lightColor = Color.valueOf("97FFA870");
    temperature = 0.5f;
    heatCapacity = 1.2f;
    viscosity = 0.4f;
    effect = StatusEffects.repairing;
    alwaysUnlocked = false;
}};
```

### 添加机械组件

#### 步骤
1. 继承`MechanicalComponentBuild`
2. 实现`update()`, `draw()`, `display()`等方法
3. 在`Z_Mechanics`中定义方块
4. 设置`buildType`

#### 示例：添加变速箱
```java
public static class GearboxBuild extends MechanicalComponentBuild {
    private float gearRatio = 1.0f;
    
    @Override
    public void update() {
        super.update();
        // 变速逻辑
        if (rotationSpeed > SPEED_THRESHOLD) {
            rotationSpeed *= gearRatio;
        }
    }
    
    @Override
    public void buildConfiguration(Table table) {
        table.slider(0.5f, 2.0f, 0.1f, gearRatio, this::setGearRatio);
    }
    
    public void setGearRatio(float ratio) {
        this.gearRatio = ratio;
        markNetworkForUpdate();
    }
}
```

### 添加状态效果

#### 步骤
1. 在`Z_StatusEffects.java`中定义状态效果
2. 设置属性(damage, effect等)
3. 关联到武器或液体

#### 示例：添加腐蚀效果
```java
public static StatusEffect corrosion = new StatusEffect("corrosion"){{
    localizedName = "腐蚀";
    damage = 0.5f;
    healthMultiplier = 0.8f;
    effectChance = 0.5f;
    
    effect = new ParticleEffect(){{
        particles = 3;
        baseLength = 15f;
        length = -15f;
        lifetime = 20f;
        spin = 3f;
        sizeFrom = 2f;
        sizeTo = 0f;
        colorFrom = Color.valueOf("FF6B6B");
        colorTo = Color.valueOf("FF6B6B");
    }};
}};
```

### 添加单位

#### 步骤
1. 在`Z_Units.java`中定义单位
2. 设置属性(speed, health等)
3. 添加武器和能力

#### 示例：添加高级单位
```java
public static UnitType eliteUnit = new UnitType("elite_unit", UnitTypes.dagger){{
    localizedName = "精英单位";
    description = "装备先进武器的高性能单位";
    speed = 1.0f;
    hitSize = 14f;
    health = 400f;
    armor = 4f;
    buildSpeed = 1.5f;
    mineSpeed = 2.0f;
    itemCapacity = 40;
    
    weapons.add(new Weapon(){{
        name = "elite_weapon";
        reload = 20f;
        x = 4f;
        y = 1.5f;
        shootSound = Sounds.shoot;
        bullet = new BulletType(4f, 40f){{
            lifetime = 50f;
            damage = 40f;
            speed = 4f;
            splashDamage = 20f;
            splashDamageRadius = 40f;
        }};
    }});
}};
```

## 构建和部署

### 构建命令

#### Desktop版本
```bash
./gradlew jar
```

#### Android版本
```bash
./gradlew jarAndroid
```

#### 完整部署
```bash
./gradlew deploy
```

### 输出文件

- `build/libs/TestModDesktop.jar` - Desktop版本
- `build/libs/TestModAndroid.jar` - Android版本
- `build/libs/TestMod.jar` - 完整版本

### 版本管理

在`build.gradle`中设置版本号:
```gradle
version '1.2.0'
```

在`mod.hjson`中设置mod信息:
```hjson
name: "TestMod"
version: 1.2.0
author: "郑zip"
minGameVersion: 150
```

## 调试技巧

### 日志输出

#### 基础日志
```java
import arc.util.Log;

Log.info("Debug message: " + value);
Log.debug("Detailed debug info");
Log.warn("Warning message");
Log.err("Error message");
```

#### 网络调试
```java
Log.info("Network ID: " + networkId);
Log.info("Rotation Speed: " + rotationSpeed);
Log.info("Stress: " + stress);
```

#### 性能调试
```java
long startTime = Time.nanos();
// 执行代码
long endTime = Time.nanos();
Log.info("Execution time: " + (endTime - startTime) / 1_000_000f + "ms");
```

### 常见问题排查

#### 方块不显示
1. 检查`buildVisibility`设置
2. 确认`requirements`正确
3. 验证`category`设置
4. 检查是否正确调用`load()`方法

#### 纹理加载失败
1. 确认纹理文件在`assets/sprites/`目录下
2. 检查纹理路径是否正确
3. 验证纹理文件格式（PNG）
4. 使用`Core.atlas.find()`调试

#### 网络同步问题
1. 确保调用`markNetworkForUpdate()`
2. 检查`networkId`分配
3. 验证邻居缓存更新
4. 添加日志跟踪网络变化

## 注意事项

### 纹理路径
- 确保纹理文件在`assets/sprites/`目录下
- 使用正确的纹理路径格式
- 支持的格式: PNG, JPG

### 网络同步
- 机械组件修改后需要调用`markNetworkForUpdate()`
- 避免频繁的网络更新
- 使用合理的更新频率

### 性能优化
- 避免在`update()`中进行大量计算
- 使用缓存减少重复计算
- 合理使用`Time.time`和`Time.delta`
- 避免在`draw()`中创建新对象

### 内存管理
- 及时清理不再使用的资源
- 避免内存泄漏
- 使用对象池复用对象
- 注意大型数据结构的生命周期

### 兼容性
- 测试不同Mindustry版本的兼容性
- 使用稳定的API
- 避免使用已废弃的方法
- 提供版本兼容性检查

## 当前项目特性

### 已实现功能
- 机械传动系统(齿轮、传动箱、应力源)
- 金属加工系统(铁锭、金锭、铜板等)
- 防御方块(铜块、铁块等)
- 物流设备(传送带)

### 计划功能
- 高级生产设备
- 自定义炮塔系统
- 特殊单位类型
- 自定义状态效果
- 粒子效果系统

## 版本兼容性

- 最低游戏版本: v150.1
- Java版本: 8 (兼容性)
- 构建工具: Gradle
- 推荐IDE: IntelliJ IDEA

## 参考资源

### 官方文档
- Mindustry Wiki: https://mindustrygame.github.io/wiki/
- Mindustry Modding: https://github.com/Anuken/Mindustry/wiki

### 优秀Mod参考
- 饱和火力 (Saturation Firepower): 复杂的炮塔和单位系统
- Extra Utilities: 实用的扩展功能
- New Horizon: 高级单位和地图

### 社区资源
- Mindustry Discord: https://discord.gg/mindustry
- Mindustry Modding Discord: https://discord.gg/mindustry-modding
- B站Mindustry社区: 搜索"Mindustry mod开发"

## 最佳实践总结

1. **模块化设计**: 将功能分解为独立的模块
2. **代码复用**: 使用继承和组合减少重复代码
3. **性能优先**: 优化热点代码，避免不必要的计算
4. **用户体验**: 提供清晰的UI和反馈
5. **测试覆盖**: 测试各种边界情况
6. **文档完善**: 编写清晰的注释和文档
7. **版本控制**: 使用Git管理代码版本
8. **社区参与**: 积极参与社区讨论和反馈

## 快速参考

### 常用导入
```java
import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.ctype.*;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.*;
import mindustry.world.blocks.crafting.*;
import mindustry.world.blocks.defense.*;
import mindustry.world.blocks.distribution.*;
import mindustry.world.blocks.power.*;
import mindustry.world.blocks.production.*;
import mindustry.world.blocks.units.*;
import mindustry.world.meta.*;
```

### 常用颜色
```java
Color.valueOf("FFFFFF")  // 白色
Color.valueOf("000000")  // 黑色
Color.valueOf("FF0000")  // 红色
Color.valueOf("00FF00")  // 绿色
Color.valueOf("0000FF")  // 蓝色
Color.valueOf("FFFF00")  // 黄色
Color.valueOf("FF00FF")  // 紫色
Color.valueOf("00FFFF")  // 青色
Color.valueOf("97FFA8")  // 修复绿
Color.valueOf("FF6B6B")  // 警告红
Color.valueOf("7CF389")  // 纳米绿
Color.valueOf("BFFFDB")  // 能量青
```

### 常用常量
```java
Mathf.PI           // π
Mathf.PI2          // 2π
Mathf.degRad       // 度转弧度
Mathf.radDeg       // 弧度转度
Time.delta         // 帧时间
Time.time          // 游戏时间
```

这个skill提供了完整的Mindustry mod开发指南，涵盖了从基础到高级的所有内容。根据实际需求选择合适的部分进行参考和使用。

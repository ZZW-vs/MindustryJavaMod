---
name: "mindustry-mod-dev"
description: "Mindustry mod开发助手,支持纯Java、纯JS以及JS+Java混搭三种开发方式。基于Lovecraftian、饱和火力等优秀mod的开发经验。"
---

# Mindustry Mod 开发助手

专门用于Mindustry模组开发的辅助工具，提供三种开发方式：**纯Java**、**纯JavaScript**、**JS+Java混搭**，基于Lovecraftian等优秀mod的开发经验。

## 三种开发方式对比

| 特性 | 纯Java | 纯JavaScript | JS+Java混搭 |
|------|--------|---------------|--------------|
| 性能 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ |
| 开发速度 | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| 灵活性 | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| 调试难度 | ⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ |
| 适用场景 | 高性能核心逻辑 | 快速原型、内容创建 | 大型复杂项目 |

## 推荐架构：JS+Java混搭（Lovecraftian模式）

### 核心思想
- **Java**：处理高性能核心逻辑、底层调用、复杂算法
- **JavaScript**：处理内容创建、UI、游戏逻辑、快速原型开发
- **桥接**：通过Java暴露API给JS调用，通过JS调用Java方法

### 项目结构
```
Mod_Java/
├── src/
│   └── zzw/
│       ├── TestMod.java              # Java主类（极简）
│       └── utils/                   # Java工具类（暴露给JS）
│           ├── ZZWMath.java         # 数学工具
│           ├── ZZWDraw.java         # 绘制工具
│           └── ZZWFormat.java       # 格式化工具
├── assets/
│   ├── scripts/                    # JS脚本（主要内容）
│   │   ├── main.js                 # JS入口
│   │   ├── globalScript.js         # 全局脚本
│   │   ├── mdl/                    # 模型层
│   │   │   ├── MDL_content.js      # 内容管理
│   │   │   ├── MDL_draw.js         # 绘制工具
│   │   │   ├── MDL_event.js        # 事件系统
│   │   │   ├── MDL_json.js         # JSON处理
│   │   │   └── MDL_util.js         # 实用工具
│   │   ├── temp/                   # 模板层
│   │   │   ├── blk/                # 方块模板
│   │   │   │   ├── BLK_baseBlock.js
│   │   │   │   ├── BLK_cogwheel.js
│   │   │   │   └── BLK_factory.js
│   │   │   ├── unit/               # 单位模板
│   │   │   ├── rs/                 # 资源模板
│   │   │   └── sta/                # 状态效果模板
│   │   ├── glb/                    # 全局层
│   │   │   ├── GLB_var.js          # 全局变量
│   │   │   ├── GLB_timer.js        # 定时器
│   │   │   └── GLB_eff.js          # 效果
│   │   └── tp/                     # 工具层
│   │       ├── TP_error.js         # 错误处理
│   │       ├── TP_log.js           # 日志
│   │       └── TP_setting.js       # 设置
│   ├── sprites/                    # 精灵图
│   │   ├── blocks/
│   │   ├── items/
│   │   └── units/
│   ├── bundles/                    # 本地化
│   └── sounds/                     # 音效
└── mod.hjson                       # Mod配置
```

## Java部分（极简核心）

### Java主类
```java
package zzw;

import arc.util.Log;
import mindustry.mod.Mod;

public class TestMod extends Mod {

    public TestMod() {
        Log.info("[ZZW] Loaded Java classes.");
    }

    @Override
    public void init() {
        
    }
}
```

### Java工具类（暴露给JS）
```java
package zzw.utils;

import arc.graphics.Color;
import arc.math.Mathf;

public class ZZWMath {
    
    public static float lerp(float a, float b, float t) {
        return Mathf.lerp(a, b, t);
    }
    
    public static float clamp(float value, float min, float max) {
        return Mathf.clamp(value, min, max);
    }
    
    public static Color rgb(float r, float g, float b) {
        return new Color(r, g, b, 1f);
    }
}
```

## JavaScript部分（主要内容）

### main.js 入口文件
```javascript
Core.settings.put("console", true);

Log.info("[ZZW] Loaded ZZW mod version: " + Vars.mods.locateMod("zzw").meta.version);

(function() {
  let findGlbScr = mod => {
    let dir = mod.root.child("scripts");
    if(!dir.exists()) return null;
    let fiSeq = dir.findAll(fi => fi.name() === "globalScript.js");
    return fiSeq.size === 0 ? null : fiSeq.get(0);
  };
  let runGlbScr = mod => {
    let fi = findGlbScr(mod);
    if(fi == null) return;
    try {
      Vars.mods.scripts.context.evaluateString(Vars.mods.scripts.scope, fi.readString(), fi.name(), 0);
    } catch(err) {
      Log.err("[ZZW] Error loading global script:\n" + err);
    };
  };

  runGlbScr(Vars.mods.locateMod("zzw"));
})();

const TP_error = require("zzw/tp/TP_error");
const TP_log = require("zzw/tp/TP_log");
const MDL_content = require("zzw/mdl/MDL_content");
const MDL_event = require("zzw/mdl/MDL_event");

MDL_event._c_onLoad(() => {
  Log.info("[ZZW] Mod loaded successfully!");
}, 12345678);
```

### globalScript.js 全局脚本
```javascript
global.zzw = {
  version: "1.0.0",
  util: {},
  var: {}
};

const JAVA = {
  Math: zzw.utils.ZZWMath,
  Draw: zzw.utils.ZZWDraw,
  Format: zzw.utils.ZZWFormat
};
```

### 模块系统（require）
```javascript
// temp/blk/BLK_cogwheel.js
const PARENT = require("zzw/temp/blk/BLK_baseTorqueBlock");
const MDL_call = require("zzw/mdl/MDL_call");
const MDL_cond = require("zzw/mdl/MDL_cond");

function comp_init(blk) {
  blk.group = BlockGroup.none;
  blk.priority = TargetPriority.transport;
  blk.update = true;
  blk.configurable = true;
}

function comp_draw(b) {
  b.drawTeamTop();
  let ang = Mathf.mod(b.torProg, 90.0);
  Draw.rect(b.block.region, b.x, b.y, ang);
}

module.exports = [
  newClass().extendClass(PARENT[0], "BLK_cogwheel").initClass()
  .setParent(Wall)
  .setTags("blk-cog")
  .setMethod({
    init: function() {
      comp_init(this);
    }
  }),

  newClass().extendClass(PARENT[1], "BLK_cogwheel").initClass()
  .setParent(Wall.WallBuild)
  .setMethod({
    draw: function() {
      comp_draw(this);
    }
  })
];
```

### 方块创建（JS方式）
```javascript
// 在MDL_content.js或main.js中
const BLK_cogwheel = require("zzw/temp/blk/BLK_cogwheel");

const [CogwheelBlock, CogwheelBuild] = BLK_cogwheel;

const cogwheelZ = new CogwheelBlock("cogwheel-z"){{
  requirements(Category.crafting, ItemStack.with(Items.copper, 15, Z_Items.Iron, 10));
  size = 1;
  health = 120;
}};
cogwheelZ.buildType = CogwheelBuild::new;
```

## 纯Java开发方式

### 项目结构
```
src/zzw/
├── TestMod.java
├── content/
│   ├── Z_Items.java
│   ├── Z_Factory.java
│   ├── Z_Blocks.java
│   └── mechanics/
│       ├── Z_Mechanics.java
│       ├── MechanicalComponentBuild.java
│       └── MechanicalBuilds.java
└── utils/
    └── ZZWUtils.java
```

### 方块创建（Java方式）
```java
public static Block cogwheelZ;

public static void load() {
    cogwheelZ = new Block("cogwheel-z") {{
        requirements(Category.crafting, ItemStack.with(Items.copper, 15, Z_Items.Iron, 10));
        size = 1;
        health = 120;
        solid = true;
        update = true;
        configurable = true;
        buildVisibility = BuildVisibility.shown;
    }};
    cogwheelZ.buildType = CogwheelBuild::new;
}

public static class CogwheelBuild extends Building {
    private float rotation = 0f;
    
    @Override
    public void update() {
        super.update();
        rotation += 2f * Time.delta;
    }
    
    @Override
    public void draw() {
        Draw.rect(block.region, x, y, rotation);
    }
}
```

## 纯JavaScript开发方式

### 项目结构
```
assets/
├── scripts/
│   ├── main.js
│   ├── blocks/
│   │   ├── cogwheel.js
│   │   └── factory.js
│   ├── items/
│   │   └── resources.js
│   └── units/
│       └── units.js
└── sprites/
```

### 方块创建（纯JS方式）
```javascript
// main.js
const cogwheelZ = new Block("cogwheel-z");
cogwheelZ.requirements(Category.crafting, ItemStack.with(Items.copper, 15, Vars.content.item("iron")));
cogwheelZ.size = 1;
cogwheelZ.health = 120;
cogwheelZ.solid = true;
cogwheelZ.update = true;

cogwheelZ.buildType = () => extend(Building, {
  rotation: 0,
  
  update() {
    this.super$update();
    this.rotation += 2 * Time.delta;
  },
  
  draw() {
    Draw.rect(this.block.region, this.x, this.y, this.rotation);
  }
});
```

## 纹理处理（三种方式通用）

### 纹理文件位置
```
assets/sprites/
├── blocks/
│   ├── mechanical/
│   │   ├── cogwheel-z.png      # 1x1方块
│   │   ├── stress_source.png
│   │   └── transmission_box.png
│   ├── factory/
│   │   ├── plate_maker_iron.png  # 2x2方块
│   │   └── plate_maker_gold.png
│   └── block/
│       └── iron_block.png
└── items/
    ├── iron.png
    └── copper.png
```

### 纹理加载规则
1. **方块名称匹配**：方块名 `cogwheel-z` 自动查找 `blocks/cogwheel-z.png`
2. **子目录支持**：Java方式使用 `Core.atlas.find("mechanical/cogwheel-z")`
3. **多部分纹理**：支持 `-top`, `-bottom`, `-team` 等后缀

### Java方式显式加载
```java
cogwheelZ.region = Core.atlas.find("mechanical/cogwheel-z");
cogwheelZ.teamRegion = Core.atlas.find("mechanical/cogwheel-z-team");
```

### JS方式显式加载
```javascript
cogwheelZ.region = Core.atlas.find("mechanical/cogwheel-z");
cogwheelZ.teamRegion = Core.atlas.find("mechanical/cogwheel-z-team");
```

## mod.hjson 配置

### JS+Java混搭模式
```hjson
name: zzw
displayName: ZZW Mod
subtitle: "v1.0"
hidden: false
java: true
main: zzw.TestMod
author: 郑zip
minGameVersion: 150
description:
'''
一个采用JS+Java混搭架构的Mindustry模组
'''
version: 1.0
```

### 纯JS模式
```hjson
name: zzw
displayName: ZZW Mod
subtitle: "v1.0"
hidden: false
java: false
author: 郑zip
minGameVersion: 150
description:
'''
纯JavaScript开发的Mindustry模组
'''
version: 1.0
```

## JS+Java互操作

### Java调用JS
```java
// Java端
import arc.util.serialization.Jval;
import mindustry.Vars;

public class ZZWBridge {
    public static void callJSFunction(String funcName, Object... args) {
        try {
            Vars.mods.scripts.scope.invokeMember(funcName, args);
        } catch (Exception e) {
            Log.err("JS call failed: " + e);
        }
    }
}
```

### JS调用Java
```javascript
// JS端
const JavaMath = zzw.utils.ZZWMath;

let result = JavaMath.lerp(0, 100, 0.5);
Log.info("Result: " + result);

let color = JavaMath.rgb(1, 0.5, 0.5);
Draw.color(color);
```

## 事件系统

### JS事件监听
```javascript
const MDL_event = require("zzw/mdl/MDL_event");

MDL_event._c_onInit(() => {
  Log.info("[ZZW] Mod initialized");
}, 10001);

MDL_event._c_onLoad(() => {
  Log.info("[ZZW] Content loaded");
  loadCustomContent();
}, 10002);

MDL_event._c_onWorldLoad(() => {
  Log.info("[ZZW] World loaded");
}, 10003);
```

## 内容加载

### JS方式加载内容
```javascript
function loadCustomContent() {
  // 加载物品
  const ironIngot = new Item("iron_ingot"){{
    localizedName = "铁锭";
    description = "基础金属原料";
    color = Color.valueOf("C0C0C0");
    hardness = 2;
    cost = 1.5;
  }};
  
  // 加载方块
  const cogwheel = new Block("cogwheel-z"){{
    requirements(Category.crafting, ItemStack.with(Items.copper, 15, ironIngot, 10));
    size = 1;
    health = 120;
    solid = true;
    update = true;
  }};
  cogwheel.buildType = () => extend(Building, {
    rotation: 0,
    update() {
      this.super$update();
      this.rotation += 2 * Time.delta;
    },
    draw() {
      Draw.rect(this.block.region, this.x, this.y, this.rotation);
    }
  });
}
```

## 实用工具函数

### JS工具库
```javascript
// mdl/MDL_util.js
module.exports = {
  lerp(a, b, t) {
    return a + (b - a) * t;
  },
  
  clamp(value, min, max) {
    return Math.min(Math.max(value, min), max);
  },
  
  rgb(r, g, b) {
    return new Color(r, g, b, 1);
  },
  
  localizeModMeta(modName) {
    let mod = Vars.mods.locateMod(modName);
    if(mod == null) return;
    mod.meta.displayName = Core.bundle.get("mod." + modName + ".name", mod.meta.displayName);
    mod.meta.description = Core.bundle.get("mod." + modName + ".description", mod.meta.description);
  }
};
```

## 绘制系统

### JS自定义绘制
```javascript
function comp_draw(b) {
  b.drawTeamTop();
  
  let ang = Mathf.mod(b.torProg, 90.0);
  
  if(b.isInv) {
    Draw.rect(b.block.invReg, b.x, b.y, b.block.cogDrawW, b.block.cogDrawW, -ang + 90.0);
    Draw.alpha(1.0 - ang / 90.0);
    Draw.rect(b.block.invReg, b.x, b.y, b.block.cogDrawW, b.block.cogDrawW, -ang);
  } else {
    Draw.rect(b.block.region, b.x, b.y, b.block.cogDrawW, b.block.cogDrawW, ang);
    Draw.alpha(ang / 90.0);
    Draw.rect(b.block.region, b.x, b.y, b.block.cogDrawW, b.block.cogDrawW, ang - 90.0);
  }
  Draw.color();
}
```

## 配置系统

### JS设置管理
```javascript
const TP_setting = require("zzw/tp/TP_setting");

// 定义设置
TP_setting.addSetting("enable-cogwheels", "启用齿轮", true, "bool");
TP_setting.addSetting("cogwheel-speed", "齿轮速度", 1.0, "slider", 0.5, 2.0);

// 获取设置
let enabled = TP_setting.getSetting("enable-cogwheels");
let speed = TP_setting.getSetting("cogwheel-speed");
```

## 调试技巧

### 日志输出
```javascript
// JS
Log.info("[ZZW] Debug message");
Log.debug("[ZZW] Detailed info");
Log.warn("[ZZW] Warning");
Log.err("[ZZW] Error");

// Java
Log.info("[ZZW] Debug message");
```

### 控制台
```javascript
// 启用控制台
Core.settings.put("console", true);

// 在控制台执行
Vars.mods.scripts.console.eval("Log.info('Hello')");
```

## 最佳实践

### 1. 架构选择
- **小型项目**：纯JS，快速开发
- **中型项目**：JS+Java混搭，平衡性能和开发速度
- **大型项目**：JS+Java混搭，Java处理核心，JS处理内容

### 2. 模块化
- 将功能分解为独立模块
- 使用require系统管理依赖
- 保持模块单一职责

### 3. 性能优化
- Java处理复杂计算和高频循环
- JS处理内容创建和UI逻辑
- 避免在draw()中创建新对象

### 4. 代码组织
```
scripts/
├── mdl/          # 模型层（核心逻辑）
├── temp/         # 模板层（可复用组件）
├── glb/          # 全局层（状态管理）
├── tp/           # 工具层（实用工具）
└── run/          # 运行层（事件处理）
```

## 参考资源

### 优秀Mod
- **Lovecraftian Library**：JS+Java混搭的典范
- **饱和火力**：纯Java的复杂炮塔系统
- **Extra Utilities**：实用扩展功能

### 学习路径
1. 先学纯JS快速上手
2. 再学JS+Java混搭提升性能
3. 最后根据需求选择架构

## 当前项目状态

本项目当前采用**纯Java**开发方式，但可以随时切换到**JS+Java混搭**模式以获得更好的开发体验和灵活性。

### 切换到JS+Java混搭的步骤
1. 保留现有的Java核心逻辑
2. 创建assets/scripts/目录结构
3. 编写main.js和globalScript.js
4. 逐步将内容创建逻辑迁移到JS
5. 在Java中暴露必要的工具类给JS调用

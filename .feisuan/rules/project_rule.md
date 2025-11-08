# 开发规范指南为保证代码质量、可维护性、安全性与可扩展性，请在开发过程中严格遵循以下规范。

##一、技术栈要求- **主框架**：Mindustry Mod 开发（基于 Arc 和 Core）
- **语言版本**：Java17（兼容 Java8）
- **构建工具**：Gradle- **SDK 版本**：
 - Mindustry 版本：v150.1 - Jabel 版本：93fde537c7- **开发环境**：Windows11- **工作目录**：`D:\mc\Mindustry\Mod_Java`

##二、目录结构说明```bashMod_Java/
├── assets/
│ ├── bundles/
│ ├── cursor/
│ └── sprites/
│ ├── blocks/
│ │ ├── Conveyor/
│ │ ├── planets/
│ │ └── wall/
│ └── items/
│ └── campfire_fire/
├── gradle/
│ └── wrapper/
├── src/
│ └── zzw/
│ └── content/
└── 参考/
```

> **说明**：
> - `src` 目录为源码根目录，采用包名 `zzw.content`。
> - `assets`为资源文件目录，包含图片、音效等。
> - `gradle/wrapper`用于存放 Gradle Wrapper 文件。
> - `参考` 目录用于存放参考资料或示例。

##三、分层架构规范| 层级 | 职责说明 | 开发约束与注意事项 |
|-------------|----------------------------------|----------------------------------------------------------------|
| **Controller** | 处理 Mod 的初始化与事件注册 | 在 Mindustry Mod 中通常通过 `Mod.java` 或 `ContentLoader` 进行注册 |
| **Service** | 实现 Mod 的业务逻辑 |通常通过 `ModContent` 类加载内容 |
| **Repository** | 内容管理与注册（如 Block、Item） | 使用 `ModContent` 和 `ContentType` 注册内容 |
| **Entity** | 对应 Mindustry 的内容类型（Block、Item、Unit等） | 不得直接返回给前端（需转换为 DTO）；包名统一为 `content` |

### 接口与实现分离- 所有接口实现类需放在接口所在包下的 `impl` 子包中（如果适用）。

## 四、安全与性能规范### 输入校验- Mindustry Mod 中无传统输入校验，但需确保注册内容合法，避免非法引用。

###事务管理- Mindustry Mod 中不涉及数据库事务，无需关注此部分。

##五、代码风格规范### 命名规范| 类型 | 命名方式 | 示例 |
|------------|----------------------|-----------------------|
| 类名 | UpperCamelCase | `ZzwContent` |
| 方法/变量 | lowerCamelCase | `registerBlocks()` |
| 常量 | UPPER_SNAKE_CASE | `MAX_BLOCK_COUNT` |

### 注释规范- 所有类、方法、字段需添加 **Javadoc** 注释。
- 使用中文注释（第一语言）。

### 类型命名规范（阿里巴巴风格）

| 后缀 |用途说明 | 示例 |
|------|------------------------------|--------------|
| DTO | 数据传输对象（Mod 中较少使用） | - |
| DO | 数据库实体对象（Mod 中较少使用） | - |
| BO |业务逻辑封装对象 | `ZzwContentBO` |
| VO | 视图展示对象（Mod 中较少使用） | - |
| Query| 查询参数封装对象 | - |

### 实体类简化工具-本项目未使用 Lombok，需手动编写 getter/setter/构造方法。

## 六、扩展性与日志规范### 接口优先原则- 所有 Mod 内容通过 `ModContent` 类进行注册。

### 日志记录- 使用 `Log.info(...)` 记录日志（Mindustry 提供的日志系统）。

##七、编码原则总结| 原则 |说明 |
|------------|--------------------------------------------|
| **SOLID** | 高内聚、低耦合，增强可维护性与可扩展性 |
| **DRY** | 避免重复代码，提高复用性 |
| **KISS** |保持代码简洁易懂 |
| **YAGNI** | 不实现当前不需要的功能 |
| **OWASP** | 防范常见安全漏洞，如 SQL 注入、XSS 等（Mod 中不适用） |

## 八、其他通用规则- 所有内容（Block、Item、Unit 等）必须在 `ModContent` 中注册。
- 所有资源文件（如 sprites）必须放置于 `assets/sprites/` 下对应子目录。
- 所有 Mod 内容类应统一放在 `src/zzw/content/` 包下。
- 使用 `Mod_Java/build.gradle` 进行构建和部署。
- 构建后生成的 jar 文件将包含 Desktop 和 Android两个版本。

##九、作者信息- **代码作者**：12005- **项目名称**：Mod_Java- **创建时间**：2025-11-0815:46:30
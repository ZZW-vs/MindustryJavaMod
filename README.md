# Create Mod

一个为Mindustry添加了基础金属加工系统和多节Boss单位的Java模组，灵感来源于Minecraft的Create模组。

**交流QQ群：276860651**

## 功能特性

### 新增物品
- **铁锭** - 基础金属原料，用于制作铁板
- **金锭** - 珍贵的金属原料，用于制作金板
- **铁板** - 通过铁锭压制而成的金属板材，用于建造各种设备
- **金板** - 通过金锭压制而成的珍贵金属板材，用于制造高级设备
- **铜板** - 通过原生铜压制而成的金属板材，用于制造各种设备

### 新增方块

#### 生产设备
- **铁板制造机** - 将铁锭加工成铁板的基础工业设备
  - 每次消耗2个铁锭，生产2个铁板
- **金板制造机** - 将金锭加工成金板的精密工业设备
  - 每次消耗2个金锭，生产2个金板
- **铜板制造机** - 将原生铜加工成铜板的基础工业设备
  - 每次消耗2个原生铜，生产2个铜板

#### 高级生产设备
- **大型铁板制造机** - 高效率的铁板生产设备，需要电力支持
  - 每次消耗3个铁锭，生产4个铁板，需要电力
- **大型金板制造机** - 高效率的金板生产设备，需要电力支持
  - 每次消耗3个金锭，生产4个金板，需要电力
- **大型铜板制造机** - 高效率的铜板生产设备，需要电力支持
  - 每次消耗3个原生铜，生产4个铜板，需要电力

#### 防御方块
- **铜块** - 由铜板和原生铜制作的防御块
  - 基础防御设施，可以合成大型铜块
- **铁块** - 由铁板和原生铜制作的防御块
  - 基础防御设施，可以合成大型铁块
- **大型铜块** - 由大量铜板和原生铜制作的大型防御块
  - 高耐久防御设施，由四个小铜块自动合成
- **大型铁块** - 由大量铁板和铁锭制作的大型防御块
  - 高耐久防御设施，由四个小铁块自动合成

#### 物流设备
- **传送带** - 用于输送物品的机械设备
  - 双厨狂喜

### 新增单位 (PU132 移植)

本模组的多节单位系统完全照搬 PU132 原版算法，包括速度传播、约束修正、血量分布等核心机制。段身带有正弦波蠕动动画，更具生物活性。

#### 电弧虫 (arcnelidia)
- 多节虫子单位，段身延迟跟随头部
- 头部发射可偏转的激光（不锁定朝向）
- 段身携带同步武器，在弹幕范围内复制头部 aim 齐射

#### 毒雾虫 (toxobyte)
- 小型多节虫子单位，25段初始长度，最多可生长至25段
- 头部发射毒液子弹，造成持续中毒伤害
- **段身增生**：每13秒生长一节新的尾部段身（原15秒，已加快）
- **分裂机制**：中间段身死亡时，后半段分裂成独立的新虫子
- **链式合并**：两条同类型虫子靠近时，可首尾合并成更长的虫子
- 段身伤害缩放 8x（段身更脆，容易被打断分裂）

#### 吸血虫 (catenapede)
- 中型多节虫子单位，2段初始长度，最多可生长至15段
- 头部发射吸血激光，攻击敌人时恢复自身血量
- **段身增生**：每26.5秒生长一节新的尾部段身（原30秒，已加快）
- **分裂机制**：中间段身死亡时，后半段分裂成独立的新虫子
- **链式合并**：两条同类型虫子靠近时，可首尾合并成更长的虫子
- 段身伤害缩放 12x（段身非常脆，鼓励玩家集中火力攻击段身）
- 血量分布速率 0.15（段身血量分配更快）

#### 噬界虫 (devourer)
- 大型多节单位，3 种段身武器（导弹 / 毁灭者 / 小激光）
- 头部发射红色大激光（continuous 连续武器）
- 段身碰撞箱 hitSize=52f，环境支持 + 全免疫
- 弹幕同步范围 240f

#### 压迫者 (oppression)
- 终极 Boss 级单位，8 个武器系统（2 头部 + 6 段身）
- **大招机制**：开大招期间（充能 + 射击）移动和旋转速度降至 7.5%（非完全锁定）
- **红色主激光**：OppressionLaserBulletType 7 层渲染（纺锤主体 + 末端虚空 + 尖刺边缘 + 散落粒子 + 白色闪光线 + 黑红菱形 + 内部线段 + 闪电），damage=9000、length=2150、width=140、lifetime=8*60
- **充能前摇特效**：5 阶段粒子效果（菱形辐射 → 尖刺菱形 → 方块粒子 → 短线段 → 主线），lifetime=4*60
- **VoidPortal 黑色菱形技能**：菱形区域伤害 + 虚空触手拉拽敌人，渲染在最上层可盖住空中单位
- **扫射激光 + 黑色圆形虚空区域**：EndSweepLaser 扫射命中时生成 VoidArea 黑色圆形，持续范围伤害
- **慢闪电**：完整移植 PU132 SlowLightning 三件套（Entity + Type + Node）
- 段身武器按 segmentIndex 分 3 组（每组 2 个），避免炮台叠加
- 段身碰撞箱 hitSize=180f，大招期间 freezeOnUlt=true
- 液压杆装饰：每节段身都绘制到父段的液压杆（WormDecal 延迟加载）
- 技能数量上限：非大招技能同时最多 8 个存在

### End 阵营飞行单位 (PU132 移植)

#### 虚空容器 (voidVessel)
- End 阵营飞行单位，两阶段攻击子弹 VoidFractureBulletType
- **Phase 1（悬停段）**：初速度 4.3f 配合 drag=0.11f 在 30 帧内衰减到 ~0.13 实现悬停跟踪目标
- **Phase 2（冲刺段）**：直线冲刺穿透，黑色激光束效果，trueSpeed 入参控制冲刺速度
- **冲刺结束**：播放 voidFractureEffect 30tick 三层激光余晖 + spikes 散射伤害
- 渲染层级 Layer.flyingUnit + 1f，显式调用 Draw.blend() 重置混合模式避免黑色不可见

#### 谜团 (enigma)
- End 阵营飞行单位（PU132 移植）

#### 克罗诺斯 (chronos)
- End 阵营飞行单位，时间停止能力
- **TimeStopAbility**：用 Time.delta 模拟全局时间停止，updating 标志防递归，maxIterations=60 防卡死

#### 盲视者 (opticaecus)
- End 阵营飞行单位（PU132 移植），60000 血，速度 1.8
- **武器1：红色激光**（LaserBulletType，1400 伤害，长度 390，宽度 30，4 秒冷却）
- **武器2：导弹发射器**（doeg-launcher，10 连发，每发 170 伤害 + 320 范围伤害，追踪 + 蛇形飞行）
- PU132 原版有隐身能力（InvisibleUnitType），v158 简化为普通 UnitType（隐身机制依赖 Invisiblec 组件，v158 无原生支持）
- 具备防作弊系统（无敌帧 + 单次上限 + 抗性递增）

#### 掠夺者 (ravager)
- End 阵营地面单位（8 腿），1650000 血，速度 0.65，护甲 15（PU132 移植）
- **武器1：噩梦激光**（EndPointBlastLaserBulletType，1210 伤害，长度 460，宽度 26.1）
  - 直线碰撞检测 + 阻挡点范围爆炸（damageRadius=110，auraDamage=9000），6 秒冷却
  - 三模块防作弊：护甲削弱 + 能力削弱 + 力场削弱
- **武器2,3：炮弹**（ArtilleryBulletType，5 连发，每发 130 伤害 + 325 范围伤害，闪电效果）
- **武器4,5：小型炮台**（EndBasicBulletType 导弹，330 伤害 + 220 范围伤害，追踪 + 蛇形飞行）
- 8 腿行走（legCount=8, legGroupSize=4, legLength=140），每腿落地造成 1400 范围伤害
- 免疫所有状态效果
- ★ v1.5.0 起：constructor 改用 `EndGroundUnit::create`（extends LegsUnit），同时具备防作弊系统和正常显示腿

#### 外径行者 (exowalker)
- Plague 阵营地面单位（8 腿），6000 血，速度 0.7，护甲 4（PU132 移植）
- **武器1-4：瘟疫导弹发射器**（small-plague-launcher，4 连发，9 伤害 + 17 范围伤害，蛇形追踪，1.5 秒冷却）
- **武器5：吸血激光**（drain-laser / SapBulletType，43 伤害，长度 80，吸血 0.4%，3 连发间隔 17.5tick）
- 8 腿行走，瘟疫色（#a3f080）涂装
- 不具备防作弊系统（仅 End 系列单位具备）

#### 瘟疫蜂群 (toxoswarmer)
- Plague 阵营地面单位（10 腿），7000 血，速度 1.1，护甲 4（PU132 移植）
- **武器1：8 连发追踪导弹**（toxo-launcher / MissileBulletType，200 伤害 + 30 范围伤害，蛇形飞行 4 秒持续）
  - 命中后分裂 2 个火焰弹（fragBullet FireBulletType，15 伤害 + 燃烧状态 4 秒）
- ★ v1.5.1 起：腿系统改用 `CustomLegsAbility`（完整移植 PU132 CLegGroup），2 组腿（小腿组 6 条 + 大腿组 4 条），由 `MixedLegUnitType.drawLegs()` 委托渲染
- 瘟疫色（#a3f080）涂装，不具备防作弊系统（仅 End 系列单位具备）

#### 荒芜者 (desolation)
- End 阵营终极地面单位（8 腿），307300 血，速度 0.7，护甲 35（PU132 移植）
- **武器1：蓄力主炮**（EnergyChargeWeapon / DesolationBulletType，2500 伤害 + 三防作弊模块，15 秒冷却 + 8 秒持续，蓄力 4 阶段红色特效）
- **武器2,3：点防激光**（end-point-defence，7 连发 / 5 连发，220 伤害，15tick 冷却）
- **武器4-7：四门副炮**（end-mount，3 连发，260 伤害，fragBullet 虚空碎裂弹）
- **武器8,9：两门闪电炮**（end-mount-2，2 连发，380 伤害 + 220 范围 + 80 闪电伤害，穿透 3 目标）
- **武器10：触手1**（desolation-tentacle，15 段 44.5 长，EndPointBlastLaserBulletType 250 伤害 + 1000 范围伤害，3 秒冷却，点射模式）
- **武器11-13：触手2-4**（apocalypse-tentacle，17/14/9 段 37.25 长，EndContinuousLaserBulletType 85 伤害，4 秒冷却，连续激光 1.5 秒持续）
- ★ v1.5.1 起：4 条触手×mirror=8 条，完整移植 PU132 NewTentacle（含两阶段 IK + 角度限制 + stab 伤害），角度限制从 65° 减至 30° 让鞭子更直
- 8 腿行走，每腿落地造成 1700 范围伤害
- 免疫所有状态效果
- ★ v1.5.0 起：constructor 改用 `EndGroundUnit::create`（extends LegsUnit），同时具备防作弊系统和正常显示腿

### 防作弊系统架构 (v1.5.0 重构)
- **EndLegsUnit extends UnitEntity**：仅用于 End 阵营飞行单位（enigma/voidVessel/chronos/opticaecus），无腿
- **EndGroundUnit extends LegsUnit**：用于 End 阵营腿单位（ravager/desolation），有腿且实现 Legsc 接口
- **Plague 阵营单位**（exowalker/toxoswarmer）：使用 `LegsUnit::create`，无防作弊系统
- 防作弊机制：多槽位无敌帧 + 抗性累积 + 伤害曲线衰减 + 单次上限 + 怒气系统 + 死亡拒绝

### 机械网络系统 (Betamindy 风格)
- 全局注册表 + 源驱动 BFS 传播转速和应力
- 所有机械组件继承 MechanicalComponentBuild
- 应力源方块通过 source 指针传播树结构
- 工厂加速系统：事件驱动 + 5 秒周期性回退扫描

### 方块合并系统
- 2×2 的小铜块 / 小铁块自动合并成大铜块 / 大铁块
- 合并时有烟雾效果和延迟检查

## 安装说明

1. 下载最新版本的模组文件
2. 将模组文件放入Mindustry的mods文件夹
3. 启动游戏，在模组列表中启用模组
4. 重新启动游戏以应用更改

## 兼容性

- 最低游戏版本：154
- 推荐游戏版本：158.1（多节单位系统主要针对 v158 适配）
- v158 兼容：反射适配 ammo/useAmmo 字段移除，Bullet.drag() 方法移除改用每帧 vel 重置，bloom 混合模式陷阱通过显式 Draw.blend() 修复

## 开发信息

- 模组作者：b站up “郑zip”
- 主类：zzw.TestMod

## 许可证

本模组遵循MIT许可证。

## 贡献

欢迎提交问题报告和功能请求！如果您想贡献代码，请先创建一个分支并提交Pull Request。

## 更新日志

### v2.1.5 (3D渲染回退到CPU软件渲染,彻底解决透视问题)
- **根因定位**: 18ad7f5 引入的 GPU 深度缓冲渲染与 Mindustry 2D batch 系统冲突
  - GPU 深度缓冲 + 透明/双面材质混合时产生透视假象
  - Mindustry 自己的 PlanetRenderer 用 GL 深度是在独立 3D 场景,不嵌入 2D 世界
  - 用户反馈: "改了高精度优化之后就出现了透明不显示奇怪透视的问题"
- **彻底回退**: WavefrontObject 恢复到 95db20d 的 CPU 软件渲染 (painter's algorithm)
  - 顶点 CPU 变换 + 透视缩放
  - 面排序 (远的先画) + 每面独立 Draw.z
  - 不使用 GL 深度测试,避免与 2D batch 冲突
- **PMXLoader 重写**: 保留 PMX 二进制解析,去掉 GPU Mesh 构建
  - 填充 WavefrontObject 的 faces/vertices/normals/uvs 结构
  - 每材质独立 Texture (Material.independentTex)
  - OFF 开关材质跳过, da=0 非 OFF 强制 1.0
  - 全部双面渲染 (cullBackfaces=false), 面排序 (singleZLayer=true)
- **Material 增强**: 新增 independentTex (Texture) 和 alpha (float) 字段
  - updateFace/face.draw 支持独立 Texture UV [0,1] 直接映射
  - 材质 alpha 调制顶点颜色
- **删除 GPU shader**: obj3d.vert 和 obj3d.frag 不再需要

### v2.1.4 (MMD专用展示台+透视彻底修复)
- **彻底修复模型透视/面变透明问题**：
  - 根因：全局 cullBackfaces 导致所有材质统一双面渲染，内部面写入深度缓冲后与外部面深度竞争
  - 修复：MeshGroup 新增 `doubleSided` 字段，每材质独立处理背面剔除
  - PMXLoader 读取每个材质的双面标志（drawFlags & 1），独立设置 doubleSided
  - 渲染时按材质属性调用 `setCullState()`：单面材质启用背面剔除，双面材质禁用
  - 动态 Z 范围：根据模型实际大小（boundRadius * scl）收紧正交投影 Z 范围，最大化深度缓冲精度，消除 z-fighting
- **新增 MMD 模型专用展示台**（[MmdDisplayBlock.java](src/zzw/content/blocks/MmdDisplayBlock.java)）：
  - 方块名 `create-mmd-display`，分类 effect，3x3 尺寸
  - 专用渲染参数：轻微俯视（rX=-15°）、保留贴图原色（maxShade=0.25）、顶部光照
  - 汉化配置界面：模型选择、旋转设置（自动旋转开关/速度/轴）、位置（高度/阴影开关）、大小（按钮+输入框+快速滑条）
  - 支持 Y/Z/X 三轴旋转，旋转速度 0-3 可调
  - 大小范围 0.1-10，高度范围 -20 到 80
- **WavefrontObject 渲染逻辑重构**：
  - 新增 `setCullState()` 方法，每材质独立设置背面剔除状态
  - 渲染分两遍：不透明组（depthMask=true）→ 透明组（depthMask=false）
  - 每组内按材质 doubleSided 独立调用 cullFace，避免全局状态干扰

### v2.1.3 (PMX直接解析+透视修复)
- **直接渲染 PMX (MMD) 模型文件**：
  - 新增 [PMXLoader.java](src/zzw/util/PMXLoader.java)，直接解析 PMX 2.0/2.1 二进制格式
  - 解析顶点(位置/法线/UV)、面索引、材质(diffuse颜色/贴图/双面标志)、贴图路径
  - 跳过骨骼/形变/物理（静态 bind-pose 渲染），按材质分组构建 MeshGroup
  - PMX 坐标系适配：反转三角形绕序（MMD顺时针→OpenGL逆时针）+ V轴翻转
  - 贴图路径自动解析（尝试 mod 文件树多种路径）
  - 无需 Blender 导出 OBJ，直接放 .pmx + 贴图到 assets/ 即可
- **修复 MMD 模型透视/面变透明问题**：
  - 根因1：OBJ 导出时面绕序未反转，导致背面被剔除看到内部
  - 根因2：cullBackfaces=false 渲染双面，内部面与外面深度竞争导致透视
  - 修复：PMX 加载时反转绕序 + 根据材质双面标志自动设置 cullBackfaces
  - 透明材质（d<1）按透明度排序，不透明先渲染写深度，透明后渲染不写深度
- **修复 MMD 模型贴图上色问题**：
  - 着色参数调整：shadeColor 606060→808080，maxShade 0.5→0.3，保留贴图原色
  - 着色方式改用 topLight（光从上方），角色模型更自然
  - 材质 diffuse 颜色直接作为顶点色，纹理采样与材质色正确混合

### v2.1.2 (MMD模型渲染支持)
- **新增 MMD/Blender 模型渲染支持**：
  - 重构 [WavefrontObject.java](src/zzw/util/WavefrontObject.java) 支持多材质多贴图渲染
  - 新增 `MeshGroup` 按材质分组，每个材质独立 Mesh + Texture，支持 MMD 等多贴图模型
  - 新增 `loadIndependentTexture()` 从 mod 文件树加载独立 Texture（不通过 atlas），支持 MMD 大贴图
  - MTL 解析增强：支持 `d` 透明度、`map_Kd` 文件名延迟加载、独立 Texture 回退
  - UV 映射：独立 Texture 采用 V 翻转（OBJ V 朝上 → OpenGL V 朝下），atlas 贴图保持原逻辑
- **加载 gale MMD 角色模型**：
  - 模型位于 `assets/blander/text_g/`（含 gale.obj + gale.mtl + 12 张贴图）
  - 58 个材质，50 个 map_Kd 纹理映射，按贴图分组合并为 ~12 个 MeshGroup
  - 在 [ZObjs.java](src/zzw/util/ZObjs.java) 注册 gale 模型，`loadObj` 支持完整相对路径（含 `/` 时不再拼 `objects/` 前缀）
  - 在 [ObjDisplayBlock.java](src/zzw/content/blocks/ObjDisplayBlock.java) 模型列表新增"MMD角色"选项
- **关于 .blend 文件**：Blender 专有二进制格式无法运行时解析，需在 Blender 中 `File → Export → Wavefront (.obj)` 导出为 .obj + .mtl + 贴图

### v2.1.1 (3D马赛克修复)
- **修复3D模型马赛克/锯齿问题**：
  - 根因：之前为追求性能禁用了alpha混合 (`Gl.disable(blend)`)，导致贴图透明边缘出现硬边马赛克
  - 修复：恢复alpha混合 (`srcAlpha/oneMinusSrcAlpha`)，透明边缘平滑过渡
  - 着色器增加 alpha discard：透明度<0.01的片段直接丢弃，避免写入深度缓冲遮挡后续像素
  - 正交投影Z范围从 ±2000 收紧到 ±500，提升深度缓冲精度，消除 z-fighting 产生的色块
  - 默认启用背面剔除 (`cullBackfaces=true`)，减少 overdraw 提升性能
- **关于Blender/MMD模型渲染**：
  - 说明：`.blend` 文件是Blender专有二进制格式，运行时解析需重写半个Blender，不可行
  - 推荐方案：在Blender中 `File → Export → Wavefront (.obj)`，勾选 Apply Modifiers / Write Materials / Write UVs
  - 导出的 .obj + .mtl + 贴图 放入 `assets/` 即可用现有WavefrontObject系统渲染（高精度）

### v2.1.0 (直接屏幕渲染+kami波次提示)
- **3D渲染重构：FBO离屏渲染 → 直接屏幕渲染**：
  - 移除FBO+FXAA方案，改为直接渲染到屏幕深度缓冲，全屏幕分辨率无放大锯齿
  - 使用正交投影矩阵匹配2D相机可见区域，宽Z范围(-2000~2000)支持3D深度
  - 模型变换矩阵：平移到世界位置 + 旋转(rZ/rY/rX) + 缩放，在GPU端完成
  - 视锥裁剪：模型不在屏幕内时跳过渲染，大幅提升多实例场景性能
  - 删除不再使用的FXAA着色器文件（fxaa.vert + fxaa.frag）
- **kami波次提示系统**：
  - 玩家成功抗过一轮弹幕后，屏幕中间显示2秒提示（Call.announce）
  - 提示内容：当前波次 + 全局最高波记录
  - 最高记录通过Core.settings持久化存储，跨会话保留
  - 记录更新时立即调用forceSave()确保持久化

### v2.0.0 (FXAA抗锯齿+kami子弹优化)
- **3D模型FXAA边缘抗锯齿**：
  - 新增FXAA 3.11后处理着色器（fxaa.vert + fxaa.frag），在FBO纹理绘制到屏幕时应用边缘平滑
  - 专修复Y轴旋转时边缘波浪状锯齿问题：检测模型轮廓边缘并子像素级平滑
  - 性能优化：非边缘像素直接跳过（~80%像素不走FXAA逻辑），仅边缘像素采样9个邻居
  - 新增`useFxaa`开关字段，默认启用，可按需关闭
  - 回退机制：FXAA着色器编译失败时自动降级为双线性过滤
- **3D渲染管线优化**：
  - 相机距离从`3f`调整为`2.7f`（模型占FBO从~80%提升至~92%），提高有效分辨率
  - 缓存`fboRegion`避免每帧`Draw.wrap()`分配，减少GC压力
- **kami子弹攻击己方单位**：
  - `KamiBulletType.collidesTeam = true`，子弹可攻击同队伍单位（含kami自身但不含owner）
- **kami目标死亡后自杀**：
  - `KamiAI`检测目标玩家失效（死亡/离开）时调用`unit.kill()`自杀
  - 无玩家目标时也自动自杀

### v1.9.9 (3D锯齿修复+kami汉化+阴影修复)
- **修复 3D 模型锯齿严重（特别是放大后）**：
  - FBO 分辨率从 1024 提升到 2048，消除放大时的边缘锯齿
  - 初始 buffer 大小从 512 改为 2048，避免首次 resize
  - 显存占用 32MB（16MB色彩+16MB深度），对现代 GPU 可接受
- **修复 kami 名称未汉化**：
  - 原因：bundle key 缺少 `create-` 前缀（mod 自动添加前缀）
  - 修复：`unit.kami.*` → `unit.create-kami.*`，现在显示"神威"
- **修复波前模型阴影大小错误**：
  - ObjDisplayBlock：阴影用 `Block.size(3) * 8f` 而非模型实际 worldSize，导致阴影过小
  - WavefrontTurret：阴影用 `Block.size * 8f` 而非模型实际 worldSize
  - 修复：阴影大小改为 `obj.boundRadius * 2f * 4f * obj.size`（模型实际 worldSize）
  - `boundRadius` 字段从 private 改为 public 以供外部访问

### v1.9.8 (3D模型显示修复+信号删除+单选)
- **修复所有 3D 模型不能显示**：
  - 原因：内存泄漏修复中将 `capturedLight`/`capturedShade` 从局部 `cpy()` 改为实例字段 `set()`，多个 DisplayBuild 共享同一 ZObjs 静态实例时，实例字段被后续调用覆盖，导致延迟 lambda 读到错误值
  - 修复：改回局部 `final Color capturedLight = lightColor.cpy()`，确保每次 draw() 调用独立捕获
  - 同时将 `fboRegion` 静态缓存改回 `Draw.wrap(buffer.getTexture())`，避免静态 TextureRegion 跨实例共享可能的纹理引用问题
- **teleporter 信号列表增强**：
  - 信号列表添加删除按钮（×），点击删除信号并移除所有关联传送器
  - 使用 `ButtonGroup` 保证单选（最多选中一个信号），取消勾选则清除选择
  - `deleteSignal()` 方法清理全局列表、队伍桶、成员传送器的 customSignal

### v1.9.7 (teleporter界面重做+kami崩溃修复)
- **teleporter 界面完全重做**：
  - 恢复 12 个颜色按钮（取消滑条方案），保留颜色快捷区
  - 新增标签页切换：颜色频道（默认）/ 自定义信号
  - 自定义信号区：输入信号名（支持中文）+ 批注 → 添加信号
  - 场上所有自定义信号显示在面板列表中，玩家可直接选择
  - 鼠标悬停信号按钮时显示批注 tooltip（自动跟随鼠标）
  - 信号列表旁显示批注预览（超过12字截断）
  - 数据结构改为 `ObjectMap<String, SignalInfo>` 存储信号成员+批注
  - 全局 `allSignals` 列表跨传送器共享信号信息
- **修复 create-kami 游戏崩溃**：
  - 原因：`KamiBulletType.despawnEffect` 为 null，子弹消失时 `BulletType.despawned()` 调用 `Effect.at()` 触发 NPE
  - 修复：设置 `despawnEffect = Fx.none` 和 `hitEffect = Fx.none`

### v1.9.6 (3D性能优化+teleporter滑条+kami修复+UI实时更新)
- **修复3D系统内存泄漏导致帧数越来越低**：
  - `buffer.resize()` 在多实例场景下反复触发（不同大小模型每帧 resize）→ 固定 FBO 分辨率 1024
  - `Draw.wrap()` 每帧创建新 TextureRegion → 缓存静态 `fboRegion` 复用
  - `lightColor.cpy()` / `shadeColor.cpy()` 每帧创建新 Color → 用实例字段 `set()` 复用
  - 删除死代码 `computeFboResolution()` 和 `cachedFboRes`
- **teleporter 界面优化**：
  - 去除 12 个分散的颜色按钮，改用滑条（0-999）选择频道
  - 频道颜色用 HSB 色环生成（`fromHsv(channel * 360 / 1000, 0.7, 0.85)`）
  - 去除自定义频道文本输入框，滑条支持 1000 个频道
  - 用 `ObjectMap<Integer, ObjectSet>` 按需创建频道桶，不预分配 1000 个空 Set
  - 存档用 `write.i()` / `read.i()` 支持 0-999 频道号
- **修复 create-kami 不会弹幕**：
  - 原因：`Units.closestTarget` 找不到敌方单位时返回 null，弹幕不执行
  - 修复：改用 PU132 原版逻辑 — 遍历 `Groups.player` 找最近玩家单位作为目标
- **修复 create-wavefront 阴影**：阴影大小 `size * 12f` → `size * 8f`，与模型实际大小匹配
- **修复 create-universal-display 面板数值不实时更新**：
  - 原因：TextField 只在构建时读取一次值，按钮改变字段后显示不更新
  - 修复：TextField 添加 `update` 回调，非编辑状态时同步显示当前字段值

### v1.9.5 (多炮台修复+teleporter自定义频道+模型优化)
- **修复3D展示台阴影奇怪**：移除椭圆阴影，改为简单圆形阴影，大小 `size * 8f * currentScale`
- **增大波前炮台3D模型**：wavefront.size 8f → 12f，模型更显眼
- **修复w-boson开火声音缺失**：`shootSound` 未设置，导致充能音结束后子弹静默发射
  - 修复：添加 `shootSound = Z_Sounds.wbosonShoot`
- **修复arc-storm/arc-caster声音不匹配**：`shootSound = Sounds.shootFlame`（火焰喷射声）不适合电弧炮台
  - 修复：改为 `Sounds.shootLancer`（电弧射击声）
- **修复arc-caster/arc-storm闪电效果奇怪**：
  - 闪电从子弹位置随机偏移 `radius` 范围内生成 → 改为从子弹位置直接发射
  - 第一道闪电沿子弹方向延伸 (`lightningInaccuracy1 = 45f`)，第二道随机方向扩散
  - 修复第二道闪电颜色 bug：`lightningC1` → `lightningC2`
  - 弹体增加白色核心圆，增强电弧视觉效果
- **延长ephemeron分裂放射效果**：EphemeronPairBulletType lifetime 720f → 1500f（25秒）
- **teleporter加强：自定义频道名称功能**：
  - 保留原12个颜色频道，新增文本输入框，玩家可输入自定义频道代码
  - 相同自定义频道名的传送器互相连接，支持任意数量频道
  - 新增 `config(String.class, ...)` 处理器，`ObjectMap<String, ObjectSet>` 按队伍分桶
  - 配置界面显示当前频道状态（颜色频道号/自定义频道名/未选择）
  - 存档支持：`write.str()` / `read.str()` 保存自定义频道名

### v1.9.4 (模型大小修复+wavefront透明修复+kami贴图+抗锯齿自适应)
- **修复模型大小无法调整**：`Draw.draw()` 延迟执行，但 `obj.size` 在 lambda 执行前就被恢复
  - 修复：在 `draw()` 调用时捕获 `size`、`lightColor`、`shadeColor`、`maxShade`、`zOffset` 的快照
  - lambda 内使用捕获值而非 `this` 字段，确保延迟执行时参数正确
- **修复wavefront贴图部分透明**：wavefront.obj 的 UV (0.027-0.816) 已是 atlas 坐标，但仍被重复映射到 atlas 子区域，采样到透明 padding
  - 修复：`buildMesh()` 中检测 UV 范围，若不完全覆盖 [0,1] 则判定为 atlas 坐标，跳过映射
- **抗锯齿自适应**：FBO 分辨率改为四级自适应 (512/1024/2048/4096)，根据模型世界大小自动选择
  - ≤32单位→512, ≤64→1024, ≤128→2048, >128→4096
- **新增 kami 单位贴图**：从 PU132 移植 kami-mkii 全套贴图 (主体+轮廓+6层彩虹+拖尾+残骸)
- **新增 RainbowUnitType**：kami 使用 RainbowUnitType，`drawBody()` 叠加 6 层彩虹贴图 (色相随时间流动+段偏移15°)
- **kami 屏障完全还原原版**：800 半径传送玩家回圆内 (PU132 原版行为)

### v1.9.3 (Bloom光效修复+3D性能优化+kami弹幕单位)
- **彻底修复光效强度和模糊失效**：根因是 `Draw.z() + Draw.flush()` 会 flush ALL pending DrawRequests，包括 bloom capture (z=99.98) 和 render (z=110.02)，导致 bloom 在 z=25-35 时就 capture，场景内容不完整
  - 修复：改用 `Draw.draw(z, runnable)` 将整个 FBO 操作注册为 DrawRequest 参与 SortedSpriteBatch 排序管线
  - `flushing=true` 标志防止 re-entrant `flushRequests()`，`Draw.flush()` 只调用 `super.flush()` 渲染 mesh buffer
  - 参考源码：Mindustry 158.1 Renderer.java `Draw.draw(Layer.bullet - 0.02f, bloom::capture)` 同样使用 Draw.draw() 注册 bloom
- **修复模型放大后阴影偏移和锯齿**：
  - FBO 分辨率改为三级 (512/1024/2048)，大模型(>100单位)自动用 2048 消除锯齿
  - FBO 纹理设置线性过滤 `TextureFilter.linear`，减少放大时的像素感
  - 阴影 Y 偏移补偿模型 X 轴旋转导致的视觉上浮
  - camera 距离恢复为 3x boundRadius*scl (模型占 FBO ~80%，避免边缘裁剪)
- **3D 模型性能优化**：`Draw.draw()` 避免了 premature flush 整个 DrawRequest 队列，多个 3D 模型同屏时不再卡顿
- **新增 kami 弹幕 Boss 单位 (PU132 移植)**：
  - 4 种弹幕模式循环：双层旋转弹环 + 交替方向弹环 + 散弹→环形扩张 + 花瓣形双向射击
  - 弹幕子弹：色相循环红色 + 外层彩色光晕 + 内层白色核心 + 大小脉动 + 拖尾效果 (kamiBullet2)
  - 屏障：800 半径，加法混合 + 红色 hue-shift + 脉动线宽圆环，阻止玩家逃离
  - 难度系统：随阶段提升弹幕密度 (difficulty 0-5)

### v1.9.2 (3D渲染纹理+GL状态+精度修复)
- **修复wavefront贴图乱码**：GPU着色器绑定的 `diffTexture.texture` 是整个图集纹理，模型UV(0-1)采样到了图集错误区域
  - 修复：`buildMesh()` 中将模型UV映射到atlas区域UV空间: `u*(u2-u)+u, v*(v2-v)+v`
- **修复光效强度和模糊失效**：3D渲染时未禁用混合，FBO内容被pre-multiply alpha，后续Draw.rect再次混合导致颜色错误，同时GL状态污染影响bloom
  - 修复：遵循PlanetRenderer模式 — 禁用混合进行3D渲染，渲染后显式恢复 `blendFunc(srcAlpha, oneMinusSrcAlpha)`
  - 不再使用 `Gl.isEnabled()` 保存状态，改为显式设置/恢复所有GL状态
- **修复模型放大后阴影偏移和边缘锯齿**：
  - worldSize因子 1.4 → 1.15 (更紧凑的fit，减少阴影偏移)
  - FBO分辨率倍率 8 → 16 像素/世界单位
  - FBO最大分辨率 1024 → 2048 (消除放大时的锯齿)
- **create-universal-display底座改为3x3炮台底座贴图**：使用vanilla `ripple-base` (3x3炮台底座)

### v1.9.1 (3D渲染系统修复+纹理支持+性能优化)
- **修复炮台模型旋转方向相反**：GPU渲染器 Mat3D.rotate(Z,+deg) 是逆时针(标准OpenGL)，与旧CPU渲染器(顺时针)相反
  - PrismTurret/WavefrontTurret: `90f - rotation` → `rotation - 90f`
  - ObjPowerTurret: `-rotation` → `rotation`
- **修复wavefront模型贴图全白**：GPU着色器新增纹理采样支持
  - Mesh新增 texCoords (UV) 顶点属性 (每顶点 7→9 float)
  - 着色器新增 `u_texture` sampler2D + `u_hasTexture` int uniform
  - OBJ加载时存储材质 map_Kd 纹理，渲染时绑定并采样
- **修复FBO污染全局GL状态导致光效消失**：
  - 保存/恢复 `Gl.depthTest`、`Gl.cullFace` 状态
  - `Gl.depthMask(false)` 必须恢复 (否则2D batch写入深度缓冲导致渲染异常)
- **修复渲染模型出现黑色大块**：
  - FBO是静态共享资源，`Draw.rect` 加入batch后未立即提交，下一个模型的FBO渲染会覆盖纹理
  - 在 `Draw.rect` 后添加 `Draw.flush()` 立即提交FBO纹理到屏幕
- **提高渲染精度**：FBO分辨率从 128-512 提升到 256-1024，2^n 对齐优化GPU效率
- **性能优化**：
  - 复用 `distortData` 数组避免每帧GC (applyDistortion)
  - 复用 `Tmp.v31` 避免Vec3分配
  - FBO初始尺寸从 256 提升到 512 减少首次resize

### v1.9.0 (3D渲染系统重做 + tenmeikiri加强)
- **3D渲染系统完全重做 (GPU深度缓冲)**：彻底重写 [WavefrontObject.java](src/zzw/util/WavefrontObject.java)
  - 旧版 CPU 软件渲染器存在面排序/z-fighting 问题，Y轴旋转时模型崩坏
  - 新版使用 GPU 深度缓冲 + FrameBuffer 离屏渲染，完美支持任意3D模型和任意轴旋转
  - OBJ 加载时构建 GPU Mesh (position3 + normal + color 属性)，三角化后上传 GPU
  - 自定义 GLSL 着色器 ([obj3d.vert](assets/shaders/obj3d.vert) + [obj3d.frag](assets/shaders/obj3d.frag))：MVP变换 + Gouraud方向光照
  - Camera3D 透视投影 + 背面剔除(可配置) + 深度测试，GPU 自动处理面遮挡
  - 支持顶点形变回调(Cons<Vec3>)，保留 ObjPowerTurret 受击形变功能
  - 公共 API 与旧版完全兼容(字段/方法/内部类不变)
- **create-tenmeikiri 全面加强**：
  - 血量 23000 → 43000 (+20000)
  - 激光伤害 7800f → 12000f
  - 激光速度 80f → 120f (激光形成更快)
  - 比例伤害 1/60 → 1/40 (对高血量单位伤害提升)
  - 超量伤害阈值 350000f → 200000f (更早触发比例伤害)
  - 闪电伤害 85f → 150f

### v1.8.0 (3D模型展示方块)
- **新增 `ObjDisplayBlock` 通用 3D 模型渲染方块**：可加载并渲染任意 `.obj` 3D 模型文件，支持自动旋转（Z/Y 轴）、缩放、阴影、底座贴图
- **新增 `flywheel-display` 飞轮展示台**：使用 MC Create 飞轮模型（258顶点/186面），金属灰色 `normalAngle` 着色，自动旋转展示机械之美
- **技术细节**：基于项目已有 `WavefrontObject` 伪3D渲染器（软件投影），在 v158 原版不支持游戏玩法层真3D的情况下实现3D模型展示

### v1.7.1 (5项炮台渲染/特效修复)
- **create-prism 3D 模型不显示**：WavefrontObject `.obj` 解析器误将注释中的 `vt ` 子串识别为纹理坐标行，导致 `hasTexture=true` 后纹理查找失败。所有行类型检测从 `contains()` 改为 `startsWith()`，并跳过注释行和空行
- **create-supernova 放置预览不显示**：`SupernovaDrawer` 继承 `DrawBlock`（其 `drawPlan()` 为空），改为继承 `DrawTurret`（有完整的放置预览渲染）
- **create-wavefront 阴影缺失 + 旋转方向相反**：draw 方法无阴影渲染代码，添加 `Drawf.shadow()` 圆形阴影；旋转公式从 `rotation - 90f` 改为 `rotation + 90f` 修正模型朝向与炮台瞄准方向一致
- **create-endgame 参数调整**：攻击范围 820→900（+10 格半径），发射间隔 300→210（-1.5 秒），激光特效 clipSize 同步调整
- **create-tenmeikiri 分割单位特效修复**：
  - 根因：`hitUnitAntiCheat` 调用 `unit.damage()` → `kill()` → `remove()` 后 `isValid()` 返回 false，导致 `createCut` 直接返回，分割效果从未创建
  - 移除 `createCut` 中的 `isValid()` 检查，改用 `unit.type != null`
  - 改用 `Draw.rect(region)` 替代 `unit.draw()`：unit 被 remove 后 `draw()` 可能不渲染，改用深拷贝的 `unit.type.region` + `unitRotation` 渲染，不依赖 unit 内部状态
  - 触发条件从 `(u.dead || u.health >= MAX_VALUE)` 改为 `(u.dead || u.health <= 0f)`，更可靠检测单位被击杀

### v1.7.0 (扭矩系统 + 传送器/传送带移植 + 经验储罐取出机制)
- **完整移植动力扭矩系统（PU_V8）**：32 个文件（22 核心类 + 10 方块类），三层架构完整还原
  - 配置层（`graphs/`）：`Graph` + `GraphTorque` 系列，定义转速/扭矩/摩擦等参数
  - 运行时层（`graph/`）：`BaseGraph` + `TorqueGraph`，BFS 转速传播 + 应力分配
  - 连接器层（`modules/`）：`GraphModule` + `GraphTorqueModule` 系列，处理端口连接与扩展更新
  - 11 个方块：手摇曲柄 / 风力涡轮机 / 水轮机 / 电动机 / 无限扭矩 / 传动轴 / 内联变速箱 / 轴路由器 / 简单传动 / 螺旋钻机 / 机械提取器
  - v155.4 适配：`Buildingc` 接口不暴露 `Building` 类方法，通过 `GraphBuildBase.asBuilding()` 桥接模式转换；`block`/`rotation`/`tile`/`enabled` 在 v155.4 中是字段而非方法；`drawRequestRegion` → `drawPlanRegion`；`liquids.total()` → `liquids.currentAmount()`；`Styles.clearTransi` → `Styles.clearTogglei`
- **经验储罐取出经验机制**：修改 exp-output（ExpHub）主动从链接的经验储罐抽取经验
  - `ExpTank.hubbable()` 从原版 `false` 改为 `true`，使储罐可被 exp-output 链接
  - `ExpHub.updateTile()` 主动调用 `ExpTank.unloadExp()` 抽取经验，凑够一个经验球后发射
  - 仅对 ExpTank 主动抽取，不抽取 ExpTurret（炮台需要经验升级）
- **Teleporter 移植（PU_V8）**：12 颜色频道传送器，玩家可配置频道进行双向传送
- **3 种传送带移植（PU_V8）**：含经验系统的传送带完美还原（DrawOver 分层渲染 + 经验球沿传送带流动）
- **exp 系列物品用法说明**：在物品介绍中写清楚经验系列物品的用法

### v1.6.1 (5炮台修复 - 严格按PU132原版)
- **create-supernova 完全重写**：修复之前重写失败的问题（不转向/不显示底座/不发射/无蓄力动画）
  - 移除 `SoulLaserTurret.updateTile()` 中的 `efficiency *= soulEfficiency()` 副作用（导致 LaserTurret.updateShooting 检查失败，炮台无法发射）
  - `chargeWarmup` 从 0.002（原版8秒充满）改为 0.015f（约1秒充满），phase 累积同步提速
  - 完整移植 PU132 6 部件 drawer（outline + 主体 + core）+ heatDrawer 加法混合渲染到 `SupernovaDrawer.draw()`
  - 保留完整机制：attractUnits 吸引单位 + 持续闪电 + 星辰闪光环 + 充能音效
- **create-endgame 慢闪电调优 + 红色光束 + 湮灭特效**：
  - 慢闪电参数：nodeLength=80（更长的闪电链）、splitChance=0.025（减少分叉）、jaggedPoints=1、jaggedness=0.06（更自然的锯齿）
  - 光束攻击（非激光）：每个眼睛发射多层叠加红色光束（f53036 红 → ff786e 淡红 → 白），持续时间 76f（原版时长）
  - 湮灭特效：被打死的单位触发 mixcol(red,1) + additive 混合 + 渲染单位 fullIcon 的湮灭效果
  - 修复编译错误：添加 `import mindustry.graphics.Layer;`
- **create-tenmeikiri 真正的单位切割动画**：重写 [UnitCutEffect.java](src/zzw/content/units/effects/UnitCutEffect.java)
  - 使用 v155.4 内置 `Draw.stencil(mask, content)` API 替代简化版两半椭圆模拟
  - mask 半平面 quad 遮罩 + content 单位贴图渲染，实现真正的单位切割
  - 摄像机偏移让两半飞出动画更具视觉冲击力
  - 末期爆炸（dynamicExplosion + scorch + deathSound）+ 持续烟尘
- **create-prism 钻石锥形修复 + 反向旋转**：
  - 修复法线剔除 bug：`Math.abs(face.normal[0].angle(Vec3.Z)) >= 90f` 在伪3D俯视相机中错误剔除大量面，通过 `cullBackfaces` 字段（默认 false）控制
  - 钻石旋转方向改为与炮台相反：`prismRotation -= prismHeat * prismRotateSpeed * Mathf.signs[id % 2]`
- **create-wavefront 3D 炮身显示修复**：与 prism 共用 WavefrontObject.cullBackfaces 修复，伪3D俯视相机不再错误剔除面

### v1.5.2 (EndGame 红光束 + tenmeikiri 切割 + prism 钻石 + wavefront 修复)
- **create-endgame 完整移植红色光束**：按 PU_V8 UnityFx.endgameLaser 原版完整移植 endgameLaserEffect。3 层颜色叠加（f53036 红 → ff786e 淡红 → 白）+ 持续时间从 22f 升级到 76f（原版时长）+ 头部偏移动画（lerp 渐进到目标点）。每个眼睛发射激光时不再只是单线，而是真实的多层叠加红光束
- **create-tenmeikiri 还原切割效果**：新增 [UnitCutEffect.java](src/zzw/content/units/effects/UnitCutEffect.java)，当大单位（hitSize>=30）被激光击杀时触发切割动画：沿激光方向将单位分为两半飞出 + 持续烟尘 + 末期爆炸（dynamicExplosion + scorch + deathSound）。简化版用两半椭圆 + 红色切线模拟切割，因 v158 EffectState 是注解生成的 pooled entity 不能继承，改用 Effect + CutData 模式
- **create-prism 还原钻石形状**：直接复制 PU_V8 原版 prism.obj 内容（6 顶点 + 8 三角形面，钻石形），将所有三角形面转为退化四边形避免 WavefrontObject odd=true 延迟渲染污染。模型 size 从 1.0f 调整到 2.5f（defaultScl(4) * 2.5 = 10 倍缩放，模型高度 25 单位，匹配炮台占地 50 单位的 1/2，原版 PU_V8 prismOffset=10f）
- **修复 create-wavefront 3D 炮身未显示**：wavefront.obj 中有 8 个三角形面（3 顶点）触发 odd=true 延迟渲染，导致 3D 炮身被 Face.data 共享数组污染后无法正常显示。将所有三角形面转为退化四边形（重复末顶点），所有面现在都是 4 顶点走即时渲染路径

### v1.5.1 (炮台问题修复)
- **慢闪电锯齿渲染优化**：在 `SlowLightningType` 添加 `jaggedPoints`/`jaggedness` 字段，draw() 方法在每段插入中间锯齿点形成真实闪电效果。性能优化：静态缓冲区避免 GC 压力，基于位置的稳定 hash 偏移避免视觉抖动。压迫者（`SlowLightningBulletType`）和 create-endgame（`EndGameTurret`）均启用 `jaggedPoints = 2`
- **完整重写 create-supernova**：严格遵循 PU_V8 原版结构重写。核心改动：
  - **drawer/heatDrawer lambda → 自定义 DrawBlock 子类 `SupernovaDrawer`**：v158 Turret.drawer 是 DrawBlock 类型而非 Cons，合并 PU_V8 的 drawer lambda（绘制 6 部件 outline + 主体 + core z+0.001）和 heatDrawer lambda（heat region 加法混合）到单个 draw() 方法
  - **修复信息显示界面**：icons() 之前只返回 `-head` 单张图导致显示不全，改为返回底座+所有部件 8 张图组合
  - **修复不能开炮**：项目配置中未添加 `consumeLiquid`，导致 `coolant == null`，`novaCharge` 累积公式中 `coolant.amount = 0` 永远累积不到 1。新增无 coolant 时的等效累积路径（用 `baseReloadSpeed() * Time.delta`）
  - **完整保留蓄力动画**：charge/phase/starHeat 三阶段充能 + attractUnits 吸引单位 + 持续闪电 + 星辰闪光环（UnityDrawf.shiningCircle）+ PitchedSoundLoop 等效（v158 soundLoop 系统重写 shouldActiveSound/activeSoundVolume）
  - **字段重命名**：`charge` → `novaCharge`（避免 shadow v158 TurretBuild.charge 充能进度字段）；`tr2` → `recoilOffset`（v158 字段名变更）
- **修复 create-banshee 子弹位置偏移**：v155.4 `bullet(type, xOffset, yOffset, ...)` 期望 LOCAL 局部坐标（rotation-90 坐标系），之前错误传入了已旋转的世界坐标 `tr3.x/tr3.y` 导致双重旋转。改为传入 LOCAL 坐标 `xOff/yOff`，bullet() 内部自动旋转。同时构造函数设置 `shootY = 0f` 清除默认前向偏移，自定义每个炮管的前向偏移
- **修复 create-prism 3D 模型渲染污染**：prism.obj 的三角形面（3 顶点）触发 WavefrontObject 的 `odd=true` 延迟渲染路径（`Draw.draw(z, face::draw)`），共享的 `Face.data` 数组被后续 `updateFace()` 覆盖污染，导致多 prism 同时存在时模型错乱。将三角形面转为退化四边形（重复末顶点），让所有面都是 4 顶点走即时渲染路径
- **优化 create-prism 模型尺寸**：原 `prism.size = 4f`（实际缩放 16 倍，模型 32 单位占满整个炮台）过大，调整为 `1.0f`（约 8 单位高，炮台的 1/4），更接近原版 PU_V8 的视觉比例

### v1.5.0
- **移植外径行者（exowalker）**：Plague 阵营地面单位（8 腿），6000 血，5 武器（4 瘟疫导弹发射器 + 1 吸血激光 SapBulletType）
- **移植瘟疫蜂群（toxoswarmer）**：Plague 阵营地面单位（6 腿），7000 血，1 武器（8 连发追踪导弹 + fragBullet 火焰弹）
- **移植荒芜者（desolation）**：End 阵营终极地面单位（8 腿），307300 血，多武器系统（蓄力主炮 + 点防激光 + 副炮 + 闪电炮 + 4 触手）
- **防作弊系统架构重构**：
  - 新增 `EndGroundUnit extends LegsUnit` 类，用于 End 阵营腿单位（ravager/desolation）
  - 原有 `EndLegsUnit extends UnitEntity` 仅用于 End 阵营飞行单位（enigma/voidVessel/chronos/opticaecus）
  - ravager constructor 从 `LegsUnit::create` 改为 `EndGroundUnit::create`（之前无防作弊系统）
  - desolation constructor 从 `EndLegsUnit::create` 改为 `EndGroundUnit::create`（之前腿不显示）
  - Plague 阵营单位（exowalker/toxoswarmer）使用 `LegsUnit::create`，无防作弊系统（仅 End 系列具备）
- **修复虚空容器激光连发机制**：3 连发大激光，每发间隔 3 秒，3 发后 10 秒冷却（reload=50 秒总周期）
- **视界虫更名**：原"视界虫"改为"盲视者"（飞行单位非多节，不应叫虫）
- **修复编译错误**：fragCone→fragRandomSpread（v158 字段名变更），legTrns 删除（v158 无此字段），shoot.burstSpacing→shoot.shotDelay，Fx.sap→Fx.sapExplosion，SapBulletType 用 color 字段而非 frontColor/backColor

### v1.4.3
- **修复掠夺者（ravager）腿不显示**：constructor 错误使用了 `UnitEntity::create`（飞行单位 entity，不实现 Legsc 接口），导致 `UnitType.drawLegs()` 因 `unit instanceof Legsc` 为 false 而不被调用。改为 `LegsUnit::create`（腿单位 entity），现在 8 条腿正常显示
- **修复掠夺者炮台贴图不显示（有影子无贴图）**：两个炮弹武器错误命名为 `create-ravager-artillery-1` 和 `-2`，两个小型炮台错误命名为 `create-ravager-small-turret-1` 和 `-2`，但贴图文件只有 `ravager-artillery.png` 和 `ravager-small-turret.png`（atlas key 为 `create-ravager-artillery` / `create-ravager-small-turret`），Weapon.load() 用 name 查找 atlas 找不到 → region.found()=false → 只画 shadow 圆形阴影不画炮台。改回 PU132 原版设计：两个炮弹武器共用 name `create-ravager-artillery`，两个小型炮台共用 name `create-ravager-small-turret`，共用同一贴图
- **噩梦激光武器 top=false**：等价于 PU132 的 `bottomWeapons.add(this)`，让武器画在 body 下方（先画武器再画 body），看起来更像嵌入式炮台

### v1.4.2
- **修复压迫者/噬界虫大激光方向不固定**：改用 rotate=false + shootCone=360f，continuous 武器激光方向=unit.rotation+baseRotation（固定），shootCone=360f 绕过 Angles.within 检查确保任何角度都能发射（之前用 rotate=true 会导致 mount.rotation 跟随目标旋转，激光方向不固定）
- **给虚空容器添加红色大激光武器**：OppressionLaserBulletType（和压迫者一样的红色大激光），rotate=false + shootCone=360f 固定方向
- **移植视界虫（opticaecus）**：End 阵营飞行单位，60000 血，装备红色激光（LaserBulletType，1400 伤害）+ 导弹发射器（MissileBulletType，10 连发），PU132 原版有隐身能力（InvisibleUnitType），v158 简化为普通 UnitType
- **移植掠夺者（ravager）**：End 阵营地面单位（8 腿），1650000 血，装备噩梦激光（EndPointBlastLaserBulletType，直线碰撞+范围爆炸）+ 两门炮弹 + 两座小型炮台，免疫所有状态效果
- **新增 EndPointBlastLaserBulletType 子弹类型**：激光直线碰撞检测 + 阻挡点范围爆炸，多层颜色叠加渲染，三模块防作弊
- 新增 ravager-nightmare-shoot、end-basic-large、end-missile、end-basic-small、end-basic、devourer-main-laser 音效
- 新增 opticaecus、ravager、doeg-launcher、doeg-destroyer、doeg-small-laser、ravager-nightmare、ravager-artillery、ravager-small-turret 贴图资源

### v1.4.1
- **修复压迫者红色大激光无法释放**：主激光武器缺少 `rotate=true`，导致 omniMovement=false + circleTarget=true 单位因 unit.rotation 不朝向目标而 shootCone(5°) 永不满足，shoot() 不被调用 → firstShotDelay 蓄力路径不走 → 完全放不出激光。添加 rotate=true + shootCone=30f，让 mount.rotation 独立朝向目标
- **修复虚空容器黑色激光不可见**：v158 bloom 在 Layer.effect+0.02f 处 apply 后保持 additive 混合模式，黑色像素在 additive 模式下完全不可见。在 draw() 和 voidFractureEffect 中显式调用 Draw.blend() 重置为 alpha 混合
- **修复 v158 Bullet.drag() NoSuchMethodError**：v158.1 移除了 Bullet 的 drag() 方法和 drag 字段，Phase 2 冲刺改为每帧重置 b.vel().trns(rotation, trueSpeed) 克服 type.drag 衰减
- 清理所有调试日志（System.out.println），catch 块改用 arc.util.Log.err 正确记录错误

### v1.2
- 移植 PU132 多节单位系统（arcnelidia / toxobyte / catenapede / devourer / oppression）
- 完整实现 WormComp 速度传播 + 约束修正算法
- 移植 VoidPortal 虚空门户技能（菱形区域伤害 + 触手拉拽）
- 移植 SlowLightning 慢闪电三件套（Entity + Type + Node）
- 移植 EndSweepLaser 扫射激光 + VoidArea 黑色圆形虚空区域
- 移植防作弊子弹系统（AntiCheatBulletTypeBase + ArmorDamageModule）
- 实现 WormDecal 液压杆装饰系统（延迟加载 + 多段绘制）
- 实现 OppressionLaserBulletType 7 层渲染大激光
- 实现 ChargeEffect 5 阶段充能前摇特效
- 添加机械网络系统（Betamindy 风格）
- 添加方块合并系统（小铜块/小铁块自动合并）
- v158 兼容性适配（ammo/useAmmo 字段反射兼容）
- 修复激光伤害检测（oppression 圆形范围 → 线段精确碰撞）
- 修复 devourer 大激光伤害不足问题
- 修复多节单位AI不主动索敌移动问题（优先攻击核心）
- 实现 circleTarget 环绕与直线冲过两种移动模式
- 技能数量上限：非大招技能同时最多 8 个存在
- 段身蠕动动画：正弦波叠加实现生物活性视觉效果

### v1.1
- 初始版本发布
- 添加基础金属加工系统
- 添加各种生产设备和防御方块

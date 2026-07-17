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

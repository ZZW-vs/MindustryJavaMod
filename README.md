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
- **经验球** - 用于升级单位和建造高级设备
- **经验储罐** - 存储经验球的设备，可与其他经验设备连接

### 新增方块

#### 生产设备
- **铁板制造机** - 将铁锭加工成铁板的基础工业设备
  - 每次消耗2个铁锭，生产2个铁板
- **金板制造机** - 将金锭加工成金板的精密工业设备
  - 每次消耗2个金锭，生产2个金板
- **铜板制造机** - 将原生铜加工成铜板的基础工业设备
  - 每次消耗2个原生铜，生产2个铜板
- **经验输出器** - 从链接的经验储罐中提取经验球并发射
  - 主动从链接的经验储罐抽取经验，凑够一个经验球后发射
- **经验焚化炉** - 焚化物品与液体转化为经验球弹出的设备
  - 通电后焚化任意物品（可为不同种类）与所有液体，每焚化 5 个物品或 10 单位液体弹出一个经验球（最高 1.2 个/秒），经验球朝方块朝向 2 格外弹出（放置时 R 键调整方向），附近经验持有者（炮台/储罐/墙）飞过拾取；耗电比原版焚化炉多 0.2/tick，液体容量 20

#### 坩埚热力系统
- **坩埚容器** - 大型熔融物容器，扩展坩埚网络的容量
  - 点击可打开信息面板：坩埚温度条（含各物品熔点标记）、内容物堆叠图、所有可熔物品的熔点参考列表
- **铸模** - 将熔融物浇注并冷却铸回物品
  - 温度过高时面板显示"温度过高，无法铸造！"提示（已汉化）

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
- **PU 墙体系统**（对照 PU132 原版补齐功能）：
  - **玻璃墙（metaglass-wall / -large）** - `LightWall`：光可穿过墙体并按 `suppression=0.8` 衰减，接入光学系统
  - **单极子墙（electrophobic-wall / -large）** - `PowerWall`：受能量弹（激光/闪电等）攻击时按弹种倍率转化为电力输出，效率超 1 过载扣血，效率超阈值显示热图；友方治疗弹命中清零
  - **铜镍合金墙（cupronickel-wall / -large）** - `HeatWall`：接入坩埚热量网络，墙体温度越高越持续灼烧并伤害周围单位
  - **限伤墙（ustone/dense/steel/dirium-wall）** - `LimitWall` / `LevelLimitWall`：补回限伤命中（`withstandFx`）与闪烁免伤（`blinkFx`）特效；迪里姆墙恢复原版 `sparkle` 待机特效
  - **经验限伤墙（ExpLimitWall）** - 对齐原版 `hubValid`/`canHub` 逻辑，血条旁显示减伤百分比图标
  - **石头开采（PU 原版机制）** - 原版石头地板 / 岩坑地板（`Blocks.stone` / `Blocks.craters`）设置 `itemDrop = stone`，用钻头钻取可获得「石头」物品；与原版一致设为 `playerUnmineable`，禁止手动挖掘
  - **经验墙信息面板（PU 原版）** - 还原 `exp.tooltip` 详细经验说明：主动等级方块与 Exp. 球、古代塔/卸载器交互且经验可自由传输；被动等级方块不与 Exp. 球/塔交互，缓慢获得经验但无法自由利用

#### 炮台系统 (PU132 还原)
本模组的高级炮台全部对照 PU132 原版逐项还原，主要修正包括：
- **双炮管交替发射**：apparition / higgs-boson 的两根炮管改为原版交替开火（`ShootAlternate`）
- **激光粗细还原**：photon 激光变细、graviton 改为原版较细的半透明激光
- **液体强化数值**：ephemeron / muon / singularity / z-boson / higgs-boson / electron / electrobomb / plasma / current / shockwire / orb 统一为水 135%、冷冻液 185%；ghost / banshee 为水 120%、冷冻液 145%
- **子弹形态**：z-boson 改为原版长条型（取原版 2/3 长度）；banshee 子弹整体尺寸微增；celsius / kelvin 射速与拖尾还原；ephemeron 阴阳粒子碰撞特效还原
- **特殊机制**：gluon 结尾改为原版小漩涡；proton 转速微增；shockwire 激光加粗；plasma 射程 65 格；arc-caster / arc-storm 蓄力与六边形效果还原
- **supernova**：三项还原修正——① 蓄力光球按原版前移到炮口（`shootLength = size × tilesize / 2 − 8 = 20`，此前误设为 8，光球贴在炮台中心）；② 补回"开火结束后炮管泛蓝"的热感发光（原版 `cooldown = 0.006f` 约 2.8 秒才褪完，v160 该字段拆为 `cooldownTime`，默认 20f 只需 0.33 秒就褪光，肉眼看不到 → 换算为 `cooldownTime = 166.7f`）；③ 转向改为读取 block 上的 `firingMoveFract`（原先写死 0.2f，导致该配置项失效），光束存在期间按该系数减速，其余时间全速跟随目标（玩家操控时即始终跟随鼠标）
- **经验激光炮台（charge / frost / fractal / swarm）**：四项按 PU132 原版还原攻击方式与特效——
  - `charge-laser-turret`：改为**蓄力单发**（`shoot.firstShotDelay = 50`，蓄力播放 `laserCharge` + `laserChargeBegin`、开火播放 `laserChargeShoot`），弹药换成原版 `shardLaser`（150 长度 / 30 伤害 + 电击状态 + 激光碎片）
  - `frost-laser-turret`：弹药换成原版 `frostLaser`（170 / 130 + 冻结状态 + 冰片射击特效），命中生成**冻结圈**（`freezeEffect` + 冻结音效 + 范围冻结/瘫痪）；命中按原版 `expGain = 2` 给炮台加经验（该炮台无碎片弹，这是它唯一的升级途径）；炮台贴图沿用原版（此前误判为无贴图，已恢复）
  - `fractal-laser-turret`：改为**蓄力裂缝激光**（蓄力 `laserFractalCharge` / `laserFractalChargeBegin`，开火 `laserFractalShoot`），命中生成 `DistFieldBulletType` **扭曲力场**（减速场内敌方单位及其子弹）并播放闪电链 + 多个随机小力场；炮台贴图沿用原版（经核对 PU_V8 参考，`fractal-laser-turret.png` 存在且为完整炮台贴图，此前误判为无贴图，已撤销透明化处理并恢复）
  - `swarm-laser-turret`：由"简化版 ExpPowerTurret"改为新建的 `BurstChargePowerTurret`——**一次装填按 20 tick 间隔依次蓄力发射 4 发**，每发独立播放蓄力特效（忠实还原原版连发蓄力攻击方式），弹药为 `branchLaser`（140 / 20 + 3 发分裂碎片）
- **endgame**：按 PU132 原版**逐行忠实移植**（draw / updateTile / updateEyes / eyeShoot / killUnits / killTiles / shoot / collision / damage 的数值、判定顺序、特效参数与图层全部照抄）——三层旋转环 + 16 眼追踪光束（光束攻击半径 = 炮台射程 820）、攻击时轮盘旋转与底座纹理亮起、眼睛朝目标偏移、内外圈逆时针依次发射秒杀光束、齐射时以自身为中心湮灭爆炸
  - **慢闪电**：改为 PU132 原版的慢闪电形态——单段长度回到原版 50（远小于延伸距离，因此会自然**弯折 + 分叉**，不再是笔直光柱），延伸距离按需求取**炮台射程的一半**（820 / 2 = 410，原版为 810），并限制同时存在数量不超过 6 条；沿线段连续施加 520 × 效率 的伤害
  - **通电就亮修复**：v158 中 `efficiency` 会被必选物品消耗（terminum）拖成 0，导致通电也不亮；改用 `power.status`（纯电力满足度）作为"通电"信号，与物品解耦，通电即亮眼睛与底部线路
  - **建筑防作弊注册**：移植 PU132 `Unity.antiCheat.addBuilding(this)`（`AntiCheatBuildings`）——炮台注册进防作弊表，每 15 帧校验：被外部模组强行从地图上抹掉就原样写回、被从实体组剔除就重新加入；仅在真的被打死或被同队拆除时才注销
  - **秒杀机制（强化）**：保留原版全部湮灭/防作弊机制（`annihilateEntity` / `annihilateUnit` 等），并对射程内**所有敌方单位与建筑**（含带复活、真实血量、特殊护盾等神秘机制者）执行强制湮灭，确保无法被常规手段规避

#### 3D模型展示设备
- **MMD模型展示台** - 专门用于展示MMD模型的3D展示台
  - 支持4种MMD模型：初音-黑/白、重音-普通/病娇
  - 可调节旋转、位置、大小等参数
  - 支持自动旋转和手动控制
- **通用3D模型展示台** - 可加载任意.obj模型的通用展示台
  - 支持自动旋转（Z/Y轴）、缩放、阴影、底座贴图
- **图片展示台** - 2D图片显示设备
  - 内置6张图片可供切换展示
  - 支持位置、大小、旋转、透明度等参数实时调整
  - 支持RGBA颜色通道调节和动画效果

- **3D 渲染引擎（光照升级 1.3 + 性能优化）** - 所有 3D 模型与炮台共用的伪 3D 渲染管线
  - **真透视相机**：采用固定针孔相机（`rbmk.gfx.Cam`），地面平面与网格 1:1 对齐，模型高度按正确透视缩短
  - **统一真光照**：`normalAngle` 与 `topLight` 两套着色合并为一份实现，环境光 + Lambert 漫反射决定明暗、Blinn 高光叠加高亮
  - **高光带本色**：高光按金属度在“白”与“材质本色”之间取色（`tintedSpecular`），避免纯白高光把有色材质洗白
  - **性能优化**：着色复用加载期预计算的模型空间法线（每面只做一次矩阵旋转，省去逐顶点求平均 + 开方）；`cullBackfaces=false` 时跳过整圈法线变换；移除每帧面数组分配与失效的排序死代码，减少手机端 GC 压力
  - **棱镜炮台（prism）着色对齐原版**：整个 3D 模型只用**单一材质色**（`fromColor`→`toColor` 渐变），光照按 PU132 原版光照环境做**乘性变暗**（环境光 0.4 + 平行光 0.56，最暗约 40% 亮度，不改变色相），不再使用不同色相的双色明暗

#### 物流设备
- **传送带** - 用于输送物品的机械设备
- **传送器** - 12颜色频道传送器，玩家可配置频道进行双向传送
  - 支持12个预设颜色频道
  - 支持自定义频道名称
  - 相同频道的传送器互相连接

#### 机械网络系统
- **手摇曲柄** - 人力驱动的动力源，提供基础扭矩
- **风力涡轮机** - 利用风力产生动力的设备
- **水轮机** - 利用水流产生动力的设备
- **电动机** - 电力驱动的动力源，提供稳定扭矩
- **无限扭矩** - 提供无限动力的特殊设备
- **传动轴** - 传输动力的基础连接件
- **内联变速箱** - 可调节传动比的变速设备
- **轴路由器** - 分配和路由动力的设备
- **简单传动** - 简单的动力传输装置
- **螺旋钻头 (auger-drill)** - 旋转钻探设备，可挖掘资源（3x3）
- **螺旋抽水机 (mechanical-extractor)** - 扭矩驱动抽水，需建在水层上（3x3）
- **螺旋石油钻井 (oil-derrick)** - 扭矩驱动抽油机，需建在油田上；消耗 10 水/秒 + 2 沙/秒，产量随油田品质与转速变化（3x3）

#### 热力系统 (PU132 移植)
- **热管** - 连接热量网络的导热管道，贴图按 4 邻居连接位掩码自动拼接；高温时发光并灼烧踩上方的单位
- **小型散热器** - 热量网络耗散端，向环境散热
- **地热加热器** - 利用热液地板产热
- **燃烧加热器** - 焚烧可燃物产热
- **太阳能集热器 / 太阳反射镜** - 反射镜为集热器聚焦光线产热
- **无限热源 / 无限冷源** - 沙盒专用热量源与冷源（冷源使网络热量归零）

#### 模块化系统 (PU132 移植)
- **剁刀 (chopper)** - 扭矩驱动的模块化切割方块（PU132 `Chopper` 完整移植）
  - 内置**蓝图编辑器**：在 9x1 零件网格上摆放旋转枢轴 / 刀刃 / 锯齿刀刃 / 连杆，实时显示连接端口、零件造价与总造价（网格由原版 7 格加长至 9 格；空置的 saw 分类标签自动隐藏）
  - 蓝图经 `IntPacker` 行程压缩后存入方块配置，支持存档读写（`writeExt`/`readExt`）
  - 按蓝图收取材料：未建满时用 `drawConstruct` 着色器绘制半成品扫描光带；可从核心自动补料（`updateAutoBuild`，沙盒/无限资源下瞬间建成）
  - 建满后随扭矩网络转速旋转，转速 >0.8 时对路径上的敌人与建筑造成分段伤害并产生击退扭矩
  - 移植支撑类：`PartInfo`/`PartType`/`PartStat`/`PartStatType`/`StatContainer`/`Segment`/`IntPacker`/`ConnectData`/`ModularConstructorUI`/`BorderImage`（`zzw.content.blocks.modular`）

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
- ★ constructor 改用 `EndGroundUnit::create`（extends LegsUnit），同时具备防作弊系统和正常显示腿

#### 外径行者 (exowalker)
- Plague 阵营地面单位（8 腿），6000 血，速度 0.7（PU132 原版不设护甲，即 0）
- **武器1-4：瘟疫导弹发射器**（small-plague-launcher，4 连发，9 伤害 + 17 范围伤害，蛇形追踪，1.5 秒冷却，`otherSide` 配对 2↔0 / 3↔1）
- **武器5：碎片激光**（drain-laser / 引擎原生 ShrapnelBulletType，43 伤害，长度 80，toColor=瘟疫色，3 连发间隔 17.5tick）
- 8 腿行走，瘟疫色（#a3f080）涂装
- 不具备防作弊系统（仅 End 系列单位具备）

#### 瘟疫蜂群 (toxoswarmer)
- Plague 阵营地面单位，7000 血，速度 1.1（PU132 原版不设护甲，即 0）
- **武器1：8 连发巡航射手弹**（toxo-launcher / `ShootingBulletType`，200 伤害 + 30 溅射，40 溅射半径）
  - 导弹飞向最近敌人并在其外侧 60 像素处绕飞（smoothness 35、reloadTime 4），
    每 4 帧朝目标发射一发 `FlameBulletType` 火焰弹（5 速 / 15 伤害，4 色渐变，穿透 2，命中小范围 4 溅射）
  - 复刻 PU132 `ShootFx.plagueShootSmokeLarge`（炮口烟雾）与 `HitFx.plagueLargeHit`（重型命中）特效
- ★ 腿系统改用 `CustomLegsAbility`（完整移植 PU132 CLegGroup），2 组腿（小腿组 6 条 + 大腿组 4 条），由 `MixedLegUnitType.drawLegs()` 委托渲染
- 瘟疫色（#a3f080）涂装，不具备防作弊系统（仅 End 系列单位具备）

#### 荒芜者 (desolation)
- End 阵营终极地面单位（8 腿），307300 血，速度 0.7，护甲 35（PU132 移植）
- **武器1：蓄力主炮**（EnergyChargeWeapon / DesolationBulletType，2500 伤害 + 三防作弊模块，15 秒冷却 + 8 秒持续，蓄力 4 阶段红色特效）
- **点防激光 ×4**（end-point-defence，镜像补左，220 伤害，单发瞬时命中）
- **副炮 ×8**（end-mount，3 连发，260 伤害，fragBullet 虚空碎裂弹；4 门 + mirror 补左）
- **闪电炮 ×8**（end-mount-2，2 连发，380 伤害 + 220 范围 + 80 闪电伤害，穿透 3 目标；4 门 + mirror 补左）
- **触手 ×4**（desolation-tentacle 15 段 44.5 长，EndPointBlastLaserBulletType 250 伤害 + 1000 范围伤害，3 秒冷却，点射；apocalypse-tentacle 17/14/9 段 37.25 长，EndContinuousLaserBulletType 85 伤害，4 秒冷却，连续激光 1.5 秒）
- ★ 4 条触手×mirror=8 条，完整移植 PU132 NewTentacle（含待机甩动 + 两阶段 IK + 角度限制 + stab 伤害）
- ★ 武器总数 21 门（主炮1 + 点防4 + end-mount8 + end-mount-2 8），对齐 PU132
- 8 腿行走，每腿落地造成 1700 范围伤害
- 免疫所有状态效果
- ★ constructor 改用 `EndGroundUnit::create`（extends LegsUnit），同时具备防作弊系统和正常显示腿

### 世界单位 (PU132 移植)

#### 大地之核 (terra)
- 由 **TerraCore 方块** 召唤的超级单位，携带一个可移动的子世界（8x18 tile）
- 召唤时自动吸收附近建筑物到自身子世界中，建筑物跟随单位自由移动和旋转（PU132 原版行为）
- **子世界原版化交互**：与原世界的交互体验完全一致，无任何自定义 UI
  - **悬停**：右下角原版信息面板直接显示子世界建筑信息（名称/血量/电力/物品条全是原版 UI）；悬停炮台等显示原版射程圈高亮
  - **点击**：打开原版配置界面 / 物品界面（与原版点击方块完全相同；物品源等配置界面正常打开）
  - **建造模式**（默认关闭建造/拆除，只保留方块交互）：点击子世界里的**主大地核心**弹出开关按钮 → **进入建造模式**（子世界边缘呼吸虚线框提示）→ 此时才能放置/拆除；再点核心按钮**退出建造模式**
  - **建造**：建造模式下光标落在平台范围时，放置/拆除直接作用于子世界，预览自动吸附子世界网格（连续坐标映射，平台可自由移动和旋转）；建造队列（单点/拖线/蓝图粘贴）落在平台区域的计划自动转译进子世界——同批计划按**锚点+相对旋转偏移**落位保持布局（单位斜着时拖线仍沿子世界网格直线，蓝图不扭曲）；建造走原版 ConstructBlock 脚手架（建造动画/资源扣费/事件），完成后新建筑自动注册进子世界正常运行；**建造光束**动画：单位中心→脚手架的橙色三角光束（拆除时红色），同原版建造单位观感
  - **让位规则**：主世界该处有建筑时不接管（优先操作地面建筑）
- **大地核心保护**（子世界唯一性）
  - 召唤时吸收范围内存在其他大地核心 → 拒绝实体化，提示"无法实体化"并**高亮罪魁方块**（闪烁选中框 3 秒）
  - 子世界里的主大地核心**不可拆除**；也**不能往子世界里放**新的大地核心
  - 读档后自动识别恢复主核心（扫描子世界内 TerraCore，无需存档格式变更）
- **渲染升级**（PU132 altBatch 方案）：子世界渲染切换到独立 SpriteBatch + z 排序 —— 建筑内部自由切层（炮台热度发光/电力连线/传送带物品动画等）与原版渲染管线一致；每个建筑绘制**柔和阴影**（放大的黑色贴图，贴图边缘透明渐变形成模糊黑边）；建筑整体显示在单位甲板之上
- **长方形碰撞箱**：与子世界平台同样大小（72x152 像素），随单位旋转
  - 敌方子弹打在整个平台范围被要塞吸收（不再穿过平台）
  - 原版悬停检测覆盖全平台
- **网格对齐**（按实测校准）：子世界网格整体居中（gridOff=0，按用户实测往左/上各移一格半=12px 校准）；TerraCore（2x2 偶数方块）吸收时正好落在子世界正中心 tile，**大地核心居中**；放置预览公式与原版 `BuildPlan.drawx()` 完全一致（tile*8+offset），ghost 与实际落位零偏差
- **存档持久化**：通过 `SaveVersion` CustomChunk 区块保存子世界建筑数据，重进地图后子世界内容完整恢复且**位置精确**（v3 存档保存建筑精确像素坐标，消除 tile 重算的亚格错位）
  - 每个建筑保存 [blockId/坐标/朝向/队伍/版本/精确x,y + 完整状态]，核心与单位的绑定关系一并保存
  - 单个建筑解析失败时按长度前缀跳过，不损坏存档；v1/v2/v3 存档完全兼容
  - **读档加固**：区块数据全量缓冲后解析（主流消费恒等于区块长度），子世界数据损坏只损失子世界内容、不再导致整个存档无法加载；版本识别不依赖 mark/reset（存档流 InflaterInputStream 包装下 markSupported 恒为 false，旧检测从未生效曾致 v3 存档读档 EOF 损坏）
- **电力连线**：渲染期间切换 Vars.world 到子世界，PowerNode 电力连线正常显示
- **子弹跟随**：子世界炮台发射的子弹（含蓄力激光）每帧跟随单位平移+旋转，不再停留在发射位置
- **视觉**：影子贴近本体（shadowElevation=0.1f）；子世界炮台不再显示开火红温光效

### 防作弊系统架构
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

## 更新日志
- 修复「大地」(terra) 在子世界内产出单位时漂移/乱窜：
  - **对齐 PU132 原版定位根因**：PU 的 terra（`UnityUnitTypes.java:2387`）是纯地面单位，仅设 `speed/health/worldWidth/worldHeight`；端口此前把它额外改成了低空飞行 + 整平台碰撞箱 + `physics=false` + 极低阻力（`accel=0.08`、`drag=0.03`），其中极低阻力是漂移的放大器——任何一次微小挤动产生的速度几乎不衰减，terra 便朝一个方向持续滑行。
  - **修掉漂移放大器**：恢复默认 `accel`/`drag`（不再覆写），位移会迅速衰减；`physics=false`（实测无效）一并移除，恢复默认 `physics=true`。
  - **保留平台特性**：`hovering`/`lowAltitude`、`hitSize=144` 整平台碰撞箱与悬停建筑信息面板等玩法不变；`WorldUnitType` 的空控制器继续保证未附身时平台不自行移动。
  - **子世界产出落点**：`ModularConstructor` 产出时先把单位落点推到平台包围盒外（避免与平台重叠），并临时 `popWorld()` 回到主世界再 `spawn()`，使新单位完全以主世界为基准生成。
- 全系列（End 除外）单位护甲加强：对照原版 v160 护甲曲线，T3 以上单位全部补齐/提升护甲，护甲为 0 的单位一律补上，**无任何削弱**（血量 / 速度 / 武器均未改动）：
  - **T6/T7 扩展单位**：citadel 20→32、empire 22→36、cygnus 10→24、sagittarius 12→26、araneidae 15→20、theraphosidae 17→28、mantle 15→20、aphelion 17→28、sedec 20→30、trigintaduo 22→34、deviation 12→20、anomaly 18→26
  - **直升机 T1-T6**：caelifera 1（不变）、schistocerca 2→4、anthophila 3→6、vespula 4→9、lepidoptera 5→14、mantodea 6→20
- **全系列（End 除外）单位血量/伤害优化**：按定位对齐原版曲线，只加强、不削弱；高速冲刺单位保持薄血，重装/辅助单位补足血量：
  - **血量调整**：
    - **直升机系列**（冲刺定位）：caelifera 150（不变）、schistocerca 150→340（对齐飞行 T2）、anthophila 450→700（对齐飞行 T3）；vespula 4000（不变）、lepidoptera 9500（不变）、mantodea 25500（不变）
    - **EMP 系列**（电磁脉冲定位）：discharge 60→120（对齐飞行 T1）、pulse 210→340（对齐飞行 T2）、emission 550→680（对齐飞行 T3）；waveform 4500（不变）、ultraviolet 12000（不变）
    - **Scar 多足**（腿部定位）：hovos 340→260（T1 加强）、ryzer 640（不变）、zena 1220（不变）、sundown 9400（不变）、rex 23000（不变）、excelsus 38000→46000（对齐 T6 腿部）
    - **Scar 飞行**（飞行定位）：whirlwind 280（不变）、jetstream 670→570（T2 加强）、vortex 1200→1050（T3 加强）
    - **灵魂/巨石**（机甲/多足定位）：stele 300→300（T1 加强）、pedestal 1200→1000（T2 加强）、pilaster 2000→1800（T3 加强）；pylon 14400（不变）、monument 32000（不变）、colossus 60000（不变）、bastion 120000（不变）
    - **辅助/能量环**（辅助定位）：adsect 180→280（对齐 T1 飞行）、comitate 420（不变）、stray 300（不变）、tendence 1200→600（对齐 T2 飞行）、liminality 2000→700（对齐 T3 飞行）
    - **海军**（海军定位）：fin 36250→31000（T5 加强）、blue 42500（不变）
    - **其他**：cache 560（不变）、anomaly 25000→35000（大型飞行单位）、excelsus 38000→46000（T6 腿部）
  - **伤害加强**：
    - **直升机系列**：机枪伤害 +40%~80%，燃烧弹/闪电/霰弹/火箭伤害 +30%~100%，霰弹枪 +28%，火箭炮 +38%，激光武器 +20%~40%
    - **EMP 系列**：基础弹 +100%，EMP弹 +100%，激光武器 +33%~50%
    - **Scar 系列**：磁轨炮 +20%~40%，导弹 +17%，连续激光 +18%~25%
    - **其他**：T6/T7 磁轨炮 +20%~50%，大型火焰武器 +20%~30%，激光武器 +40%~50%
  - **EMP T1-T5**：discharge 2（不变）、pulse 4（不变）、emission 6→7、waveform 8→10、ultraviolet 10→14
  - **Scar 多足 T1-T6**：hovos 0→1、ryzer 0→2、zena 0→4、sundown 4→8、rex 12→16、excelsus 18→32
  - **Scar 飞行 T1-T3**：whirlwind 1→2、jetstream 2→4、vortex 3→6
  - **koruh**：buffer 0→4、omega 0→6、cache 6→8
  - **灵魂(巨石) T1-T7**：stele 5→6、pedestal 10→11、pilaster 15→16、pylon 23→25、monument 32→34、colossus 45→47、bastion 100→104
  - **巨石辅助 / 能量环**：adsect 0→2、comitate 0→4、stray 0→2、tendence 0→4、liminality 0→6
  - **海军**：fin 17→18、blue 18→20
- **重点单位数值再加强**（citadel / empire / colossus / monument / pylon / cygnus / sagittarius / sedec / trigintaduo）：
  - **citadel（君主）**：护甲 24→32；攻击范围 400→432（+4 格）；主磁轨炮射速 60→48，左右火焰喷射器射速 3→2.5 / 2→1.5，火焰子弹伤害 50→65
  - **empire（帝国）**：护甲 30→36；火焰伤害 75→120；导弹伤害 22→52；炮弹伤害 15→38
  - **colossus（巨像）**：护甲 47→60；主激光 1920→2500、伴随闪电 48→65；环绕闪电球伤害 200→260
  - **monument（丰碑）**：护甲 34→40；激光 640→850；电磁炮弹 6000→7500
  - **pylon（塔）**：护甲 25→26；主激光 2000→2500、伴随闪电 56→72、副激光 192→256
  - **cygnus（天鹅座）**：护甲 18→24
  - **sagittarius（人马座）**：护甲 26（保持不变）
  - **sedec（壁垒）**：护甲 24→30
  - **trigintaduo（天枢）**：护甲 30→34
- **炮台数值调整**（banshee / chopper / fallout 系 / electrobomb / frost-laser-turret / kelvin / prism）：
  - **banshee（女妖）**：四种弹药伤害 ×1.25（石墨 116→145、硅 100→125、火成岩 154→193、钍 170→213），整体 DPS 至少比 ghost 高约 20%
  - **chopper（蓝图剁刀）**：刀刃伤害 8→32、锯齿刀刃 18→72（约 ×4），并在对应的刀具模块介绍里标注攻击伤害
  - **fallout / catastrophe / calamity / extinction**：连续激光伤害整体加强约 30%（95→125、240→310、580→750、770→1000）
  - **electrobomb（电磁炸弹）**：修复"炮弹只能打到射程最远处"——`collidesTiles` 改回 `true`，炮弹在射程内命中敌方单位/建筑即引爆（地形仍不阻挡）
  - **frost-laser-turret（霜冻激光）**：按 PU_V8 原版还原攻击方式——细三层描线激光 + 命中点扩散光圈（`blip`），激光束止于首个命中目标，不再永远画到最大长度（此前误用原版粗激光渲染且束长拉满，表现为"只打到最远处"）
  - **kelvin（开尔文）**：伤害 30→36
  - **prism（棱镜）**：伤害 320→460
- 加强 End 阵营大激光与整体数值：
  - **共享大激光** (`OppressionLaserBulletType`) 伤害 9000 → 15000、建筑伤害倍率 0.4 → 0.6（压迫者主激光与虚空容器大激光共用此弹体）
  - **虚空容器 (void-vessel)**：发射大激光期间（充能中 + 激光跟随中）锁定移动与转向，表现与压迫者大招一致；血量 10000 → 13000，小碎裂弹伤害 600 → 800
  - **压迫者 (oppression)**：大招期间速度倍率 0.12 → 0.05（几乎转不动）；头部炮弹 410 → 520、虚空碎裂弹 800 → 1000
  - **其余 End 单位小幅加强武器伤害**：谜团 200 → 260、克罗诺斯 510 → 650、盲视者 1400 → 1700、掠夺者 1210 → 1500、荒芜者 2500 → 3000、噬界虫主激光 2650 → 3000、深海恐惧奇异点激光 3500 → 4500、天启切割激光 3100 → 3600
- kami（神）弹幕 Boss 弹幕 AI 完整对齐 PU132 原版（"完美移植"，此前为简化版）：
  - **补齐全部 6 个弹幕模式**：此前简化版只有 basicPattern1 / basicPattern2 / expandPattern / flowerPattern 四个，现补回 `flowerPattern2`（35 秒长时花瓣，用 `petal` 双向连发 + `stopChangeDirection` 行为）与 `hyperSpeedPattern`（advance 类，120 秒高速冲刺，带跑道视觉绘制与冲刺逻辑）
  - **模式选取系统**：新增 `KamiPattern.PatternType`（permanent / basic / bossBasic / advance），含 `able` / `limit` / `priority`；`KamiAI.reset()` 按"洗牌 + 优先级排序 + 限额抽取"从池中选题，与原版一致
  - **延迟回调队列**：新增 `KamiAI.delays`（`KamiDelay`），支持在模式运行中延迟若干帧回调，用于连发 / 分段弹幕
  - **连发 (burst)**：新增 `KamiAI.burst(...)`，一次发射多颗并支持首发/后续不同的角度与速度
  - **多阶段图案**：新增 `KamiPattern.StagePattern`（含 `Stage` / `StageData`），负 `time` 时按各阶段时长自动累加总时长，用于 expandPattern / flowerPattern 的分阶段弹幕
  - **图案绘制**：`bossBasic` 类型模式走 `KamiPattern.draw`，用 `RainbowUnitType.trailRegion` 绘制 3 个旋转矩形轨迹特效
  - **自定义激光**：新增 `KamiLaserBulletType`（两端点激光，线段/椭圆碰撞，`hyperSpeedLaser1` 行为）与 `Z_Bullets.kamiLaser2`
  - **数据层**：新增 `KamiBulletData` / `KamiLaserData`，承载宽/长/转向/行为等字段（等价替代 PU132 的注解自定义实体，v160 不引入实体注册）
  - **说明与取舍**：`difficulty` 忠实保持恒为 0（PU132 原版从不递增）；`Angles.shotgun` 用确定性均匀散布实现替代（原 arc 实现不确定）；kami `rotateSpeed=0` 导致 `lookAt` 失效，改用直接设置 `unit.rotation`（视觉与原版一致）
- 借鉴 VE 强化绘制工具与渲染设施（**不移植 VE 代码**，仅借鉴其思路在本项目自研实现）：
  - **新增 `SegmentedRegion`（分段弯曲贴图）**：把一张贴图沿长边切成若干段逐段变形，可绘制弯曲光束 / 能量弧 / 波浪带；顶点格式对齐游戏内 `Draw.vert` 的 6 float 布局，且整条带子<b>一次提交</b>（VE 原版每段提交一次，绘制调用数从 `subDiv` 降到 1）
  - **新增 `PointTrail`（批量折线拖尾 / 光带）**：由中心点列表生成可变宽连续光带，相邻分段共享边点（法线取前后差分）故拐弯不出缝，整条一次 `Draw.vert` 提交；比 `TexturedTrail` 更轻量，可直接当普通字段用
  - **新增 `ImpactBatch`（换批机制 / 冲击帧）**：`beginSwap()` 临时把 `Core.batch` 换成本类实例，其间重写的 `setShader`/`setBlending` 为空操作，使 `Draw.shader()`/`Draw.blend()` 失效——从而给一段绘制<b>强制套上统一着色器与混合模式</b>（`beginSwap`/`endSwap` 须成对，建议 `try/finally`）
  - **性能**：`BlackHoleSFX` 帧缓冲仅在窗口尺寸变化时才 `resize`，不再每帧重建 GL 纹理
- 开发模板（`参考/模板`）升级至 Mindustry 1.60 并对齐 VE 高级写法：
  - 全部模板 API 版本标注 `158.1` → `160.1`，示例 `mod.hjson` 最低游戏版本 `154` → `160`
  - **新增「星球模板.java」**：系统讲解 `Planet` 注册（构造即注册）、恒星光焰（`SunMesh`）、行星表面（`NoiseMesh`/`MultiMesh`）、云层（`HexSkyMesh`）、轨道/光照/大气/战役限制等完整字段，并附带一个最小可用的 `PlanetGenerator`（`getColor` 定色 + `genTile` 生成地形 + `pass` 撒矿），对应 VE 的 `VEPlanets`/`ProximaPlanetGenerator`
  - **重写「科技树模板.java」**：补上 VE 核心写法 `planet.techTree = nodeRoot(...)`（自定义星球整棵树），并移植本项目 `Z_TechTree` 的 `attach`/`node`/`nodeProduce` 辅助方法（160 无 `TechTree.get(parent)`，改用 `content.techNode` 字段）；补充 `Objectives` 清单与 VE 倒金字塔布局 `InvertedPyramidTreeLayout` 说明
  - 更新「Mod主类模板.java」加载顺序为 `物品 → 液体 → 状态 → 子弹 → 方块 → 单位 → 星球 → 科技树`，并在 `loadContent()` 方法体补上星球加载步骤
## 更新日志
- 修复 kami（神）出生即自毁：目标失效或场内暂无可追踪玩家时不再 `unit.kill()`，改为对齐 PU132 原版——清空目标 (`target = null`) 并在下一帧重新索敌，无玩家时仅待机等待
- End 系列 desolation / apocalypse 触手动画与武器数量还原 PU132 原版：
  - **触手待机动画**：移除此前"无目标不摆动"的优化，恢复 PU132 原版——待机时触手持续甩动（`TentacleAbility` 默认 `swayMag` 0.08→0.6），攻击时停止摆动并追踪目标
  - **desolation 武器数量**：补齐后排 4 门 end-mount-2 副炮（此前漏了 (85,-48.25)/(68.75,-65.75) 及镜像）；点防 end-point-defence 恢复镜像（2→4 座）；武器总数对齐 PU132 的 21 门
  - **apocalypse**：武器数量（8 小炮 + 3 激光 + 4 导弹巢 + quetzalcoatl = 16）与 4 条触手本已与原版一致，未改动
- 着色器系统重构为统一注册中心 `ShaderLib`（借鉴 Vanilla-Expansion 的注册中心模式）：
  - 全模组着色器集中登记：黑洞 / 碎裂消散 / 汽化消散 / 切割模板 / 3D 模型直通着色器，原先散落在 `UnityShaders`、`BlackHoleShader` 与 `WavefrontObject` 内联的着色器全部迁入
  - 统一生命周期 `load()` / `dispose()`（重复调用安全、headless 自动跳过），并由 `BlackHoleSFX` 在应用退出时统一释放 GL 资源；此前 `UnityShaders.load()` 从未被调用，碎裂/汽化着色器实例实际为 null 的隐患一并修复
  - 统一资源访问：全部走 mod 自身 `shaders/` 目录（`tree.get`），不再用 `Core.files.internal("shaders/…")` 误指游戏内置 assets；并为所有着色器提供共享的内联顶点着色器，摆脱对游戏内置 `.vert` 的跨版本依赖
  - 所有 uniform 名称与 `apply()` 计算逻辑保持不变，视觉表现无变化
- End 系列单位攻击特效还原 PU132 原版视觉（保留此前调过的节奏/平衡参数）：
  - **oppression 主激光**：颜色公式改回 scarColor 脉冲明暗（`mul(1+sin)`，原为泛白 `lerp(white)`）；散落粒子 22→45、白色闪线 9→18、黑红菱形 20→40、内部线段 10→20、闪电 2→5，`drawEndEdge`/`drawEndVoid` 粒子 7→14 / 11→22；并补回此前完全缺失的命中特效 `endDeathLaserHit`（按目标体积 `hitSize` 播放烟尘+火花）与 `endHitRail`
  - **oppression 充能特效**：补回 PU132 缺失的两段爆发动画——主线爆发 9 组×9 菱形（`t>0`）与主线前 30 方块粒子；13 个尖刺菱形由静止简化为随时间旋转/伸缩的动画版；改用 PU132 的 `Utils.seedr/seedr2/seedr3` 随机序列
  - **oppression 发射特效**：粒子数 35→75（PU132 原版）
  - **oppression 快闪电 (destroyer-4)**：视觉还原 PU132——节点间距 300→80、节点动画 7f、分裂概率 0.06、持续 160f，并移除自创锯齿渲染；仅保留伤害平衡调整
- 平衡与功能调整（本轮批量）：
  - 传送带吞吐：steel/dirium/mechanical 传送带速度分别调整为 15 / 24 / 15 物品每秒（面板同步显示），mechanical 血量 320
  - 反应堆与发电：uranium-reactor 电力输出 120000/秒、耗电 1800/秒、启动 18 秒；seebeck-generator 发电量提高并在信息面板写明具体发电量；absorber 范围 +5 格（90）、最高 82/秒；solar-collector 升温加快
  - 加热/制冷：infi-heater 升温更快；infi-torque 支持点击输入目标转速（留空则持续加速）；新增制冷机 cooling-heater（消耗冷冻液制冷，最低 -200℃）
  - 墙体：补齐 ustone/dense/steel/dirium/shielded/metaglass/electrophobic 系列血量；铜镍合金墙（cupronickel-wall / -large）新增温度限伤机制——常温限伤 100，每高于常温 100℃ 降 5，每低于常温 50℃ 加 5
  - 容器汉化：exp-tank 为「exp.容器」，exp-chest 为「exp.储罐」
  - 模块化构造器配方重排：原版 T3 单位归入 1 级，项目 T3 单位保留 2 级并新增 Scar 系列 T1 hovos，arcnelidia / toxobyte 移入 3 级；补全 Scar 单位升级链（hovos→ryzer→zena→sundown→rex 依次接入原版四重构器，rex→excelsus 接入 recursive-reconstructor 的 T6 档）
- 螺旋石油钻井 (oil-derrick) 对齐原版 oil-extractor（`Fracker`），回归"抽油机"形态：
  - `result=Liquids.oil`、`attribute=Attribute.oil`、`baseEfficiency=0`：**必须建在油田上**，产量由"油田品质 × 扭矩转速"决定（转速为平方关系）
  - 消耗 10 水/秒 + 2 沙/秒（水由 `consumeLiquid` 连续抽走；沙由 `itemUseTime=30` 每 0.5 秒消耗 1 个）；缺料时依旧转动，只是不出油
  - 保留底座 / 螺旋钻杆(随扭矩旋转) / 顶部井架 / 石油液位 四层贴图
- 修复 endgame 炮台"普通眼睛光束对敌方建筑无伤害"：v160 中炮台索敌建筑需
  `Turret.targetBlocks` 与 `BulletType.targetBlocks` 同时为 true（原占位子弹与方块均未开启），
  导致基类 `target` 永远取不到建筑，而眼睛发射以 `target != null` 为前置条件，范围内只有建筑时眼睛完全不开火；
  现已两者同时开启，眼睛光束可正常索敌并湮灭敌方建筑（与齐射光束行为一致）
- 修复坩埚系统旧存档加载的两处崩溃隐患（配方表改动后存档原料 id 可能失效）：
  - 坩埚网络读档：原料 id 解析不到时跳过该条目，不再把 ingredient 为 null 的流体写入网络（避免 `OrderedMap` 的 `key.hashCode()` 空指针）
  - 铸模读档：改用 `instanceof` 判定，避免 id 指向非物品原料时强制转换抛 `ClassCastException`
- 补回 PU132 的 1×1 拼装式坩埚 `modular-crucible`（与 PU_V8 的 3×3 坩埚并存）：
  - 相邻自动拼接外壁（按邻居位掩码选贴图变体），可自由拼出任意形状的坩埚池；眼睛按钮可开盖查看内部熔融物
  - 连接限制：拼装坩埚只与同类、坩埚泵、保温坩埚相连，不再自动并入 3×3 坩埚 / 坩埚通道 / 铸造模具 / 液体装载器 / 坩埚源 的网络
- 修复 3×3 坩埚"自动开口方向奇怪"：底座连通判定由不对称偏移改为沿 `Geometry.d4` 方向偏移 `size/2+1` 格，西/南底座不再恒显示开口
- 补充坩埚系列方块介绍文案（坩埚 / 拼装坩埚 / 坩埚通道 / 坩埚泵 / 铸造模具 / 保温坩埚 / 坩埚源 / 坩埚流体装载器 / 终端坩埚 / 终末锻造炉）
- 坩埚系统全套切换为 PU_V8 公开版（贴图 + 尺寸一并替换）：
  - 坩埚熔炉改为 3×3：绘制按 PU_V8 顺序（地板 → 熔融液 → 未熔固体碎块 → 四方向底座锥口 → 热量叠加），底座依相邻坩埚连通情况在"闭合/开口"贴图间切换
  - 铸模改为 3×3：改用 PU_V8 的 地板/铸盘/浇注熔液/四方向底座 贴图
  - 坩埚泵改为 1×1：改用 PU_V8 的 地板/底座/流向箭头 贴图
  - 贴图全量覆盖 `crucible/`、`crucible-cast/`、`crucible-pump/`，并清理旧版遗留切片
- 机制对齐 PU 原版：
  - 坩埚网络相变/合金结算按方块容量份额分摊（修正多方块网络速率被放大 N 倍的问题）
  - 经验源 ExpSource 恢复 PU_V8 行为：周期仅向相邻经验方块注入，点按放出经验球
  - 经验球被接收时增加命中标记与消散特效（对齐 PU_V8 `accepted`）
  - 坩埚泵还原 PU_V8：改为扭矩转速驱动（`curve(lastVelocity,0,50)*0.2`）、泵送量正比于源网络熔融存量、不再耗电，注册力矩图（`GraphTorque(0.1f,10f)` 连接 `(0,1,0,1)`）
  - 复核确认铸模（PU_V8 无 priority 概念）与燃烧加热器（与 PU132 逐行一致）无需修改
- 单位/弹体机制审计（对照 PU132/PU_V8）后补回明确遗漏项：
  - 段身受击"记仇"：`SegmentUnitEntity.damage` 恢复 PU132 `WormSegmentUnit.damage` 行为——段身被打时通知头部 `WormAI.setTarget`，头部转而追踪攻击者（此前 `setTarget` 定义后从未调用，机制长期失效）
  - 磁轨炮弹寿命缩放：araneidae / theraphosidae 的磁轨炮弹补回 `scaleLife=true`（PU132 `scaleVelocity`，v159 改名），弹体寿命随距离缩放
  - 海军单位朝向：fin / blue 补回 `faceTarget=false`（PU_V8），舰船朝行进方向而非盯着目标
  - 复核确认：段身随玩家头部瞄准齐射（`SegmentUnitEntity` 弹幕同步）已实现；WormAI 接敌距离与段身记仇等模组注释明示的有意改动、以及 `TimeStopAbility` 等 README 已记载的简化，均保留不动
- 修复坩埚源/坩埚泵配置面板崩溃：`CrucibleIngredient.icon` 在 `loadContent()` 注册阶段捕获的 `fullIcon` 为 null，导致打开配置面板时 `TextureRegionDrawable` 空指针崩溃；改为 `icon()` 延迟解析（首次使用时从 item/liquid 重新读取），并同步更新坩埚源/泵/显示元素调用点
- 剁刀（chopper）蓝图编辑器：网格由原版 7 格加长至 9 格（枢轴仍位于网格 x=0，即方块旋转中心）；自动隐藏没有任何可放置零件的空分类标签（如原版空置的 saw）
- 剁刀伤害与命中判定：刀身零件伤害提高约 50%（刀刃 5→8、锯齿刀刃 12→18）；命中判定由原版"径向环 + 角度门"改为按刀身几何的精确线段判定（沿刀刃方向定位分段、垂直方向限定判定带，判定带随转速加宽做扫掠补偿），侧后方不再误命中
- 扭矩钻头功能区分：修复螺旋抽水机（mechanical-extractor）缺失 `result`/`attribute` 的问题——补回 PU_V8 `rotary-water-extractor` 的 `result=Liquids.water`、`attribute=Attribute.water`、`pumpAmount=0.2`、`liquidCapacity=60`，此前该方块抽不出任何液体
- 新增螺旋石油钻井（oil-derrick）：扭矩驱动的石油抽取器（`SolidPump`，`result=Liquids.oil`、`attribute=Attribute.oil`，需建在油层上），绘制为螺旋钻杆样式（类 `zzw.content.mechanics.torque.blocks.production.OilDerrick`）

## 安装说明

1. 下载最新版本的模组文件
2. 将模组文件放入Mindustry的mods文件夹
3. 启动游戏，在模组列表中启用模组
4. 重新启动游戏以应用更改

## 兼容性

- 最低游戏版本：160
- 推荐游戏版本：160.1（与 Vanilla-Expansion 同版本）
- v158 兼容：反射适配 ammo/useAmmo 字段移除，Bullet.drag() 方法移除改用每帧 vel 重置，bloom 混合模式陷阱通过显式 Draw.blend() 修复

## 开发信息

- 模组作者：b站up "郑zip"
- 主类：zzw.TestMod

## 许可证

本模组遵循MIT许可证。

## 贡献

欢迎提交问题报告和功能请求！如果您想贡献代码，请先创建一个分支并提交Pull Request。
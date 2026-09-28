package zzw.content;

import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.content.StatusEffects;
import mindustry.entities.bullet.ShrapnelBulletType;
import mindustry.gen.Sounds;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.meta.Env;
import zzw.content.blocks.turrets.BoostItemTurret;
import zzw.content.blocks.turrets.ShootBarrelSpread;

// 雷光系炮台升级线: Smite(雷罚) → Rift(破空) / RiftPro(破空Pro) → Fracture(裂电)
// 全部使用 ShrapnelBulletType("3叉激光"), 由 ShootBarrelSpread 实现"多管 + 每管散射"
//                ^_^ 本项目是和ai一同经行创做的
public class Z_Other {
    public static ItemTurret smite, rift, rift_pro, fracture;
    public static void load() {

        // ---- T1 雷罚: 4 根细炮管, 每管 3 发散射, 激光比雷光细, 射程/长度与雷光持平 ----
        smite = new BoostItemTurret("smite"){{
            //建造需求
            requirements(Category.turret, ItemStack.with(Items.copper, 240 , Items.graphite, 240 , Items.thorium, 120));

            // 基础属性
            reload = 48f ; // 重装时间
            shake = 3f ; // 屏幕震动
            range = 95f ; // 射程 (与雷光相近)
            recoil = 3f ; // 后坐力
            shootCone = 30 ; // 射击锥角
            size = 4 ; // 占用格子大小
            envEnabled |= Env.space; // 支持太空环境
            scaledHealth = 220 ;

            // 音效
            shootSound = Sounds.shootFuse; // 射击音效
            shootSoundVolume = 0.9f ; // 射击音效音量

            // 冷却系统
            coolant = consumeCoolant( 0.3f ); // 消耗冷却液

            // 射击模式: 4 根炮管, 每管 3 发间隔 20° (共 12 发)
            shoot = new ShootBarrelSpread(4, 5f, 3, 20f);

            // 弹药系统: 长度/射程同雷光, 激光更细, 单发伤害略低
            ammo(
                Items.titanium, new ShrapnelBulletType(){{
                    length = 105f ; // 激光长度 (射程 + 10)
                    damage = 58f ; // 伤害
                    ammoMultiplier = 4f ; // 弹药倍率
                    width = 13f ; // 激光宽度 (比雷光细)
                    reloadMultiplier = 1.3f ; // 重装倍率
                }},
                Items.thorium, new ShrapnelBulletType(){{
                    length = 105f ; // 激光长度
                    damage = 98f ; // 伤害
                    ammoMultiplier = 5f ; // 弹药倍率
                    width = 13f ; // 激光宽度
                    toColor = Pal.thoriumPink; // 颜色
                    shootEffect = smokeEffect = Fx.thoriumShoot; // 特效
                }},
                Items.surgeAlloy, new ShrapnelBulletType(){{
                    length = 105f ; // 激光长度
                    damage = 115f ; // 伤害
                    ammoMultiplier = 6f ; // 弹药倍率
                    width = 13f ; // 激光宽度
                    toColor = Pal.surge; // 颜色
                    buildingDamageMultiplier = 0.2f ; // 对建筑减伤80%
                    status = StatusEffects.shocked; // 附加麻痹效果
                }},
                Items.plastanium, new ShrapnelBulletType(){{
                    range = 97f ;
                    length = 115f ; // 激光长度 (最长)
                    damage = 46f ; // 伤害 (最低)
                    ammoMultiplier = 7f ; // 弹药倍率
                    width = 10f ; // 激光宽度 (最细)
                    reloadMultiplier = 0.8f ; // 射速最快
                    speed = 2f ; // 飞行速度更快
                    lifetime = 30f ; // 生命时间最长
                    toColor = Pal.plastanium; // 颜色
                    // 确保整个激光突刺都有伤害效果
                    pierce = true ; // 贯穿效果
                    pierceBuilding = true ; // 贯穿建筑
                    // 添加持续伤害效果，让整个激光长度都有伤害
                    statusDuration = 60f ; // 状态持续时间
                    buildingDamageMultiplier = 1f ; // 对建筑正常伤害
                    hitEffect = Fx.hitLaser ; // 击中特效
                }}
            );
            // 存储冷却时间
            depositCooldown = 1.0f ;
        }};

        // ---- T2 破空: 单根巨型炮管, 激光比雷光更大/更远/更粗, 单发极重, 射程最远 ----
        rift = new BoostItemTurret("rift"){{
            //建造需求
            requirements(Category.turret, ItemStack.with(Items.copper, 300 , Items.graphite, 300 , Items.thorium, 150));

            // 基础属性
            reload = 52f ; // 重装时间
            shake = 8f ; // 屏幕震动
            range = 145f ; // 射程 (比雷光远)
            recoil = 9f ; // 后坐力
            shootCone = 30 ; // 射击锥角
            size = 5 ; // 占用格子大小
            envEnabled |= Env.space; // 支持太空环境
            scaledHealth = 240 ;

            // 音效
            shootSound = Sounds.shootFuse; // 射击音效
            shootSoundVolume = 0.9f ; // 射击音效音量

            // 冷却系统
            coolant = consumeCoolant( 0.3f ); // 消耗冷却液

            // 射击模式: 1 根炮管, 3 发间隔 20°
            shoot = new ShootBarrelSpread(1, 0f, 3, 20f);

            // 弹药系统: 比雷光更长/更粗/更高伤
            ammo(
                Items.titanium, new ShrapnelBulletType(){{
                    length = 150f ; // 激光长度 (比雷光长)
                    damage = 128f ; // 伤害
                    ammoMultiplier = 4f ; // 弹药倍率
                    width = 34f ; // 激光宽度 (比雷光粗)
                    reloadMultiplier = 1.3f ; // 重装倍率
                }},
                Items.thorium, new ShrapnelBulletType(){{
                    length = 150f ; // 激光长度
                    damage = 215f ; // 伤害
                    ammoMultiplier = 5f ; // 弹药倍率
                    width = 34f ; // 激光宽度
                    toColor = Pal.thoriumPink; // 颜色
                    shootEffect = smokeEffect = Fx.thoriumShoot; // 特效
                }},
                Items.surgeAlloy, new ShrapnelBulletType(){{
                    length = 150f ; // 激光长度
                    damage = 248f ; // 伤害
                    ammoMultiplier = 6f ; // 弹药倍率
                    width = 34f ; // 激光宽度
                    toColor = Pal.surge; // 颜色
                    buildingDamageMultiplier = 0.2f ; // 对建筑减伤80%
                    status = StatusEffects.shocked; // 附加麻痹效果
                }},
                Items.plastanium, new ShrapnelBulletType(){{
                    range = 147f ;
                    length = 160f ; // 激光长度 (最长)
                    damage = 98f ; // 伤害 (最低)
                    ammoMultiplier = 7f ; // 弹药倍率
                    width = 28f ; // 激光宽度 (较细)
                    reloadMultiplier = 0.8f ; // 射速最快
                    speed = 2f ; // 飞行速度更快
                    lifetime = 30f ; // 生命时间最长
                    toColor = Pal.plastanium; // 颜色
                    // 确保整个激光突刺都有伤害效果
                    pierce = true ; // 贯穿效果
                    pierceBuilding = true ; // 贯穿建筑
                    // 添加持续伤害效果，让整个激光长度都有伤害
                    statusDuration = 60f ; // 状态持续时间
                    buildingDamageMultiplier = 1f ; // 对建筑正常伤害
                    hitEffect = Fx.hitLaser ; // 击中特效
                }}
            );
            // 存储冷却时间
            depositCooldown = 1.0f ;
        }};

        // ---- T3 破空Pro: 4 根炮管, 每管 3 发散射, 激光与雷光同大/同远/同粗 ----
        rift_pro = new BoostItemTurret("rift_pro"){{
            //建造需求
            requirements(Category.turret, ItemStack.with(Items.copper, 310 , Items.graphite, 310 , Items.thorium, 160));

            // 基础属性
            reload = 52f ; // 重装时间
            shake = 4f ; // 屏幕震动
            range = 120f ; // 射程
            recoil = 4f ; // 后坐力
            shootCone = 30 ; // 射击锥角
            size = 5 ; // 占用格子大小
            envEnabled |= Env.space; // 支持太空环境
            scaledHealth = 250 ;

            // 音效
            shootSound = Sounds.shootFuse; // 射击音效
            shootSoundVolume = 0.9f ; // 射击音效音量

            // 冷却系统
            coolant = consumeCoolant( 0.3f ); // 消耗冷却液

            // 射击模式: 4 根炮管, 每管 3 发间隔 20° (共 12 发)
            shoot = new ShootBarrelSpread(4, 6f, 3, 20f);

            // 弹药系统: 与雷光同大/同远/同粗, 伤害略高
            ammo(
                Items.titanium, new ShrapnelBulletType(){{
                    length = 120f ; // 激光长度
                    damage = 76f ; // 伤害
                    ammoMultiplier = 4f ; // 弹药倍率
                    width = 18f ; // 激光宽度 (与雷光相近)
                    reloadMultiplier = 1.3f ; // 重装倍率
                }},
                Items.thorium, new ShrapnelBulletType(){{
                    length = 120f ; // 激光长度
                    damage = 114f ; // 伤害
                    ammoMultiplier = 5f ; // 弹药倍率
                    width = 18f ; // 激光宽度
                    toColor = Pal.thoriumPink; // 颜色
                    shootEffect = smokeEffect = Fx.thoriumShoot; // 特效
                }},
                Items.surgeAlloy, new ShrapnelBulletType(){{
                    length = 120f ; // 激光长度
                    damage = 137f ; // 伤害
                    ammoMultiplier = 6f ; // 弹药倍率
                    width = 18f ; // 激光宽度
                    toColor = Pal.surge; // 颜色
                    buildingDamageMultiplier = 0.2f ; // 对建筑减伤80%
                    status = StatusEffects.shocked; // 附加麻痹效果
                }},
                Items.plastanium, new ShrapnelBulletType(){{
                    range = 122f ;
                    length = 130f ; // 激光长度 (最长)
                    damage = 64f ; // 伤害 (最低)
                    ammoMultiplier = 7f ; // 弹药倍率
                    width = 15f ; // 激光宽度 (较细)
                    reloadMultiplier = 0.8f ; // 射速最快
                    speed = 2f ; // 飞行速度更快
                    lifetime =30f ; // 生命时间最长
                    toColor = Pal.plastanium; // 颜色
                    // 确保整个激光突刺都有伤害效果
                    pierce = true ; // 贯穿效果
                    pierceBuilding = true ; // 贯穿建筑
                    // 添加持续伤害效果，让整个激光长度都有伤害
                    statusDuration = 60f ; // 状态持续时间
                    buildingDamageMultiplier = 1f ; // 对建筑正常伤害
                    hitEffect = Fx.hitLaser ; // 击中特效
                }}
            );
            // 存储冷却时间
            depositCooldown = 1.0f ;
        }};

        // ---- T4 裂电: 3 根巨型炮管, 每管 3 发散射, 激光与破空同级 (更大/更远/更粗) ----
        fracture = new BoostItemTurret("fracture"){{
            //建造需求
            requirements(Category.turret, ItemStack.with(Items.copper, 350 , Items.graphite, 350 , Items.thorium, 190));

            // 基础属性
            reload = 55f ; // 重装时间
            shake = 7f ; // 屏幕震动
            range = 138f ; // 射程 (比破空更远)
            recoil = 7f ; // 后坐力
            shootCone = 30 ; // 射击锥角
            size = 6 ; // 占用格子大小
            envEnabled |= Env.space; // 支持太空环境
            scaledHealth = 260 ;

            // 音效
            shootSound = Sounds.shootFuse; // 射击音效
            shootSoundVolume = 0.9f ; // 射击音效音量

            // 冷却系统
            coolant = consumeCoolant( 0.3f ); // 消耗冷却液

            // 射击模式: 3 根炮管, 每管 3 发间隔 20° (共 9 发)
            shoot = new ShootBarrelSpread(3, 8f, 3, 20f);

            // 弹药系统: 与破空同级的大口径激光
            ammo(
                Items.titanium, new ShrapnelBulletType(){{
                    length = 160f ; // 激光长度
                    damage = 210f ; // 伤害
                    ammoMultiplier = 4f ; // 弹药倍率
                    width = 30f ; // 激光宽度
                    reloadMultiplier = 1.3f ; // 重装倍率
                }},
                Items.thorium, new ShrapnelBulletType(){{
                    length = 160f ; // 激光长度
                    damage = 321f ; // 伤害
                    ammoMultiplier = 5f ; // 弹药倍率
                    width = 30f ; // 激光宽度
                    toColor = Pal.thoriumPink; // 颜色
                    shootEffect = smokeEffect = Fx.thoriumShoot; // 特效
                }},
                Items.surgeAlloy, new ShrapnelBulletType(){{
                    length = 160f ; // 激光长度
                    damage = 362f ; // 伤害
                    ammoMultiplier = 6f ; // 弹药倍率
                    width = 30f ; // 激光宽度
                    toColor = Pal.surge; // 颜色
                    buildingDamageMultiplier = 0.2f ; // 对建筑减伤80%
                    status = StatusEffects.shocked; // 附加麻痹效果
                }},
                Items.plastanium, new ShrapnelBulletType(){{
                    range = 137 ;
                    length = 180f ; // 激光长度 (最长)
                    damage = 182f ; // 伤害 (最低)
                    ammoMultiplier = 7f ; // 弹药倍率
                    width = 25f ; // 激光宽度 (较细)
                    reloadMultiplier = 0.8f ; // 射速最快
                    speed = 2f ; // 飞行速度更快
                    lifetime = 30f ; // 生命时间最长
                    toColor = Pal.plastanium; // 颜色
                    // 确保整个激光突刺都有伤害效果
                    pierce = true ; // 贯穿效果
                    pierceBuilding = true ; // 贯穿建筑
                    // 添加持续伤害效果，让整个激光长度都有伤害
                    statusDuration = 60f ; // 状态持续时间
                    buildingDamageMultiplier = 1f ; // 对建筑正常伤害
                    hitEffect = Fx.hitLaser ; // 击中特效
                }}
            );
            // 存储冷却时间
            depositCooldown = 1.0f ;
        }};
    }
}

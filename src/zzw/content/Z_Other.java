package zzw.content;

import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.entities.bullet.ShrapnelBulletType;
import mindustry.entities.pattern.ShootSpread;
import mindustry.gen.Sounds;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.meta.Env;
import zzw.content.blocks.turrets.BoostItemTurret;

//TODO ^_^ 本项目是和ai一同经行创做的
public class Z_Other {
    public static ItemTurret smite;
    public static void load() {

        smite = new BoostItemTurret("smite"){{
            //建造需求
            requirements(Category.turret, ItemStack.with(Items.copper, 350, Items.graphite, 380, Items.silicon, 360, Items.plastanium, 200, Items.thorium, 220, Z_Items.umbrium, 370, Items.surgeAlloy, 290));

            // 基础属性
            reload = 35f ; // 重装时间
            shake = 4f ;// 屏幕震动
            range = 90f ; // 射程
            recoil = 5f ; // 后坐力
            shootCone = 30 ; // 射击锥角
            size = 4 ; // 占用格子大小
            envEnabled |= Env.space; // 支持太空环境
            scaledHealth = 220 ;

            // 音效
            shootSound = Sounds.shootFuse; // 射击音效
            shootSoundVolume = 0.9f ; // 射击音效音量

            // 冷却系统
             coolant = consumeCoolant( 0.3f ); // 消耗冷却液
            // 射击模式：3发散射，间隔20度
            shoot = new ShootSpread( 3 , 20f );

            // 弹药系统
            ammo(
                Items.titanium, new ShrapnelBulletType(){{
                    length = 10; // 激光长度（射程+10）
                    damage = 66f ; // 伤害
                    ammoMultiplier = 4f ; // 弹药倍率
                    width = 17f ; // 激光宽度
                    reloadMultiplier = 1.3f ; // 重装倍率
                }},
                Items.thorium, new ShrapnelBulletType (){{
                    length = 10;// 激光长度
                    damage = 105f ; // 伤害
                    ammoMultiplier = 5f ; // 弹药倍率
                    toColor = Pal.thoriumPink; // 颜色
                    shootEffect = smokeEffect = Fx.thoriumShoot; // 特效
                }}
            );
            // 存储冷却时间
            depositCooldown = 1.0f ;
        }};
    }
}

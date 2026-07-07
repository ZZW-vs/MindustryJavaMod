package zzw.content.blocks;

import arc.util.*;
import mindustry.content.*;
import mindustry.entities.bullet.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.draw.*;
import mindustry.world.meta.*;
import mindustry.entities.part.*;

/**
 * 测试用方块：无限弹药炮台 + 高血量切换阵营墙
 * 仅在沙盒/编辑器模式可见
 */
public class Z_TestBlocks {

    public static Block testTurret, testWall;

    /** 切换阵营到下一个基础队伍 */
    private static Team nextTeam(Team current) {
        for (int i = 0; i < Team.baseTeams.length; i++) {
            if (Team.baseTeams[i] == current) {
                return Team.baseTeams[(i + 1) % Team.baseTeams.length];
            }
        }
        return Team.baseTeams[0];
    }

    public static void load() {
        testTurret = new TestTurretBlock();
        testWall = new TestWallBlock();
    }

    // ===== 测试炮台：齐射炮风格，无限铜弹药，点击切换阵营 =====
    public static class TestTurretBlock extends ItemTurret {
        public TestTurretBlock() {
            super("test-turret");
            requirements(Category.turret, BuildVisibility.sandboxOnly, ItemStack.with());

            ammo(
                Items.copper, new BasicBulletType(2.5f, 11) {{
                    width = 7f;
                    height = 9f;
                    lifetime = 60f;
                    ammoMultiplier = 2;
                    hitEffect = despawnEffect = Fx.hitBulletColor;
                    hitColor = backColor = trailColor = Pal.copperAmmoBack;
                    frontColor = Pal.copperAmmoFront;
                }}
            );

            drawer = new DrawTurret() {{
                parts.add(new RegionPart("-side") {{
                    progress = PartProgress.warmup;
                    moveX = 0.6f;
                    moveRot = -15f;
                    mirror = true;
                    layerOffset = 0.001f;
                    moves.add(new PartMove(PartProgress.recoil, 0.5f, -0.5f, -8f));
                }}, new RegionPart("-barrel") {{
                    progress = PartProgress.recoil;
                    moveY = -2.5f;
                }});
            }};

            size = 2;
            range = 190f;
            reload = 31f;
            consumeAmmoOnce = false;
            ammoEjectBack = 3f;
            recoil = 0f;
            shake = 1f;
            shoot.shots = 4;
            shoot.shotDelay = 3f;
            ammoUseEffect = Fx.casing2;
            health = 800;
            shootSound = Sounds.shoot;
            limitRange();
            coolant = consumeCoolant(0.2f);
        }

        public class TestTurretBuild extends ItemTurretBuild {
            @Override
            public boolean hasAmmo() {
                // 无限弹药：始终有弹药
                return true;
            }

            @Override
            public BulletType useAmmo() {
                // 无限弹药：不消耗，直接返回铜弹药类型
                return ammoTypes.get(Items.copper);
            }

            @Override
            public void tapped() {
                Team next = nextTeam(team);
                changeTeam(next);
                Fx.placeBlock.at(x, y, size);
                Sounds.click.at(x, y);
                Log.info("[TestTurret] 阵营切换: @ -> @", team, next);
            }

            @Override
            public boolean acceptItem(Building source, Item item) {
                // 不接受任何物品输入
                return false;
            }
        }
    }

    // ===== 测试墙：1w 血量，点击切换阵营 =====
    public static class TestWallBlock extends Wall {
        public TestWallBlock() {
            super("test-wall");
            requirements(Category.defense, BuildVisibility.sandboxOnly, ItemStack.with());
            size = 1;
            health = 10000;
            scaledHealth = 10000;
        }

        public class TestWallBuild extends WallBuild {
            @Override
            public void tapped() {
                Team next = nextTeam(team);
                changeTeam(next);
                Fx.placeBlock.at(x, y, size);
                Sounds.click.at(x, y);
                Log.info("[TestWall] 阵营切换: @ -> @", team, next);
            }
        }
    }
}
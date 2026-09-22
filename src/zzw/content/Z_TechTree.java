package zzw.content;

import arc.struct.Seq;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.content.TechTree;
import mindustry.content.TechTree.TechNode;
import mindustry.ctype.UnlockableContent;
import mindustry.game.Objectives.Objective;
import mindustry.game.Objectives.Produce;
import mindustry.game.Objectives.Research;
import mindustry.content.UnitTypes;
import mindustry.type.ItemStack;
import zzw.content.Z_Items;
import zzw.content.Z_Liquids;
import zzw.content.blocks.Z_AdvTurrets;
import zzw.content.blocks.Z_Blocks;
import zzw.content.Z_Factory;
import zzw.content.blocks.Z_SoulTurrets;
import zzw.content.blocks.Z_Turrets;
import zzw.content.optics.Z_Optics;
import zzw.content.units.Z_MonolithUnits;

import static mindustry.content.TechTree.nodeProduce;

/**
 * PU132 科技树移植版 (unity.content.UnityTechTree)。
 *
 * <p>把全部 PU 内容挂到原版科技树的对应父节点下 (区块挂原版方块节点、
 * 单位挂 fortress、生产物品挂 lead/graphite/thorium/surgeAlloy 的生产链)。</p>
 *
 * <p>★ v158 适配: PU132 的 {@code TechTree.get(parent)} 在 v158.1 中不存在,
 * 改用 {@link UnlockableContent#techNode} 公开字段直接取原版节点;
 * {@code node/objectives/requirements} 逻辑与 PU132 完全一致。</p>
 *
 * <p>★ PU132 原版即未包含 koruh 经验方块、scar/koruh 单位的科技树节点
 * (这些内容在 PU 中通过沙盒/区块解锁), 本移植保持一致。</p>
 *
 * @author PU132 原作, 移植: zzw
 */
public class Z_TechTree{
    private static TechNode context = null;

    public static void load(){
        //region blocks

        attach(Blocks.surgeSmelter, () -> {
            node(Z_Factory.darkAlloyForge);
            node(Z_Blocks.monolithAlloyForge);
            node(Z_Factory.sparkAlloyForge, Seq.with(new Research(Z_Items.sparkAlloy)), () -> {
                node(Z_Turrets.orb, () -> {
                    node(Z_Turrets.shielder);
                    node(Z_Turrets.shockwire, () -> {
                        node(Z_Turrets.current, () -> {
                            node(Z_Turrets.plasma, () -> {
                                node(Z_Turrets.electrobomb);
                            });
                        });
                    });
                });
            });
        });

        attach(Blocks.powerNode, () -> {
            node(Z_Optics.lightLamp, () -> {
                node(Z_Optics.lightReflector, () -> {
                    node(Z_Optics.lightDivisor);
                });

                node(Z_Optics.oilLamp);
            });
        });

        attach(Blocks.arc, () -> {
            node(Z_SoulTurrets.diviner, Seq.with(new Research(Z_Items.monolite)), () -> {
                node(Z_SoulTurrets.mage, () -> {
                    node(Z_AdvTurrets.heatRay, () -> {
                        node(Z_AdvTurrets.incandescence);
                    });

                    node(Z_AdvTurrets.oracle, Seq.with(new Research(Z_Items.monolithAlloy)));
                });

                node(Z_AdvTurrets.recluse, () -> {
                    node(Z_SoulTurrets.blackout);
                });
            });

            node(Z_SoulTurrets.ricochet, Seq.with(new Research(Z_Items.monolite)), () -> {
                node(Z_SoulTurrets.shellshock, Seq.with(new Research(Z_Items.monolithAlloy)), () -> {
                    node(Z_SoulTurrets.purge);
                });

                node(Z_AdvTurrets.lifeStealer, () -> {
                    node(Z_AdvTurrets.absorberAura);
                });
            });
        });

        attach(Blocks.titaniumWall, () -> {
            node(Z_Blocks.metaglassWall, () -> {
                node(Z_Blocks.metaglassWallLarge);
            });

            node(Z_Blocks.electrophobicWall, Seq.with(new Research(Z_Items.monolite)), () -> {
                node(Z_Blocks.electrophobicWallLarge);
            });
        });

        attach(Blocks.siliconCrucible, () -> {
            node(Z_Factory.irradiator, Seq.with(new Research(Items.thorium), new Research(Items.titanium), new Research(Items.surgeAlloy)));
        });

        attach(Blocks.overdriveProjector, () -> {
            node(Z_Blocks.superCharger, Seq.with(new Research(Z_Factory.irradiator)));
        });

        attach(Blocks.surgeTower, () -> {
            node(Z_Turrets.absorber, Seq.with(new Research(Z_Factory.sparkAlloyForge)));
        });

        //endregion
        //region units

        attach(UnitTypes.fortress, () -> {
            // PU132: monolith 机甲/无人机树挂在原版 fortress 单位下
            node(Z_MonolithUnits.stele, () -> {
                node(Z_MonolithUnits.pedestal, () -> {
                    node(Z_MonolithUnits.pilaster, () -> {
                        node(Z_MonolithUnits.pylon, () -> {
                            node(Z_MonolithUnits.monument, () -> {
                                node(Z_MonolithUnits.colossus, () -> {
                                    node(Z_MonolithUnits.bastion);
                                });
                            });
                        });
                    });
                });

                node(Z_MonolithUnits.adsect, () -> {
                    node(Z_MonolithUnits.comitate);
                });
            });
        });

        //endregion
        //region items

        attach(Items.lead, () -> {
            nodeProduce(Z_Items.nickel);
        });

        attach(Items.graphite, () -> {
            nodeProduce(Z_Items.monolite);

            nodeProduce(Z_Items.stone, () -> {
                nodeProduce(Z_Items.denseAlloy, () -> {
                    nodeProduce(Z_Items.steel, () -> {
                        nodeProduce(Z_Liquids.lava, () -> {
                            nodeProduce(Z_Items.dirium);
                        });
                    });
                });
            });
        });

        attach(Items.thorium, () -> {
            nodeProduce(Z_Items.archDebris, Seq.with(new Research(Z_Items.monolite)), () -> {
                nodeProduce(Z_Items.monolithAlloy);
            });
        });

        attach(Items.surgeAlloy, () -> {
            nodeProduce(Z_Items.imberium, () -> {
                nodeProduce(Z_Items.sparkAlloy);

                nodeProduce(Z_Items.irradiantSurge);
            });
        });

        // ★ 兜底: 没挂科技节点的 PU 内容统一 alwaysUnlocked (普罗塞直接可造)
        unlockRemaining();
    }

    /** 把 children 挂到 parent (原版内容) 的科技树节点下。 */
    private static void attach(UnlockableContent parent, Runnable children){
        context = parent.techNode;
        children.run();
    }

    private static void node(UnlockableContent content, ItemStack[] requirements, Seq<Objective> objectives, Runnable children){
        TechNode node = new TechNode(context, content, requirements);
        if(objectives != null) node.objectives = objectives;

        TechNode prev = context;
        context = node;
        children.run();
        context = prev;
    }

    private static void node(UnlockableContent content, ItemStack[] requirements, Runnable children){
        node(content, requirements, null, children);
    }

    private static void node(UnlockableContent content, Seq<Objective> objectives, Runnable children){
        node(content, content.researchRequirements(), objectives, children);
    }

    private static void node(UnlockableContent content, Runnable children){
        node(content, content.researchRequirements(), children);
    }

    private static void node(UnlockableContent content, Seq<Objective> objectives){
        node(content, content.researchRequirements(), objectives, () -> {});
    }

    /**
     * ★ 兜底解锁 (用户要求: PU 内容必须能在普罗塞世界直接使用):
     * 遍历全部本模组内容统一 alwaysUnlocked — 普罗塞战役里无需研究即可建造/生产。
     *
     * ★ 修复 (2026-09): 之前只解锁 techNode==null 的内容, 但经验系等自制内容
     *   挂了科技节点 (techNode != null) 被跳过 → Serpulo 战役里未研究就锁定、
     *   无法建造 (用户反馈: 经验墙炮台等"默认不显示、无法建造")。
     *   现在无论是否挂科技节点, 所有 create- 前缀本模组内容一律立即解锁。
     */
    private static void unlockRemaining(){
        int[] count = {0};
        mindustry.Vars.content.each(c -> {
            if(!(c instanceof mindustry.ctype.UnlockableContent u)) return;
            if(!u.name.startsWith("create-")) return;
            // 段身等 hidden 内容保持隐藏
            if(u instanceof mindustry.type.UnitType ut && ut.hidden) return;
            // ★ 全环境可见: 强制覆盖 envEnabled, 避免 PU 移植方块因环境位不
            //   含 Env.terrestrial 而在 Serpulo(陆地块) 中被 hidden 过滤掉、只在
            //   Erekir/太空(EasicEnvironment/space) 显示 (用户实测: 埃里克尔可见,
            //   赛普罗缺很多方块). Serpulo/Erekir/太空任意行星均显示.
            if(u instanceof mindustry.world.Block blk && blk.envEnabled != mindustry.world.meta.Env.any){
                blk.envEnabled = mindustry.world.meta.Env.any;
            }
            if(!u.alwaysUnlocked){
                u.alwaysUnlocked = true;
                count[0]++;
            }
        });
        arc.util.Log.info("[techtree] 兜底解锁 @ 个本模组内容 (普罗塞可直接建造)", count[0]);
    }

    private static void node(UnlockableContent content){
        node(content, () -> {});
    }

    private static void nodeProduce(UnlockableContent content, Seq<Objective> objectives, Runnable children){
        objectives.add(new Produce(content));
        node(content, content.researchRequirements(), objectives, children);
    }

    private static void nodeProduce(UnlockableContent content, Runnable children){
        nodeProduce(content, new Seq<>(), children);
    }

    private static void nodeProduce(UnlockableContent content){
        nodeProduce(content, () -> {});
    }

}

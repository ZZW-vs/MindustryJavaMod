package zzw.content.blocks.modular;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.Texture;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.graphics.gl.FrameBuffer;
import arc.math.Mathf;
import arc.math.geom.Point2;
import arc.math.geom.Rect;
import arc.scene.ui.layout.Table;
import arc.struct.IntSeq;
import arc.struct.IntSet;
import arc.struct.OrderedMap;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.entities.Units;
import mindustry.gen.Building;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import mindustry.world.modules.ItemModule;
import zzw.content.graphics.UnityDrawf;
import zzw.content.mechanics.torque.blocks.GraphBlock;
import zzw.content.mechanics.torque.modules.GraphTorqueModule;
import zzw.util.UnityUtils;

import static mindustry.Vars.*;

/**
 * 蓝图剁刀 (PU132 younggamExperimental.blocks.Chopper 完整移植)。
 *
 * <p>由扭矩驱动的模块化切割方块: 玩家在蓝图编辑 UI 里用零件(旋转枢轴/刀刃/锯刃/连杆)
 * 拼出一把刀, 方块按蓝图收取材料, 建满后随扭矩转速旋转, 对路径上的敌人与建筑造成伤害。</p>
 *
 * <p>本类为单实例设计 (静态 partInfo 表), 请勿创建多个 Chopper。</p>
 */
public class Chopper extends GraphBlock{
    static final IntSet collidedBlocks = new IntSet();
    static final PartInfo[] partInfo = new PartInfo[4];
    static final float knockbackMult = 10f;
    static final int mask = 65535;
    final PartType[] categories = new PartType[]{PartType.blade, PartType.saw};
    final TextureRegion[] baseRegions = new TextureRegion[4];
    TextureRegion topRegion, partsRegion;
    float partCostAccum = 0.2f, autoBuildDelay = 10;
    int gridW = 1, gridH = 1,
        spriteGridSize = 32, spriteGridPadding,
        tx, ty;
    private int index;

    public Chopper(String name){
        super(name);
        rotate = solid = configurable = acceptsItems = true;
        config(String.class, (ChopperBuild build, String value) -> build.changed = build.setBluePrintFromString(value));
        config(IntSeq.class, (ChopperBuild build, IntSeq value) -> build.changed = build.setBluePrint(UnityUtils.unpackInts(value)));
        configClear((ChopperBuild build) -> build.setBluePrint(null));
    }

    protected void setGridW(int s){
        gridW = Math.min(16, s);
    }

    protected void setGridH(int s){
        gridH = Math.min(16, s);
    }

    PartType[] getPartsCategories(){
        return categories;
    }

    @Override
    public void load(){
        super.load();
        topRegion = Core.atlas.find(name + "-top");
        for(int i = 0; i < 4; i++) baseRegions[i] = Core.atlas.find(name + "-base" + (i + 1));
        partsRegion = Core.atlas.find(name + "-parts");
        // 本类仅为单实例设计, 零件贴图共用静态 partInfo
        partInfo[0].sprite = partInfo[3].sprite = Core.atlas.find(name + "-rod");
        partInfo[1].sprite = Core.atlas.find(name + "-blade1");
        partInfo[1].sprite2 = Core.atlas.find(name + "-blade2");
        partInfo[2].sprite = Core.atlas.find(name + "-sblade");
        for(int i = 0; i < 2; i++) categories[i].region = Core.atlas.find(name + "-category" + (i + 1));
        tx = spriteGridPadding * 2 + gridW * spriteGridSize;
        ty = spriteGridPadding * 2 + gridH * spriteGridSize;
    }

    protected void addPart(String name, String desc, PartType category, int tx, int ty, int tw, int th, boolean cannotPlace, boolean isRoot, Point2 prePlace, ItemStack[] cost, byte[] connectOut, byte[] connectIn, PartStat... stats){
        partInfo[index++] = new PartInfo(name, desc, category, tx, ty, tw, th, cannotPlace, isRoot, prePlace, cost, connectOut, connectIn, stats);
    }

    protected void addPart(String name, String desc, PartType category, int tx, int ty, int tw, int th, ItemStack[] cost, byte[] connectOut, byte[] connectIn, PartStat... stats){
        partInfo[index++] = new PartInfo(name, desc, category, tx, ty, tw, th, cost, connectOut, connectIn, stats);
    }

    public class ChopperBuild extends GraphBuild{
        final OrderedMap<Item, Integer> blueprintRemainingCost = new OrderedMap<>(12);
        final IntSeq bluePrint = new IntSeq();
        final Seq<Segment> hitSegments = new Seq<>();
        final FrameBuffer buffer = new FrameBuffer(tx, ty);
        final StatContainer currentStats = new StatContainer();
        final Rect detectRect = new Rect();
        float originalMaxHp,
            speedDmgMul,
            aniProg, aniSpeed, aniTime;
        int totalItemCountCost, totalItemCountPaid,
            knockbackTorque, inertia = 5,
            bladeRadius;
        boolean changed;

        float getPaidRatio(){
            if(totalItemCountCost == 0) return 0f;
            return ((float)totalItemCountPaid) / totalItemCountCost;
        }

        boolean setBluePrintFromString(String s){
            return setBluePrint(UnityUtils.unpackIntsFromString(s));
        }

        boolean setBluePrint(IntSeq s){
            if(!bluePrint.equals(s)){
                bluePrint.clear();
                if(s != null) bluePrint.addAll(s);
                return true;
            }
            return false;
        }

        TextureRegion getBufferRegion(){
            TextureRegion tex = Draw.wrap(buffer.getTexture());
            tex.v = tex.v2;
            tex.v2 = tex.u;
            return tex;
        }

        @Override
        public void displayExt(Table table){
            table.row();
            table.table().left().update(sub -> {
                sub.clearChildren();
                if(totalItemCountPaid == totalItemCountCost) return;
                sub.left();
                if(blueprintRemainingCost.isEmpty()) sub.labelWrap("No blueprint").color(Color.lightGray);
                else{
                    for(var i : blueprintRemainingCost){
                        sub.image(i.key.uiIcon).size(iconMed);
                        sub.add((i.value >>> 16) + "/" + (i.value & mask));
                        sub.row();
                    }
                }
            });
        }

        void updateAutoBuild(){
            if(totalItemCountPaid >= totalItemCountCost) return;
            if(state.rules.infiniteResources || team.rules().infiniteResources || team.rules().cheat){
                for(var i : blueprintRemainingCost){
                    int temp = i.value & mask;
                    i.value <<= 16;
                    i.value += temp;
                }
                totalItemCountPaid = totalItemCountCost;
                applyStats();
                return;
            }
            if(timer(timerDump, autoBuildDelay)){
                CoreBuild core = team.core();
                if(core == null) return;
                ItemModule cItems = core.items;
                for(var i : blueprintRemainingCost){
                    if((i.value >>> 16) < (i.value & mask) && cItems.get(i.key) > 0){
                        cItems.remove(i.key, 1);
                        totalItemCountPaid++;
                        blueprintRemainingCost.put(i.key, i.value + (1 << 16));
                        if(totalItemCountPaid == totalItemCountCost) applyStats();
                        return;
                    }
                }
            }
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            int value = blueprintRemainingCost.get(item, 0);
            boolean hasSpace = value >>> 16 < (value & mask);
            return super.acceptItem(source, item) || hasSpace;
        }

        @Override
        public void handleItem(Building source, Item item){
            if(totalItemCountPaid == totalItemCountCost) return;
            totalItemCountPaid++;
            int value = blueprintRemainingCost.get(item, 0);
            blueprintRemainingCost.put(item, value + (1 << 16));
            if(totalItemCountPaid == totalItemCountCost) applyStats();
        }

        void resetStats(){
            inertia = 5;
            hitSegments.clear();
            if(originalMaxHp > 0f) maxHealth = originalMaxHp;
        }

        void applyStats(){
            inertia = 5 + currentStats.inertia;
            originalMaxHp = maxHealth;
            maxHealth = originalMaxHp + currentStats.hpinc;
            heal(currentStats.hpinc * health / originalMaxHp);
            hitSegments.set(currentStats.segments);
            int r = 0;
            for(int i = 0, len = hitSegments.size; i < len; i++) r = Math.max(r, hitSegments.get(i).end * 8);
            detectRect.setCentered(x, y, r * 2f);
            bladeRadius = r;
        }

        /**
         * 精确的刀刃命中判定: 把目标相对旋转中心的位置分解到"沿刀刃方向 (along)"与
         * "垂直刀刃方向 (perp)", 仅当目标落在刀身分段范围内、且横向偏差不超过判定带宽度时才命中。
         *
         * <p>相比 PU132 原版的"径向环 + 角度门"判定, 这里直接按刀身几何判定:
         * 不再把侧后方的目标算作命中, 也不会漏掉贴近刀身的目标。</p>
         */
        float getHitDamage(float rx, float ry, float rot){
            float drx = Mathf.cosDeg(rot);
            float dry = Mathf.sinDeg(rot);
            // 沿刀刃方向 / 垂直刀刃方向的偏移 (世界单位)
            float along = rx * drx + ry * dry;
            float perp = ry * drx - rx * dry;
            // 位于旋转中心后方 → 不在刀身上
            if(along <= 0f) return 0f;
            // 判定带半宽 = 半个格 (4) + 转速扫掠补偿 (转得越快, 一帧扫过越宽)
            float halfWidth = 4f + Mathf.clamp(speedDmgMul * 2f, 0f, 16f);
            if(Math.abs(perp) > halfWidth) return 0f;
            // 沿刀身方向落在哪个分段上 (网格索引 → 世界单位: ×8)
            for(var seg : hitSegments){
                if(seg.start * 8f <= along && seg.end * 8f >= along){
                    return seg.damage * Mathf.clamp(along * 0.1f);
                }
            }
            return 0f;
        }

        void onIntCollider(int cx, int cy, float rot){
            Building build = world.build(cx, cy);
            boolean collide = build != null && collidedBlocks.add(tile.pos());
            if(collide && build.team != team){
                float k = getHitDamage((cx - tileX()) * tilesize, (cy - tileY()) * tilesize, rot);
                build.damage(k);
                knockbackTorque += k * knockbackMult;
            }
        }

        void damageChk(float rot){
            float drx = Mathf.cosDeg(rot);
            float dry = Mathf.sinDeg(rot);
            collidedBlocks.clear();
            world.raycastEachWorld(x, y, x + drx * bladeRadius, y + dry * bladeRadius, (cx, cy) -> {
                onIntCollider(cx, cy, rot);
                return false;
            });
            Units.nearbyEnemies(team, detectRect, unit -> {
                if(!unit.checkTarget(false, true)) return;
                float k = getHitDamage(unit.x - x, unit.y - y, rot);
                if(k > 0f){
                    unit.damage(k);
                    unit.impulse(-dry * k * 10f, drx * k * 10f);
                    knockbackTorque += k * knockbackMult;
                }
            });
        }

        void accumStats(PartInfo part, int x, int y, int[][] grid){
            PartStat iner = part.stats.get(PartStatType.mass);
            if(iner != null) currentStats.inertia += iner.asInt() * x;
            PartStat hp = part.stats.get(PartStatType.hp);
            if(hp != null) currentStats.hpinc += hp.asInt();

            PartStat collides = part.stats.get(PartStatType.collides);
            if(collides != null && collides.asBool()){
                PartStat damage = part.stats.get(PartStatType.damage);
                int dmg = damage != null ? damage.asInt() : 0;
                if(currentStats.segments.isEmpty()) currentStats.segments.add(new Segment(x, x + part.tw, dmg));
                else{
                    boolean appended = false;
                    for(var i : currentStats.segments){
                        if(i.damage == dmg && i.end == x){
                            i.end += part.tw;
                            appended = true;
                            break;
                        }
                    }
                    if(!appended) currentStats.segments.add(new Segment(x, x + part.tw, dmg));
                }
            }
        }

        void drawPartBuffer(PartInfo part, int x, int y, int[][] grid){
            Draw.rect(part.sprite, (x + part.tw * 0.5f) * 32f, (y + part.th * 0.5f) * 32f, part.tw * 32f, part.th * 32f);
            if(part.sprite2 != null && x + 1 < grid.length && grid[x + 1][y] == 0) Draw.rect(part.sprite2, (x + part.tw * 0.5f + 1f) * 32f, (y + part.th * 0.5f) * 32f, part.tw * 32f, part.th * 32f);
        }

        @Override
        public void buildConfiguration(Table table){
            table.button(Tex.whiteui, Styles.clearNonei, 50f, () -> {
                BaseDialog dialog = new BaseDialog("Edit Blueprint");
                dialog.setFillParent(false);
                ModularConstructorUI mtd = ModularConstructorUI.applyModularConstructorUI(dialog.cont, partsRegion, Math.round(partsRegion.width / 32f), Math.round(partsRegion.height / 32f),
                    partInfo, gridW, gridH, bluePrint, getPartsCategories(), partCostAccum
                );
                dialog.buttons.button("@ok", () -> {
                    configure(mtd.getPackedSave());
                    dialog.hide();
                }).size(130f, 60f);
                dialog.update(() -> {
                    if(!(tile.build instanceof ChopperBuild)) dialog.hide();
                });
                dialog.show();
            }).size(50f).get().getStyle().imageUp = Icon.pencil;
        }

        @Override
        public void configured(mindustry.gen.Unit builder, Object value){
            changed = false;
            super.configured(builder, value);
            if(!changed) return;
            resetStats();
            float cstMult = 1f;
            int len = bluePrint.size;
            for(int p = 0; p < len; p++){
                int temp = bluePrint.get(p);
                if(temp != 0){
                    PartInfo partL = partInfo[temp - 1];
                    cstMult += partCostAccum * partL.tw * partL.th;
                }
            }
            cstMult -= partCostAccum;
            totalItemCountCost = totalItemCountPaid = 0;
            int[][] gridPrint = new int[len / gridH][gridH];
            blueprintRemainingCost.clear();
            for(int p = 0; p < len; p++){
                int temp = bluePrint.get(p);
                if(temp != 0){
                    PartInfo partL = partInfo[temp - 1];
                    ItemStack[] prtTmp = partL.cost;
                    for(var cstItem : prtTmp){
                        int cur = blueprintRemainingCost.get(cstItem.item, 0);
                        int increment = Mathf.floor(cstItem.amount * cstMult);
                        blueprintRemainingCost.put(cstItem.item, cur + increment);
                        totalItemCountCost += increment;
                    }
                }
                gridPrint[p / gridH][p % gridH] = temp;
            }
            currentStats.clear();
            for(int p = 0; p < len; p++){
                int temp = bluePrint.get(p);
                if(temp == 0) continue;
                accumStats(partInfo[temp - 1], p / gridH, p % gridH, gridPrint);
            }
            if(!headless){
                Draw.draw(Draw.z(), () -> {
                    Tmp.m1.set(Draw.proj());
                    Draw.proj(0f, 0f, tx, ty);
                    buffer.begin(Color.clear);
                    Draw.color(Color.white);
                    for(int p = 0; p < len; p++){
                        int temp = bluePrint.get(p);
                        if(temp == 0) continue;
                        drawPartBuffer(partInfo[temp - 1], p / gridH, p % gridH, gridPrint);
                    }
                    buffer.end();
                    Draw.proj(Tmp.m1);
                    Draw.reset();
                });
            }
        }

        @Override
        public String config(){
            if(bluePrint.isEmpty()) return "";
            return IntPacker.packArray(bluePrint).toStringPack();
        }

        @Override
        public void writeExt(Writes write){
            if(bluePrint.isEmpty()){
                write.i(0);
                return;
            }
            IntSeq tmp = IntPacker.packArray(bluePrint).packed;
            int len = tmp.size;
            write.s(len);
            for(int i = 0; i < len; i++) write.i(tmp.get(i));
            if(blueprintRemainingCost.isEmpty()) write.s(0);
            else{
                write.s(blueprintRemainingCost.size);
                for(var i : blueprintRemainingCost){
                    write.s(i.key.id);
                    write.s(i.value >>> 16);
                }
            }
        }

        @Override
        public void readExt(Reads read, byte revision){
            short packedSize = read.s();
            IntSeq pack = new IntSeq();
            for(int i = 0; i < packedSize; i++) pack.add(read.i());
            configureAny(pack);
            short costSize = read.s();
            if(costSize > 0){
                for(int i = 0; i < costSize; i++){
                    Item item = content.item(read.s());
                    short paid = read.s();
                    int cur = blueprintRemainingCost.get(item, -1);
                    if(cur != -1){
                        blueprintRemainingCost.put(item, (paid << 16) + cur);
                        totalItemCountPaid += paid;
                    }
                }
            }
            if(totalItemCountPaid == totalItemCountCost) applyStats();
        }

        @Override
        public void updatePre(){
            GraphTorqueModule<?> tGraph = torque();
            tGraph.setInertia(inertia);
            tGraph.force = -knockbackTorque;
            knockbackTorque = 0;
            aniTime += Time.delta;
            float prog = getPaidRatio();
            if(aniProg < prog){
                aniSpeed = (prog - aniProg) * 0.1f;
                aniProg += aniSpeed;
            }else{
                aniProg = prog;
                aniSpeed = 0f;
            }
            speedDmgMul = tGraph.getNetwork().lastVelocity;
            updateAutoBuild();
        }

        @Override
        public void updatePost(){
            if(getPaidRatio() >= 1f && speedDmgMul > 0.8f) damageChk(torque().getRotation());
        }

        @Override
        public void draw(){
            float rot = torque().getRotation();
            Draw.rect(baseRegions[rotation], x, y);
            TextureRegion blades = getBufferRegion();
            if(blades != null){
                Draw.z(Layer.turret);
                if(getPaidRatio() < 1f){
                    blades.setU2(Mathf.map(aniProg, 0f, 1f, blades.u, blades.u2));
                    UnityDrawf.drawConstruct(blades, aniProg, Pal.accent, 1f, aniTime * 0.5f, Layer.turret, tex -> Draw.rect(tex, x + tex.width * 0.125f, y, tex.width * 0.25f, tex.height * 0.25f, 0f, tex.height * 0.25f * 0.5f, rot));
                }else Draw.rect(blades, x + blades.width * 0.125f, y, blades.width * 0.25f, blades.height * 0.25f, 0f, blades.height * 0.25f * 0.5f, rot);
                Draw.rect(topRegion, x, y);
            }
            drawTeamTop();
        }
    }
}
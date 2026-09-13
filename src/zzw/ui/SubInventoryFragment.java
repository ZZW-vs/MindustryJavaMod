package zzw.ui;

import arc.Core;
import arc.Events;
import arc.func.Boolp;
import arc.func.Func;
import arc.func.Prov;
import arc.graphics.g2d.TextureRegion;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.actions.Actions;
import arc.scene.event.HandCursorListener;
import arc.scene.event.InputEvent;
import arc.scene.event.Touchable;
import arc.scene.ui.Image;
import arc.scene.ui.layout.Stack;
import arc.scene.ui.layout.Table;
import arc.struct.IntSet;
import arc.util.Time;
import mindustry.game.EventType.WithdrawEvent;
import mindustry.core.UI;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Tex;
import mindustry.input.InputHandler;
import mindustry.type.Item;
import zzw.content.type.WorldUnitType;

import java.util.Arrays;

import static mindustry.Vars.content;
import static mindustry.Vars.itemTransferRange;
import static mindustry.Vars.net;
import static mindustry.Vars.player;
import static mindustry.Vars.state;
import static mindustry.Vars.tilesize;

/**
 * 子世界建筑物品栏界面 (v158 BlockInventoryFragment 子世界适配版).
 *
 * <p>★ 为什么不复用原版 {@code mindustry.ui.fragments.BlockInventoryFragment}:
 * <ul>
 *   <li>取物走 {@code Call.requestItem}, 服务端校验 {@code player.within(build,
 *       itemTransferRange)} —— 子世界建筑的 x/y 是子世界坐标 (数值很小),
 *       玩家距离校验永远失败 → 表现为"容器能打开能看到物品但拿不出来";</li>
 *   <li>物品图标可点击判定 {@code canPick} 同样包含该距离校验 (客户端就点不动);</li>
 *   <li>定位 {@code updateTablePosition} 按建筑 x/y 换算屏幕坐标 → 弹到地图原点;</li>
 *   <li>关键方法全部 private 且 Building 是类 (无法代理), 只能整份适配。</li>
 * </ul>
 * 适配点: 取物对子世界建筑直接本地转账 (语义与 {@code Call.takeItems} 服务端一致),
 * 飞行物品特效用投影后的主世界坐标; 定位同理。主世界建筑行为与原版完全一致。</p>
 */
public class SubInventoryFragment{
    private static final float holdWithdraw = 20f;
    private static final float holdShrink = 120f;

    /** 子世界建筑 → 投影后主世界坐标 (由 WorldUnitType 注入; 返回 null = 非子世界建筑) */
    public static Func<Building, Vec2> subPositionResolver;

    Table table = new Table();
    Building build;
    float holdTime = 0f, emptyTime;
    boolean holding, held;
    float[] shrinkHoldTimes = new float[content.items().size];
    Item lastItem;

    {
        Events.on(mindustry.game.EventType.WorldLoadEvent.class, e -> hide());
    }

    public void build(Group parent){
        table.name = "subworld-inventory";
        table.setTransform(true);
        parent.setTransform(true);
        parent.addChild(table);
    }

    public void showFor(Building t){
        if(this.build == t){
            hide();
            return;
        }
        this.build = t;
        // ★ 不调用原版 Call.requestBlockSnapshot: 子世界建筑不在主世界同步范围,
        //   物品数量显示直接读 build.items (联机时子世界本就不同步)
        if(build == null || !build.block.isAccessible() || build.items == null || build.items.total() == 0){
            return;
        }
        rebuild(true);
    }

    public void hide(){
        if(table == null) return;

        table.actions(Actions.scaleTo(0f, 1f, 0.06f, arc.math.Interp.pow3Out), arc.scene.actions.Actions.run(() -> {
            table.clearChildren();
            table.clearListeners();
            table.update(null);
        }), Actions.visible(false));
        table.touchable = Touchable.disabled;
        build = null;
    }

    private void takeItem(int requested){
        if(!build.canWithdraw()) return;

        //take everything
        int amount = Math.min(requested, player.unit().maxAccepted(lastItem));

        if(amount > 0){
            Vec2 proj = subPositionResolver == null ? null : subPositionResolver.get(build);
            if(proj != null){
                // ★ 子世界建筑: 直接本地转账 (Call.takeItems 服务端语义: removeStack +
                // addItem + 飞行物品特效); 原版 requestItem 的距离校验按子世界坐标算,
                // 玩家永远"不在范围内", 走原版路径拿不到东西
                int removed = build.removeStack(lastItem, amount);
                if(removed > 0){
                    player.unit().addItem(lastItem, removed);
                    Item item = lastItem;
                    float px = proj.x, py = proj.y;
                    var target = player.unit();
                    for(int j = 0; j < Mathf.clamp(removed / 3, 1, 8); j++){
                        Time.run(j * 3f, () -> InputHandler.transferItemEffect(item, px, py, target));
                    }
                }
            }else{
                Call.requestItem(player, build, lastItem, amount);
            }
            holding = false;
            holdTime = 0f;
            held = true;

            if(net.client()) Events.fire(new WithdrawEvent(build, player, lastItem, amount));
        }
    }

    private void rebuild(boolean actions){
        IntSet container = new IntSet();

        Arrays.fill(shrinkHoldTimes, 0);
        holdTime = emptyTime = 0f;

        table.clearChildren();
        table.clearActions();
        table.background(Tex.inventory);
        table.touchable = Touchable.enabled;
        table.update(() -> {

            if(state.isMenu() || build == null || !build.isValid() || !build.block.isAccessible() || emptyTime >= holdShrink){
                hide();
            }else{
                if(build.items.total() == 0){
                    emptyTime += Time.delta;
                }else{
                    emptyTime = 0f;
                }

                if(holding && lastItem != null && (holdTime += Time.delta) >= holdWithdraw){
                    holdTime = 0f;

                    //take one when held
                    takeItem(1);
                }

                updateTablePosition();
                if(build.block.hasItems){
                    boolean dirty = false;
                    if(shrinkHoldTimes.length != content.items().size) shrinkHoldTimes = new float[content.items().size];

                    for(int i = 0; i < content.items().size; i++){
                        boolean has = build.items.has(content.item(i));
                        boolean had = container.contains(i);
                        if(has){
                            shrinkHoldTimes[i] = 0f;
                            dirty |= !had;
                        }else if(had){
                            shrinkHoldTimes[i] += Time.delta;
                            dirty |= shrinkHoldTimes[i] >= holdShrink;
                        }
                    }
                    if(dirty) rebuild(false);
                }

                if(table.getChildren().isEmpty()){
                    hide();
                }
            }
        });

        int cols = 3;
        int row = 0;

        table.margin(4f);
        table.defaults().size(8 * 5).pad(4f);

        if(build.block.hasItems){

            // 子世界建筑: 玩家骑着大地单位就在建筑旁边, 跳过原版 itemTransferRange 距离判定
            boolean sub = subPositionResolver != null && subPositionResolver.get(build) != null;

            for(int i = 0; i < content.items().size; i++){
                Item item = content.item(i);
                if(!build.items.has(item)) continue;

                container.add(i);

                Boolp canPick = () -> !player.dead() && player.unit().acceptsItem(item) && !state.isPaused()
                    && (sub || player.within(build, itemTransferRange));

                HandCursorListener l = new HandCursorListener();
                l.enabled = canPick;

                Element image = itemImage(item.uiIcon, () -> {
                    if(build == null || !build.isValid()){
                        return "";
                    }
                    return round(build.items.get(item));
                });
                image.addListener(l);

                Boolp validClick = () -> !(!canPick.get() || build == null || !build.isValid() || build.items == null || !build.items.has(item));

                image.addListener(new arc.scene.event.ClickListener(){

                    @Override
                    public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                        held = false;
                        if(validClick.get()){
                            lastItem = item;
                            holding = true;
                        }

                        return super.touchDown(event, x, y, pointer, button);
                    }

                    @Override
                    public void clicked(InputEvent event, float x, float y){
                        if(!validClick.get() || held) return;

                        //take all
                        takeItem(build.items.get(lastItem = item));
                    }

                    @Override
                    public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button){
                        super.touchUp(event, x, y, pointer, button);

                        holding = false;
                        lastItem = null;
                    }
                });
                table.add(image);

                if(row++ % cols == cols - 1) table.row();
            }
        }

        if(row == 0){
            table.setSize(0f, 0f);
        }

        updateTablePosition();

        table.visible = true;

        if(actions){
            table.setScale(0f, 1f);
            table.actions(Actions.scaleTo(1f, 1f, 0.07f, arc.math.Interp.pow3Out));
        }else{
            table.setScale(1f, 1f);
        }
    }

    private String round(float f){
        f = (int)f;
        if(f >= 1000000){
            return (int)(f / 1000000f) + "[gray]" + UI.millions;
        }else if(f >= 1000){
            return (int)(f / 1000) + UI.thousands;
        }else{
            return (int)f + "";
        }
    }

    private void updateTablePosition(){
        Vec2 proj = subPositionResolver == null ? null : subPositionResolver.get(build);
        Vec2 v;
        if(proj != null){
            v = Core.input.mouseScreen(proj.x + build.block.size * tilesize / 2f,
                proj.y + build.block.size * tilesize / 2f);
        }else{
            v = Core.input.mouseScreen(build.x + build.block.size * tilesize / 2f,
                build.y + build.block.size * tilesize / 2f);
        }
        table.pack();
        table.setPosition(v.x, v.y, arc.util.Align.topLeft);
    }

    private Element itemImage(TextureRegion region, Prov<CharSequence> text){
        Stack stack = new Stack();

        Table t = new Table().left().bottom();
        t.label(text);

        stack.add(new Image(region));
        stack.add(t);
        return stack;
    }
}

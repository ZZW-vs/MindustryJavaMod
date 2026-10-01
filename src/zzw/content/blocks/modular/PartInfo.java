package zzw.content.blocks.modular;

import arc.graphics.g2d.TextureRegion;
import arc.math.geom.Point2;
import arc.struct.OrderedMap;
import arc.struct.Seq;
import mindustry.type.ItemStack;
import zzw.content.util.GraphicUtils;

/**
 * 模块化方块零件定义 (PU132 younggamExperimental.PartInfo 移植)。
 *
 * <p>描述一个可放置零件的贴图区域 (tx/ty/tw/th 为零件图集坐标)、造价、
 * 连接规则与属性列表, 供蓝图编辑 UI 与方块运行时读取。</p>
 */
public class PartInfo{
    public final String name, desc;
    public final PartType category;
    public final int tx, ty, tw, th;
    public final boolean cannotPlace, isRoot;
    public final Point2 prePlace;
    public final ItemStack[] cost;
    public final byte[] connectOut, connectIn;
    public final OrderedMap<PartStatType, PartStat> stats = new OrderedMap<>(12);
    public TextureRegion sprite, sprite2, texRegion;
    final Seq<ConnectData> connInList = new Seq<>(), connOutList = new Seq<>();
    int id;

    public PartInfo(String name, String desc, PartType category, int tx, int ty, int tw, int th, boolean cannotPlace, boolean isRoot, Point2 prePlace, ItemStack[] cost, byte[] connectOut, byte[] connectIn, PartStat... stats){
        this.name = name;
        this.desc = desc;
        this.category = category;
        this.tx = tx;
        this.ty = ty;
        this.tw = tw;
        this.th = th;
        this.cannotPlace = cannotPlace;
        this.isRoot = isRoot;
        this.prePlace = prePlace;
        this.cost = cost;
        this.connectOut = connectOut;
        this.connectIn = connectIn;
        for(var i : stats) this.stats.put(i.category, i);
    }

    public PartInfo(String name, String desc, PartType category, int tx, int ty, int tw, int th, ItemStack[] cost, byte[] connectOut, byte[] connectIn, PartStat... stats){
        this(name, desc, category, tx, ty, tw, th, false, false, null, cost, connectOut, connectIn, stats);
    }

    /** 预计算每个零件的进出连接点坐标 (Pu132 原逻辑只在首次调用时填充)。 */
    public static void preCalcConnection(PartInfo[] partsConfig){
        for(int i = 0, len = partsConfig.length; i < len; i++){
            PartInfo pInfo = partsConfig[i];
            if(pInfo.connInList.isEmpty()){
                for(int j = 0, iLen = pInfo.connectIn.length; j < iLen; j++){
                    if(pInfo.connectIn[j] != 0) pInfo.connInList.add(ConnectData.getConnectSidePos(j, pInfo.tw, pInfo.th).id(pInfo.connectIn[j]));
                }
            }
            if(pInfo.connOutList.isEmpty()){
                for(int j = 0, iLen = pInfo.connectOut.length; j < iLen; j++){
                    if(pInfo.connectOut[j] != 0) pInfo.connOutList.add(ConnectData.getConnectSidePos(j, pInfo.tw, pInfo.th).id(pInfo.connectOut[j]));
                }
            }
        }
    }

    /** 从零件图集中切出每个零件对应的贴图区域。 */
    public static void assignPartSprites(PartInfo[] partsConfig, TextureRegion partsSprite, int spriteW, int spriteH){
        for(int i = 0, len = partsConfig.length; i < len; i++){
            PartInfo pinfo = partsConfig[i];
            pinfo.id = i;
            pinfo.texRegion = GraphicUtils.getRegionRect(partsSprite, pinfo.tx, pinfo.ty, pinfo.tw, pinfo.th, spriteW, spriteH);
        }
    }
}
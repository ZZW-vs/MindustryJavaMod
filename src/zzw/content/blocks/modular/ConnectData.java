package zzw.content.blocks.modular;

import arc.math.geom.Geometry;
import arc.math.geom.Point2;

/**
 * 零件连接点数据 (PU132 younggamExperimental.ConnectData 移植)。
 *
 * <p>把零件边界上的一个连接槽位 (index) 换算成相对网格坐标与朝向方向。</p>
 */
public class ConnectData{
    public final Point2 dir;
    public final int x, y;
    public byte id;

    private ConnectData(int x, int y, Point2 dir){
        this.x = x;
        this.y = y;
        this.dir = dir.cpy();
    }

    private ConnectData(int index){
        this(0, 0, Geometry.d4(index));
    }

    public ConnectData id(byte id){
        this.id = id;
        return this;
    }

    public static ConnectData getConnectSidePos(int index, int sizeW, int sizeH){
        if(sizeH == 1 && sizeW == 1) return new ConnectData(index);
        int cind = index - sizeH;
        int lastSub = sizeH;
        int gx = sizeW - 1;
        int gy = 0;
        int side = 0;
        Point2 forwardDir = Geometry.d4(3);
        while(cind >= 0){
            side++;
            gx += forwardDir.x * (lastSub - 1);
            gy += forwardDir.y * (lastSub - 1);
            forwardDir = Geometry.d4(3 - side);
            lastSub = side % 2 == 1 ? sizeW : sizeH;
            cind -= lastSub;
        }
        gx += forwardDir.x * (cind + lastSub);
        gy += forwardDir.y * (cind + lastSub);
        return new ConnectData(gx, gy, Geometry.d4(side % 4));
    }
}
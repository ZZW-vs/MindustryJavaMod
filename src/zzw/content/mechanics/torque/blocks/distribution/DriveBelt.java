package zzw.content.mechanics.torque.blocks.distribution;

import arc.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.Tmp;
import arc.util.io.*;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.graphics.*;
import mindustry.ui.Bar;
import zzw.content.mechanics.torque.UnityDrawf;
import zzw.content.mechanics.torque.blocks.*;

import static mindustry.Vars.*;

/**
 * 传动带 (PU_V8 unity.world.blocks.distribution.DriveBelt 移植)
 * <p>
 * 跨距离传递扭矩: 一端有一个固定扭矩接口 (连接相邻轴/齿轮), 另一端可用鼠标配置到
 * {@code maxRange} 格范围内另一条传动带。两条带之间以皮带相连, 使两侧扭矩网络
 * 共享同一转速 (刚性耦合), 从而绕过直线轴的限制。
 * <p>
 * 物理说明: 原版节点为 {@code TransmissionTorqueGraphNode(friction, inertia, ratio=1)},
 * 其 {@code update()} 每帧把两个网络的速度按动量守恒重新分配:
 * <pre>
 *   总动量 = i1*v1 + i2*v2
 *   总惯量 = i1 + ratio*i2      (ratio = 1)
 *   共同速度 = 总动量 / 总惯量
 *   t1.velocity = 共同速度;  t2.velocity = 共同速度 * ratio
 * </pre>
 * 本项目图框架只沿相邻端口建网, 无法把跨距离的两张网在结构上合并, 因此在
 * {@link DriveBeltBuild#updatePost()} 中直接复现该速度融合 (原版实际行为), 效果等同于共享转速。
 * <p>
 * 参数取自 参考/PU_V8/main/src/unity/content/blocks/YoungchaBlocks.java L203-225。
 */
public class DriveBelt extends GraphBlock{
    /** 可链接的最大距离 (格) */
    public float maxRange = 5f;
    /** 皮带轮半径 (世界单位) */
    public float wheelSize = 4f;
    /** 每条带最多可连接的带数 (原版 distanceConnection 的 maxConnections) */
    public int maxConnections = 1;

    /** 4 个朝向本体贴图 (1~4) */
    protected final TextureRegion[] rotationRegions = new TextureRegion[4];
    /** 旋转的皮带轮 */
    protected TextureRegion rotator;

    public DriveBelt(String name){
        super(name);

        rotate = true;
        solid = true;
        configurable = true;

        config(Integer.class, (DriveBeltBuild build, Integer pos) -> build.configureLink(pos));
        config(Point2[].class, (DriveBeltBuild build, Point2[] points) -> {
            build.clearLinks();
            for(Point2 p : points){
                build.configureLink(Point2.pack(p.x + build.tileX(), p.y + build.tileY()));
            }
        });
    }

    @Override
    public void load(){
        super.load();

        for(int i = 0; i < 4; i++) rotationRegions[i] = Core.atlas.find(name + "-" + (i + 1));
        rotator = Core.atlas.find(name + "-rotator");
    }

    @Override
    public void init(){
        super.init();
        // 皮带可伸出本体很远, 扩大裁剪范围避免远景被裁掉
        clipSize = Math.max(clipSize, maxRange * tilesize + wheelSize);
    }

    @Override
    public void setBars(){
        super.setBars();
        addBar("connections", entity -> {
            if(entity instanceof DriveBeltBuild deb){
                return new Bar(
                    () -> Core.bundle.format("bar.powerlines", deb.links.size, maxConnections),
                    () -> Pal.items,
                    () -> (float)deb.links.size / maxConnections
                );
            }
            return null;
        });
    }

    public class DriveBeltBuild extends GraphBuild{
        /** 已链接的传动带位置 (世界打包坐标, 双向记录) */
        final IntSeq links = new IntSeq();

        @Override
        public void updatePost(){
            var myNet = torque().getNetwork();
            if(myNet == null) return;

            for(int i = 0; i < links.size; i++){
                Building b = world.build(links.get(i));
                if(!(b instanceof DriveBeltBuild odb)) continue;
                // 每一对链接只由 id 较小的一方处理一次, 避免两侧重复融合
                if(odb.id < id) continue;

                var oNet = odb.torque().getNetwork();
                if(oNet == null || oNet.id == myNet.id) continue;

                // 原版 TransmissionTorqueGraphNode.update() (ratio = 1) 的速度融合
                float totalMomentum = myNet.lastInertia * myNet.lastVelocity + oNet.lastInertia * oNet.lastVelocity;
                float totalInertia = myNet.lastInertia + oNet.lastInertia;
                if(totalInertia <= 0f) continue;
                float v = totalMomentum / totalInertia;
                myNet.lastVelocity = v;
                oNet.lastVelocity = v;
            }
        }

        @Override
        public void onDelete(){
            // 对称解除所有链接, 再清空自身 (防止留下指向已拆除带子的悬空记录)
            int[] arr = links.toArray();
            for(int p : arr){
                if(world.build(p) instanceof DriveBeltBuild odb) odb.links.removeValue(pos());
            }
            links.clear();
        }

        @Override
        public boolean onConfigureBuildTapped(Building other){
            if(linkValid(other instanceof DriveBeltBuild db ? db : null)){
                configure(other.pos());
                return false;
            }

            if(other == this){
                if(links.size == 0){
                    // 自动连接范围内最近的若干条带 (最多 maxConnections 条)
                    int total = 0;
                    for(DriveBeltBuild cand : potentialLinks()){
                        if(total++ >= maxConnections) break;
                        configure(cand.pos());
                    }
                }else{
                    // 断开全部链接
                    int[] arr = links.toArray();
                    for(int p : arr) configure(p);
                }
                deselect();
                return false;
            }

            return true;
        }

        /** 切换一条链接: 已连则断开, 未连且双方均有空位则连上 (原版 Integer 配置语义) */
        public void configureLink(int pos){
            Building other = world.build(pos);
            if(!(other instanceof DriveBeltBuild odb) || odb == this) return;

            if(links.contains(pos)){
                removeLink(pos);
            }else if(linkValid(odb) && links.size < maxConnections && odb.links.size < ((DriveBelt)odb.block).maxConnections){
                addLink(pos, odb);
            }
        }

        /** 建立双向链接 */
        public void addLink(int pos, DriveBeltBuild odb){
            if(!links.contains(pos)) links.add(pos);
            if(!odb.links.contains(this.pos())) odb.links.add(this.pos());
        }

        /** 解除双向链接 */
        public void removeLink(int pos){
            links.removeValue(pos);
            if(world.build(pos) instanceof DriveBeltBuild odb) odb.links.removeValue(this.pos());
        }

        /** 解除全部链接 (对称) */
        public void clearLinks(){
            int[] arr = links.toArray();
            for(int p : arr) removeLink(p);
        }

        /** 目标是否可作为链接对象 (同队且在任一侧的距离范围内) */
        public boolean linkValid(DriveBeltBuild other){
            if(other == null || other == this || other.team != team) return false;
            float range = Math.max(maxRange, ((DriveBelt)other.block).maxRange) * tilesize;
            return dst(other) <= range;
        }

        /** 收集范围内可连接的传动带, 按距离由近到远排序 (原版 getPotentialLinks 简化版) */
        public Seq<DriveBeltBuild> potentialLinks(){
            Seq<DriveBeltBuild> out = new Seq<>();
            Geometry.circle(tileX(), tileY(), (int)(maxRange + 2), (cx, cy) -> {
                if(world.build(cx, cy) instanceof DriveBeltBuild odb && odb != this
                    && linkValid(odb) && !links.contains(odb.pos())
                    && odb.links.size < ((DriveBelt)odb.block).maxConnections
                    && !out.contains(odb)){
                    out.add(odb);
                }
            });
            out.sort((a, b) -> Float.compare(a.dst2(this), b.dst2(this)));
            return out;
        }

        @Override
        public Point2[] config(){
            Point2[] out = new Point2[links.size];
            for(int i = 0; i < out.length; i++){
                out[i] = Point2.unpack(links.get(i)).sub(tileX(), tileY());
            }
            return out;
        }

        @Override
        public void writeExt(Writes write){
            write.i(links.size);
            for(int i = 0; i < links.size; i++) write.i(links.get(i));
        }

        @Override
        public void readExt(Reads read, byte revision){
            int n = read.i();
            links.clear();
            for(int i = 0; i < n; i++) links.add(read.i());
        }

        @Override
        public void drawSelect(){
            super.drawSelect();

            Lines.stroke(1f);
            Draw.color(Pal.accent);
            Drawf.circles(x, y, maxRange * tilesize);
            for(int i = 0; i < links.size; i++){
                Building b = world.build(links.get(i));
                if(b != null){
                    Drawf.square(b.x, b.y, b.block.size * tilesize / 2f + 1f, Pal.place);
                }else{
                    Point2 p = Point2.unpack(links.get(i));
                    Drawf.square(p.x * tilesize, p.y * tilesize, tilesize / 2f + 1f, Pal.place);
                }
            }
            Draw.reset();
        }

        @Override
        public void draw(){
            float r = torque() == null ? 0f : torque().getRotation() * (4f / wheelSize);

            Draw.rect(rotationRegions[rotation], x, y, 0f);
            Draw.z(Layer.blockOver);
            Drawf.shadow(rotator, x - 1f, y - 1f, r);
            Drawf.spinSprite(rotator, x, y, r);
            Draw.z(Layer.power);

            for(int i = 0; i < links.size; i++){
                Building b = world.build(links.get(i));
                if(!(b instanceof DriveBeltBuild odb)) continue;
                // 每对只画一次: 范围小者或 id 小者为准 (原版逻辑)
                DriveBelt ob = (DriveBelt)odb.block;
                if(ob.maxRange < maxRange || odb.id < id){
                    drawBelt(team, x, y, odb.x, odb.y, r, wheelSize, ob.wheelSize);
                }
            }

            drawTeamTop();
        }
    }

    /**
     * 绘制两条带之间的皮带 (原版 drawBelt 移植): 两段切线 + 两个皮带轮圆弧 + 沿全程移动的齿纹。
     *
     * @param r 皮带轮当前旋转量 (度), 用于齿纹移动相位
     */
    public static void drawBelt(Team team, float x1, float y1, float x2, float y2, float r, float size1, float size2){
        final float d = Mathf.dst(x2 - x1, y2 - y1);
        if(d < 0.001f) return;
        final float f = Mathf.sqrt(Math.max(0f, d * d - Mathf.sqr(size2 - size1)));
        final float a = size1 > size2 ? Mathf.atan2(size1 - size2, f) : (size1 < size2 ? Mathf.pi - Mathf.atan2(size2 - size1, f) : Mathf.halfPi);
        final float a2 = Mathf.pi - a;
        final float na = Mathf.atan2(x2 - x1, y2 - y1);
        Tmp.v1.set(x2 - x1, y2 - y1).scl(1f / d); // 两轮连线方向
        Tmp.v2.set(Tmp.v1).rotateRad(a).scl(size1).add(x1, y1);  // 切线点 1
        Tmp.v3.set(Tmp.v1).rotateRad(-a).scl(size1).add(x1, y1); // 切线点 2
        Tmp.v4.set(Tmp.v1).rotateRad(a - Mathf.pi).scl(-size2).add(x2, y2); // 切线点 3
        Tmp.v5.set(Tmp.v1).rotateRad(Mathf.pi - a).scl(-size2).add(x2, y2); // 切线点 4

        // 外圈粗皮带 (直线段 + 绕过两轮的圆弧)
        Lines.stroke(3f, team.color.cpy().lerp(Pal.gray, 0.5f));
        Lines.line(Tmp.v2.x, Tmp.v2.y, Tmp.v4.x, Tmp.v4.y);
        Lines.line(Tmp.v3.x, Tmp.v3.y, Tmp.v5.x, Tmp.v5.y);
        UnityDrawf.arc(x1, y1, size1, na - a, na + a - (2 * Mathf.pi));
        UnityDrawf.arc(x2, y2, size2, na - a2 + Mathf.pi, na + a2 + Mathf.pi - (2 * Mathf.pi));

        // 内圈细线
        Lines.stroke(1f, team.color.cpy().lerp(Pal.gray, 0.2f));
        Lines.line(Tmp.v2.x, Tmp.v2.y, Tmp.v4.x, Tmp.v4.y);
        Lines.line(Tmp.v3.x, Tmp.v3.y, Tmp.v5.x, Tmp.v5.y);
        UnityDrawf.arc(x1, y1, size1, na - a, na + a - (2 * Mathf.pi));
        UnityDrawf.arc(x2, y2, size2, na - a2 + Mathf.pi, na + a2 + Mathf.pi - (2 * Mathf.pi));

        // 皮带总长 = 两段圆弧 + 两段直线
        final float l1 = Math.abs(2 * a - (2 * Mathf.pi)) * size1;
        final float l2 = Math.abs(2 * a2 - (2 * Mathf.pi)) * size2;
        final float total = l1 + l2 + f * 2;
        if(total < 0.001f) return;

        // 沿皮带全程均匀布置移动齿纹 (相位随轮子旋转推进)
        r = r * size1 * (Mathf.pi / 180f);
        Lines.stroke(1f, team.color);
        Vec2 prev = new Vec2();
        Vec2 cur = new Vec2();
        for(int i = 0; i < 10; i++){
            float len = i * 0.1f * total;
            beltPoint(cur, len - r, total, l1, l2, f, na, a, a2, x1, y1, x2, y2, size1, size2);
            prev.set(cur);
            beltPoint(cur, len - (r + 2f), total, l1, l2, f, na, a, a2, x1, y1, x2, y2, size1, size2);
            Lines.line(cur.x, cur.y, prev.x, prev.y, false);
        }

        Draw.color();
    }

    /** 按沿皮带走过的长度 len 求出皮带上的点, 写入 out (原版 drawBelt 内部 getPt 的逻辑) */
    private static void beltPoint(Vec2 out, float len, float total, float l1, float l2, float f, float na, float a, float a2,
                                  float x1, float y1, float x2, float y2, float size1, float size2){
        len = Mathf.mod(len, total);
        if(len < l1){
            float ang = Mathf.lerp(na - a, na + a - (2 * Mathf.pi), len / l1);
            out.set(Mathf.cos(ang) * size1 + x1, Mathf.sin(ang) * size1 + y1);
        }else if(len < l1 + f){
            out.set(Tmp.v2).lerp(Tmp.v4, Mathf.curve(len - l1, 0, f));
        }else if(len < l1 + f + l2){
            float ang = Mathf.lerp(na - a2 + Mathf.pi, na + a2 + Mathf.pi - (2 * Mathf.pi), Mathf.curve(len - l1 - f, 0, l2));
            out.set(Mathf.cos(ang) * size2 + x2, Mathf.sin(ang) * size2 + y2);
        }else{
            out.set(Tmp.v5).lerp(Tmp.v3, Mathf.curve(len - (l1 + f + l2), 0, f));
        }
    }
}

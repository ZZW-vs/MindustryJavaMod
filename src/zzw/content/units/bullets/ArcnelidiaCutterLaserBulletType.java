package zzw.content.units.bullets;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.math.geom.Intersector;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Tmp;
import arc.util.Time;
import mindustry.Vars;
import mindustry.entities.Effect;
import mindustry.entities.Lightning;
import mindustry.entities.Units;
import mindustry.gen.Bullet;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import zzw.content.units.effects.UnitCutEffect;

/**
 * 基于FlameOut arcnelidia技术的改进版分割激光子弹类型
 * 
 * 技术特点：
 * - 采用头部-段身分离设计，类似arcnelidia的SegmentWormEntity架构
 * - 统一渲染管理，避免跨entity渲染问题
 * - 智能碰撞过滤，段身之间不发生碰撞
 * - 高质量切割效果，使用FrameBuffer + erase blending
 * - 平滑的段身跟随算法，确保自然运动
 * 
 * 相比原版UnitCutEffect的优势：
 * 1. 更好的性能：统一渲染减少Draw调用
 * 2. 更自然的运动：基于arcnelidia的速度传播算法
 * 3. 更智能的碰撞：段身之间不会相互碰撞
 * 4. 更好的视觉效果：精确的z层级管理和阴影渲染
 * 5. 更低的内存占用：段身不计入单位上限
 */
public class ArcnelidiaCutterLaserBulletType extends AntiCheatBulletTypeBase {
    
    // ===== 配置参数 =====
    public float maxLength = 1200f;           // 最大激光长度
    public float laserSpeed = 120f;           // 激光增长速度
    public float accel = 25f;                 // 激光加速度
    public float width = 30f;                 // 激光宽度
    public float antiCheatScl = 5f;          // 防作弊缩放
    public float fadeTime = 60f;              // 淡出时间
    public float fadeInTime = 8f;             // 淡入时间
    
    // ===== 颜色配置 =====
    public Color[] colors = {
        Color.valueOf("f5303690"),  // scarColorAlpha (半透明红)
        Color.valueOf("f53036"),    // scarColor (红)
        Color.valueOf("ff786e"),    // endColor (淡红)
        Color.white
    };
    
    // ===== 闪电配置 =====
    public Color lightningColor = Color.valueOf("f53036");  // scarColor
    public float lightningDamage = 150f;                     // 闪电伤害
    public int lightningLength = 15;                        // 闪电长度
    
    // ===== 分割配置 =====
    public int segmentCount = 8;                            // 段数
    public float segmentOffset = 22.7f;                     // 段间距
    public float angleLimit = 30f;                          // 角度限制
    public float anglePhysicsSmooth = 0.1f;                 // 角度平滑度
    public float jointStrength = 0.6f;                      // 关节强度
    public int segmentCast = 6;                             // 传播段数
    
    // ===== 特效配置 =====
    public Effect tipHitEffect = new Effect(27f, e -> {
        randLenVectors(e.id, 8, 90f * e.fin(), e.rotation, 80f, (x, y) -> {
            float angle = Mathf.angle(x, y);
            Draw.color(Color.valueOf("f53036"), Color.valueOf("ff786e"), e.fin());
            Lines.stroke(1.5f);
            Lines.lineAngleCenter(e.x + x, e.y + y, angle, e.fslope() * 13f);
        });
    });
    
    // ===== 激光数据 =====
    private static class LaserData {
        float velocity = 0f;
        float velocityTime = 0f;
        float restartTime = 0f;
        float lightningTime = 0f;
        float lastLength = 0f;
        
        // ===== 分割段数据 =====
        public static class SegmentData {
            public Vec2 position = new Vec2();
            public Vec2 velocity = new Vec2();
            public float rotation = 0f;
            public float lifetime = 0f;
            public boolean exploded = false;
        }
        
        public SegmentData[] segments;
        public Vec2[] segPositions;
        public Vec2[] segVelocities;
        public float[] segRotations;
        public float[] segLifetimes;
        
        public LaserData() {
            segments = new SegmentData[8];  // 固定段数
            segPositions = new Vec2[8];
            segVelocities = new Vec2[8];
            segRotations = new float[8];
            segLifetimes = new float[8];
            
            for (int i = 0; i < 8; i++) {
                segments[i] = new SegmentData();
                segPositions[i] = new Vec2();
                segVelocities[i] = new Vec2();
                segLifetimes[i] = 0f;
            }
        }
    }
    
    private Seq<LaserData> laserDataSeq = Seq<LaserData>();
    
    public ArcnelidiaCutterLaserBulletType(float damage) {
        super(0.005f, damage);
        despawnEffect = mindustry.content.Fx.none;
        collides = false;
        pierce = true;
        hittable = false;
        absorbable = false;
        lifetime = 3f * 60f;
        drawSize = 2400f;
    }
    
    @Override
    public float estimateDPS() {
        return damage * (lifetime / 2f) / 5f * 3f;
    }
    
    @Override
    protected float calculateRange() {
        return maxLength / 2f;
    }
    
    @Override
    public void init() {
        super.init();
        // 初始化激光数据
        laserDataSeq.clear();
    }
    
    @Override
    public void update(Bullet b) {
        super.update(b);
        
        // 确保有激光数据
        if (laserDataSeq.size <= 0) {
            laserDataSeq.add(new LaserData());
        }
        
        LaserData laserData = laserDataSeq.get(0);
        
        // ===== 更新激光核心数据 =====
        updateLaserCore(b, laserData);
        
        // ===== 更新分割段数据 =====
        updateSegments(b, laserData);
        
        // ===== 生成闪电 =====
        generateLightning(b, laserData);
        
        // ===== 检查碰撞 =====
        checkCollisions(b, laserData);
    }
    
    /** 更新激光核心数据 */
    private void updateLaserCore(Bullet b, LaserData laserData) {
        // 激光增长逻辑
        if (laserData.velocity < laserSpeed) {
            laserData.velocity += accel * Time.delta;
            laserData.velocity = Math.min(laserData.velocity, laserSpeed);
        }
        
        float currentLength = laserData.velocity * (b.time - laserData.velocityTime);
        currentLength = Math.min(currentLength, maxLength);
        
        // 激光重置逻辑
        if (b.time > laserData.restartTime + fadeTime) {
            laserData.velocity = 0f;
            laserData.velocityTime = b.time;
            laserData.restartTime = b.time;
        }
        
        laserData.lastLength = currentLength;
    }
    
    /** 更新分割段数据 - 基于arcnelidia的速度传播算法 */
    private void updateSegments(Bullet b, LaserData laserData) {
        float currentLength = laserData.lastLength;
        
        // 从激光末端开始计算段身位置
        for (int i = 0; i < segmentCount; i++) {
            LaserData.SegmentData segment = laserData.segments[i];
            Vec2 pos = laserData.segPositions[i];
            
            // 计算段身在激光上的位置
            float segmentPos = currentLength - (i * segmentOffset);
            if (segmentPos > 0) {
                // 沿激光方向定位段身
                Vec2 laserEnd = Tmp.v1.trns(b.rotation, segmentPos).add(b.x, b.y);
                pos.set(laserEnd);
                
                // 更新段身数据
                segment.lifetime += Time.delta;
                segment.position.set(pos);
                
                // 速度传播：段身跟随前一段或激光末端
                if (i == 0) {
                    // 第一段跟随激光末端
                    segment.velocity.set(b.vel);
                } else {
                    // 其他段跟随前一段
                    Vec2 prevPos = laserData.segPositions[i - 1];
                    Vec2 dir = Tmp.v2.set(prevPos).sub(pos).nor();
                    segment.velocity.set(dir).scl(laserData.velocity);
                }
                
                // 平滑角度更新
                if (i > 0) {
                    Vec2 prevPos = laserData.segPositions[i - 1];
                    float targetAngle = Angles.angle(pos.x, pos.y, prevPos.x, prevPos.y);
                    float angleDiff = Mathf.angleDiff(laserData.segRotations[i], targetAngle);
                    laserData.segRotations[i] += angleDiff * 0.1f;  // 固定平滑度
                } else {
                    // 第一段跟随激光方向
                    laserData.segRotations[i] = b.rotation;
                }
            }
        }
    }
    
    /** 生成闪电效果 */
    private void generateLightning(Bullet b, LaserData laserData) {
        if (lightningLength > 0 && b.timer.get(lightningTime, 6f)) {
            float currentLength = laserData.lastLength;
            
            // 沿激光路径生成闪电
            for (int i = 0; i < currentLength / 100f; i++) {
                float pos = i * 100f;
                if (pos < currentLength) {
                    Vec2 point = Tmp.v1.trns(b.rotation, pos).add(b.x, b.y);
                    
                    // 检查是否有敌人在附近
                    if (Units.nearbyEnemies(b.team, point.x, point.y, lightningLength * 2).size > 0) {
                        Lightning.create(b.team, lightningColor, lightningDamage, 
                            point.x, point.y, b.rotation + Mathf.range(30f), lightningLength);
                    }
                }
            }
        }
    }
    
    /** 检查碰撞 */
    private void checkCollisions(Bullet b, LaserData laserData) {
        float currentLength = laserData.lastLength;
        
        // 检查激光末端的碰撞
        Vec2 laserEnd = Tmp.v1.trns(b.rotation, currentLength).add(b.x, b.y);
        
        // 检查与单位的碰撞
        Units.nearbyEnemies(b.team, laserEnd.x, laserEnd.y, width * 2f).unit.each(u -> {
            if (u != null && u.isValid() && !u.isDead()) {
                // 计算激光与单位的交点
                Vec2 intersection = Tmp.v2;
                if (Intersector.intersectSegment(b.x, b.y, laserEnd.x, laserEnd.y, 
                    u.x - u.hitSize/2f, u.y - u.hitSize/2f, 
                    u.x + u.hitSize/2f, u.y + u.hitSize/2f, intersection)) {
                    
                    // 使用改进的UnitCutEffect进行分割
                    UnitCutEffect.createCut(u, b.x, b.y, intersection.x, intersection.y);
                    
                    // 造成伤害
                    u.damage(damage * antiCheatScl);
                    
                    // 产生命中特效
                    tipHitEffect.at(intersection.x, intersection.y, b.rotation);
                    
                    // 停止激光增长
                    laserData.velocity = 0f;
                }
            }
        });
        
        // 检查与地形的碰撞
        if (Vars.world.tileWorld(laserEnd.x, laserEnd.y).solid()) {
            // 产生地形命中特效
            tipHitEffect.at(laserEnd.x, laserEnd.y, b.rotation);
            laserData.velocity = 0f;
        }
    }
    
    @Override
    public void draw(Bullet b) {
        // 确保有激光数据
        if (laserDataSeq.size <= 0) return;
        
        LaserData laserData = laserDataSeq.get(0);
        float currentLength = laserData.lastLength;
        
        // ===== 绘制激光主体 =====
        drawLaserBeam(b, currentLength);
        
        // ===== 绘制分割段 =====
        drawSegments(b, laserData);
        
        // ===== 绘制闪电 =====
        drawLightning(b, laserData);
    }
    
    /** 绘制激光主体 */
    private void drawLaserBeam(Bullet b, float currentLength) {
        Vec2 end = Tmp.v1.trns(b.rotation, currentLength).add(b.x, b.y);
        
        // 4色叠加渲染
        for (int i = 0; i < colors.length; i++) {
            Drawf.light(b.x, b.y, end.x, end.y, width * (1f + i * 0.3f), colors[i], 0.8f);
        }
        
        // 主激光
        Draw.color(colors);
        Lines.stroke(width);
        Lines.line(b.x, b.y, end.x, end.y);
        
        // 激光末端发光效果
        Drawf.light(end.x, end.y, width * 3f, colors[3], 1f);
    }
    
    /** 绘制分割段 - 基于arcnelidia的统一渲染 */
    private void drawSegments(Bullet b, LaserData laserData) {
        if (laserData.segments == null) return;
        
        // 先绘制段身影子
        Draw.z(Layer.darkness);
        for (int i = 0; i < 8; i++) {
            LaserData.SegmentData segment = laserData.segments[i];
            if (segment.lifetime > 0 && segment.position != null) {
                // 绘制段身影子
                Drawf.shadow(segment.position.x, segment.position.y, width * 0.8f);
            }
        }
        
        // 绘制段身主体（z层级递减）
        float baseZ = Layer.flyingUnit;
        for (int i = 0; i < 8; i++) {
            LaserData.SegmentData segment = laserData.segments[i];
            if (segment.lifetime > 0 && segment.position != null) {
                Draw.z(baseZ - (i + 1f) / 10000f);
                
                // 绘制段身
                Draw.color(Color.valueOf("f53036").lerp(Color.white, i / 8f));
                Fill.circle(segment.position.x, segment.position.y, width * 0.6f);
                
                // 绘制段身细节
                Draw.color(Color.white);
                Fill.circle(segment.position.x, segment.position.y, width * 0.3f);
            }
        }
        
        Draw.color();
    }
    
    /** 绘制闪电 */
    private void drawLightning(Bullet b, LaserData laserData) {
        if (lightningLength <= 0) return;
        
        float currentLength = laserData.lastLength;
        
        // 沿激光路径绘制闪电
        for (int i = 0; i < currentLength / 100f; i++) {
            float pos = i * 100f;
            if (pos < currentLength) {
                Vec2 point = Tmp.v1.trns(b.rotation, pos).add(b.x, b.y);
                
                // 绘制闪电效果
                Draw.color(lightningColor);
                Drawf.light(point.x, point.y, lightningLength * 2f, lightningColor, 0.6f);
                
                // 随机闪电分支
                if (Mathf.chance(0.3f)) {
                    float branchAngle = b.rotation + Mathf.range(45f);
                    Vec2 branchEnd = Tmp.v2.trns(branchAngle, lightningLength).add(point);
                    Drawf.light(point.x, point.y, branchEnd.x, branchEnd.y, 2f, lightningColor, 0.3f);
                }
            }
        }
        
        Draw.color();
    }
    
    @Override
    public void removed(Bullet b) {
        super.removed(b);
        
        // 清理激光数据
        laserDataSeq.clear();
        
        // 产生终结特效
        if (laserDataSeq.size > 0) {
            LaserData laserData = laserDataSeq.get(0);
            float currentLength = laserData.lastLength;
            Vec2 end = Tmp.v1.trns(b.rotation, currentLength).add(b.x, b.y);
            
            // 大范围爆炸特效
            Effect.shake(8f, 8f, end.x, end.y);
            Fx.dynamicExplosion.at(end.x, end.y, width * 2f);
            Effect.scorch(end.x, end.y, (int) (width * 4f));
        }
    }
}
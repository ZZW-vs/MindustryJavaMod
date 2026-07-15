package zzw.content.units.types;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Tmp;
import mindustry.entities.Leg;
import mindustry.gen.Unit;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;

/**
 * 荒芜者 UnitType (模仿 FlameOut DespondencyUnitType 腿部动画)
 *
 * 重写 drawLegs 添加阴影腿贴图 (leg-shadow + leg-base-shadow), 实现立体腿部效果
 * 参考: FlameOut-1.1.3 DespondencyUnitType.drawLegs (L204-237)
 */
public class DesolationUnitType extends UnitType {
    static Vec2 legOff = new Vec2();

    public TextureRegion legShadowRegion, legShadowBaseRegion;

    public DesolationUnitType(String name) {
        super(name);
    }

    @Override
    public void load() {
        super.load();
        // 加载阴影腿贴图
        legShadowRegion = Core.atlas.find(name + "-leg-shadow");
        legShadowBaseRegion = Core.atlas.find(name + "-leg-base-shadow");
    }

    @Override
    public <T extends Unit & mindustry.gen.Legsc> void drawLegs(T unit) {
        // ★ 阴影腿 (模仿 FlameOut DespondencyUnitType.drawLegs L205-234)
        if (shadowElevation > 0 && legShadowRegion.found() && legShadowBaseRegion.found()) {
            float invDrown = 1f - unit.drownTime;
            float scl = shadowElevation * invDrown * shadowElevationScl;
            Leg[] legs = unit.legs();

            Draw.color(Pal.shadow);
            for (int j = legs.length - 1; j >= 0; j--) {
                int i = (j % 2 == 0 ? j / 2 : legs.length - 1 - j / 2);
                Leg leg = legs[i];
                boolean flip = i >= legs.length / 2f;
                int flips = Mathf.sign(flip);
                float elev = 0f;
                if (leg.moving) {
                    elev = Mathf.slope(1f - leg.stage);
                }
                float mid = (elev / 2f + 0.5f) * scl;

                Vec2 position = unit.legOffset(legOff, i).add(unit);

                Vec2 v1 = Tmp.v1.set(leg.base).sub(leg.joint).inv().setLength(legExtension).add(leg.joint);

                Lines.stroke(legShadowRegion.height * legShadowRegion.scl() * flips);
                Lines.line(legShadowRegion, position.x + (shadowTX * scl), position.y + (shadowTY * scl),
                           leg.joint.x + (shadowTX * mid), leg.joint.y + (shadowTY * mid), false);

                Lines.stroke(legShadowBaseRegion.height * legShadowBaseRegion.scl() * flips);
                Lines.line(legShadowBaseRegion, v1.x + (shadowTX * mid), v1.y + (shadowTY * mid),
                           leg.base.x + (shadowTX * elev * scl), leg.base.y + (shadowTY * elev * scl), false);
            }

            Draw.color();
        }

        // 调用原版 drawLegs 画正常腿
        super.drawLegs(unit);
    }
}

package zzw.content.units.types;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.util.Time;
import mindustry.gen.Unit;
import mindustry.type.UnitType;

/**
 * 彩虹单位类型 (PU132 RainbowUnitType 移植版)
 *
 * 在基础 UnitType 之上叠加 6 层彩虹贴图, 每层色相随时间流动 + 段偏移
 * 用于 kami 弹幕 Boss 单位
 *
 * 参考: PU132 unity/type/RainbowUnitType.java
 */
public class RainbowUnitType extends UnitType {
    private static final Color tmpColor = new Color();

    public int segments = 6;
    public float offset = 15f;
    public TextureRegion[] rainbowRegions;
    public TextureRegion trailRegion;

    public RainbowUnitType(String name) {
        super(name);
    }

    @Override
    public void load() {
        super.load();
        trailRegion = Core.atlas.find(name + "-trail");
        rainbowRegions = new TextureRegion[segments];
        for (int i = 0; i < segments; i++) {
            rainbowRegions[i] = Core.atlas.find(name + "-rainbow-" + (i + 1));
        }
    }

    @Override
    public void drawBody(Unit unit) {
        super.drawBody(unit);
        // ★ 6 层彩虹叠加: 每层色相随时间流动 + 段偏移 (offset=15° * i)
        for (int i = 0; i < segments; i++) {
            Draw.color(tmpColor.set(1f, 0f, 0f, 1f).shiftHue(Time.time + (offset * i)));
            Draw.rect(rainbowRegions[i], unit.x, unit.y, unit.rotation - 90f);
        }
        Draw.reset();
    }
}

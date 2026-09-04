package zzw.content.units.ai;

import arc.util.Time;
import mindustry.entities.units.UnitController;
import mindustry.gen.Unit;

public class MonolithSoulAI implements UnitController {
    public Unit unit;

    @Override
    public void unit(Unit unit) {
        this.unit = unit;
    }

    @Override
    public Unit unit() {
        return unit;
    }
}
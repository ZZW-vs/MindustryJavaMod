package zzw.content.units.type.decal;

import arc.func.Func;
import arc.graphics.Pixmap;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import mindustry.gen.Unit;

public abstract class UnitDecorationType{
    public String name;
    public Func<Object, UnitDecoration> decalType;

    public UnitDecorationType(String name){
        this.name = name;
        this.decalType = obj -> new UnitDecoration(this);
    }

    public void load(){}

    public void draw(Unit unit, UnitDecoration deco){
        // 默认绘制逻辑
    }

    public void drawIcon(Func<TextureRegion, Pixmap> prov, Pixmap icon, Func<Object, TextureRegion> outliner){
        // 默认图标绘制逻辑
    }

    public void update(Unit unit, UnitDecoration deco){
        // 默认更新逻辑
    }

    public void added(Unit unit, UnitDecoration deco){
        // 默认添加逻辑
    }

    public static class UnitDecoration{
        public UnitDecorationType type;

        public UnitDecoration(UnitDecorationType type){
            this.type = type;
        }
    }
}
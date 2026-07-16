package zzw.content.units.rotor;

import arc.Core;
import arc.graphics.g2d.TextureRegion;
import mindustry.io.JsonIO;

/**
 * 直升机旋翼定义 (移植自 PU_V8 unity.entities.Rotor)
 * @author younggam
 * @author GlennFolker
 */
public class Rotor {
    public final String name;

    public TextureRegion bladeRegion, bladeOutlineRegion, bladeGhostRegion, bladeShadeRegion, topRegion;

    public boolean mirror;
    public float x;
    public float y;

    public float rotOffset = 0f;
    public float speed = 29f;
    public float shadeSpeed = 3f;
    public float ghostAlpha = 0.6f;
    public float shadowAlpha = 0.4f;
    public float bladeFade = 1f;

    public int bladeCount = 4;

    public Rotor(String name) {
        this.name = name;
    }

    public void load() {
        bladeRegion = Core.atlas.find(name + "-blade");
        bladeOutlineRegion = Core.atlas.find(name + "-blade-outline", Core.atlas.find("error"));
        bladeGhostRegion = Core.atlas.find(name + "-blade-ghost", Core.atlas.find("error"));
        bladeShadeRegion = Core.atlas.find(name + "-blade-shade", Core.atlas.find("error"));
        topRegion = Core.atlas.find(name + "-top");
    }

    public Rotor copy() {
        return JsonIO.copy(this, new Rotor(name));
    }
}

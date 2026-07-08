package zzw.content.units;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.Tmp;
import mindustry.gen.Bullet;
import mindustry.entities.bullet.BasicBulletType;

public class EndBasicBulletType extends AntiCheatBulletTypeBase {
    // PU132 原版颜色: scarColor(#f53036), endColor(#ff786e)
    public Color backColor = Color.valueOf("f53036");
    public Color frontColor = Color.valueOf("ff786e");
    public float width = 5f, height = 7f;
    public float shrinkX = 0f, shrinkY = 0.5f;
    public float spin = 0;
    public String sprite = "bullet";

    public TextureRegion backRegion;
    public TextureRegion frontRegion;

    public EndBasicBulletType(float speed, float damage, String bulletSprite) {
        super(speed, damage);
        this.sprite = bulletSprite;
        this.hitColor = frontColor;
        this.lightColor = backColor;
    }

    public EndBasicBulletType(float speed, float damage) {
        this(speed, damage, "bullet");
    }

    @Override
    public void load() {
        super.load();
        backRegion = arc.Core.atlas.find(sprite + "-back");
        frontRegion = arc.Core.atlas.find(sprite);
    }

    @Override
    public void draw(Bullet b) {
        float height = this.height * ((1f - shrinkY) + shrinkY * b.fout());
        float width = this.width * ((1f - shrinkX) + shrinkX * b.fout());
        float offset = -90 + (spin != 0 ? Mathf.randomSeed(b.id, 360f) + b.time * spin : 0f);

        Draw.color(backColor);
        Draw.rect(backRegion, b.x, b.y, width, height, b.rotation() + offset);
        Draw.color(frontColor);
        Draw.rect(frontRegion, b.x, b.y, width, height, b.rotation() + offset);

        Draw.reset();
    }
}
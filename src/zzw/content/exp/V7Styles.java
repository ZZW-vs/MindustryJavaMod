package zzw.content.exp;

import arc.graphics.Color;
import arc.scene.ui.ImageButton;
import arc.scene.ui.TextButton;
import mindustry.ui.Fonts;
import mindustry.ui.Styles;

import static mindustry.gen.Tex.flatDownBase;
import static mindustry.gen.Tex.pane;

/**
 * PU_V8 V7Styles (移植, UI按钮样式)
 * 参考: PU_V8 main/src/unity/v8/V7Styles.java
 */
public class V7Styles {
    public static ImageButton.ImageButtonStyle
        clearTransi = new ImageButton.ImageButtonStyle(){{
            down = Styles.flatDown;
            up = Styles.black6;
            over = Styles.flatOver;
            disabled = Styles.black8;
            imageDisabledColor = Color.lightGray;
            imageUpColor = Color.white;
    }};
    public static ImageButton.ImageButtonStyle clearToggleTransi = new ImageButton.ImageButtonStyle(){{
            down = Styles.flatDown;
            checked = Styles.flatDown;
            up = Styles.black6;
            over = Styles.flatOver;
    }};
    public static TextButton.TextButtonStyle transt = new TextButton.TextButtonStyle(){{
        down = Styles.flatDown;
        up = Styles.none;
        over = Styles.flatOver;
        font = Fonts.def;
        fontColor = Color.white;
        disabledFontColor = Color.gray;
    }};
}

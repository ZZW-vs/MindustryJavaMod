package zzw.content.blocks;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.scene.ui.Button;
import arc.scene.ui.layout.Table;
import arc.scene.ui.TextField;
import arc.util.Strings;
import arc.util.Time;
import mindustry.gen.Building;
import mindustry.gen.Tex;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;
import zzw.util.WavefrontObject;

import java.util.Arrays;

/**
 * 3x3 万能模型展示台
 * 支持8种内置3D模型切换, 玩家可调整位置/颜色/大小
 */
public class ObjDisplayBlock extends Block {

    public TextureRegion baseRegion;

    public ObjDisplayBlock(String name) {
        super(name);
        size = 3;
        update = true;
        solid = true;
        destructible = true;
        allowDiagonal = true;
        configurable = true;
        buildVisibility = BuildVisibility.shown;
    }

    @Override
    public void load() {
        super.load();
        // 使用 3x3 炮台底座贴图 (vanilla ripple-base 是 3x3 炮台底座)
        baseRegion = Core.atlas.find("ripple-base");
    }

    @Override
    public TextureRegion[] icons() {
        return new TextureRegion[]{baseRegion};
    }

    public class DisplayBuild extends Building {
        float angle = 0f;
        float currentScale = 1f;
        Color currentColor = null; // null = 原色(使用模型默认颜色)
        char currentAxis = 'Z';
        float currentOffset = 0f;
        int currentModel = 0;

        // 内置模型列表 (不含 MMD, MMD 在 MmdDisplayBlock 专用展示台)
        private final WavefrontObject[] MODELS = {
            zzw.util.ZObjs.flywheel,
            zzw.util.ZObjs.cogwheel,
            zzw.util.ZObjs.largeCogwheel,
            zzw.util.ZObjs.waterWheel,
            zzw.util.ZObjs.crushingWheel,
            zzw.util.ZObjs.cube,
            zzw.util.ZObjs.prism,
            zzw.util.ZObjs.wavefront
        };

        private final String[] MODEL_NAMES = {
            "飞轮", "小齿轮", "大齿轮", "水车", "粉碎轮", "方块", "棱镜", "波前"
        };

        @Override
        public void updateTile() {
            angle += Time.delta * 0.5f;
            if (angle > 360f) angle -= 360f;
        }

        @Override
        public void draw() {
            // 底座 (3x3 炮台底座贴图)
            if (baseRegion.found()) {
                Draw.rect(baseRegion, x, y);
            }

            // 阴影 - 基于模型实际 worldSize (boundRadius * 2 * defaultScl * obj.size * scale)
            Draw.z(Layer.blockBuilding - 1f);
            WavefrontObject obj = MODELS[currentModel];
            float shadowSize = (obj != null) ? obj.boundRadius * 2f * 4f * obj.size * currentScale : size * 8f * currentScale;
            Drawf.shadow(x, y, shadowSize);

            drawModel();
        }

        private void drawModel() {
            WavefrontObject obj = MODELS[currentModel];
            if (obj == null || obj.faces == null || obj.faces.size == 0) return;

            // 保存原始渲染参数
            Color origLight = obj.lightColor.cpy();
            Color origShade = obj.shadeColor.cpy();
            float origSize = obj.size;
            float origMaxShade = obj.maxShade;

            if (currentColor != null) {
                // 玩家选择了颜色
                obj.lightColor.set(currentColor);
                obj.shadeColor.set(currentColor.r * 0.2f, currentColor.g * 0.2f, currentColor.b * 0.2f);
                obj.maxShade = 0.85f;
            } else {
                // ★ 原色模式: lightColor=白色不调制材质色, shadeColor=黑色
                obj.lightColor.set(Color.white);
                obj.shadeColor.set(Color.black);
                obj.maxShade = 0.75f;
            }
            // ★ size = 原始size * currentScale, 而非覆盖为 currentScale
            // 否则 wavefront(size=8) 在 currentScale=1 时会缩小到 1/8
            obj.size = origSize * currentScale;

            // 旋转角度
            float rX = -25f;
            float rY = 0f;
            float rZ = 0f;
            if (currentAxis == 'Y') {
                rY = angle;
            } else {
                rZ = angle;
            }

            // ★ zOffset 用足够大的值区分多实例, 避免面在 batch 中交叉穿插
            // (0.01f 太小, 不同实例的 face z 值几乎相同, 在 batch 中交叉排序 → 面乱搅和)
            obj.zOffset = (id % 1000) * 1.0f;
            obj.draw(x, y + currentOffset, rX, rY, rZ);

            // 恢复原始参数
            obj.lightColor.set(origLight);
            obj.shadeColor.set(origShade);
            obj.size = origSize;
            obj.maxShade = origMaxShade;
            obj.zOffset = 0f;
        }

        @Override
        public void buildConfiguration(Table table) {
            table.clear();

            // ===== 1. 模型选择 =====
            table.table(Tex.pane, t -> {
                t.add("模型选择").pad(4f).row();
                Table modelTable = new Table();
                modelTable.defaults().pad(2f);
                Button[] modelButtons = new Button[MODELS.length];
                for (int i = 0; i < MODELS.length; i++) {
                    final int idx = i;
                    modelButtons[i] = modelTable.button(MODEL_NAMES[i], Styles.flatTogglet, () -> {
                        currentModel = idx;
                        Arrays.stream(modelButtons).forEach(b -> b.setChecked(false));
                        modelButtons[idx].setChecked(true);
                    }).size(65f, 28f).get();
                    modelButtons[i].setChecked(idx == currentModel);
                    if ((i + 1) % 4 == 0) modelTable.row();
                }
                t.add(modelTable);
            }).growX().padBottom(4f);
            table.row();

            // ===== 2. 位置 =====
            table.table(Tex.pane, t -> {
                t.add("位置").pad(4f).row();
                Table posTable = new Table();
                posTable.defaults().pad(2f);

                // 旋转轴
                Button[] axisButtons = new Button[2];
                axisButtons[0] = posTable.button("Z轴旋转", Styles.flatTogglet, () -> {
                    currentAxis = 'Z';
                    axisButtons[0].setChecked(true);
                    axisButtons[1].setChecked(false);
                }).size(80f, 28f).get();
                axisButtons[1] = posTable.button("Y轴旋转", Styles.flatTogglet, () -> {
                    currentAxis = 'Y';
                    axisButtons[1].setChecked(true);
                    axisButtons[0].setChecked(false);
                }).size(80f, 28f).get();
                axisButtons[0].setChecked(currentAxis == 'Z');
                axisButtons[1].setChecked(currentAxis == 'Y');
                posTable.row();

                // 高度: -/输入框/+
                posTable.button("-", Styles.flatt, () -> {
                    currentOffset = Mathf.clamp(currentOffset - 2f, -20f, 64f);
                }).size(30f, 28f);

                TextField offsetField = posTable.field(Strings.fixed(currentOffset, 0), text -> {
                    if (Strings.canParseFloat(text)) {
                        currentOffset = Mathf.clamp(Strings.parseFloat(text), -20f, 64f);
                    }
                }).padLeft(4f).padRight(4f).width(60f).get();
                offsetField.setMessageText("高度");
                // ★ 实时更新: 按钮改变值后输入框同步显示 (不覆盖正在输入的文本)
                offsetField.update(() -> {
                    if (!offsetField.hasKeyboard()) {
                        offsetField.setText(Strings.fixed(currentOffset, 0));
                    }
                });

                posTable.button("+", Styles.flatt, () -> {
                    currentOffset = Mathf.clamp(currentOffset + 2f, -20f, 64f);
                }).size(30f, 28f);

                t.add(posTable);
            }).growX().padBottom(4f);
            table.row();

            // ===== 3. 颜色 =====
            table.table(Tex.pane, t -> {
                t.add("颜色").pad(4f).row();
                Table colorTable = new Table();
                colorTable.defaults().pad(2f);

                String[] colorNames = {"原色", "白色", "金色", "红色", "蓝色", "绿色", "灰色"};
                Color[] colors = {null, Color.white, Color.valueOf("FFD700"), Color.red, Color.blue, Color.green, Color.gray};
                Button[] colorButtons = new Button[colors.length];

                for (int i = 0; i < colors.length; i++) {
                    final int idx = i;
                    colorButtons[idx] = colorTable.button(colorNames[idx], Styles.flatTogglet, () -> {
                        currentColor = colors[idx];
                        updateToggleButtons(colorButtons, idx);
                    }).size(50f, 28f).get();
                    colorButtons[idx].setChecked(idx == 0);
                    if ((i + 1) % 4 == 0) colorTable.row();
                }
                t.add(colorTable);
            }).growX().padBottom(4f);
            table.row();

            // ===== 4. 大小 =====
            table.table(Tex.pane, t -> {
                t.add("大小").pad(4f).row();
                Table scaleTable = new Table();
                scaleTable.defaults().pad(2f);

                scaleTable.button("-", Styles.flatt, () -> {
                    currentScale = Mathf.clamp(currentScale - 0.5f, 0.1f, 20f);
                }).size(30f, 28f);

                TextField scaleField = scaleTable.field(Strings.fixed(currentScale, 1), text -> {
                    if (Strings.canParseFloat(text)) {
                        currentScale = Mathf.clamp(Strings.parseFloat(text), 0.1f, 20f);
                    }
                }).padLeft(4f).padRight(4f).width(60f).get();
                scaleField.setMessageText("大小");
                // ★ 实时更新: 按钮改变值后输入框同步显示 (不覆盖正在输入的文本)
                scaleField.update(() -> {
                    if (!scaleField.hasKeyboard()) {
                        scaleField.setText(Strings.fixed(currentScale, 1));
                    }
                });

                scaleTable.button("+", Styles.flatt, () -> {
                    currentScale = Mathf.clamp(currentScale + 0.5f, 0.1f, 20f);
                }).size(30f, 28f);

                t.add(scaleTable);
            }).growX();
        }

        private void updateToggleButtons(Button[] buttons, int selected) {
            for (int i = 0; i < buttons.length; i++) {
                buttons[i].setChecked(i == selected);
            }
        }

        @Override
        public boolean configTapped() {
            return true;
        }
    }
}

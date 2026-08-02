package zzw.content.blocks;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.scene.ui.Button;
import arc.scene.ui.CheckBox;
import arc.scene.ui.Label;
import arc.scene.ui.Slider;
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

/**
 * MMD 模型专用展示台
 * - 专门针对 PMX/MMD 模型的渲染设置
 * - 支持位置/高度/大小/旋转轴/旋转速度调节
 * - 汉化界面
 */
public class MmdDisplayBlock extends Block {

    public TextureRegion baseRegion;

    public MmdDisplayBlock(String name) {
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
        baseRegion = Core.atlas.find("ripple-base");
    }

    @Override
    public TextureRegion[] icons() {
        return new TextureRegion[]{baseRegion};
    }

    public class MmdDisplayBuild extends Building {
        float angle = 0f;
        float currentScale = 1f;
        char currentAxis = 'Y';
        float currentOffset = 0f;
        float rotateSpeed = 0.5f;
        boolean autoRotate = true;
        boolean groundShadow = true;

        // MMD 模型列表 (可扩展)
        private final WavefrontObject[] MODELS = {
            zzw.util.ZObjs.gale
        };

        private final String[] MODEL_NAMES = {
            "樱子 Idol"
        };

        int currentModel = 0;

        @Override
        public void updateTile() {
            if (autoRotate) {
                angle += Time.delta * rotateSpeed;
                if (angle > 360f) angle -= 360f;
                if (angle < 0f) angle += 360f;
            }
        }

        @Override
        public void draw() {
            if (baseRegion.found()) {
                Draw.rect(baseRegion, x, y);
            }

            // 阴影
            if (groundShadow) {
                Draw.z(Layer.blockBuilding - 1f);
                WavefrontObject obj = MODELS[currentModel];
                float shadowSize = (obj != null) ? obj.boundRadius * 2f * 4f * obj.size * currentScale : size * 8f * currentScale;
                Drawf.shadow(x, y, shadowSize);
            }

            drawModel();
        }

        private void drawModel() {
            WavefrontObject obj = MODELS[currentModel];
            if (obj == null || obj.faces == null || obj.faces.size == 0) return;

            // 保存原始参数
            Color origLight = obj.lightColor.cpy();
            Color origShade = obj.shadeColor.cpy();
            float origSize = obj.size;
            float origMaxShade = obj.maxShade;

            // MMD 专用渲染参数: 保留贴图原色, 轻微顶部光照
            obj.lightColor.set(Color.white);
            obj.shadeColor.set(Color.valueOf("909090"));
            obj.maxShade = 0.25f;
            obj.size = origSize * currentScale;

            // 旋转角度
            float rX = -15f;  // MMD 模型轻微俯视
            float rY = 0f;
            float rZ = 0f;
            if (currentAxis == 'Y') {
                rY = angle;
            } else if (currentAxis == 'Z') {
                rZ = angle;
            } else {
                rX = angle - 15f;
            }

            obj.zOffset = (id % 1000) * 1.0f;
            obj.draw(x, y + currentOffset, rX, rY, rZ);

            // 恢复
            obj.lightColor.set(origLight);
            obj.shadeColor.set(origShade);
            obj.size = origSize;
            obj.maxShade = origMaxShade;
            obj.zOffset = 0f;
        }

        @Override
        public void buildConfiguration(Table table) {
            table.clear();

            // ===== 模型选择 =====
            table.table(Tex.pane, t -> {
                t.add("模型选择").pad(4f).row();
                Table modelTable = new Table();
                modelTable.defaults().pad(2f);
                Button[] modelButtons = new Button[MODELS.length];
                for (int i = 0; i < MODELS.length; i++) {
                    final int idx = i;
                    modelButtons[i] = modelTable.button(MODEL_NAMES[i], Styles.flatTogglet, () -> {
                        currentModel = idx;
                        for (Button b : modelButtons) b.setChecked(false);
                        modelButtons[idx].setChecked(true);
                    }).size(90f, 28f).get();
                    modelButtons[i].setChecked(idx == currentModel);
                }
                t.add(modelTable);
            }).growX().padBottom(4f);
            table.row();

            // ===== 旋转设置 =====
            table.table(Tex.pane, t -> {
                t.add("旋转设置").pad(4f).row();
                Table rotTable = new Table();
                rotTable.defaults().pad(2f);

                // 自动旋转开关
                CheckBox autoBox = rotTable.check("自动旋转", b -> autoRotate = b).padRight(6f).get();
                autoBox.setChecked(autoRotate);

                // 旋转速度
                rotTable.add("速度");
                Slider speedSlider = rotTable.slider(0f, 3f, 0.1f, rotateSpeed, v -> rotateSpeed = v).width(80f).get();
                Label speedLabel = new Label("");
                speedLabel.update(() -> speedLabel.setText(Strings.fixed(rotateSpeed, 1)));
                rotTable.add(speedLabel).width(24f).padLeft(2f);
                rotTable.row();

                // 旋转轴
                Button[] axisButtons = new Button[3];
                axisButtons[0] = rotTable.button("Y轴", Styles.flatTogglet, () -> {
                    currentAxis = 'Y';
                    updateAxisButtons(axisButtons, 'Y');
                }).size(55f, 26f).get();
                axisButtons[1] = rotTable.button("Z轴", Styles.flatTogglet, () -> {
                    currentAxis = 'Z';
                    updateAxisButtons(axisButtons, 'Z');
                }).size(55f, 26f).get();
                axisButtons[2] = rotTable.button("X轴", Styles.flatTogglet, () -> {
                    currentAxis = 'X';
                    updateAxisButtons(axisButtons, 'X');
                }).size(55f, 26f).get();
                updateAxisButtons(axisButtons, currentAxis);

                t.add(rotTable);
            }).growX().padBottom(4f);
            table.row();

            // ===== 位置 =====
            table.table(Tex.pane, t -> {
                t.add("位置").pad(4f).row();
                Table posTable = new Table();
                posTable.defaults().pad(2f);

                posTable.add("高度");
                posTable.button("-", Styles.flatt, () -> {
                    currentOffset = Mathf.clamp(currentOffset - 2f, -20f, 80f);
                }).size(28f, 26f);

                TextField offsetField = posTable.field(Strings.fixed(currentOffset, 0), text -> {
                    if (Strings.canParseFloat(text)) {
                        currentOffset = Mathf.clamp(Strings.parseFloat(text), -20f, 80f);
                    }
                }).width(50f).get();
                offsetField.setMessageText("高度");
                offsetField.update(() -> {
                    if (!offsetField.hasKeyboard()) {
                        offsetField.setText(Strings.fixed(currentOffset, 0));
                    }
                });

                posTable.button("+", Styles.flatt, () -> {
                    currentOffset = Mathf.clamp(currentOffset + 2f, -20f, 80f);
                }).size(28f, 26f);

                // 地面阴影
                CheckBox shadowBox = posTable.check("阴影", b -> groundShadow = b).padLeft(6f).get();
                shadowBox.setChecked(groundShadow);

                t.add(posTable);
            }).growX().padBottom(4f);
            table.row();

            // ===== 大小 =====
            table.table(Tex.pane, t -> {
                t.add("大小").pad(4f).row();
                Table scaleTable = new Table();
                scaleTable.defaults().pad(2f);

                scaleTable.button("-", Styles.flatt, () -> {
                    currentScale = Mathf.clamp(currentScale - 0.2f, 0.1f, 10f);
                }).size(28f, 26f);

                TextField scaleField = scaleTable.field(Strings.fixed(currentScale, 1), text -> {
                    if (Strings.canParseFloat(text)) {
                        currentScale = Mathf.clamp(Strings.parseFloat(text), 0.1f, 10f);
                    }
                }).width(50f).get();
                scaleField.setMessageText("大小");
                scaleField.update(() -> {
                    if (!scaleField.hasKeyboard()) {
                        scaleField.setText(Strings.fixed(currentScale, 1));
                    }
                });

                scaleTable.button("+", Styles.flatt, () -> {
                    currentScale = Mathf.clamp(currentScale + 0.2f, 0.1f, 10f);
                }).size(28f, 26f);

                // 滑条快速调节
                scaleTable.add("快速");
                scaleTable.slider(0.1f, 5f, 0.1f, currentScale, v -> {
                    currentScale = Mathf.clamp(v, 0.1f, 5f);
                }).width(100f);

                t.add(scaleTable);
            }).growX();
        }

        private void updateAxisButtons(Button[] buttons, char axis) {
            buttons[0].setChecked(axis == 'Y');
            buttons[1].setChecked(axis == 'Z');
            buttons[2].setChecked(axis == 'X');
        }

        @Override
        public boolean configTapped() {
            return true;
        }
    }
}

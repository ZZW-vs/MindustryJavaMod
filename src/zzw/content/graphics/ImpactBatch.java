package zzw.content.graphics;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.g2d.Batch;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.SpriteBatch;
import arc.graphics.gl.Shader;
import arc.math.Mat;

/**
 * 换批机制 / 冲击帧批处理器 (借鉴 Vanilla-Expansion 的 FixedSpriteBatch 思路改写)。
 *
 * <p>用途: 给一段绘制内容<b>强制套上统一着色器与混合模式</b>, 且不受其间
 * {@code Draw.shader()} / {@code Draw.blend()} 切来切去的影响 —— 即所谓
 * "冲击帧" (整屏/整片区域被同一套着色器处理)。</p>
 *
 * <p>原理 (新手向):</p>
 * <ol>
 *   <li>游戏所有 2D 绘制最终都走 {@code Core.batch}。{@link #beginSwap(Shader)}
 *       把 {@code Core.batch} 临时换成本类实例 (一个可"锁着色器"的 SpriteBatch);</li>
 *   <li>本类重写了 {@code setShader}/{@code setBlending}, 把它们<b>变成空操作</b>,
 *       于是中途任何 {@code Draw.shader(...)} / {@code Draw.blend(...)} 都改不动它,
 *       着色器被"锁死";</li>
 *   <li>{@link #endSwap()} 时先 flush 本批, 再把 {@code Core.batch} 还原回去。</li>
 * </ol>
 *
 * <p>⚠ 使用约束: begin/end 必须成对, 且中间不要抛异常 (否则 Core.batch 不会还原)。
 * 建议配合 try/finally 使用:</p>
 * <pre>{@code
 * ImpactBatch.beginSwap(ShaderLib.blackHole);
 * try{
 *     // 这里画的任何东西都会被 blackHole 着色器处理
 *     Draw.rect(region, x, y);
 * }finally{
 *     ImpactBatch.endSwap();
 * }
 * }</pre>
 *
 * @author GlennFolker (原作思路), 改写: zzw
 */
public class ImpactBatch extends SpriteBatch{
    /** 全局单例 (惰性创建)。 */
    public static ImpactBatch batch;
    /** 换批前的原始批 (endSwap 时还原)。 */
    protected static Batch lastBatch;

    /** 保存换批前后的投影矩阵, 换批后需重新应用, 否则新批用的是自己的默认矩阵。 */
    private static final Mat savedProj = new Mat(), savedTrans = new Mat();

    public ImpactBatch(){
        super(1024);
    }

    /** 惰性获取单例。 */
    public static ImpactBatch inst(){
        if(batch == null) batch = new ImpactBatch();
        return batch;
    }

    /**
     * 开始换批: 把 {@code Core.batch} 换成锁了指定着色器的本类实例。
     *
     * @param shader 强制使用的着色器 (传 null 表示沿用批默认着色器)
     */
    public static void beginSwap(Shader shader){
        ImpactBatch b = inst();
        if(Core.batch == b) return; // 已在换批中, 忽略重复调用

        lastBatch = Core.batch;
        savedProj.set(Draw.proj());
        savedTrans.set(Draw.trans());

        // 先把原批的待渲染顶点出栈, 否则它们会滞留在旧批里, 等到 endSwap 之后才渲染 → 顺序错乱
        Draw.flush();

        Core.batch = b;
        b.setFixedShader(shader);

        // 新批有自己的投影/变换矩阵, 还原成换批前的值, 保证坐标一致
        Draw.proj(savedProj);
        Draw.trans(savedTrans);
    }

    /** 结束换批: flush 本批并还原 {@code Core.batch}。 */
    public static void endSwap(){
        if(Core.batch != batch) return; // 不在换批中, 忽略
        Draw.flush();
        Core.batch = lastBatch;
        lastBatch = null;
    }

    /** 当前是否处于换批状态。 */
    public static boolean swapping(){
        return batch != null && Core.batch == batch;
    }

    /** 强制设置着色器 (绕过 {@link Draw#shader} 的锁定逻辑)。 */
    public void setFixedShader(Shader shader){
        super.setShader(shader, true);
    }

    /** 强制设置混合模式 (绕过 {@link Draw#blend} 的锁定逻辑)。 */
    public void setFixedBlending(Blending blending){
        super.setBlending(blending);
    }

    // ===== 锁定: 忽略绘制过程中的着色器 / 混合切换 =====

    @Override
    protected void setShader(Shader shader, boolean apply){
        // 空实现: 换批期间 Draw.shader(...) 无法改变本批着色器, 实现"着色器锁"
    }

    @Override
    protected void setBlending(Blending blending){
        // 空实现: 换批期间 Draw.blend(...) 无法改变本批混合模式
    }

    /** 释放本批持有的 GL 资源 (模组卸载时调用)。 */
    public static void disposeAll(){
        if(batch != null){
            batch.dispose();
            batch = null;
        }
        lastBatch = null;
    }
}

package com.example.yhviewer

import android.view.animation.PathInterpolator
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween

/**
 * v17：全应用统一的动效参数（参考 emilkowalski/skills 的动效标准）。
 *
 *  - 出现 / 消失、对点按的响应 → 强 ease-out：一开始就动，手感立刻有回应
 *  - 屏幕上的移动 → 强 ease-in-out；抽屉、面板 → iOS 式抽屉曲线
 *  - 界面动效都在 300ms 以内；消失比出现快（系统响应要干脆）
 *  - 按压：按下 100ms、松手 160ms，不回弹（阅读工具要"稳"，不要"晃"）
 *
 * 新的动画请从这里取值，不要再手写 tween(160)。
 */
object Motion {
    /** cubic-bezier(0.23, 1, 0.32, 1)：界面出现、消失、点按响应 */
    val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    /** cubic-bezier(0.77, 0, 0.175, 1)：元素在屏幕上移动、变形 */
    val EaseInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
    /** cubic-bezier(0.32, 0.72, 0, 1)：抽屉、从底部出现的操作栏 */
    val EaseDrawer = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

    const val PRESS_MS = 100
    const val RELEASE_MS = 160
    const val ENTER_MS = 160
    const val EXIT_MS = 120
    const val SCREEN_MS = 180
    const val BAR_ENTER_MS = 220
    const val PAGE_TURN_MS = 180
    /** 加载超过这么久才显示转圈；快的加载不闪一下 */
    const val SPINNER_DELAY_MS = 300L

    fun <T> enter(): TweenSpec<T> = tween(ENTER_MS, easing = EaseOut)
    fun <T> exit(): TweenSpec<T> = tween(EXIT_MS, easing = EaseOut)

    /** 给 Android View 动画（ObjectAnimator / ValueAnimator）用的同一条 ease-out */
    fun easeOutInterpolator() = PathInterpolator(0.23f, 1f, 0.32f, 1f)
}

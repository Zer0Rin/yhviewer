package com.example.yhviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** v11：目录快速滚动条的位置换算。纯函数，JVM 可测 */
internal object FastScroll {
    /** 少于这么多章不显示（一两屏就能滑完） */
    const val MIN_ITEMS = 40

    /** 拖到轨道的 fraction（0..1）处 → 第几项 */
    fun indexAt(fraction: Float, count: Int): Int {
        if (count <= 0) return 0
        val f = if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0f
        return (f * (count - 1)).roundToInt()
    }

    /** 列表当前位置 → 滑块在轨道上的位置（0..1）。第一屏 = 0，最后一屏 = 1 */
    fun thumbFraction(firstVisible: Int, visibleCount: Int, count: Int): Float {
        val scrollable = count - visibleCount
        if (scrollable <= 0) return 0f
        return (firstVisible.toFloat() / scrollable).coerceIn(0f, 1f)
    }
}

/**
 * 叠在 LazyColumn 上的快速滚动条：右侧一条细滑块，按住或拖动轨道直接跳到对应章节，
 * 拖动时左边显示章节号和标题。用 matchParentSize() 放进列表所在的 Box。
 */
@Composable
internal fun FastScroller(
    state: LazyListState,
    count: Int,
    title: (Int) -> String,
    /** 显示用的章节号（筛选后列表第 i 项不一定是第 i 章） */
    number: (Int) -> Int = { it + 1 },
    modifier: Modifier = Modifier,
) {
    if (count < FastScroll.MIN_ITEMS) return
    val yh = LocalYH.current
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val listFraction by remember(count) {
        derivedStateOf {
            FastScroll.thumbFraction(state.firstVisibleItemIndex, state.layoutInfo.visibleItemsInfo.size, count)
        }
    }
    val fraction = if (dragging) dragFraction else listFraction
    val thumbHeight: Dp = 44.dp
    val trackWidth: Dp = 28.dp

    BoxWithConstraints(modifier) {
        val travel = (maxHeight - thumbHeight).coerceAtLeast(0.dp)
        val thumbTop = travel * fraction

        // 拖动时的章节提示：跟着滑块，在轨道左边
        if (dragging) {
            val index = FastScroll.indexAt(dragFraction, count)
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = -(trackWidth + 8.dp), y = (thumbTop - 6.dp).coerceAtLeast(0.dp))
                    .widthIn(max = 220.dp)
                    .shadow(6.dp, PillShape)
                    .background(yh.surface, PillShape)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("第 ${number(index)} 章", style = MaterialTheme.typography.labelMedium, color = yh.accent)
                Text(title(index), style = MaterialTheme.typography.bodySmall, color = yh.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        // 轨道：整条都能按，按下即跳
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .width(trackWidth)
                .testTag("TocFastScroller")
                .semantics { contentDescription = "快速滚动目录" }
                .pointerInput(count) {
                    fun jump(y: Float) {
                        val h = size.height.toFloat()
                        val thumb = thumbHeight.toPx()
                        dragFraction = ((y - thumb / 2) / (h - thumb).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        val target = FastScroll.indexAt(dragFraction, count)
                        scope.launch { state.scrollToItem(target) }
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        dragging = true
                        jump(down.position.y)
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                change.consume()
                                jump(change.position.y)
                            }
                        } finally {
                            dragging = false
                        }
                    }
                },
        ) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = thumbTop)
                    .width(if (dragging) 6.dp else 4.dp)
                    .height(thumbHeight)
                    .background(if (dragging) yh.accent else yh.subtle, PillShape),
            )
        }
    }
}

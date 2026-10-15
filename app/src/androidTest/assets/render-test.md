# 第 4 步渲染测试

这是一段**粗体**、*斜体*、`行内代码` 和 [外部链接](https://example.com/)。

裸网址也应变成链接：https://example.com/linkify

## 表格

| 检查项 | 预期 |
|---|---|
| 标题 | 有下划线分隔 |
| 表格 | 窄屏可横向滚动 |
| 深色模式 | 跟随系统 |

## 代码高亮

```kotlin
fun main() {
    val greeting = "Hello, Reader"
    println(greeting)
}
```

```python
def add(a: int, b: int) -> int:
    return a + b
```

## 安全测试

下面这行是 md 里的原始 HTML，应该**显示成文字**，不弹窗：

<script>alert('md html leaked')</script>

代码块里的结束标签不应提前闭合模板里的 script：

```html
</script><script>alert('json breakout')</script>
```

外网图片应被沙箱拦截（显示为破图或空白）：

![remote](https://www.google.com/favicon.ico)

## 引用与列表

> AI 生成的东西，值得一个好的阅读器。

1. 第一项
2. 第二项
   - 嵌套项

---

结尾：中文、English、emoji 😀 混排正常。

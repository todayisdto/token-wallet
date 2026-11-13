package io.github.balancewidget

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The launcher inflates widget layouts with a LayoutInflater filter that only
 * allows classes annotated with @RemoteView. Anything else - most notably a
 * bare <View> used as a spacer - throws InflateException, and the widget shows
 * up completely empty with no error visible to the user. Nothing about that is
 * caught at compile time, so it is checked here.
 */
class WidgetLayoutTest {

    private val allowedViews = setOf(
        "LinearLayout",
        "RelativeLayout",
        "FrameLayout",
        "GridLayout",
        "TextView",
        "ImageView",
        "ImageButton",
        "Button",
        "Chronometer",
        "ProgressBar",
        "AnalogClock"
    )

    @Test
    fun widgetLayoutsOnlyUseRemoteViewClasses() {
        val start = File(System.getProperty("user.dir") ?: ".")
        val layoutDir = generateSequence(start) { it.parentFile }
            .map { File(it, "src/main/res/layout") }
            .firstOrNull { it.isDirectory }
        assertTrue("找不到 res/layout 目录", layoutDir != null)

        val widgetLayouts = layoutDir!!.listFiles()?.filter { file ->
            file.name.startsWith("widget_") && file.name.endsWith(".xml")
        }
        assertTrue("没有找到小组件布局", widgetLayouts != null && widgetLayouts.isNotEmpty())

        widgetLayouts!!.forEach { file ->
            val document = DocumentBuilderFactory.newInstance()
                .apply { isNamespaceAware = false }
                .newDocumentBuilder()
                .parse(file)
            val nodes = document.getElementsByTagName("*")
            val tags = (0 until nodes.length)
                .map { nodes.item(it).nodeName.substringAfterLast('.') }
                .filter { it.isNotBlank() }
                .toSet()
            val notAllowed = tags - allowedViews
            assertTrue(
                "${file.name} 使用了 RemoteViews 不允许 inflate 的视图: $notAllowed",
                notAllowed.isEmpty()
            )
        }
    }
}

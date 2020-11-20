package me.zeroeightsix.kami.gui.hudgui

import me.zeroeightsix.kami.event.KamiEventBus
import me.zeroeightsix.kami.gui.rgui.windows.BasicWindow
import me.zeroeightsix.kami.setting.GuiConfig
import me.zeroeightsix.kami.util.graphics.VertexHelper
import me.zeroeightsix.kami.util.math.Vec2f
import org.kamiblue.commons.utils.DisplayEnum

open class HudElement(
        name: String
) : BasicWindow(name, 20.0f, 20.0f, 100.0f, 50.0f, SettingGroup.HUD_GUI) {

    // Annotations
    private val annotation =
            javaClass.annotations.firstOrNull { it is Info } as? Info
                    ?: throw IllegalStateException("No Annotation on class " + this.javaClass.canonicalName + "!")

    val alias = arrayOf(name, *annotation.alias)
    val category = annotation.category
    val description = annotation.description
    var alwaysListening = annotation.alwaysListening

    annotation class Info(
            val alias: Array<String> = [],
            val description: String,
            val category: Category,
            val alwaysListening: Boolean = false,
            val enabledByDefault: Boolean = false
    )

    enum class Category(override val displayName: String) : DisplayEnum {
        CLIENT("Client"),
        COMBAT("Combat"),
        PLAYER("Player"),
        WORLD("World"),
        MISC("Misc")
    }
    // End of annotations

    val settingList get() = GuiConfig.getGroupOrPut("HudGui").getGroupOrPut(originalName).getSettings()

    override fun onGuiInit() {
        super.onGuiInit()
        if (alwaysListening || visible.value) KamiEventBus.subscribe(this)
    }

    override fun onClosed() {
        super.onClosed()
        if (alwaysListening || visible.value) KamiEventBus.subscribe(this)
    }

    final override fun onTick() { super.onTick() }

    final override fun onRender(vertexHelper: VertexHelper, absolutePos: Vec2f) {
        super.onRender(vertexHelper, absolutePos)
        renderHud(vertexHelper)
    }

    open fun renderHud(vertexHelper: VertexHelper) {}

    init {
        visible.valueListeners.add { _, it ->
            if (it) KamiEventBus.subscribe(this)
            else if (!alwaysListening) KamiEventBus.unsubscribe(this)
        }

        if (!annotation.enabledByDefault) visible.value = false
    }

}
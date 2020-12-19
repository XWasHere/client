package me.zeroeightsix.kami.module.modules.render

import me.zeroeightsix.kami.event.events.RenderWorldEvent
import me.zeroeightsix.kami.event.events.SafeTickEvent
import me.zeroeightsix.kami.manager.managers.FriendManager
import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.setting.Settings
import me.zeroeightsix.kami.util.EntityUtils
import me.zeroeightsix.kami.util.EntityUtils.getTargetList
import me.zeroeightsix.kami.util.color.ColorHolder
import me.zeroeightsix.kami.util.color.DyeColors
import me.zeroeightsix.kami.util.color.HueCycler
import org.kamiblue.event.listener.listener
import me.zeroeightsix.kami.util.graphics.ESPRenderer
import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayer
import org.kamiblue.commons.utils.MathUtils.convertRange
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

@Module.Info(
        name = "Tracers",
        description = "Draws lines to other living entities",
        category = Module.Category.RENDER
)
object Tracers : Module() {
    private val page = register(Settings.e<Page>("Page", Page.ENTITY_TYPE))

    /* Entity type settings */
    private val players = register(Settings.booleanBuilder("Players").withValue(true).withVisibility { page.value == Page.ENTITY_TYPE }.build())
    private val friends = register(Settings.booleanBuilder("Friends").withValue(false).withVisibility { page.value == Page.ENTITY_TYPE && players.value }.build())
    private val sleeping = register(Settings.booleanBuilder("Sleeping").withValue(false).withVisibility { page.value == Page.ENTITY_TYPE && players.value }.build())
    private val mobs = register(Settings.booleanBuilder("Mobs").withValue(true).withVisibility { page.value == Page.ENTITY_TYPE }.build())
    private val passive = register(Settings.booleanBuilder("PassiveMobs").withValue(false).withVisibility { page.value == Page.ENTITY_TYPE && mobs.value }.build())
    private val neutral = register(Settings.booleanBuilder("NeutralMobs").withValue(true).withVisibility { page.value == Page.ENTITY_TYPE && mobs.value }.build())
    private val hostile = register(Settings.booleanBuilder("HostileMobs").withValue(true).withVisibility { page.value == Page.ENTITY_TYPE && mobs.value }.build())
    private val invisible = register(Settings.booleanBuilder("Invisible").withValue(true).withVisibility { page.value == Page.ENTITY_TYPE }.build())
    private val range = register(Settings.integerBuilder("Range").withValue(64).withRange(1, 256).withVisibility { page.value == Page.ENTITY_TYPE }.build())

    /* Color settings */
    private val colorPlayer = register(Settings.enumBuilder(DyeColors::class.java, "PlayerColor").withValue(DyeColors.KAMI).withVisibility { page.value == Page.COLOR }.build())
    private val colorFriend = register(Settings.enumBuilder(DyeColors::class.java, "FriendColor").withValue(DyeColors.RAINBOW).withVisibility { page.value == Page.COLOR }.build())
    private val colorPassive = register(Settings.enumBuilder(DyeColors::class.java, "PassiveMobColor").withValue(DyeColors.GREEN).withVisibility { page.value == Page.COLOR }.build())
    private val colorNeutral = register(Settings.enumBuilder(DyeColors::class.java, "NeutralMobColor").withValue(DyeColors.YELLOW).withVisibility { page.value == Page.COLOR }.build())
    private val colorHostile = register(Settings.enumBuilder(DyeColors::class.java, "HostileMobColor").withValue(DyeColors.RED).withVisibility { page.value == Page.COLOR }.build())

    /* General rendering settings */
    private val rangedColor = register(Settings.booleanBuilder("RangedColor").withValue(true).withVisibility { page.value == Page.RENDERING }.build())
    private val colorChangeRange = register(Settings.integerBuilder("ColorChangeRange").withValue(16).withRange(1, 128).withVisibility { page.value == Page.RENDERING && rangedColor.value }.build())
    private val playerOnly = register(Settings.booleanBuilder("PlayerOnly").withValue(true).withVisibility { page.value == Page.RENDERING && rangedColor.value }.build())
    private val colorFar = register(Settings.enumBuilder(DyeColors::class.java, "FarColor").withValue(DyeColors.WHITE).withVisibility { page.value == Page.COLOR }.build())
    private val aFar = register(Settings.integerBuilder("FarAlpha").withValue(127).withRange(0, 255).withStep(1).withVisibility { page.value == Page.RENDERING && rangedColor.value }.build())
    private val a = register(Settings.integerBuilder("TracerAlpha").withValue(255).withRange(0, 255).withStep(1).withVisibility { page.value == Page.RENDERING }.build())
    private val yOffset = register(Settings.integerBuilder("yOffsetPercentage").withValue(0).withRange(0, 100).withStep(5).withVisibility { page.value == Page.RENDERING }.build())
    private val thickness = register(Settings.floatBuilder("LineThickness").withValue(2.0f).withRange(0.25f, 5.0f).withStep(0.25f).withVisibility { page.value == Page.RENDERING }.build())

    private enum class Page {
        ENTITY_TYPE, COLOR, RENDERING
    }

    private var renderList = ConcurrentHashMap<Entity, Pair<ColorHolder, Float>>() /* <Entity, <RGBAColor, AlphaMultiplier>> */
    private var cycler = HueCycler(600)
    private val renderer = ESPRenderer()

    init {
        listener<RenderWorldEvent> {
            renderer.aTracer = a.value
            renderer.thickness = thickness.value
            renderer.tracerOffset = yOffset.value
            for ((entity, pair) in renderList) {
                val rgba = pair.first.clone()
                rgba.a = (rgba.a * pair.second).toInt()
                renderer.add(entity, rgba)
            }
            renderer.render(true)
        }

        listener<SafeTickEvent> {
            cycler++
            alwaysListening = renderList.isNotEmpty()

            val player = arrayOf(players.value, friends.value, sleeping.value)
            val mob = arrayOf(mobs.value, passive.value, neutral.value, hostile.value)
            val entityList = if (isEnabled) {
                getTargetList(player, mob, invisible.value, range.value.toFloat(), ignoreSelf = false)
            } else {
                ArrayList()
            }

            val cacheMap = HashMap<Entity, Pair<ColorHolder, Float>>()
            for (entity in entityList) {
                cacheMap[entity] = Pair(getColor(entity), 0f)
            }

            for ((entity, pair) in renderList) {
                cacheMap.computeIfPresent(entity) { _, cachePair -> Pair(cachePair.first, min(pair.second + 0.075f, 1f)) }
                cacheMap.computeIfAbsent(entity) { Pair(getColor(entity), pair.second - 0.05f) }
                if (pair.second < 0f) cacheMap.remove(entity)
            }
            renderList.clear()
            renderList.putAll(cacheMap)
        }
    }

    private fun getColor(entity: Entity): ColorHolder {
        val color = (when {
            FriendManager.isFriend(entity.name) -> colorFriend.value
            entity is EntityPlayer -> colorPlayer.value
            EntityUtils.isPassiveMob(entity) -> colorPassive.value
            EntityUtils.isCurrentlyNeutral(entity) -> colorNeutral.value
            else -> colorHostile.value
        } as DyeColors).color

        return if (color == DyeColors.RAINBOW.color) {
            getRangedColor(entity, cycler.currentRgba(a.value))
        } else {
            color.a = a.value
            getRangedColor(entity, color)
        }
    }

    private fun getRangedColor(entity: Entity, rgba: ColorHolder): ColorHolder {
        if (!rangedColor.value || playerOnly.value && entity !is EntityPlayer) return rgba
        val distance = mc.player.getDistance(entity)
        val colorFar = (colorFar.value as DyeColors).color
        colorFar.a = aFar.value
        val r = convertRange(distance, 0f, colorChangeRange.value.toFloat(), rgba.r.toFloat(), colorFar.r.toFloat()).toInt()
        val g = convertRange(distance, 0f, colorChangeRange.value.toFloat(), rgba.g.toFloat(), colorFar.g.toFloat()).toInt()
        val b = convertRange(distance, 0f, colorChangeRange.value.toFloat(), rgba.b.toFloat(), colorFar.b.toFloat()).toInt()
        val a = convertRange(distance, 0f, colorChangeRange.value.toFloat(), a.value.toFloat(), colorFar.a.toFloat()).toInt()
        return ColorHolder(r, g, b, a)
    }
}

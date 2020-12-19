package me.zeroeightsix.kami.module.modules.render

import me.zeroeightsix.kami.KamiMod
import me.zeroeightsix.kami.event.events.ChunkEvent
import me.zeroeightsix.kami.event.events.RenderWorldEvent
import me.zeroeightsix.kami.event.events.SafeTickEvent
import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.setting.Setting.SettingListeners
import me.zeroeightsix.kami.setting.Settings
import me.zeroeightsix.kami.util.EntityUtils.getInterpolatedPos
import me.zeroeightsix.kami.util.TimerUtils
import me.zeroeightsix.kami.util.color.ColorHolder
import me.zeroeightsix.kami.util.graphics.GlStateUtils
import me.zeroeightsix.kami.util.graphics.KamiTessellator
import me.zeroeightsix.kami.util.text.MessageSendHelper
import net.minecraft.client.Minecraft
import net.minecraft.world.chunk.Chunk
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.apache.commons.lang3.SystemUtils
import org.kamiblue.event.listener.listener
import org.lwjgl.opengl.GL11.GL_LINE_LOOP
import org.lwjgl.opengl.GL11.glLineWidth
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sqrt

@Module.Info(
        name = "NewChunks",
        description = "Highlights newly generated chunks",
        category = Module.Category.RENDER
)
object NewChunks : Module() {
    private val relative = register(Settings.b("Relative", true))
    private val autoClear = register(Settings.b("AutoClear", true))
    private val saveNewChunks = register(Settings.b("SaveNewChunks", false))
    private val saveOption = register(Settings.enumBuilder(SaveOption::class.java, "SaveOption").withValue(SaveOption.EXTRA_FOLDER).withVisibility { saveNewChunks.value })
    private val saveInRegionFolder = register(Settings.booleanBuilder("InRegion").withValue(false).withVisibility { saveNewChunks.value })
    private val alsoSaveNormalCoords = register(Settings.booleanBuilder("SaveNormalCoords").withValue(false).withVisibility { saveNewChunks.value })
    private val closeFile = register(Settings.booleanBuilder("CloseFile").withValue(false).withVisibility { saveNewChunks.value })
    private val renderMode = register(Settings.e<RenderMode>("RenderMode", RenderMode.BOTH))
    private val yOffset = register(Settings.integerBuilder("YOffset").withValue(0).withRange(-256, 256).withStep(4).withVisibility { isWorldMode })
    private val customColor = register(Settings.booleanBuilder("CustomColor").withValue(false).withVisibility { isWorldMode })
    private val red = register(Settings.integerBuilder("Red").withRange(0, 255).withValue(255).withStep(1).withVisibility { customColor.value && isWorldMode })
    private val green = register(Settings.integerBuilder("Green").withRange(0, 255).withValue(255).withStep(1).withVisibility { customColor.value && isWorldMode })
    private val blue = register(Settings.integerBuilder("Blue").withRange(0, 255).withValue(255).withStep(1).withVisibility { customColor.value && isWorldMode })
    private val range = register(Settings.integerBuilder("RenderRange").withValue(256).withRange(64, 1024).withStep(64))
    val radarScale = register(Settings.doubleBuilder("RadarScale").withRange(1.0, 10.0).withValue(2.0).withStep(0.1).withVisibility { isRadarMode })
    private val removeMode = register(Settings.e<RemoveMode>("RemoveMode", RemoveMode.MAX_NUM))
    private val maxNum = register(Settings.integerBuilder("MaxNum").withRange(1000, 100_000).withValue(10_000).withStep(1000).withVisibility { removeMode.value == RemoveMode.MAX_NUM })

    private var lastSetting = LastSetting()
    private var logWriter: PrintWriter? = null
    private val timer = TimerUtils.TickTimer(TimerUtils.TimeUnit.MINUTES)
    val chunks = HashSet<Chunk>()

    override fun onDisable() {
        logWriterClose()
        chunks.clear()
        MessageSendHelper.sendChatMessage("$chatName Saved and cleared chunks!")
    }

    override fun onEnable() {
        timer.reset()
    }

    init {
        listener<SafeTickEvent> {
            if (it.phase == TickEvent.Phase.END && autoClear.value && timer.tick(10L)) {
                chunks.clear()
                MessageSendHelper.sendChatMessage("$chatName Cleared chunks!")
            }
        }

        listener<RenderWorldEvent> {
            if (renderMode.value == RenderMode.RADAR) return@listener
            val y = yOffset.value.toDouble() + if (relative.value) getInterpolatedPos(mc.player, KamiTessellator.pTicks()).y else 0.0
            glLineWidth(2.0f)
            GlStateUtils.depth(false)
            val color = if (customColor.value) ColorHolder(red.value, green.value, blue.value) else ColorHolder(155, 144, 255)
            val buffer = KamiTessellator.buffer
            for (chunk in chunks) {
                if (sqrt(chunk.pos.getDistanceSq(mc.player)) > range.value) continue
                KamiTessellator.begin(GL_LINE_LOOP)
                buffer.pos(chunk.pos.xStart.toDouble(), y, chunk.pos.zStart.toDouble()).color(color.r, color.g, color.b, 255).endVertex()
                buffer.pos(chunk.pos.xEnd + 1.toDouble(), y, chunk.pos.zStart.toDouble()).color(color.r, color.g, color.b, 255).endVertex()
                buffer.pos(chunk.pos.xEnd + 1.toDouble(), y, chunk.pos.zEnd + 1.toDouble()).color(color.r, color.g, color.b, 255).endVertex()
                buffer.pos(chunk.pos.xStart.toDouble(), y, chunk.pos.zEnd + 1.toDouble()).color(color.r, color.g, color.b, 255).endVertex()
                KamiTessellator.render()
            }
            GlStateUtils.depth(true)
        }

        listener<ChunkEvent> {
            if (it.packet.isFullChunk) return@listener
            chunks.add(it.chunk)
            if (saveNewChunks.value) saveNewChunk(it.chunk)
            if (removeMode.value == RemoveMode.MAX_NUM && chunks.size > maxNum.value) {
                var removeChunk = chunks.first()
                var maxDist = Double.MIN_VALUE
                chunks.forEach { c ->
                    if (c.pos.getDistanceSq(mc.player) > maxDist) {
                        maxDist = c.pos.getDistanceSq(mc.player)
                        removeChunk = c
                    }
                }
                chunks.remove(removeChunk)
            }
        }

        listener<net.minecraftforge.event.world.ChunkEvent.Unload> {
            if (removeMode.value == RemoveMode.UNLOAD)
                chunks.remove(it.chunk)
        }
    }

    // needs to be synchronized so no data gets lost
    private fun saveNewChunk(chunk: Chunk) {
        saveNewChunk(testAndGetLogWriter(), getNewChunkInfo(chunk))
    }

    private fun getNewChunkInfo(chunk: Chunk): String {
        var rV = String.format("%d,%d,%d", System.currentTimeMillis(), chunk.x, chunk.z)
        if (alsoSaveNormalCoords.value) {
            rV += String.format(",%d,%d", chunk.x * 16 + 8, chunk.z * 16 + 8)
        }
        return rV
    }

    private fun testAndGetLogWriter(): PrintWriter? {
        if (lastSetting.testChangeAndUpdate()) {
            logWriterClose()
            logWriterOpen()
        }
        return logWriter
    }

    private fun logWriterClose() {
        if (logWriter != null) {
            logWriter!!.close()
            logWriter = null
            lastSetting = LastSetting() // what if the settings stay the same?
        }
    }

    private fun logWriterOpen() {
        val filepath = path.toString()
        try {
            logWriter = PrintWriter(BufferedWriter(FileWriter(filepath, true)), true)
            var head = "timestamp,ChunkX,ChunkZ"
            if (alsoSaveNormalCoords.value) {
                head += ",x coordinate,z coordinate"
            }
            logWriter!!.println(head)
        } catch (e: Exception) {
            e.printStackTrace()
            KamiMod.LOG.error(chatName + " some exception happened when trying to start the logging -> " + e.message)
            MessageSendHelper.sendErrorMessage(chatName + " onLogStart: " + e.message)
        }
    }

    private val path: Path
        get() {
            // code from baritone (https://github.com/cabaletta/baritone/blob/master/src/main/java/baritone/cache/WorldProvider.java)

            var file: File? = null
            val dimension = mc.player.dimension

            // If there is an integrated server running (Aka Singleplayer) then do magic to find the world save file
            if (mc.isSingleplayer) {
                try {
                    file = mc.integratedServer?.getWorld(dimension)?.chunkSaveLocation
                } catch (e: Exception) {
                    e.printStackTrace()
                    KamiMod.LOG.error("some exception happened when getting canonicalFile -> " + e.message)
                    MessageSendHelper.sendErrorMessage(chatName + " onGetPath: " + e.message)
                }

                // Gets the "depth" of this directory relative the the game's run directory, 2 is the location of the world
                if (file?.toPath()?.relativize(mc.gameDir.toPath())?.nameCount != 2) {
                    // subdirectory of the main save directory for this world
                    file = file?.parentFile
                }
            } else { // Otherwise, the server must be remote...
                file = makeMultiplayerDirectory().toFile()
            }

            // We will actually store the world data in a subfolder: "DIM<id>"
            if (dimension != 0) { // except if it's the overworld
                file = File(file, "DIM$dimension")
            }

            // maybe we want to save it in region folder
            if (saveInRegionFolder.value) {
                file = File(file, "region")
            }
            file = File(file, "newChunkLogs")
            val date = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Date())
            file = File(file, mc.getSession().username + "_" + date + ".csv") // maybe dont safe the name actually. But I also dont want to make another option...
            val rV = file.toPath()
            try {
                if (!Files.exists(rV)) { // ovsly always...
                    Files.createDirectories(rV.parent)
                    Files.createFile(rV)
                }
            } catch (e: IOException) {
                e.printStackTrace()
                KamiMod.LOG.error("some exception happened when trying to make the file -> " + e.message)
                MessageSendHelper.sendErrorMessage(chatName + " onCreateFile: " + e.message)
            }
            return rV
        }

    private fun makeMultiplayerDirectory(): Path {
        var rV = Minecraft.getMinecraft().gameDir
        var folderName: String
        when (saveOption.value) {
            SaveOption.LITE_LOADER_WDL -> {
                folderName = mc.currentServerData?.serverName ?: "Offline"
                rV = File(rV, "saves")
                rV = File(rV, folderName)
            }
            SaveOption.NHACK_WDL -> {
                folderName = nHackInetName
                rV = File(rV, "config")
                rV = File(rV, "wdl-saves")
                rV = File(rV, folderName)

                // extra because name might be different
                if (!rV.exists()) {
                    MessageSendHelper.sendWarningMessage("$chatName nhack wdl directory doesnt exist: $folderName")
                    MessageSendHelper.sendWarningMessage("$chatName creating the directory now. It is recommended to update the ip")
                }
            }
            else -> {
                folderName = mc.currentServerData?.serverName + "-" + mc.currentServerData?.serverIP
                if (SystemUtils.IS_OS_WINDOWS) {
                    folderName = folderName.replace(":", "_")
                }
                rV = File(rV, "KAMI_NewChunks")
                rV = File(rV, folderName)
            }
        }
        return rV.toPath()
    }

    // if there is no port then we have to manually include the standard port..
    private val nHackInetName: String
        get() {
            var folderName = mc.currentServerData?.serverIP ?: "Offline"
            if (SystemUtils.IS_OS_WINDOWS) {
                folderName = folderName.replace(":", "_")
            }
            if (hasNoPort(folderName)) {
                folderName += "_25565" // if there is no port then we have to manually include the standard port..
            }
            return folderName
        }

    private fun hasNoPort(ip: String): Boolean {
        if (!ip.contains("_")) {
            return true
        }
        val sp = ip.split("_").toTypedArray()
        val ending = sp[sp.size - 1]
        // if it is numeric it means it might be a port...
        return ending.toIntOrNull() != null
    }

    private fun saveNewChunk(log: PrintWriter?, data: String) {
        log!!.println(data)
    }

    private enum class SaveOption {
        EXTRA_FOLDER, LITE_LOADER_WDL, NHACK_WDL
    }

    @Suppress("unused")
    private enum class RemoveMode {
        UNLOAD, MAX_NUM, NEVER
    }

    enum class RenderMode {
        WORLD, RADAR, BOTH
    }

    val isRadarMode get() = renderMode.value == RenderMode.BOTH || renderMode.value == RenderMode.RADAR
    private val isWorldMode get() = renderMode.value == RenderMode.BOTH || renderMode.value == RenderMode.WORLD

    private class LastSetting {
        var lastSaveOption: SaveOption? = null
        var lastInRegion = false
        var lastSaveNormal = false
        var dimension = 0
        var ip: String? = null
        fun testChangeAndUpdate(): Boolean {
            if (testChange()) {
                // so we dont have to do this process again next time
                update()
                return true
            }
            return false
        }

        fun testChange(): Boolean {
            // these somehow include the test whether its null
            return saveOption.value != lastSaveOption
                    || saveInRegionFolder.value != lastInRegion
                    || alsoSaveNormalCoords.value != lastSaveNormal
                    || dimension != mc.player.dimension
                    || mc.currentServerData?.serverIP != ip
        }

        private fun update() {
            lastSaveOption = saveOption.value as SaveOption
            lastInRegion = saveInRegionFolder.value
            lastSaveNormal = alsoSaveNormalCoords.value
            dimension = mc.player.dimension
            ip = mc.currentServerData?.serverIP
        }
    }

    init {
        closeFile.settingListener = SettingListeners {
            if (closeFile.value) {
                logWriterClose()
                MessageSendHelper.sendChatMessage("$chatName Saved file!")
                closeFile.value = false
            }
        }
    }
}
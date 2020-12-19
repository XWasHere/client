package me.zeroeightsix.kami.module.modules.misc

import me.zeroeightsix.kami.event.events.PacketEvent
import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.util.text.MessageSendHelper
import net.minecraft.item.ItemWrittenBook
import net.minecraft.network.play.client.CPacketClickWindow
import org.kamiblue.event.listener.listener

/**
 * @author IronException
 * Used with permission from ForgeHax
 * https://github.com/fr1kin/ForgeHax/blob/bb522f8/src/main/java/com/matt/forgehax/mods/AntiBookKick.java
 * Permission (and ForgeHax is MIT licensed):
 * https://discordapp.com/channels/573954110454366214/634010802403409931/693919755647844352
 */
@Module.Info(
        name = "AntiBookKick",
        category = Module.Category.MISC,
        description = "Prevents being kicked by clicking on books",
        showOnArray = Module.ShowOnArray.OFF
)
object AntiBookKick : Module() {
    init {
        listener<PacketEvent.PostSend> {
            if (it.packet !is CPacketClickWindow) return@listener
            if (it.packet.clickedItem.getItem() !is ItemWrittenBook) return@listener

            it.cancel()
            MessageSendHelper.sendWarningMessage(chatName
                    + " Don't click the book \""
                    + it.packet.clickedItem.displayName
                    + "\", shift click it instead!")
            mc.player.openContainer.slotClick(it.packet.slotId, it.packet.usedButton, it.packet.clickType, mc.player)
        }
    }
}
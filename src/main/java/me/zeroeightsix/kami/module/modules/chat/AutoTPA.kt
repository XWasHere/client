package me.zeroeightsix.kami.module.modules.chat

import me.zeroeightsix.kami.event.events.PacketEvent
import me.zeroeightsix.kami.manager.managers.FriendManager
import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.setting.Settings
import me.zeroeightsix.kami.util.text.MessageDetectionHelper.detect
import me.zeroeightsix.kami.util.text.MessageSendHelper
import me.zeroeightsix.kami.util.text.Regexes
import net.minecraft.network.play.server.SPacketChat
import org.kamiblue.event.listener.listener

@Module.Info(
        name = "AutoTPA",
        description = "Automatically accept or decline /TPAs",
        category = Module.Category.CHAT
)
object AutoTPA : Module() {
    private val friends = register(Settings.b("AlwaysAcceptFriends", true))
    private val mode = register(Settings.e<Mode>("Response", Mode.DENY))

    private enum class Mode {
        ACCEPT, DENY
    }

    init {
        listener<PacketEvent.Receive> {
            if (it.packet !is SPacketChat || !it.packet.getChatComponent().unformattedText.detect(true, Regexes.TPA_REQUEST)) return@listener
            /* I tested that getting the first word is compatible with chat timestamp, and it as, as this is Receive and chat timestamp is after Receive */
            val name = it.packet.getChatComponent().unformattedText.split(" ").toTypedArray()[0]

            when (mode.value) {
                Mode.ACCEPT -> MessageSendHelper.sendServerMessage("/tpaccept $name")
                Mode.DENY -> {
                    if (friends.value && FriendManager.isFriend(name)) {
                        MessageSendHelper.sendServerMessage("/tpaccept $name")
                    } else {
                        MessageSendHelper.sendServerMessage("/tpdeny $name")
                    }
                }
                else -> {
                    // do nothing
                }
            }
        }
    }
}
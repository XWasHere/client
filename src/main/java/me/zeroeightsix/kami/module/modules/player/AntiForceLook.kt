package me.zeroeightsix.kami.module.modules.player

import me.zeroeightsix.kami.event.events.PacketEvent
import me.zeroeightsix.kami.mixin.extension.rotationPitch
import me.zeroeightsix.kami.mixin.extension.rotationYaw
import me.zeroeightsix.kami.module.Module
import net.minecraft.network.play.server.SPacketPlayerPosLook
import org.kamiblue.event.listener.listener

@Module.Info(
        name = "AntiForceLook",
        category = Module.Category.PLAYER,
        description = "Stops server packets from turning your head"
)

object AntiForceLook : Module() {
    init {
        listener<PacketEvent.Receive> {
            if (it.packet !is SPacketPlayerPosLook || mc.player == null) return@listener
            it.packet.rotationYaw = mc.player.rotationYaw
            it.packet.rotationPitch = mc.player.rotationPitch
        }
    }
}
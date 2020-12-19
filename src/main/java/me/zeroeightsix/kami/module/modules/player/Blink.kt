package me.zeroeightsix.kami.module.modules.player

import me.zeroeightsix.kami.event.events.ConnectionEvent
import me.zeroeightsix.kami.event.events.PacketEvent
import me.zeroeightsix.kami.event.events.SafeTickEvent
import me.zeroeightsix.kami.mixin.extension.x
import me.zeroeightsix.kami.mixin.extension.y
import me.zeroeightsix.kami.mixin.extension.z
import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.setting.Settings
import net.minecraft.client.entity.EntityOtherPlayerMP
import net.minecraft.network.play.client.CPacketPlayer
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.kamiblue.event.listener.listener
import java.util.*

@Module.Info(
        name = "Blink",
        category = Module.Category.PLAYER,
        description = "Cancels server side packets"
)
object Blink : Module() {
    private val cancelPacket = register(Settings.b("CancelPackets", false))
    private val autoReset = register(Settings.b("AutoReset", true))
    private val resetThreshold = register(Settings.integerBuilder("ResetThreshold").withValue(20).withRange(1, 100).withVisibility { autoReset.value })

    private const val ENTITY_ID = -114514
    private val packets = LinkedList<CPacketPlayer>()
    private var clonedPlayer: EntityOtherPlayerMP? = null
    private var sending = false

    init {
        listener<PacketEvent.Send> {
            if (!sending && it.packet is CPacketPlayer) {
                it.cancel()
                packets.add(it.packet)
            }
        }

        listener<SafeTickEvent> {
            if (it.phase != TickEvent.Phase.END) return@listener
            if (autoReset.value && packets.size >= resetThreshold.value) {
                end()
                begin()
            }
        }

        listener<ConnectionEvent.Disconnect> {
            mc.addScheduledTask {
                packets.clear()
                clonedPlayer = null
            }
        }
    }

    override fun onEnable() {
        begin()
    }

    override fun onDisable() {
        end()
    }

    private fun begin() {
        if (mc.player == null) return
        clonedPlayer = EntityOtherPlayerMP(mc.world, mc.session.profile).apply {
            copyLocationAndAnglesFrom(mc.player)
            rotationYawHead = mc.player.rotationYawHead
            inventory.copyInventory(mc.player.inventory)
            noClip = true
        }.also {
            mc.world.addEntityToWorld(ENTITY_ID, it)
        }
    }

    private fun end() {
        mc.addScheduledTask {
            val player = mc.player
            val connection = mc.connection
            if (player == null || connection == null) return@addScheduledTask

            if (cancelPacket.value || mc.connection == null) {
                packets.peek()?.let { player.setPosition(it.x, it.y, it.z) }
                packets.clear()
            } else {
                sending = true
                while (packets.isNotEmpty()) connection.sendPacket(packets.poll())
                sending = false
            }

            clonedPlayer?.setDead()
            mc.world?.removeEntityFromWorld(ENTITY_ID)
            clonedPlayer = null
        }
    }

    override fun getHudInfo(): String {
        return packets.size.toString()
    }
}
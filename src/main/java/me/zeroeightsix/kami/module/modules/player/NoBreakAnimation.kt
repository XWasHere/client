package me.zeroeightsix.kami.module.modules.player

import me.zeroeightsix.kami.event.events.PacketEvent
import me.zeroeightsix.kami.event.events.SafeTickEvent
import me.zeroeightsix.kami.module.Module
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityEnderCrystal
import net.minecraft.network.play.client.CPacketPlayerDigging
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import org.kamiblue.event.listener.listener

@Module.Info(
        name = "NoBreakAnimation",
        category = Module.Category.PLAYER,
        description = "Prevents block break animation server side"
)
object NoBreakAnimation : Module() {
    private var isMining = false
    private var lastPos: BlockPos? = null
    private var lastFacing: EnumFacing? = null

    init {
        // Lower priority so we process the packet at the last
        listener<PacketEvent.Send>(500) {
            if (it.packet !is CPacketPlayerDigging) return@listener
            // skip crystals and living entities
            for (entity in mc.world.getEntitiesWithinAABBExcludingEntity(null, AxisAlignedBB(it.packet.position))) {
                if (entity is EntityEnderCrystal || entity is EntityLivingBase) {
                    resetMining()
                    return@listener
                }
            }
            if (it.packet.action == CPacketPlayerDigging.Action.START_DESTROY_BLOCK) {
                isMining = true
                lastPos = it.packet.position
                lastFacing = it.packet.facing
            }
            if (it.packet.action == CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK) {
                resetMining()
            }
        }

        listener<SafeTickEvent> {
            if (!mc.gameSettings.keyBindAttack.isKeyDown) {
                resetMining()
                return@listener
            }
            if (isMining) {
                lastPos?.let { lastPos ->
                    lastFacing?.let { lastFacing ->
                        mc.player.connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.ABORT_DESTROY_BLOCK, lastPos, lastFacing))
                    }
                }
            }
        }
    }

    private fun resetMining() {
        isMining = false
        lastPos = null
        lastFacing = null
    }
}
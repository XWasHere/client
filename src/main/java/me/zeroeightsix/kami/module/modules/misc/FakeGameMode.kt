package me.zeroeightsix.kami.module.modules.misc

import me.zeroeightsix.kami.event.events.SafeTickEvent
import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.setting.ModuleConfig.setting
import me.zeroeightsix.kami.util.event.listener
import net.minecraft.world.GameType

@Module.Info(
    name = "FakeGameMode",
    description = "Fakes your current gamemode client side",
    category = Module.Category.MISC
)
object FakeGameMode : Module() {
    private val gamemode = setting("Mode", GameMode.CREATIVE)

    @Suppress("UNUSED")
    private enum class GameMode(val gameType: GameType) {
        SURVIVAL(GameType.SURVIVAL),
        CREATIVE(GameType.CREATIVE),
        ADVENTURE(GameType.ADVENTURE),
        SPECTATOR(GameType.SPECTATOR)
    }

    private var prevGameMode: GameType? = null

    init {
        listener<SafeTickEvent> {
            mc.playerController.setGameType(gamemode.value.gameType)
        }
    }

    override fun onEnable() {
        if (mc.player == null) disable()
        else prevGameMode = mc.playerController.currentGameType
    }

    override fun onDisable() {
        if (mc.player != null) prevGameMode?.let { mc.playerController.setGameType(it) }
    }
}
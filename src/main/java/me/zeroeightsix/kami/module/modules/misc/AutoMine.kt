package me.zeroeightsix.kami.module.modules.misc

import me.zeroeightsix.kami.command.Command
import me.zeroeightsix.kami.event.events.BaritoneCommandEvent
import me.zeroeightsix.kami.event.events.ConnectionEvent
import me.zeroeightsix.kami.event.events.SafeTickEvent
import me.zeroeightsix.kami.mixin.extension.sendClickBlockToController
import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.setting.ModuleConfig.setting
import me.zeroeightsix.kami.util.BaritoneUtils
import me.zeroeightsix.kami.util.event.listener
import me.zeroeightsix.kami.util.text.MessageSendHelper

@Module.Info(
    name = "AutoMine",
    description = "Automatically mines chosen ores",
    category = Module.Category.MISC
)
object AutoMine : Module() {

    private val manual = setting("Manual", false)
    private val iron = setting("Iron", false)
    private val diamond = setting("Diamond", false)
    private val gold = setting("Gold", false)
    private val coal = setting("Coal", false)
    private val log = setting("Logs", false)

    override fun onEnable() {
        if (mc.player == null) {
            disable()
        } else {
            run()
        }
    }

    override fun onDisable() {
        BaritoneUtils.cancelEverything()
    }

    private fun run() {
        if (mc.player == null || isDisabled || manual.value) return

        val blocks = ArrayList<String>()

        if (iron.value) blocks.add("iron_ore")
        if (diamond.value) blocks.add("diamond_ore")
        if (gold.value) blocks.add("gold_ore")
        if (coal.value) blocks.add("coal_ore")
        if (log.value) {
            blocks.add("log")
            blocks.add("log2")
        }

        if (blocks.isEmpty()) {
            MessageSendHelper.sendBaritoneMessage("Error: you have to choose at least one thing to mine. To mine custom blocks run the &7" + Command.getCommandPrefix() + "b mine block&f command")
            BaritoneUtils.cancelEverything()
            return
        }

        MessageSendHelper.sendBaritoneCommand("mine", *blocks.toTypedArray())
    }

    init {
        listener<SafeTickEvent> {
            if (manual.value) {
                mc.sendClickBlockToController(true)
            }
        }

        listener<ConnectionEvent.Disconnect> {
            disable()
        }

        listener<BaritoneCommandEvent> { event ->
            if (event.command.names.any { it.contains("cancel") }) {
                disable()
            }
        }

        with({ run() }) {
            iron.listeners.add(this)
            diamond.listeners.add(this)
            gold.listeners.add(this)
            coal.listeners.add(this)
            log.listeners.add(this)
        }
    }
}

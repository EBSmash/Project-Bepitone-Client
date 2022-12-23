package com.lambda.modules

import baritone.api.BaritoneAPI
import baritone.api.utils.BetterBlockPos
import com.lambda.ExamplePlugin
import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.event.events.GuiEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.module.Category
import com.lambda.client.plugin.api.PluginModule
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraft.block.BlockAir
import net.minecraft.block.BlockObsidian
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiDisconnected
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.lang.Integer.parseInt
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Deque
import java.util.LinkedList
import java.util.Queue
import kotlin.collections.LinkedHashSet
import kotlin.concurrent.thread

internal object Scanner : PluginModule(
    name = "Bepitone Scanner",
    category = Category.MISC,
    description = "X3 owo",
    pluginMain = ExamplePlugin
) {

    private var queue : Deque<LinkedHashSet<BlockPos>> = LinkedList()

    private var currentFile = 0
    private var username = ""
    private var finishedWithFile = false
    private fun requestNewFile() {
        thread {
            try {
                val url = URL("http://${Breaker.url}/scan/$currentFile/$username")
                queue.clear()
                val connection = url.openConnection()
                BufferedReader(InputStreamReader(connection.getInputStream())).use { inp ->
                    var line: String?
                    var fileFirstLine = true
                    while (inp.readLine().also { line = it } != null) {
                        if (fileFirstLine) {
                            fileFirstLine = false
                            if (line.toString().contains("DISABLE")) {
                                disable()
                            }
                            if (line.toString().contains(".")) {
                                currentFile = line.toString().split(".")[0].toInt()
                            } else {
                                currentFile = line.toString().toInt()
                            }
                        } else {
                            if(line.toString() == "") {
                                return@use
                            } else {
                                val queueBuffer: LinkedHashSet<BlockPos> = LinkedHashSet()
                                 for (coordBuffer in line.toString().split("#")) {
                                     queueBuffer.add(BlockPos(coordBuffer.split(" ")[0].toInt(),
                                         255,
                                         coordBuffer.split(" ")[1].toInt()))
                                 }
                                 queue.add(queueBuffer)
                            }
                        }
                    }
                    finishedWithFile = true
                }
            } catch (_: ConnectException) {
                MessageSendHelper.sendChatMessage("Incorrect IP")
                disable()
            } catch (_: IOException) {
                MessageSendHelper.sendChatMessage("oopsy poopsy")
                disable()
            }
        }
    }

    private fun negPosCheck(fileNum : Int) :Int {
        if (fileNum % 2 == 0) {
            return 1
        }
        return 0
    }
    init {
        onEnable {
            currentFile = negPosCheck(mc.player.posZ.toInt())
            if (Breaker.isEnabled) {
                MessageSendHelper.sendChatMessage("Please disable Bepitone Breaker before using scanner.")
                disable()
            }
        }
        safeListener<TickEvent.ClientTickEvent> {
            if (username != mc.player.displayNameString) {
                username = mc.player.displayNameString
            }
        }
        onDisable {
        }
    }
    private fun disconnectHook () {

    }
}

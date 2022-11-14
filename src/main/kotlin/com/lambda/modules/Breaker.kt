package com.lambda.modules

import baritone.api.BaritoneAPI
import com.lambda.ExamplePlugin
import com.lambda.client.module.Category
import com.lambda.client.plugin.api.PluginModule
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.Timer
import com.lambda.client.util.items.swapToItem
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraft.init.Blocks
import net.minecraft.init.Items
import net.minecraft.network.play.client.CPacketPlayerDigging
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.lang.Integer.parseInt
import java.net.ConnectException
import java.net.URL
import java.util.*


internal object Breaker : PluginModule(name = "BepitoneBreaker", category = Category.MISC, description = "", pluginMain = ExamplePlugin) {
    var queue: Queue<String> = PriorityQueue<String>()
    var list: ArrayList<String> = ArrayList()

    private val miningTimer = TickTimer(TimeUnit.MILLISECONDS)

    private var lastHitVec: Vec3d? = null

    var x = 0
    var z = 0

    var traveling = false

    var baritonePaused = false

    var shouldBreak = false

    var empty = false;

    var state = 0

    var broken = true


    private val server by setting("Bepitone API Server IP", "Unchanged")

    init {

        onEnable {
            state = 0;
            BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("allowplace false")
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (state == 0) {
                //sent get req
                try {
                    val url = URL("https://$server:8000/assign")
                    val connection = url.openConnection()
                    BufferedReader(InputStreamReader(connection.getInputStream())).use { inp ->
                        var line: String?
                        //for each line
                        while (inp.readLine().also { line = it } != null) {
                            //debug print
//                        MessageSendHelper.sendChatMessage(line.toString())

                            if (line.toString() == "") {
                                empty = true;
                                break
                            } else {
                                empty = false;
                            }

                            if (!empty) {
                                queue.add(line.toString())
                            }
                        }
                        if (empty) {
                            state = 0
                            return@safeListener
                        } else {
                            state = 1
                        }
                    }
                } catch (_: ConnectException) {
                    MessageSendHelper.sendErrorMessage("failed to connect to api \n Check that you set the ip. \n if you have Message EBS#2574.")
                    disable()
                } catch (_: IOException) {
                    MessageSendHelper.sendChatMessage("Either Something went very wrong or WE FINSIHEDDD.")
                    disable()
                }
            }
            if (state == 1) {

                if (!queue.isEmpty()) {


                    if (broken) {
                        var coord = queue.poll();

                        if (coord == null) {
                            state = 0
                            return@safeListener
                        }

                        x = parseInt(coord.split(" ")[0])
                        z = parseInt(coord.split(" ")[1])

                        broken = false;
                    }
                    if (mc.world.getBlockState(BlockPos(x, 255, z).south()).block == Blocks.AIR) {
                        shouldBreak = false;
                        traveling = true;
                        broken = true
                    } else {
                        shouldBreak = true;
                    }


                    //goto thingy
                    if (!traveling) {
                        BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("goto $x 256 ${z + 1}")
                        traveling = true
                    }


                    if (!BaritoneAPI.getProvider().primaryBaritone.customGoalProcess.isActive && shouldBreak) {
                        swapToItem(Items.DIAMOND_PICKAXE)
                        if (baritonePaused){
                            BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("pause")
                            connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.START_DESTROY_BLOCK, BlockPos(x, 255, z), EnumFacing.DOWN))
                            baritonePaused = false;
                        }
                        if (miningTimer.tick(2600)) {
                            connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK, BlockPos(x, 255, z), EnumFacing.DOWN))
                            broken = true;
                            traveling = false
                            BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("resume")
                            baritonePaused = true
                        }
                    }


                } else{

                    state = 0
                }
            }
        }
    }

}


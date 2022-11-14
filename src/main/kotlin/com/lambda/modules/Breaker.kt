package com.lambda.modules

import baritone.api.BaritoneAPI
import com.lambda.ExamplePlugin
import com.lambda.client.manager.managers.PlayerPacketManager.sendPlayerPacket
import com.lambda.client.mixin.extension.syncCurrentPlayItem
import com.lambda.client.module.Category
import com.lambda.client.plugin.api.PluginModule
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.items.swapToItem
import com.lambda.client.util.math.RotationUtils.getRotationTo
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import com.lambda.client.util.world.getHitVec
import net.minecraft.init.Blocks
import net.minecraft.init.Items
import net.minecraft.network.play.client.CPacketPlayerDigging
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.RayTraceResult
import net.minecraft.util.math.Vec3d
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.lang.Double.parseDouble
import java.lang.Integer.parseInt
import java.net.ConnectException
import java.net.URL
import java.util.*
import kotlin.collections.ArrayList


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

    val toBreak = ArrayList<BlockPos>()


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
                    val url = URL("http://2.tcp.ngrok.io:17605/assign")
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
                        var line = queue.poll();

                        if (line == null) {
                            state = 0
                            return@safeListener
                        }

                        val list = line.split("#")

                        val coord1 = BlockPos(parseInt(list[0].split(" ")[0]), 255, parseInt(list[0].split(" ")[1]))
                        val coord2 = BlockPos(parseInt(list[1].split(" ")[0]), 255, parseInt(list[1].split(" ")[1]))
                        val coord3 = BlockPos(parseInt(list[2].split(" ")[0]), 255, parseInt(list[2].split(" ")[1]))

                        x = coord2.x
                        z = coord2.z

                        toBreak

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
                        BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("goto ${x+3} 256 ${z + 1}")
                        traveling = true
                    }

                    for (pos in toBreak) {
                        if (!BaritoneAPI.getProvider().primaryBaritone.customGoalProcess.isActive && shouldBreak) {
                            swapToItem(Items.DIAMOND_PICKAXE)

                            if (baritonePaused) {
                                BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("pause")
                                baritonePaused = false;
                            }
                            lastHitVec = getHitVec(pos, EnumFacing.UP)

                            val rotation = lastHitVec?.let { getRotationTo(it) }
                            if (rotation != null) {
                                sendPlayerPacket {
                                    rotate(rotation)
                                }
                            }
                            mc.player.connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.START_DESTROY_BLOCK, pos, EnumFacing.UP))

                            if (miningTimer.tick(10)) {
                                lastHitVec = getHitVec(pos, EnumFacing.UP)

                                val rotation = lastHitVec?.let { getRotationTo(it) }
                                if (rotation != null) {
                                    sendPlayerPacket {
                                        rotate(rotation)
                                    }
                                }

                                mc.player.connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK, pos, EnumFacing.UP))

                                broken = true;
                                traveling = false
                                BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("resume")
                                baritonePaused = true
                            }
                        }
                    }

                } else {
                    BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("sel clear")
                    state = 0
                }
            }
        }
    }

    var didBreakLastTick = false

    fun stopBreakingBlock() {
        mc.playerController.resetBlockRemoving()
        didBreakLastTick = false
    }

    fun tick(isLeftClick: Boolean) {
        val trace: RayTraceResult = mc.objectMouseOver
        val isBlockTrace = trace.typeOfHit == RayTraceResult.Type.BLOCK
        if (isLeftClick && isBlockTrace) {
            if (!didBreakLastTick) {
                mc.playerController.syncCurrentPlayItem()
                mc.playerController.clickBlock(trace.blockPos, trace.sideHit)
                mc.player.swingArm(EnumHand.MAIN_HAND)
            }

            // Attempt to break the block
            if (mc.playerController.onPlayerDamageBlock(trace.blockPos, trace.sideHit)) {
                mc.player.swingArm(EnumHand.MAIN_HAND)
            }
            didBreakLastTick = true
        } else if (didBreakLastTick) {
            stopBreakingBlock()
            didBreakLastTick = false
        }
    }
}


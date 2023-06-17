package dev.andante.companion.api.game

import com.google.gson.GsonBuilder
import dev.andante.companion.api.event.TitleEvents
import dev.andante.companion.api.event.WorldJoinCallback
import dev.andante.companion.api.game.instance.GameInstance
import dev.andante.companion.api.game.type.GameType
import dev.andante.companion.api.helper.FileHelper
import dev.andante.companion.api.scoreboard.ScoreboardAccessor
import dev.andante.companion.api.server.ServerTracker
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.RenderLayer
import net.minecraft.text.Text
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Tracks the active game.
 */
object GameTracker {
    private val LOGGER: Logger = LoggerFactory.getLogger("[MCCI: Companion] Game Tracker")

    /**
     * The active game instance.
     */
    var gameInstance: GameInstance<*>? = null; private set

    /**
     * The current game type.
     */
    val gameType: GameType<*>? get() = gameInstance?.type

    init {
        // register tick event
        ClientTickEvents.END_CLIENT_TICK.register(::tick)

        // register world join event
        WorldJoinCallback.EVENT.register { onJoinWorld() }

        // register chat event
        ClientReceiveMessageEvents.GAME.register { text, overlay -> gameInstance?.onGameMessage(text, overlay) }

        // register title events
        TitleEvents.TITLE.register { text -> gameInstance?.onTitle(text) }
        TitleEvents.SUBTITLE.register { text -> gameInstance?.onSubtitle(text) }

        // debug hud
        HudRenderCallback.EVENT.register(::renderHud)
    }

    private fun tick(client: MinecraftClient) {
        // do not tick if not on mcc island
        if (!ServerTracker.isConnectedToMccIsland) {
            return
        }

        try {
            // check scoreboard objective
            ScoreboardAccessor.getSidebarObjective(client)?.let { objective ->
                val objectiveName = objective.displayName

                // retrieve the text containing the world name
                val worldNameText = objectiveName.siblings.elementAtOrNull(2)
                if (worldNameText != null) {
                    // retrieve the raw world name string
                    val worldName = worldNameText.string

                    // parse to game type
                    val type = GameType.ofScoreboardName(worldName)
                    if (type != null) {
                        if (gameInstance == null || type != gameType) {
                            clearGameInstance()

                            // create instance
                            LOGGER.info("Creating game instance of type ${type.id}")
                            gameInstance = type.createInstance()
                        }
                    } else {
                        clearGameInstance()
                    }
                } else {
                    clearGameInstance()
                }
            }
        } catch (throwable: Throwable) {
            LOGGER.error("Something went wrong ticking the MCCI: Companion game tracker", throwable)
        }
    }

    private fun onJoinWorld() {
        clearGameInstance()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun renderHud(context: DrawContext, tickDelta: Float) {
        if (!ServerTracker.isConnectedToMccIsland || !FabricLoader.getInstance().isDevelopmentEnvironment) {
            return
        }

        // render game instance debug hud
        gameInstance?.let { instance ->
            val textRenderer = MinecraftClient.getInstance().textRenderer

            var largestWidth = 0
            val renderBuffer = mutableListOf<Text>()

            // setup consumer for pass-offs
            val textRendererConsumer: (Text) -> Unit = { text ->
                // add to render buffer
                renderBuffer.add(text)

                // update largest width
                val width = textRenderer.getWidth(text)
                if (width > largestWidth) {
                    largestWidth = width
                }
            }

            // add type
            textRendererConsumer(Text.literal(instance.type.toString()))
            textRendererConsumer(Text.literal(instance::class.simpleName))

            // pass off to instance
            instance.renderDebugHud(textRendererConsumer)

            // render text
            val startX = 10
            val startY = 30

            // render background
            val border = 2
            val backgroundX = startX - border
            val backgroundY = startY - border

            context.fill(RenderLayer.getGuiOverlay(),
                backgroundX, backgroundY,
                backgroundX + largestWidth + 1 + border,
                backgroundY + ((textRenderer.fontHeight + 1) * renderBuffer.size) + border,
                0x7C000000
            )

            // render text from buffer
            var i = 0
            renderBuffer.forEach { text ->
                context.drawTextWithShadow(textRenderer, text, startX, startY + ((textRenderer.fontHeight + 1) * i), 0xFFFFFF)
                i++
            }
        }
    }

    private fun clearGameInstance() {
        val instance = gameInstance
        if (instance != null) {
            LOGGER.info("Clearing active game instance")

            // call instance on remove
            instance.onRemove()

            // flush to json
            val file = FileHelper.companionFile("game_instances/${instance.type.id}/${instance.uuid}.json")
            file.parentFile.mkdirs()
            instance.toJson()?.let { json ->
                val gson = GsonBuilder().setPrettyPrinting().create()
                file.writeText(gson.toJson(json))
            }

            // remove instance
            gameInstance = null
        }
    }
}
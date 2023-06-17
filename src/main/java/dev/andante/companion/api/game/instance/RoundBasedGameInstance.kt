package dev.andante.companion.api.game.instance

import com.google.gson.JsonObject
import dev.andante.companion.api.game.round.Round
import dev.andante.companion.api.game.round.RoundFactory
import dev.andante.companion.api.game.round.RoundManager
import dev.andante.companion.api.game.type.GameType
import dev.andante.companion.api.sound.CompanionSoundManager
import net.minecraft.text.Text

/**
 * An instance of a game type.
 */
@Suppress("LeakingThis")
abstract class RoundBasedGameInstance<R : Round, T : RoundBasedGameInstance<R, T>>(
    /**
     * The type of this instance.
     */
    type: GameType<T>,

    /**
     * The factory to create a round instance.
     */
    roundFactory: RoundFactory<R>
) : GameInstance<T>(type) {
    /**
     * The round manager for this instance.
     */
    open val roundManager: RoundManager<out Round, T> = RoundManager(this, roundFactory)

    override fun onTitle(text: Text) {
        roundManager.onTitle(text)
    }

    override fun onGameMessage(text: Text, overlay: Boolean) {
        roundManager.onGameMessage(text)
    }

    open fun onRoundInitialize(round: Round, isFirstRound: Boolean) {
    }

    open fun onGameStart(round: Round) {
    }

    open fun onRoundStart(round: Round, firstRound: Boolean) {
        CompanionSoundManager.playMusic(type.settings.musicLoopSoundEvent)
    }

    open fun onRoundFinish(round: Round) {
        CompanionSoundManager.stopMusic()
    }

    open fun onGameEnd(round: Round) {
    }

    override fun renderDebugHud(textRendererConsumer: (Text) -> Unit) {
        roundManager.renderDebugHud(textRendererConsumer)
    }

    override fun toJson(): JsonObject {
        val json = JsonObject()
        val roundManagerJson = roundManager.toJson()
        json.add(RoundManager.SERIALIZATION_KEY, roundManagerJson)
        return json
    }
}
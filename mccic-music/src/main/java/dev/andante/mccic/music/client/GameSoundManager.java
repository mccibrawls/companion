package dev.andante.mccic.music.client;

import dev.andante.mccic.api.client.event.MCCIClientRespawnEvent;
import dev.andante.mccic.api.client.event.MCCIGameEvents;
import dev.andante.mccic.api.client.event.MCCISoundPlayEvent;
import dev.andante.mccic.api.client.tracker.GameTracker;
import dev.andante.mccic.api.client.util.SoundFactory;
import dev.andante.mccic.api.game.GameRegistry;
import dev.andante.mccic.api.game.GameState;
import dev.andante.mccic.api.game.Games;
import dev.andante.mccic.api.sound.MCCICSounds;
import dev.andante.mccic.music.MCCICMusic;
import dev.andante.mccic.music.client.config.HITWSoundOnOtherDeath;
import dev.andante.mccic.music.client.config.MusicClientConfig;
import dev.andante.mccic.music.client.sound.VolumeAdjustableSoundInstance;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

import java.util.function.Function;

@Environment(EnvType.CLIENT)
public class GameSoundManager {
    private final GameTracker gameTracker;

    private MinecraftClient client;
    private SoundManager soundManager;

    private SoundInstance lastSound;
    private long lastOvertimeIntroAt;

    public static final Identifier OVERTIME_INTRO_MUSIC_ID = new Identifier("mcc", "games.global.music.overtime_intro_music");
    public static final Identifier OVERTIME_LOOP_MUSIC_ID = new Identifier("mcc", "games.global.music.overtime_loop_music");

    public static final GameSoundManager INSTANCE = new GameSoundManager(GameTracker.INSTANCE);

    public GameSoundManager(GameTracker gameTracker) {
        this.gameTracker = gameTracker;
        this.lastOvertimeIntroAt = -1;

        ClientTickEvents.START_CLIENT_TICK.register(this::tick);
        MCCISoundPlayEvent.EVENT.register(this::onSoundPlay);
        MCCIGameEvents.STATE_UPDATE.register(this::onStateUpdate);
        MCCIClientRespawnEvent.EVENT.register(this::onRespawn);
    }

    private void tick(MinecraftClient client) {
        this.client = client;
        this.soundManager = client.getSoundManager();

        MusicClientConfig config = MusicClientConfig.getConfig();
        if (config.transitionToOvertime()) {
            if (this.lastOvertimeIntroAt != -1 && this.client.world != null) {
                long time = this.client.world.getTime();
                if (time - this.lastOvertimeIntroAt >= config.overtimeTransitionTicks()) {
                    this.lastOvertimeIntroAt = -1;
                    this.stopLastSound();
                }
            }
        } else {
            this.lastOvertimeIntroAt = -1;
        }
    }

    private void onSoundPlay(MCCISoundPlayEvent.Context context) {
        Identifier id = context.getSoundFileIdentifier();
        if (MusicClientConfig.getConfig().transitionToOvertime()) {
             if (id.equals(OVERTIME_INTRO_MUSIC_ID)) {
                if (this.client.world != null) {
                    this.lastOvertimeIntroAt = this.client.world.getTime();
                }
            }
        } else {
            if (id.equals(OVERTIME_LOOP_MUSIC_ID)) {
                this.stopLastSound();
            }
        }
    }

    protected void onStateUpdate(GameState state, GameState oldState) {
        switch (state) {
            case ACTIVE -> this.playCurrent(MusicClientConfig::gameMusicVolume);
            case POST_ROUND_SELF -> {
                MusicClientConfig config = MusicClientConfig.getConfig();

                if (this.gameTracker.getGame().orElse(null) == Games.TGTTOS) {
                    if (config.stopMusicOnChickenHit()) {
                        this.stopLastSound();
                    }
                } else {
                    if (config.stopMusicOnDeath()) {
                        this.stopLastSound();
                    }
                }

                this.soundManager.play(PositionedSoundInstance.master(SoundEvent.of(MCCICSounds.EARLY_ELIMINATION), 1.0f, config.sfxVolume()), 7);
            }
            case POST_ROUND, POST_GAME -> this.stopLastSound();
        }
    }

    protected void onRespawn(ClientPlayerEntity player) {
        if (MusicClientConfig.getConfig().stopMusicOnDeath()) {
            if (!this.gameTracker.getGameState().ends()) {
                this.playCurrent(MusicClientConfig::gameMusicVolumeAfterDeath);
            }
        }
    }

    public void playCurrent(Function<MusicClientConfig, Float> volume) {
        this.gameTracker.getGame().ifPresent(game -> {
            Identifier id = new Identifier(MCCICMusic.MOD_ID, "game.%s".formatted(GameRegistry.INSTANCE.getId(game)));
            SoundInstance sound = new VolumeAdjustableSoundInstance(id, () -> this.calculateVolume(volume));
            this.stopLastSound();
            this.lastSound = sound;
            this.soundManager.play(sound);
        });
    }

    private float calculateVolume(Function<MusicClientConfig, Float> volumeFunction) {
        MusicClientConfig config = MusicClientConfig.getConfig();
        float volume = volumeFunction.apply(config);

        if (this.lastOvertimeIntroAt != -1 && this.client.world != null) {
            long time = this.client.world.getTime();
            long diff = time - this.lastOvertimeIntroAt;
            float mod = 1.0F - (Math.min(1.0F, (float) diff / config.overtimeTransitionTicks()));
            return volume * mod;
        }

        return volume;
    }

    public void stopLastSound() {
        if (this.lastSound != null) {
            this.soundManager.stop(this.lastSound);
        }
    }

    public void playHoleInTheWallOtherDeathSound(MusicClientConfig config, HITWSoundOnOtherDeath deathSoundConfig, Random random) {
        float volume = config.sfxVolume();
        float pitch = (deathSoundConfig.hasRandomPitch() ? random.nextFloat() * 0.17F : 0.0F) + 1.0F;
        for (Identifier sound : deathSoundConfig.getSounds()) {
            this.soundManager.play(SoundFactory.create(sound, SoundCategory.MASTER, volume, pitch));
        }
    }
}

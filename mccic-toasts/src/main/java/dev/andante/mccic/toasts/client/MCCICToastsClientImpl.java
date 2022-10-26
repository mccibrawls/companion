package dev.andante.mccic.toasts.client;

import dev.andante.mccic.api.client.UnicodeIconsStore;
import dev.andante.mccic.api.client.UnicodeIconsStore.Icon;
import dev.andante.mccic.api.client.event.MCCIChatEvent;
import dev.andante.mccic.api.event.EventResult;
import dev.andante.mccic.config.client.ClientConfigRegistry;
import dev.andante.mccic.config.client.command.MCCICConfigCommand;
import dev.andante.mccic.toasts.MCCICToasts;
import dev.andante.mccic.toasts.client.config.ToastsClientConfig;
import dev.andante.mccic.toasts.client.config.ToastsConfigScreen;
import dev.andante.mccic.toasts.client.toast.AdaptableIconToast;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.OptionalInt;

@Environment(EnvType.CLIENT)
public final class MCCICToastsClientImpl implements MCCICToasts, ClientModInitializer {
    public static final Identifier QUEST_TOAST_TEXTURE = new Identifier(MOD_ID, "textures/gui/toasts/quests.png");
    public static final Identifier ACHIEVEMENT_TOAST_TEXTURE = new Identifier(MOD_ID, "textures/gui/toasts/achievements.png");

    public static final String QUEST_COMPLETE_TEXT = "\\(.\\) Quest complete: ";
    public static final String ACHIEVEMENT_UNLOCKED_TEXT = "\\(.\\) Achievement unlocked: \\[";

    @Override
    public void onInitializeClient() {
        ClientConfigRegistry.INSTANCE.registerAndLoad(ToastsClientConfig.CONFIG_HOLDER, ToastsConfigScreen::new);
        MCCICConfigCommand.registerNewConfig(ID, ToastsConfigScreen::new);
        MCCIChatEvent.EVENT.register(this::onChatEvent);
    }

    public EventResult onChatEvent(MCCIChatEvent.Context context) {
        ToastsClientConfig config = ToastsClientConfig.getConfig();
        Text message = context.message();
        String raw = context.getRaw();

        if (config.quests()) {
            OptionalInt opt = processPrefix(message, raw, QUEST_COMPLETE_TEXT, Icon.QUEST_BOOK);
            if (opt.isPresent()) {
                int sub = opt.getAsInt();
                String name = raw.substring(sub);
                new AdaptableIconToast(QUEST_TOAST_TEXTURE,
                    Text.translatable("toast.%s.quest_complete.title".formatted(MOD_ID)),
                    Text.translatable("toast.%s.quest_complete.description".formatted(MOD_ID), name)
                ).add();
                return EventResult.cancel();
            }
        }

        if (config.achievements()) {
            OptionalInt opt = processPrefix(message, raw, ACHIEVEMENT_UNLOCKED_TEXT, Icon.ACHIEVEMENT);
            if (opt.isPresent()) {
                int sub = opt.getAsInt();
                String name = raw.substring(sub, raw.length() - 1);
                new AdaptableIconToast(ACHIEVEMENT_TOAST_TEXTURE,
                    Text.translatable("toast.%s.achievement_unlocked.title".formatted(MOD_ID)),
                    Text.translatable("toast.%s.achievement_unlocked.description".formatted(MOD_ID), name)
                ).add();
                return EventResult.cancel();
            }
        }

        return EventResult.pass();
    }

    public static OptionalInt processPrefix(Text message, String raw, String pattern, Icon icon) {
        return raw.matches(pattern + ".+") && UnicodeIconsStore.textContainsIcon(message, icon)
            ? OptionalInt.of(pattern.replaceAll("\\\\", "").length())
            : OptionalInt.empty();
    }
}

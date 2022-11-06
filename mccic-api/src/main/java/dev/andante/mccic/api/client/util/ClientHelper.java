package dev.andante.mccic.api.client.util;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.andante.mccic.api.mixin.client.access.BossBarHudAccessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

@Environment(EnvType.CLIENT)
public interface ClientHelper {
    static Map<UUID, ClientBossBar> getBossBars() {
        MinecraftClient client = MinecraftClient.getInstance();
        BossBarHud hud = client.inGameHud.getBossBarHud();
        return ((BossBarHudAccessor) hud).getBossBars();
    }

    static Stream<ClientBossBar> getBossBarStream() {
        return getBossBars().values().stream();
    }

    static void drawOpaqueBlack(int x1, int y1, int x2, int y2) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableTexture();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SrcFactor.ZERO, GlStateManager.DstFactor.ONE);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        int alpha = (int) (255 * 0.5F);
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        buffer.vertex(x1, y1, 0).color(0, 0, 0, alpha).next();
        buffer.vertex(x1, y2, 0).color(0, 0, 0, alpha).next();
        buffer.vertex(x2, y2, 0).color(0, 0, 0, alpha).next();
        buffer.vertex(x2, y1, 0).color(0, 0, 0, alpha).next();
        tessellator.draw();
    }
}
package com.ruasol.market.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.ruasol.market.RuasolMarket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RuasolMarket.MODID, value = Dist.CLIENT)
public class MarketAnnouncementOverlay {
    private static String title = "";
    private static String body = "";
    private static long until = 0L;

    public static void show(String t, String b) {
        title = t == null || t.trim().isEmpty() ? "Kadim Duyuru" : t.trim();
        body = b == null ? "" : b.trim();
        until = System.currentTimeMillis() + 5500L;
    }

    public static String currentTitle() { return System.currentTimeMillis() < until ? title : ""; }
    public static String currentBody() { return System.currentTimeMillis() < until ? body : ""; }

    @SubscribeEvent
    public static void onOverlay(RenderGameOverlayEvent.Post event) {
        if (System.currentTimeMillis() >= until) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.fontRenderer == null) return;
        MatrixStack ms = event.getMatrixStack();
        int x = 10;
        int y = mc.getMainWindow().getScaledHeight() / 2 - 34;
        int w = 184;
        int h = 64;
        AbstractGui.fill(ms, x + 2, y + 2, x + w + 2, y + h + 2, 0x66000000);
        AbstractGui.fill(ms, x, y, x + w, y + h, 0xDD17100A);
        stroke(ms, x, y, w, h, 0xFFD6A950);
        AbstractGui.fill(ms, x + 4, y + 4, x + w - 4, y + 19, 0x66351F0E);
        mc.fontRenderer.drawString(ms, trim(title, 24), x + 12, y + 8, 0xFFFFD875);
        drawWrapped(ms, trim(body, 70), x + 12, y + 28, 158, 0xFFE8DEC4);
    }

    private static void stroke(MatrixStack ms, int x, int y, int w, int h, int color) {
        AbstractGui.fill(ms, x, y, x + w, y + 1, color);
        AbstractGui.fill(ms, x, y + h - 1, x + w, y + h, color);
        AbstractGui.fill(ms, x, y, x + 1, y + h, color);
        AbstractGui.fill(ms, x + w - 1, y, x + w, y + h, color);
    }

    private static void drawWrapped(MatrixStack ms, String text, int x, int y, int maxWidth, int color) {
        Minecraft mc = Minecraft.getInstance();
        String line = "";
        for (String word : text.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (mc.fontRenderer.getStringWidth(next) > maxWidth && !line.isEmpty()) {
                mc.fontRenderer.drawString(ms, line, x, y, color);
                y += 11; line = word;
            } else line = next;
        }
        if (!line.isEmpty()) mc.fontRenderer.drawString(ms, line, x, y, color);
    }

    private static String trim(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 3)) + "...";
    }
}

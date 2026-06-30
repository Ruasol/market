package com.ruasol.market.client;

import com.google.gson.Gson;
import com.ruasol.market.data.MarketSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ClientPacketHandler {
    private static final Gson GSON = new Gson();

    public static void openMarket(String json) {
        Minecraft mc = Minecraft.getInstance();
        MarketSnapshot snapshot = GSON.fromJson(json, MarketSnapshot.class);
        if (snapshot == null) snapshot = new MarketSnapshot();
        mc.displayGuiScreen(new RuasolMarketScreen(snapshot));
    }

    public static void showAnnouncement(String title, String body) {
        MarketAnnouncementOverlay.show(title, body);
        RuasolMarketScreen.setExternalAnnouncement(title, body);
    }
}

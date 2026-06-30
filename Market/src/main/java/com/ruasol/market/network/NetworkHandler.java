package com.ruasol.market.network;

import com.ruasol.market.RuasolMarket;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;

public class NetworkHandler {
    private static final String PROTOCOL = "405";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(RuasolMarket.MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );
    private static int index = 0;

    public static void register() {
        CHANNEL.registerMessage(index++, OpenMarketPacket.class, OpenMarketPacket::encode, OpenMarketPacket::decode, OpenMarketPacket::handle);
        CHANNEL.registerMessage(index++, MarketActionPacket.class, MarketActionPacket::encode, MarketActionPacket::decode, MarketActionPacket::handle);
        CHANNEL.registerMessage(index++, MarketAnnouncementPacket.class, MarketAnnouncementPacket::encode, MarketAnnouncementPacket::decode, MarketAnnouncementPacket::handle);
    }

    public static void openMarket(ServerPlayerEntity player, String view, int page, String selectedId, String message) {
        String json = RuasolMarket.SERVICE.snapshotJson(player, view, page, selectedId, message);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenMarketPacket(json));
    }

    public static void sendAnnouncement(ServerPlayerEntity player, String title, String body) {
        if (player == null) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new MarketAnnouncementPacket(title, body));
    }
}

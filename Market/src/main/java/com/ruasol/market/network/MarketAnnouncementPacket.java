package com.ruasol.market.network;

import com.ruasol.market.client.ClientPacketHandler;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class MarketAnnouncementPacket {
    private final String title;
    private final String body;

    public MarketAnnouncementPacket(String title, String body) {
        this.title = title == null ? "Kadim Duyuru" : title;
        this.body = body == null ? "" : body;
    }

    public static void encode(MarketAnnouncementPacket msg, PacketBuffer buf) {
        buf.writeString(msg.title, 128);
        buf.writeString(msg.body, 512);
    }

    public static MarketAnnouncementPacket decode(PacketBuffer buf) {
        return new MarketAnnouncementPacket(buf.readString(128), buf.readString(512));
    }

    public static void handle(MarketAnnouncementPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.showAnnouncement(msg.title, msg.body)));
        ctx.get().setPacketHandled(true);
    }
}

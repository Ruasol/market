package com.ruasol.market.network;

import com.ruasol.market.client.ClientPacketHandler;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenMarketPacket {
    private final String json;

    public OpenMarketPacket(String json) { this.json = json == null ? "{}" : json; }

    public static void encode(OpenMarketPacket msg, PacketBuffer buf) { buf.writeString(msg.json, 1048576); }
    public static OpenMarketPacket decode(PacketBuffer buf) { return new OpenMarketPacket(buf.readString(1048576)); }

    public static void handle(OpenMarketPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.openMarket(msg.json)));
        ctx.get().setPacketHandled(true);
    }
}

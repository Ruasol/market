package com.ruasol.market.network;

import com.ruasol.market.RuasolMarket;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class MarketActionPacket {
    public final String action;
    public final String id;
    public final double amount;
    public final int value;
    public final String view;
    public final int page;
    public final String selectedId;
    public final String text;

    public MarketActionPacket(String action, String id, double amount, int value, String view, int page, String selectedId, String text) {
        this.action = safe(action); this.id = safe(id); this.amount = amount; this.value = value;
        this.view = safe(view); this.page = page; this.selectedId = safe(selectedId); this.text = safe(text);
    }

    private static String safe(String s) { return s == null ? "" : s; }

    public static void encode(MarketActionPacket msg, PacketBuffer buf) {
        buf.writeString(msg.action, 256);
        buf.writeString(msg.id, 128);
        buf.writeDouble(msg.amount);
        buf.writeInt(msg.value);
        buf.writeString(msg.view, 128);
        buf.writeInt(msg.page);
        buf.writeString(msg.selectedId, 128);
        buf.writeString(msg.text, 512);
    }

    public static MarketActionPacket decode(PacketBuffer buf) {
        return new MarketActionPacket(buf.readString(256), buf.readString(128), buf.readDouble(), buf.readInt(), buf.readString(128), buf.readInt(), buf.readString(128), buf.readString(512));
    }

    public static void handle(MarketActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity sender = ctx.get().getSender();
            if (sender == null) return;
            String targetView = msg.view == null || msg.view.isEmpty() ? "HOME" : msg.view;
            int targetPage = Math.max(0, msg.page);
            String targetSelected = msg.selectedId;
            String result = "";
            if ("NAV".equals(msg.action)) {
                result = RuasolMarket.SERVICE.rateLimitNavigation(sender);
                if (result != null && !result.isEmpty()) {
                    sender.sendMessage(new StringTextComponent("§6[Pazar] §7" + result), sender.getUniqueID());
                    return; // Kritik performans düzeltmesi: limit doluyken snapshot/JSON/packet üretme.
                }
                String[] parts = msg.text.split("\\|", -1);
                if (parts.length > 0 && !parts[0].isEmpty()) targetView = parts[0];
                if (parts.length > 1) try { targetPage = Integer.parseInt(parts[1]); } catch (Exception ignored) { }
                if (parts.length > 2) targetSelected = parts[2];
            } else {
                result = RuasolMarket.SERVICE.handleClientAction(sender, msg.action, msg.id, msg.amount, msg.value, msg.text);
            }
            NetworkHandler.openMarket(sender, targetView, targetPage, targetSelected, result);
        });
        ctx.get().setPacketHandled(true);
    }
}

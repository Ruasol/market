package com.ruasol.market;

import com.ruasol.market.command.MarketCommands;
import com.ruasol.market.data.MarketConfig;
import com.ruasol.market.data.MarketService;
import com.ruasol.market.network.NetworkHandler;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(RuasolMarket.MODID)
public class RuasolMarket {
    public static final String MODID = "ruasolmarket";
    public static final Logger LOG = LogManager.getLogger("RuasolMarket");
    public static MarketConfig CONFIG;
    public static MarketService SERVICE;
    private long nextExpireCheck = 0L;

    public RuasolMarket() {
        CONFIG = MarketConfig.load();
        SERVICE = new MarketService();
        NetworkHandler.register();
        MinecraftForge.EVENT_BUS.register(this);
        LOG.info("Ruasol Market v3.6.4 loaded. Grand economy loaded: phased WAL audit, pazar postası, takip sistemi, shards, admin recovery and premium UI ready.");
    }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent event) {
        MarketCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) event.getPlayer();
            MinecraftServer server = player.getServer();
            SERVICE.expireListings(server);
            SERVICE.tryDeliverStorage(player);
            SERVICE.processPayouts(player);
        }
    }


    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            SERVICE.clearSession(((ServerPlayerEntity) event.getPlayer()).getUniqueID());
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long now = System.currentTimeMillis();
        if (now >= nextExpireCheck) {
            nextExpireCheck = now + Math.max(15, CONFIG.expireCheckSeconds) * 1000L;
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) SERVICE.expireListings(server);
        }
    }
}

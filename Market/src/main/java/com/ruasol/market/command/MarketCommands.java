package com.ruasol.market.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.ruasol.market.RuasolMarket;
import com.ruasol.market.data.MarketConfig;
import com.ruasol.market.network.NetworkHandler;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;

public class MarketCommands {
    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        LiteralArgumentBuilder<CommandSource> root = Commands.literal("pazar")
            .requires(s -> s.hasPermissionLevel(0))
            .executes(c -> {
                NetworkHandler.openMarket(c.getSource().asPlayer(), "HOME", 0, "", "");
                return 1;
            });

        root.then(Commands.literal("fiyat")
            .then(Commands.argument("miktar", DoubleArgumentType.doubleArg(0))
                .executes(c -> {
                    ServerPlayerEntity p = c.getSource().asPlayer();
                    msg(p, RuasolMarket.SERVICE.setWizardPrice(p, DoubleArgumentType.getDouble(c, "miktar")));
                    NetworkHandler.openMarket(p, "SELL", 0, "", "");
                    return 1;
                })));

        root.then(Commands.literal("teklif")
            .then(Commands.argument("ilanId", StringArgumentType.word())
                .then(Commands.argument("miktar", DoubleArgumentType.doubleArg(0.01D))
                    .executes(c -> {
                        ServerPlayerEntity p = c.getSource().asPlayer();
                        String id = StringArgumentType.getString(c, "ilanId");
                        double amount = DoubleArgumentType.getDouble(c, "miktar");
                        msg(p, RuasolMarket.SERVICE.offerOrBid(p, id, amount));
                        NetworkHandler.openMarket(p, "DETAIL", 0, id, "");
                        return 1;
                    }))));

        root.then(Commands.literal("kabul")
            .then(Commands.argument("teklifId", StringArgumentType.word())
                .executes(c -> {
                    ServerPlayerEntity p = c.getSource().asPlayer();
                    msg(p, RuasolMarket.SERVICE.acceptOffer(p, StringArgumentType.getString(c, "teklifId")));
                    NetworkHandler.openMarket(p, "OFFERS", 0, "", "");
                    return 1;
                })));

        root.then(Commands.literal("teklifiptal")
            .then(Commands.argument("teklifId", StringArgumentType.word())
                .executes(c -> {
                    ServerPlayerEntity p = c.getSource().asPlayer();
                    msg(p, RuasolMarket.SERVICE.cancelOffer(p, StringArgumentType.getString(c, "teklifId")));
                    NetworkHandler.openMarket(p, "MY_OFFERS", 0, "", "");
                    return 1;
                })));

        root.then(Commands.literal("iptal")
            .then(Commands.argument("ilanId", StringArgumentType.word())
                .executes(c -> {
                    ServerPlayerEntity p = c.getSource().asPlayer();
                    msg(p, RuasolMarket.SERVICE.cancelListing(p, StringArgumentType.getString(c, "ilanId")));
                    NetworkHandler.openMarket(p, "MINE", 0, "", "");
                    return 1;
                })));

        LiteralArgumentBuilder<CommandSource> credit = Commands.literal("kredi")
            .executes(c -> {
                ServerPlayerEntity p = c.getSource().asPlayer();
                msg(p, "Pazar Kredin: " + RuasolMarket.SERVICE.credits(p.getUniqueID()));
                NetworkHandler.openMarket(p, "HOME", 0, "", "");
                return 1;
            });

        credit.then(Commands.literal("ver")
            .requires(s -> s.hasPermissionLevel(2))
            .then(Commands.argument("oyuncu", EntityArgument.player())
                .then(Commands.argument("miktar", IntegerArgumentType.integer(1))
                    .executes(c -> {
                        ServerPlayerEntity target = EntityArgument.getPlayer(c, "oyuncu");
                        int amount = IntegerArgumentType.getInteger(c, "miktar");
                        RuasolMarket.SERVICE.addCredits(target.getUniqueID(), amount);
                        msg(c.getSource().asPlayer(), target.getGameProfile().getName() + " kredi aldı: +" + amount + " / yeni: " + RuasolMarket.SERVICE.credits(target.getUniqueID()));
                        return 1;
                    }))));

        credit.then(Commands.literal("al")
            .requires(s -> s.hasPermissionLevel(2))
            .then(Commands.argument("oyuncu", EntityArgument.player())
                .then(Commands.argument("miktar", IntegerArgumentType.integer(1))
                    .executes(c -> {
                        ServerPlayerEntity target = EntityArgument.getPlayer(c, "oyuncu");
                        int amount = IntegerArgumentType.getInteger(c, "miktar");
                        RuasolMarket.SERVICE.removeCredits(target.getUniqueID(), amount);
                        msg(c.getSource().asPlayer(), target.getGameProfile().getName() + " kredisi azaltıldı: -" + amount + " / yeni: " + RuasolMarket.SERVICE.credits(target.getUniqueID()));
                        return 1;
                    }))));

        root.then(credit);

        

        LiteralArgumentBuilder<CommandSource> guild = Commands.literal("lonca")
            .requires(src -> src.hasPermissionLevel(2));

        guild.then(Commands.literal("olustur")
            .then(Commands.argument("id", StringArgumentType.word())
                .then(Commands.argument("ad", StringArgumentType.greedyString())
                    .executes(c -> {
                        ServerPlayerEntity op = c.getSource().asPlayer();
                        msg(op, RuasolMarket.SERVICE.adminGuildCreate(op, StringArgumentType.getString(c, "id"), StringArgumentType.getString(c, "ad")));
                        return 1;
                    }))));

        guild.then(Commands.literal("sil")
            .then(Commands.argument("id", StringArgumentType.word())
                .executes(c -> {
                    ServerPlayerEntity op = c.getSource().asPlayer();
                    msg(op, RuasolMarket.SERVICE.adminGuildDelete(op, StringArgumentType.getString(c, "id")));
                    return 1;
                })));

        guild.then(Commands.literal("uyeekle")
            .then(Commands.argument("id", StringArgumentType.word())
                .then(Commands.argument("oyuncu", EntityArgument.player())
                    .executes(c -> {
                        ServerPlayerEntity op = c.getSource().asPlayer();
                        msg(op, RuasolMarket.SERVICE.adminGuildAdd(op, EntityArgument.getPlayer(c, "oyuncu"), StringArgumentType.getString(c, "id")));
                        return 1;
                    }))));

        guild.then(Commands.literal("uyesil")
            .then(Commands.argument("id", StringArgumentType.word())
                .then(Commands.argument("oyuncu", EntityArgument.player())
                    .executes(c -> {
                        ServerPlayerEntity op = c.getSource().asPlayer();
                        msg(op, RuasolMarket.SERVICE.adminGuildRemove(op, EntityArgument.getPlayer(c, "oyuncu"), StringArgumentType.getString(c, "id")));
                        return 1;
                    }))));

        root.then(guild);

        root.then(Commands.literal("sezon")
            .requires(src -> src.hasPermissionLevel(2))
            .then(Commands.literal("sifirla")
                .then(Commands.argument("ad", StringArgumentType.greedyString())
                    .executes(c -> {
                        ServerPlayerEntity op = c.getSource().asPlayer();
                        msg(op, RuasolMarket.SERVICE.adminSeasonReset(op, StringArgumentType.getString(c, "ad")));
                        return 1;
                    }))));

        root.then(Commands.literal("reload")
            .requires(s -> s.hasPermissionLevel(2))
            .executes(c -> {
                RuasolMarket.CONFIG = MarketConfig.load();
                c.getSource().sendFeedback(new StringTextComponent("§6[Pazar] §eConfig yenilendi."), true);
                return 1;
            }));

        root.then(Commands.literal("admintest")
            .requires(s -> s.hasPermissionLevel(2))
            .executes(c -> {
                ServerPlayerEntity p = c.getSource().asPlayer();
                msg(p, RuasolMarket.SERVICE.adminSelfTest(p, "all"));
                return 1;
            })
            .then(Commands.argument("mod", StringArgumentType.word())
                .executes(c -> {
                    ServerPlayerEntity p = c.getSource().asPlayer();
                    msg(p, RuasolMarket.SERVICE.adminSelfTest(p, StringArgumentType.getString(c, "mod")));
                    return 1;
                })));

        root.then(Commands.literal("admin")
            .requires(s -> s.hasPermissionLevel(2))
            .executes(c -> {
                NetworkHandler.openMarket(c.getSource().asPlayer(), "ADMIN", 0, "", "");
                return 1;
            }));

        dispatcher.register(root);
    }

    private static void msg(ServerPlayerEntity p, String s) {
        StringTextComponent t = new StringTextComponent("§6[Pazar] §e" + s);
        t.mergeStyle(TextFormatting.GOLD);
        p.sendMessage(t, p.getUniqueID());
    }
}

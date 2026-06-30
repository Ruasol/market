package com.ruasol.market.util;

import com.ruasol.market.RuasolMarket;
import com.ruasol.market.data.MarketModels.Category;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.item.*;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.*;
import java.security.MessageDigest;
import java.util.Base64;

public class ItemCodec {
    public static String encode(ItemStack stack) throws IOException {
        CompoundNBT tag = new CompoundNBT();
        stack.write(tag);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(tag, out);
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    public static ItemStack decode(String base64) throws IOException {
        byte[] raw = Base64.getDecoder().decode(base64);
        CompoundNBT tag = CompressedStreamTools.readCompressed(new ByteArrayInputStream(raw));
        return ItemStack.read(tag);
    }

    public static int encodedBytes(String base64) {
        try { return Base64.getDecoder().decode(base64).length; } catch (Exception e) { return Integer.MAX_VALUE; }
    }

    public static String registryName(ItemStack stack) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key == null ? "unknown:unknown" : key.toString();
    }

    public static String hash(ItemStack stack) throws IOException {
        return sha256(encode(stack));
    }

    public static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes("UTF-8"));
            StringBuilder b = new StringBuilder();
            for (byte by : bytes) b.append(String.format("%02x", by));
            return b.toString();
        } catch (Exception e) { return "hash-error"; }
    }

    public static Category categoryOf(ItemStack s) {
        Item i = s.getItem();
        String path = "";
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(i);
        if (key != null) path = key.getPath();
        if (i instanceof BlockItem) return Category.BLOCK;
        if (i.isFood()) return Category.FOOD;
        if (i instanceof ArmorItem) return Category.ARMOR;
        if (i instanceof SwordItem || path.contains("sword") || path.contains("blade") || path.contains("katana")) return Category.WEAPON;
        if (i instanceof ToolItem || path.contains("pickaxe") || path.contains("axe") || path.contains("shovel") || path.contains("hoe")) return Category.TOOL;
        if (i instanceof PotionItem) return Category.POTION;
        if (i instanceof BookItem || i instanceof EnchantedBookItem || i == Items.WRITABLE_BOOK || i == Items.WRITTEN_BOOK) return Category.BOOK;
        if (path.contains("ingot") || path.contains("gem") || path.contains("nugget") || path.contains("dust") || path.contains("hide") || path.contains("leather")) return Category.MATERIAL;
        return Category.OTHER;
    }

    public static String validateTradable(ItemStack stack, String encodedBase64) {
        if (stack == null || stack.isEmpty()) return "Boş item pazara konamaz.";
        String id = registryName(stack);
        if (RuasolMarket.CONFIG.blacklistItemIds.contains(id)) return "Bu item pazar karalistesinde: " + id;
        if (!RuasolMarket.CONFIG.allowWrittenBooks && stack.getItem() == Items.WRITTEN_BOOK) return "Yazılı kitap satışı kapalı. NBT spam/istismar riski nedeniyle engellendi.";
        if (!RuasolMarket.CONFIG.allowShulkerBoxes && stack.getItem() instanceof BlockItem) {
            try {
                if (((BlockItem) stack.getItem()).getBlock() instanceof ShulkerBoxBlock) return "Shulker box satışı kapalı. İç içe NBT/dupe riskini azaltmak için engellendi.";
            } catch (Throwable ignored) { }
        }
        if (encodedBytes(encodedBase64) > RuasolMarket.CONFIG.maxItemNbtBytes) return "Item NBT verisi çok büyük. Sınır: " + RuasolMarket.CONFIG.maxItemNbtBytes + " byte.";
        return null;
    }
}

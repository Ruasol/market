package com.ruasol.market.data;

import com.ruasol.market.RuasolMarket;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MarketConfig {
    public double taxPercent = 5.0D;
    public double extraDayFee = 100.0D;
    public int featuredCreditCost = 15;
    public int announcementCreditCost = 30;
    public int auctionCreditCost = 0;
    public double fixedThreeDayFee = 200.0D;
    public double fixedFiveDayFee = 400.0D;
    public int auctionThreeHourCreditCost = 5;
    public int auctionFiveHourCreditCost = 10;
    public boolean allowMarketCreditAuctions = true;
    public int maxAuctionHours = 3;
    public int featuredHours = 24;
    public int expireCheckSeconds = 60;
    public int maxActiveListingsPerPlayer = 25;
    public int maxStorageItemsPerPlayer = 100;
    public int maxTransactionLogEntries = 1500;
    public int maxExpirationsPerCheck = 25;
    public int maxStorageRowsPerSnapshot = 5;
    public int maxOffersRowsPerSnapshot = 6;
    public int softSaveIntervalSeconds = 30;
    public int auctionBidBroadcastCooldownSeconds = 10;
    public int maxPayoutRowsPerSnapshot = 3;
    public int snapshotDebugBytesWarn = 750000;
    public int activeListCacheSeconds = 5;
    public int maxNotificationRowsPerSnapshot = 5;
    public int maxFavoritesRowsPerSnapshot = 5;
    public boolean writeDataShards = false;
    public boolean enableUiSounds = true;
    public int legendarySealCreditCost = 30;
    public int prestigeSealCreditCost = 5;
    public int maxPrestigeLevel = 5;
    public int maxGuilds = 30;
    public int maxGuildMembersPerGuild = 25;
    public int maxFavoritesPerPlayer = 200;
    public int maxNotificationsPerPlayer = 60;
    public int maxPayoutsProcessedPerClick = 25;
    public double maxRcTransactionAmount = 1000000000D;
    public int maxCreditTransactionAmount = 1000000;
    public boolean enableMarketEvents = true;
    public int maxItemNbtBytes = 65536;
    public double minBidIncrementPercent = 5.0D;
    public int antiSnipeWindowSeconds = 30;
    public int antiSnipeExtendSeconds = 60;
    public boolean allowShulkerBoxes = false;
    public boolean allowWrittenBooks = false;
    public boolean allowInternalTestEconomy = false;
    public List<String> blacklistItemIds = new ArrayList<String>();

    public static File file() { return new File("config", "ruasol_market.properties"); }

    public static MarketConfig load() {
        MarketConfig c = new MarketConfig();
        c.blacklistItemIds.add("minecraft:command_block");
        c.blacklistItemIds.add("minecraft:barrier");
        c.blacklistItemIds.add("minecraft:structure_block");
        c.blacklistItemIds.add("minecraft:jigsaw");
        try {
            File f = file();
            if (!f.getParentFile().exists()) f.getParentFile().mkdirs();
            Properties p = new Properties();
            if (f.exists()) {
                try (InputStream in = new FileInputStream(f)) { p.load(new InputStreamReader(in, StandardCharsets.UTF_8)); }
            }
            c.taxPercent = clamp(readDouble(p, "tax_percent", c.taxPercent), 0D, 95D);
            c.extraDayFee = Math.max(0D, readDouble(p, "extra_day_fee", c.extraDayFee));
            c.featuredCreditCost = Math.max(0, readInt(p, "featured_credit_cost", c.featuredCreditCost));
            c.announcementCreditCost = Math.max(0, readInt(p, "announcement_credit_cost", c.announcementCreditCost));
            c.auctionCreditCost = Math.max(0, readInt(p, "auction_credit_cost", c.auctionCreditCost));
            c.fixedThreeDayFee = Math.max(0D, readDouble(p, "fixed_three_day_fee", c.fixedThreeDayFee));
            c.fixedFiveDayFee = Math.max(0D, readDouble(p, "fixed_five_day_fee", c.fixedFiveDayFee));
            c.auctionThreeHourCreditCost = Math.max(0, readInt(p, "auction_three_hour_credit_cost", c.auctionThreeHourCreditCost));
            c.auctionFiveHourCreditCost = Math.max(0, readInt(p, "auction_five_hour_credit_cost", c.auctionFiveHourCreditCost));
            c.allowMarketCreditAuctions = readBool(p, "allow_market_credit_auctions", c.allowMarketCreditAuctions);
            c.maxAuctionHours = Math.max(1, Math.min(24, readInt(p, "max_auction_hours", c.maxAuctionHours)));
            c.featuredHours = Math.max(1, Math.min(168, readInt(p, "featured_hours", c.featuredHours)));
            c.expireCheckSeconds = Math.max(15, readInt(p, "expire_check_seconds", c.expireCheckSeconds));
            c.maxActiveListingsPerPlayer = Math.max(1, readInt(p, "max_active_listings_per_player", c.maxActiveListingsPerPlayer));
            c.maxStorageItemsPerPlayer = Math.max(1, readInt(p, "max_storage_items_per_player", c.maxStorageItemsPerPlayer));
            c.maxTransactionLogEntries = Math.max(100, readInt(p, "max_transaction_log_entries", c.maxTransactionLogEntries));
            c.maxExpirationsPerCheck = Math.max(1, readInt(p, "max_expirations_per_check", c.maxExpirationsPerCheck));
            c.maxStorageRowsPerSnapshot = Math.max(1, Math.min(5, readInt(p, "max_storage_rows_per_snapshot", c.maxStorageRowsPerSnapshot)));
            c.maxOffersRowsPerSnapshot = Math.max(1, Math.min(6, readInt(p, "max_offers_rows_per_snapshot", c.maxOffersRowsPerSnapshot)));
            c.softSaveIntervalSeconds = Math.max(5, readInt(p, "soft_save_interval_seconds", c.softSaveIntervalSeconds));
            c.auctionBidBroadcastCooldownSeconds = Math.max(0, readInt(p, "auction_bid_broadcast_cooldown_seconds", c.auctionBidBroadcastCooldownSeconds));
            c.maxPayoutRowsPerSnapshot = Math.max(1, Math.min(3, readInt(p, "max_payout_rows_per_snapshot", c.maxPayoutRowsPerSnapshot)));
            c.snapshotDebugBytesWarn = Math.max(0, readInt(p, "snapshot_debug_bytes_warn", c.snapshotDebugBytesWarn));
            c.activeListCacheSeconds = Math.max(1, Math.min(30, readInt(p, "active_list_cache_seconds", c.activeListCacheSeconds)));
            c.maxNotificationRowsPerSnapshot = Math.max(1, Math.min(5, readInt(p, "max_notification_rows_per_snapshot", c.maxNotificationRowsPerSnapshot)));
            c.maxFavoritesRowsPerSnapshot = Math.max(1, Math.min(5, readInt(p, "max_favorites_rows_per_snapshot", c.maxFavoritesRowsPerSnapshot)));
            c.writeDataShards = readBool(p, "write_data_shards", c.writeDataShards);
            c.enableUiSounds = readBool(p, "enable_ui_sounds", c.enableUiSounds);
            c.legendarySealCreditCost = Math.max(0, readInt(p, "legendary_seal_credit_cost", c.legendarySealCreditCost));
            c.prestigeSealCreditCost = Math.max(0, readInt(p, "prestige_seal_credit_cost", c.prestigeSealCreditCost));
            c.maxPrestigeLevel = Math.max(1, Math.min(10, readInt(p, "max_prestige_level", c.maxPrestigeLevel)));
            c.maxGuilds = Math.max(1, Math.min(100, readInt(p, "max_guilds", c.maxGuilds)));
            c.maxGuildMembersPerGuild = Math.max(1, Math.min(200, readInt(p, "max_guild_members_per_guild", c.maxGuildMembersPerGuild)));
            c.maxFavoritesPerPlayer = Math.max(10, Math.min(1000, readInt(p, "max_favorites_per_player", c.maxFavoritesPerPlayer)));
            c.maxNotificationsPerPlayer = Math.max(10, Math.min(500, readInt(p, "max_notifications_per_player", c.maxNotificationsPerPlayer)));
            c.maxPayoutsProcessedPerClick = Math.max(1, Math.min(100, readInt(p, "max_payouts_processed_per_click", c.maxPayoutsProcessedPerClick)));
            c.maxRcTransactionAmount = Math.max(1D, readDouble(p, "max_rc_transaction_amount", c.maxRcTransactionAmount));
            c.maxCreditTransactionAmount = Math.max(1, readInt(p, "max_credit_transaction_amount", c.maxCreditTransactionAmount));
            c.enableMarketEvents = readBool(p, "enable_market_events", c.enableMarketEvents);
            c.maxItemNbtBytes = Math.max(1024, readInt(p, "max_item_nbt_bytes", c.maxItemNbtBytes));
            c.minBidIncrementPercent = clamp(readDouble(p, "min_bid_increment_percent", c.minBidIncrementPercent), 0D, 100D);
            c.antiSnipeWindowSeconds = Math.max(0, readInt(p, "anti_snipe_window_seconds", c.antiSnipeWindowSeconds));
            c.antiSnipeExtendSeconds = Math.max(0, readInt(p, "anti_snipe_extend_seconds", c.antiSnipeExtendSeconds));
            c.allowShulkerBoxes = readBool(p, "allow_shulker_boxes", c.allowShulkerBoxes);
            c.allowWrittenBooks = readBool(p, "allow_written_books", c.allowWrittenBooks);
            c.allowInternalTestEconomy = readBool(p, "allow_internal_test_economy", c.allowInternalTestEconomy);
            c.blacklistItemIds = readCsv(p, "blacklist_item_ids", c.blacklistItemIds);

            put(p, "tax_percent", c.taxPercent, "Satıştan kesilecek vergi yüzdesi. 5 = %5.");
            put(p, "extra_day_fee", c.extraDayFee, "1 gün ücretsizdir. Ek her gün için bakiyeden alınacak ücret.");
            put(p, "featured_credit_cost", c.featuredCreditCost, "İlanı öne çıkarma bedeli. Ürün ekleme akışındaki Öne Çıkar ve sonradan kullanılan FEATURE işlemi için kullanılır.");
            put(p, "announcement_credit_cost", c.announcementCreditCost, "Herkese Duyuru bedeli. İlan açılırken global duyuru geçmek için harcanan Pazar Kredisi.");
            put(p, "auction_credit_cost", c.auctionCreditCost, "Eski uyumluluk alanı. Yeni akışta açık artırma taban bedeli kullanılmaz; 1-2 saat ücretsizdir.");
            put(p, "fixed_three_day_fee", c.fixedThreeDayFee, "Sabit ilan 3 gün bedeli.");
            put(p, "fixed_five_day_fee", c.fixedFiveDayFee, "Sabit ilan 5 gün bedeli.");
            put(p, "auction_three_hour_credit_cost", c.auctionThreeHourCreditCost, "Açık artırma 3 saat bedeli (Pazar Kredisi).");
            put(p, "auction_five_hour_credit_cost", c.auctionFiveHourCreditCost, "Açık artırma 5 saat bedeli (Pazar Kredisi).");
            put(p, "allow_market_credit_auctions", c.allowMarketCreditAuctions, "Açık artırmalar Pazar Kredisi birimiyle yapılabilsin mi? Sabit fiyatlı normal pazar her zaman RC kullanır.");
            put(p, "max_auction_hours", c.maxAuctionHours, "Açık artırma maksimum saat sınırı.");
            put(p, "featured_hours", c.featuredHours, "Öne çıkarma kaç saat sürsün.");
            put(p, "expire_check_seconds", c.expireCheckSeconds, "Süre dolumu kontrol aralığı. Tick spam yapmamak için en az 15.");
            put(p, "max_active_listings_per_player", c.maxActiveListingsPerPlayer, "Oyuncu başına aktif ilan sınırı.");
            put(p, "max_storage_items_per_player", c.maxStorageItemsPerPlayer, "Oyuncu başına depo sınırı.");
            put(p, "max_transaction_log_entries", c.maxTransactionLogEntries, "Son kaç transaction saklanacak.");
            put(p, "max_payout_rows_per_snapshot", c.maxPayoutRowsPerSnapshot, "Depo ekranında tek snapshotta gösterilecek bekleyen ödeme satırı.");
            put(p, "snapshot_debug_bytes_warn", c.snapshotDebugBytesWarn, "Snapshot JSON bu byte eşiğini aşarsa server log uyarısı verir. 0 = kapalı.");
            put(p, "active_list_cache_seconds", c.activeListCacheSeconds, "Pazar liste cache zaman kovası. UI spamde stream/sort yükünü düşürür.");
            put(p, "max_notification_rows_per_snapshot", c.maxNotificationRowsPerSnapshot, "Pazar Postası ekranında tek snapshotta gösterilecek bildirim satırı.");
            put(p, "max_favorites_rows_per_snapshot", c.maxFavoritesRowsPerSnapshot, "Takip edilen ürünler ekranında tek snapshotta gösterilecek satır.");
            put(p, "write_data_shards", c.writeDataShards, "Ana data yanında debug/backup için parçalı data shard dosyaları yazılsın mı.");
            put(p, "enable_ui_sounds", c.enableUiSounds, "Client UI sesleri aktif olsun mu. Ses assetleri yoksa sessiz fallback yapar.");
            put(p, "legendary_seal_credit_cost", c.legendarySealCreditCost, "Ürün eklerken Efsanevi İlan mührü basma Pazar Kredisi bedeli.");
            put(p, "prestige_seal_credit_cost", c.prestigeSealCreditCost, "Aktif ilana ek prestij mührü basma Pazar Kredisi bedeli.");
            put(p, "max_prestige_level", c.maxPrestigeLevel, "Bir ilanın ulaşabileceği en yüksek prestij mührü seviyesi.");
            put(p, "max_guilds", c.maxGuilds, "Toplam ticaret loncası sınırı. O(n*m) lonca sayımı ve UI yükünü sınırlamak için kullanılır.");
            put(p, "max_guild_members_per_guild", c.maxGuildMembersPerGuild, "Bir ticaret loncasındaki maksimum üye sayısı.");
            put(p, "max_favorites_per_player", c.maxFavoritesPerPlayer, "Oyuncu başı takip/favori ilan sınırı.");
            put(p, "max_notifications_per_player", c.maxNotificationsPerPlayer, "Oyuncu başı Pazar Postası kayıt sınırı.");
            put(p, "max_payouts_processed_per_click", c.maxPayoutsProcessedPerClick, "Tek tıkta işlenecek bekleyen ödeme sınırı.");
            put(p, "max_rc_transaction_amount", c.maxRcTransactionAmount, "Tek işlemde kabul edilecek maksimum RC miktarı. Kötü niyetli packet/overflow koruması.");
            put(p, "max_credit_transaction_amount", c.maxCreditTransactionAmount, "Tek işlemde kabul edilecek maksimum Pazar Kredisi miktarı.");
            put(p, "enable_market_events", c.enableMarketEvents, "Pazar event/duyuru altyapısı aktif olsun mu.");
            put(p, "max_item_nbt_bytes", c.maxItemNbtBytes, "Pazara konabilecek item NBT byte sınırı.");
            put(p, "min_bid_increment_percent", c.minBidIncrementPercent, "Açık artırmada minimum artış yüzdesi.");
            put(p, "anti_snipe_window_seconds", c.antiSnipeWindowSeconds, "Son kaç saniyede teklif gelirse süre uzasın.");
            put(p, "anti_snipe_extend_seconds", c.antiSnipeExtendSeconds, "Anti-snipe süre uzatma saniyesi.");
            put(p, "allow_shulker_boxes", c.allowShulkerBoxes, "Shulker box satışı açık mı? Dupe/kaçak taşıma riski nedeniyle varsayılan false.");
            put(p, "allow_written_books", c.allowWrittenBooks, "Yazılı kitap satışı açık mı? NBT spam riski nedeniyle varsayılan false.");
            put(p, "allow_internal_test_economy", c.allowInternalTestEconomy, "Vault/Essentials yoksa test ekonomisi kullanılsın mı? Production için false kalmalı; ekonomi yoksa para işlemleri kilitlenir.");
            p.setProperty("blacklist_item_ids", join(c.blacklistItemIds));
            try (OutputStream out = new FileOutputStream(f)) { p.store(new OutputStreamWriter(out, StandardCharsets.UTF_8), "Ruasol Market config"); }
        } catch (Exception e) {
            RuasolMarket.LOG.error("Could not load config", e);
        }
        return c;
    }

    private static void put(Properties p, String k, Object v, String comment) {
        p.setProperty(k, String.valueOf(v));
    }
    private static int readInt(Properties p, String k, int d) { try { return Integer.parseInt(p.getProperty(k, String.valueOf(d)).trim()); } catch (Exception e) { return d; } }
    private static double readDouble(Properties p, String k, double d) { try { return Double.parseDouble(p.getProperty(k, String.valueOf(d)).trim()); } catch (Exception e) { return d; } }
    private static boolean readBool(Properties p, String k, boolean d) { try { return Boolean.parseBoolean(p.getProperty(k, String.valueOf(d)).trim()); } catch (Exception e) { return d; } }
    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
    private static List<String> readCsv(Properties p, String k, List<String> d) {
        String raw = p.getProperty(k, join(d));
        List<String> out = new ArrayList<String>();
        for (String s : raw.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }
    private static String join(List<String> list) {
        StringBuilder b = new StringBuilder();
        for (int i=0;i<list.size();i++) { if (i>0) b.append(','); b.append(list.get(i)); }
        return b.toString();
    }
}

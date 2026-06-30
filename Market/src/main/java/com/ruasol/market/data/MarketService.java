package com.ruasol.market.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ruasol.market.RuasolMarket;
import com.ruasol.market.economy.EconomyBridge;
import com.ruasol.market.network.NetworkHandler;
import com.ruasol.market.util.ItemCodec;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.ruasol.market.data.MarketModels.*;

public class MarketService {
    private final Gson gson = new Gson();
    private final File storageRoot = resolveStorageRoot();
    private final File dataFile = new File(storageRoot, "ruasol_market_data.json");
    private final File walFile = new File(storageRoot, "ruasol_market_wal.jsonl");
    private final File emergencyFile = new File(storageRoot, "ruasol_market_emergency_recovery.jsonl");
    private final File shardDir = new File(storageRoot, "ruasol_market_shards");
    private MarketData data = new MarketData();
    private final Map<UUID, Wizard> wizards = new ConcurrentHashMap<UUID, Wizard>();
    private final Map<String, Long> actionRateLimit = new ConcurrentHashMap<String, Long>();
    private final Map<String, Long> auctionBidBroadcasts = new ConcurrentHashMap<String, Long>();
    private final Map<String, List<Listing>> listingCache = new HashMap<String, List<Listing>>();
    private long cacheEpoch = 0L;
    private boolean softDirty = false;
    private long nextSoftSaveAt = 0L;


    private static File resolveStorageRoot() {
        String override = System.getProperty("ruasolmarket.dataDir");
        if (override != null && !override.trim().isEmpty()) return new File(override.trim());
        String levelName = "world";
        File props = new File("server.properties");
        if (props.exists()) {
            try (InputStream in = new FileInputStream(props)) {
                Properties p = new Properties();
                p.load(in);
                String configured = p.getProperty("level-name");
                if (configured != null && !configured.trim().isEmpty()) levelName = configured.trim();
            } catch (Exception ignored) {}
        }
        return new File(new File(levelName), "serverconfig");
    }

    public MarketService() { load(); }

    public synchronized void load() {
        try {
            if (!dataFile.getParentFile().exists()) dataFile.getParentFile().mkdirs();
            if (dataFile.exists()) {
                try (Reader r = new InputStreamReader(new FileInputStream(dataFile), StandardCharsets.UTF_8)) {
                    MarketData d = gson.fromJson(r, MarketData.class);
                    if (d != null) data = d;
                }
            } else save();
            sanitizeData();
            recoverFromWalAndData();
        } catch (Exception e) { RuasolMarket.LOG.error("Market data load failed", e); }
    }

    public synchronized boolean save() {
        try {
            if (!dataFile.getParentFile().exists()) dataFile.getParentFile().mkdirs();
            File tmp = new File(dataFile.getParentFile(), dataFile.getName()+".tmp");
            try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) { gson.toJson(data, w); }
            try {
                Files.move(tmp.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException atomicNotSupported) {
                Files.move(tmp.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            softDirty = false;
            nextSoftSaveAt = System.currentTimeMillis() + Math.max(5, RuasolMarket.CONFIG == null ? 30 : RuasolMarket.CONFIG.softSaveIntervalSeconds) * 1000L;
            invalidateCaches();
            writeDataShards();
            return true;
        } catch (Exception e) { RuasolMarket.LOG.error("Market data save failed", e); return false; }
    }

    private void invalidateCaches() {
        cacheEpoch++;
        listingCache.clear();
    }

    private void sanitizeData() {
        if (data.listings == null) data.listings = new LinkedHashMap<String, Listing>();
        if (data.offers == null) data.offers = new LinkedHashMap<String, Offer>();
        if (data.storage == null) data.storage = new ArrayList<StorageEntry>();
        if (data.recoveryStorage == null) data.recoveryStorage = new ArrayList<StorageEntry>();
        if (data.payouts == null) data.payouts = new ArrayList<PendingPayout>();
        if (data.credits == null) data.credits = new HashMap<String, Integer>();
        if (data.playerNames == null) data.playerNames = new HashMap<String, String>();
        if (data.stats == null) data.stats = new HashMap<String, Stats>();
        if (data.transactions == null) data.transactions = new ArrayList<TransactionEntry>();
        if (data.notifications == null) data.notifications = new ArrayList<NotificationEntry>();
        if (data.favorites == null) data.favorites = new HashMap<String, Set<String>>();
        if (data.guilds == null) data.guilds = new LinkedHashMap<String, TradeGuild>();
        if (data.seasonArchives == null) data.seasonArchives = new ArrayList<SeasonArchive>();
        for (TradeGuild g : data.guilds.values()) { if (g.members == null) g.members = new LinkedHashSet<String>(); if (g.id == null) g.id = ""; if (g.displayName == null || g.displayName.isEmpty()) g.displayName = g.id; if (g.motto == null) g.motto = "Loncanın güzidelerine bak."; }
        long now = System.currentTimeMillis();
        for (Listing l : data.listings.values()) {
            if (l.state == null) l.state = ListingState.LISTED;
            if (l.category == null) l.category = Category.OTHER;
            if (l.type == null) l.type = ListingType.FIXED;
            if (l.currency == null) l.currency = CurrencyUnit.RC;
            if (l.likes == null) l.likes = new LinkedHashSet<String>();
            if (l.itemHash == null) l.itemHash = "";
            if (l.sellerName == null) l.sellerName = "?";
            if (l.guildId == null) l.guildId = "";
            if (l.quantity <= 0) l.quantity = 1;
            if (l.prestigeLevel < 0) l.prestigeLevel = 0;
            if (l.type == ListingType.AUCTION && l.startPrice <= 0 && l.buyoutPrice > 0) l.startPrice = l.buyoutPrice;
            if (l.expiresAt <= 0) l.expiresAt = now + 86400000L;
        }
        for (Offer o : data.offers.values()) {
            if (o.state == null) o.state = OfferState.ESCROWED;
            if (o.currency == null) o.currency = CurrencyUnit.RC;
        }
        for (PendingPayout pp : data.payouts) if (pp.currency == null) pp.currency = CurrencyUnit.RC;
        for (StorageEntry se : data.storage) if (se.ownerName == null) se.ownerName = "?";
        for (StorageEntry se : data.recoveryStorage) if (se.ownerName == null) se.ownerName = "?";
        for (TransactionEntry te : data.transactions) { if (te.currency == null) te.currency = CurrencyUnit.RC; if (te.phase == null) te.phase = TransactionPhase.NONE; }
        for (Stats st : data.stats.values()) { if (st.badges == null) st.badges = new LinkedHashSet<String>(); refreshReputation(st); }
        data.dataVersion = 405;
        trimTransactions();
        save();
    }

    public Wizard wizard(ServerPlayerEntity p) { remember(p); return wizards.computeIfAbsent(p.getUniqueID(), k -> new Wizard()); }
    public void clearWizard(ServerPlayerEntity p) { wizards.remove(p.getUniqueID()); }

    public synchronized List<Listing> active() { return active(null, SortMode.FEATURED); }

    public synchronized List<Listing> active(Category c, SortMode sort) {
        final long now = System.currentTimeMillis();
        final long cacheMs = Math.max(1, RuasolMarket.CONFIG == null ? 5 : RuasolMarket.CONFIG.activeListCacheSeconds) * 1000L;
        final long timeBucket = now / cacheMs; // UI spamde tekrar stream/sort maliyetini azaltır.
        String key = cacheEpoch + ":" + timeBucket + ":" + (c == null ? "ALL" : c.name()) + ":" + (sort == null ? SortMode.FEATURED : sort).name();
        List<Listing> cached = listingCache.get(key);
        if (cached != null) return new ArrayList<Listing>(cached);
        List<Listing> list = new ArrayList<Listing>();
        for (Listing l : data.listings.values()) {
            if (l.state != ListingState.LISTED) continue;
            if (l.expiresAt <= now) continue;
            if (c != null && l.category != c) continue;
            list.add(l);
        }
        Comparator<Listing> cmp;
        if (sort == SortMode.PRICE_LOW) cmp = Comparator.comparingDouble(l -> visiblePrice(l));
        else if (sort == SortMode.PRICE_HIGH) cmp = Comparator.comparingDouble((Listing l) -> visiblePrice(l)).reversed();
        else if (sort == SortMode.NEWEST) cmp = Comparator.comparingLong((Listing l) -> l.createdAt).reversed();
        else if (sort == SortMode.ENDING_SOON) cmp = Comparator.comparingLong(l -> l.expiresAt);
        else if (sort == SortMode.LIKES) cmp = Comparator.comparingInt((Listing l) -> l.likes == null ? 0 : l.likes.size()).reversed();
        else cmp = Comparator.comparing((Listing l) -> !isFeatured(l)).thenComparingLong(l -> l.expiresAt);
        list.sort(cmp);
        if (listingCache.size() > 48) listingCache.clear();
        listingCache.put(key, new ArrayList<Listing>(list));
        return list;
    }

    public synchronized List<Listing> auctions() {
        List<Listing> out = new ArrayList<Listing>();
        for (Listing l : active(null, SortMode.ENDING_SOON)) if (l.type == ListingType.AUCTION) out.add(l);
        return out;
    }

    public synchronized List<Listing> mine(UUID seller) {
        return data.listings.values().stream()
                .filter(l -> seller.equals(l.seller))
                .filter(l -> l.state == ListingState.LISTED)
                .sorted(Comparator.comparingLong((Listing l)->l.expiresAt))
                .collect(Collectors.toList());
    }

    public synchronized List<Offer> offersFor(UUID seller) {
        return data.offers.values().stream()
                .filter(o -> o.state == OfferState.ESCROWED)
                .filter(o -> { Listing l = data.listings.get(o.listingId); return l != null && seller.equals(l.seller) && l.type == ListingType.FIXED && l.state == ListingState.LISTED; })
                .sorted(Comparator.comparingDouble((Offer o)->o.amount).reversed())
                .collect(Collectors.toList());
    }

    public synchronized List<Offer> myOffers(UUID buyer) {
        return data.offers.values().stream()
                .filter(o -> buyer.equals(o.buyer))
                .filter(o -> o.state == OfferState.ESCROWED)
                .sorted(Comparator.comparingLong((Offer o)->o.createdAt).reversed())
                .collect(Collectors.toList());
    }

    public synchronized List<StorageEntry> storage(UUID owner) { return data.storage.stream().filter(s -> owner.equals(s.owner)).collect(Collectors.toList()); }
    public synchronized List<PendingPayout> payouts(UUID owner) { return data.payouts.stream().filter(p -> owner.equals(p.owner)).collect(Collectors.toList()); }
    public synchronized int credits(UUID player) { return data.credits.getOrDefault(player.toString(), 0); }
    public synchronized Map<String, Stats> stats() { return data.stats; }
    public synchronized List<TransactionEntry> transactions() { return new ArrayList<TransactionEntry>(data.transactions); }
    public synchronized List<NotificationEntry> notifications(UUID owner) {
        List<NotificationEntry> out = new ArrayList<NotificationEntry>();
        if (owner == null) return out;
        for (NotificationEntry n : data.notifications) if (owner.equals(n.owner)) out.add(n);
        out.sort(Comparator.comparingLong((NotificationEntry n) -> n.createdAt).reversed());
        return out;
    }
    public synchronized boolean isFavorite(UUID owner, String listingId) {
        Set<String> set = data.favorites == null ? null : data.favorites.get(owner.toString());
        return set != null && set.contains(listingId);
    }
    public synchronized int recoveryItemCount() { return data.recoveryStorage == null ? 0 : data.recoveryStorage.size(); }

    private void remember(ServerPlayerEntity p) {
        if (p == null) return;
        String key = p.getUniqueID().toString();
        String name = p.getGameProfile() == null ? key.substring(0, Math.min(8, key.length())) : p.getGameProfile().getName();
        if (data.playerNames != null) data.playerNames.put(key, name);
        Stats st = data.stats == null ? null : data.stats.get(key);
        if (st != null) st.playerName = name;
    }

    public synchronized String rateLimitNavigation(ServerPlayerEntity p) {
        String limited = rateLimit(p, "NAV");
        return limited == null ? "" : limited;
    }

    public synchronized void clearSession(UUID player) {
        if (player == null) return;
        wizards.remove(player);
        String prefix = player.toString() + ":";
        actionRateLimit.keySet().removeIf(k -> k.startsWith(prefix));
    }

    public synchronized String adminRecoverLocks(ServerPlayerEntity p) {
        if (!isAdmin(p)) return "Bu işlem için OP yetkisi gerekli.";
        int recovered = recoverLockedListings("admin_manual");
        tx(TransactionType.ADMIN_ACTION, TransactionStatus.COMMITTED, "", null, p.getUniqueID(), null, recovered, 0, "", "Manual lock/recovery sweep");
        save();
        return "Kurtarma taraması tamamlandı. Açılan kilit: " + recovered + ". Kurtarma item kuyruğu: " + recoveryItemCount();
    }

    public synchronized String adminEconomyTest(ServerPlayerEntity p) {
        if (!isAdmin(p)) return "Bu işlem için OP yetkisi gerekli.";
        boolean ok = EconomyBridge.forceReconnect();
        String status = EconomyBridge.debugStatus();
        tx(TransactionType.ECONOMY_TEST, ok ? TransactionStatus.COMMITTED : TransactionStatus.FAILED, "", null, p.getUniqueID(), null, 0, 0, "", status);
        save();
        return ok ? "Ekonomi bridge hazır: " + status : "Ekonomi bridge bulunamadı: " + status;
    }



    public synchronized String adminResolveOneRecovery(ServerPlayerEntity p) {
        if (!isAdmin(p)) return "Bu işlem için OP yetkisi gerekli.";
        if (data.recoveryStorage == null || data.recoveryStorage.isEmpty()) return "Kurtarma kuyruğunda item yok.";
        StorageEntry s = data.recoveryStorage.get(0);
        if (ownerStorageCount(s.owner) >= RuasolMarket.CONFIG.maxStorageItemsPerPlayer) {
            tx(TransactionType.RECOVERY_RESOLVE, TransactionStatus.FAILED, "", null, p.getUniqueID(), s.owner, 0, 0, "", "Recovery resolve blocked: owner storage is full for " + s.itemName);
            save();
            return "Kurtarma çözülemedi: oyuncunun deposu dolu. Item kurtarma kuyruğunda bırakıldı: " + s.itemName;
        }
        data.recoveryStorage.remove(0);
        data.storage.add(s);
        tx(TransactionType.RECOVERY_RESOLVE, TransactionStatus.RECOVERED, "", null, p.getUniqueID(), s.owner, 0, 0, "", "Recovery item normal depoya alındı: " + s.itemName);
        notifyPlayer(s.owner, "Kurtarma çözüldü", s.itemName + " isimli itemin normal depoya alındı.", "");
        save();
        return "Bir recovery item normal depoya alındı: " + s.itemName;
    }

    public synchronized String adminReport(ServerPlayerEntity p) {
        if (!isAdmin(p)) return "Bu işlem için OP yetkisi gerekli.";
        return "Rapor: ilan=" + data.listings.size() + ", teklif=" + data.offers.size() + ", depo=" + data.storage.size() + ", recovery=" + recoveryItemCount() + ", tx=" + data.transactions.size() + ", bildirim=" + data.notifications.size();
    }



    public synchronized String adminSelfTest(ServerPlayerEntity p, String mode) {
        if (!isAdmin(p)) return "Bu işlem için OP yetkisi gerekli.";
        String m = mode == null ? "all" : mode.toLowerCase(Locale.ROOT);
        StringBuilder b = new StringBuilder();
        if (m.equals("all") || m.equals("economy")) b.append("Ekonomi=").append(EconomyBridge.isReady() ? "HAZIR" : "KILITLI").append(" ").append(EconomyBridge.mode()).append("; ");
        if (m.equals("all") || m.equals("storage")) b.append("Depo=").append(data.storage.size()).append("/").append(RuasolMarket.CONFIG.maxStorageItemsPerPlayer).append(" recovery=").append(recoveryItemCount()).append("; ");
        if (m.equals("all") || m.equals("auction")) { int auctions=0; for (Listing l:data.listings.values()) if(l.type==ListingType.AUCTION && l.state==ListingState.LISTED) auctions++; b.append("Aktif müzayede=").append(auctions).append("; "); }
        if (m.equals("all") || m.equals("snapshot")) { String js = snapshotJson(p, "HOME", 0, "", "selftest"); b.append("SnapshotBytes=").append(js.length()).append("; "); }
        tx(TransactionType.ADMIN_ACTION, TransactionStatus.COMMITTED, "", null, p.getUniqueID(), null, 0, 0, "", "Admin selftest: " + m + " => " + b.toString());
        save();
        return b.length() == 0 ? "Bilinmeyen test modu. economy/storage/auction/snapshot/all" : b.toString();
    }

    public synchronized void addCredits(UUID player, int amount) {
        int safe = Math.max(0, amount);
        data.credits.put(player.toString(), Math.max(0, credits(player) + safe));
        notifyPlayer(player, "Pazar Kredisi", "Pazar Kredin arttı: +" + safe + " / yeni bakiye: " + credits(player), "");
        tx(TransactionType.CREDIT_ADMIN, TransactionStatus.COMMITTED, "", null, null, player, safe, 0, "", "Admin credit give");
        save();
    }

    public synchronized void removeCredits(UUID player, int amount) {
        int safe = Math.max(0, amount);
        data.credits.put(player.toString(), Math.max(0, credits(player) - safe));
        notifyPlayer(player, "Pazar Kredisi", "Pazar Kredin azaltıldı: -" + safe + " / yeni bakiye: " + credits(player), "");
        tx(TransactionType.CREDIT_ADMIN, TransactionStatus.COMMITTED, "", null, null, player, -safe, 0, "", "Admin credit take");
        save();
    }

    public synchronized boolean takeCredits(UUID player, int amount) {
        if (amount <= 0) return true;
        int c = credits(player);
        if (c < amount) return false;
        data.credits.put(player.toString(), c - amount);
        return true;
    }

    private int asCreditAmount(double amount) { if (!Double.isFinite(amount) || amount <= 0D) return 0; if (amount >= Integer.MAX_VALUE) return Integer.MAX_VALUE; return Math.max(0, (int)Math.round(amount)); }
    private boolean isWholeCredit(double amount) { return Double.isFinite(amount) && Math.abs(amount - Math.rint(amount)) < 0.0000001D; }
    private String trimText(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        if (max <= 0) return "";
        return t.length() <= max ? t : t.substring(0, Math.max(0, max - 3)) + "...";
    }

    private double fixedDurationFee(int days) {
        if (days >= 5) return Math.max(0D, RuasolMarket.CONFIG.fixedFiveDayFee);
        if (days >= 3) return Math.max(0D, RuasolMarket.CONFIG.fixedThreeDayFee);
        return 0D;
    }

    private int auctionDurationCreditCost(int hours) {
        if (hours >= 5) return Math.max(0, RuasolMarket.CONFIG.auctionFiveHourCreditCost);
        if (hours >= 3) return Math.max(0, RuasolMarket.CONFIG.auctionThreeHourCreditCost);
        return 0;
    }

    private boolean sameTemplate(ItemStack stack, ItemStack template) {
        if (stack == null || template == null || stack.isEmpty() || template.isEmpty()) return false;
        return ItemStack.areItemsEqual(stack, template) && ItemStack.areItemStackTagsEqual(stack, template);
    }

    private int countMatchingInventory(ServerPlayerEntity p, ItemStack template) {
        if (p == null || template == null || template.isEmpty()) return 0;
        int total = 0;
        for (int i = 0; i < p.inventory.getSizeInventory(); i++) {
            ItemStack slot = p.inventory.getStackInSlot(i);
            if (sameTemplate(slot, template)) total += slot.getCount();
        }
        return total;
    }


    private int listingQuantity(Listing l, ItemStack decodedTemplate) {
        int q = l == null ? 1 : Math.max(1, l.quantity);
        if ((l == null || l.quantity <= 1) && decodedTemplate != null && !decodedTemplate.isEmpty()) q = Math.max(q, decodedTemplate.getCount());
        return q;
    }

    private List<ItemStack> splitStacks(ItemStack template, int quantity) {
        List<ItemStack> out = new ArrayList<ItemStack>();
        if (template == null || template.isEmpty() || quantity <= 0) return out;
        ItemStack base = template.copy();
        base.setCount(1);
        int max = Math.max(1, Math.min(base.getMaxStackSize(), 64));
        int left = quantity;
        while (left > 0) {
            int take = Math.min(max, left);
            ItemStack part = base.copy();
            part.setCount(take);
            out.add(part);
            left -= take;
        }
        return out;
    }

    private int freeCapacity(ServerPlayerEntity p, ItemStack template) {
        if (p == null || template == null || template.isEmpty()) return 0;
        ItemStack base = template.copy(); base.setCount(1);
        int max = Math.max(1, Math.min(base.getMaxStackSize(), 64));
        int cap = 0;
        for (int i = 0; i < p.inventory.getSizeInventory(); i++) {
            ItemStack slot = p.inventory.getStackInSlot(i);
            if (slot.isEmpty()) cap += max;
            else if (sameTemplate(slot, base)) cap += Math.max(0, max - slot.getCount());
        }
        return cap;
    }

    private boolean canFitAll(ServerPlayerEntity p, ItemStack template, int quantity) {
        return p != null && freeCapacity(p, template) >= quantity;
    }

    private boolean addSplitStacksToInventory(ServerPlayerEntity p, ItemStack template, int quantity) {
        if (!canFitAll(p, template, quantity)) return false;
        for (ItemStack part : splitStacks(template, quantity)) if (!p.inventory.addItemStackToInventory(part.copy())) return false;
        return true;
    }

    private ItemStack extractMatchingInventory(ServerPlayerEntity p, ItemStack template, int amount) {
        if (p == null || template == null || template.isEmpty() || amount <= 0) return ItemStack.EMPTY;
        int need = amount;
        ItemStack out = template.copy();
        out.setCount(0);
        for (int i = 0; i < p.inventory.getSizeInventory() && need > 0; i++) {
            ItemStack slot = p.inventory.getStackInSlot(i);
            if (!sameTemplate(slot, template)) continue;
            int take = Math.min(need, slot.getCount());
            if (take <= 0) continue;
            if (out.isEmpty()) { out = slot.copy(); out.setCount(take); }
            else out.setCount(out.getCount() + take);
            slot.shrink(take);
            if (slot.getCount() <= 0) p.inventory.setInventorySlotContents(i, ItemStack.EMPTY);
            need -= take;
        }
        return need == 0 ? out : ItemStack.EMPTY;
    }

    public synchronized String setWizardPrice(ServerPlayerEntity p, double price) {
        remember(p);
        if (!Double.isFinite(price)) return "Fiyat geçersiz.";
        if (price < 0) return "Fiyat negatif olamaz.";
        if (price > RuasolMarket.CONFIG.maxRcTransactionAmount) return "Fiyat güvenlik sınırını aşıyor. Max: " + fmt(RuasolMarket.CONFIG.maxRcTransactionAmount);
        wizard(p).price = price;
        return "Fiyat ayarlandı: " + fmt(price);
    }

    public synchronized String setWizardMode(ServerPlayerEntity p, ListingType type) {
        Wizard w = wizard(p);
        w.mode = type == null ? ListingType.FIXED : type;
        if (w.mode == ListingType.FIXED) w.days = Math.max(1, w.days);
        return w.mode == ListingType.AUCTION ? "Açık artırma modu seçildi." : "Sabit fiyat modu seçildi.";
    }

    public synchronized String setWizardAuctionCurrency(ServerPlayerEntity p, CurrencyUnit unit) {
        Wizard w = wizard(p);
        w.auctionCurrency = unit == null ? CurrencyUnit.RC : unit;
        if (w.auctionCurrency == CurrencyUnit.MARKET_CREDIT && !RuasolMarket.CONFIG.allowMarketCreditAuctions) {
            w.auctionCurrency = CurrencyUnit.RC;
            return "Pazar Kredisi açık artırmaları configte kapalı; birim RC olarak ayarlandı.";
        }
        return "Açık artırma birimi: " + w.auctionCurrency.title;
    }

    public synchronized String setWizardDays(ServerPlayerEntity p, int days) {
        int selected = days >= 5 ? 5 : (days >= 3 ? 3 : 1);
        wizard(p).days = selected;
        return "İlan süresi: " + selected + " gün.";
    }

    public synchronized String setWizardAuctionHours(ServerPlayerEntity p, int hours) {
        int selected = hours >= 5 ? 5 : (hours >= 3 ? 3 : Math.max(1, Math.min(2, hours)));
        wizard(p).auctionHours = Math.max(1, Math.min(Math.max(5, RuasolMarket.CONFIG.maxAuctionHours), selected));
        return "Açık artırma süresi: " + wizard(p).auctionHours + " saat.";
    }

    public synchronized String setWizardQuantity(ServerPlayerEntity p, int quantity) {
        wizard(p).quantity = Math.max(1, quantity);
        return "Adet ayarlandı: " + wizard(p).quantity;
    }

    public synchronized String toggleWizardFeature(ServerPlayerEntity p) {
        Wizard w = wizard(p);
        w.featuredBoost = !w.featuredBoost;
        return w.featuredBoost ? "Öne Çıkar seçildi. Bedel: " + RuasolMarket.CONFIG.featuredCreditCost + " Pazar Kredisi." : "Öne Çıkar kaldırıldı.";
    }

    public synchronized String toggleWizardAnnouncement(ServerPlayerEntity p) {
        Wizard w = wizard(p);
        w.broadcastBoost = !w.broadcastBoost;
        return w.broadcastBoost ? "Herkese Duyuru seçildi. Bedel: " + RuasolMarket.CONFIG.announcementCreditCost + " Pazar Kredisi." : "Herkese Duyuru kaldırıldı.";
    }

    public synchronized String startWizardFromHand(ServerPlayerEntity p) {
        remember(p);
        ItemStack hand = p.getHeldItemMainhand();
        if (hand.isEmpty()) return "Elinde pazara koyulacak bir ürün olmalı.";
        try {
            ItemStack template = hand.copy();
            template.setCount(1);
            String encoded = ItemCodec.encode(template);
            String invalid = ItemCodec.validateTradable(template, encoded);
            if (invalid != null) return invalid;
            Wizard w = wizard(p);
            w.itemBase64 = encoded;
            w.itemName = hand.getDisplayName().getString();
            w.itemRegistryName = ItemCodec.registryName(hand);
            w.itemHash = ItemCodec.sha256(encoded);
            w.category = ItemCodec.categoryOf(hand);
            w.quantity = Math.max(1, Math.min(hand.getCount(), 64));
            return "Ürün önizlemeye alındı: " + w.itemName + " §8(İlan onaylanana kadar item elinde kalır.)";
        } catch (Exception e) {
            RuasolMarket.LOG.error("Could not encode item", e);
            return "Ürün verisi okunamadı. Bu item güvenli şekilde pazara alınmadı.";
        }
    }

    public synchronized String createListing(ServerPlayerEntity p, Wizard w) {
        remember(p);
        if (w == null || w.itemBase64 == null) return "Önce elindeki ürünü seçmelisin.";
        if (mine(p.getUniqueID()).size() >= RuasolMarket.CONFIG.maxActiveListingsPerPlayer) return "Aktif ilan sınırına ulaştın.";
        if (w.mode != ListingType.AUCTION && !EconomyBridge.isReady()) return "Ekonomi sağlayıcısı kilitli. RC sabit ilan açılamaz.";

        ItemStack template;
        try { template = ItemCodec.decode(w.itemBase64); }
        catch (Exception e) { return "Seçili ürün şablonu okunamadı. Lütfen ürünü yeniden seç."; }
        if (template.isEmpty()) return "Seçili ürün verisi bozuk görünüyor. Lütfen ürünü yeniden seç.";
        template.setCount(1);
        String templateEncoded;
        try { templateEncoded = ItemCodec.encode(template.copy()); } catch (Exception e) { return "Seçili ürün şablonu işlenemedi."; }
        String invalid = ItemCodec.validateTradable(template.copy(), templateEncoded);
        if (invalid != null) return invalid;

        int quantity = Math.max(1, w.quantity);
        int available = countMatchingInventory(p, template);
        if (available < quantity) return "Envanterinde yeterli ürün yok. Gerekli: " + quantity + ", bulunan: " + available;

        boolean auction = w.mode == ListingType.AUCTION;
        int days = w.days >= 5 ? 5 : (w.days >= 3 ? 3 : 1);
        int hours = w.auctionHours >= 5 ? 5 : (w.auctionHours >= 3 ? 3 : Math.max(1, Math.min(2, w.auctionHours)));
        double fee = auction ? 0D : fixedDurationFee(days);
        int creditCost = (auction ? auctionDurationCreditCost(hours) : 0)
                + (w.legendarySeal ? RuasolMarket.CONFIG.legendarySealCreditCost : 0)
                + (w.featuredBoost ? RuasolMarket.CONFIG.featuredCreditCost : 0)
                + (w.broadcastBoost ? RuasolMarket.CONFIG.announcementCreditCost : 0);
        CurrencyUnit auctionCurrency = w.auctionCurrency == null ? CurrencyUnit.RC : w.auctionCurrency;
        if (auction && auctionCurrency == CurrencyUnit.RC && !EconomyBridge.isReady()) return "Ekonomi sağlayıcısı kilitli. RC açık artırma açılamaz.";
        if (auctionCurrency == CurrencyUnit.MARKET_CREDIT && !RuasolMarket.CONFIG.allowMarketCreditAuctions) return "Pazar Kredisi açık artırmaları configte kapalı.";
        if (creditCost > 0 && credits(p.getUniqueID()) < creditCost) return "Bu işlem için yeterli Pazar Kredin yok. Gerekli: " + creditCost;
        if (!EconomyBridge.has(p, fee)) return "Yeterli bakiyen yok. Gerekli ilan ücreti: " + fmt(fee);

        double price = Math.max(0D, w.price);
        if (!auction && price <= 0D) return "Sabit ilan fiyatı sıfırdan büyük olmalı.";
        if (auction && auctionCurrency == CurrencyUnit.MARKET_CREDIT) {
            if (!isWholeCredit(price)) return "Pazar Kredisi açık artırma başlangıç fiyatı tam sayı olmalı.";
            if (price > RuasolMarket.CONFIG.maxCreditTransactionAmount) return "Pazar Kredisi başlangıç fiyatı güvenlik sınırını aşıyor. Max: " + RuasolMarket.CONFIG.maxCreditTransactionAmount;
            price = asCreditAmount(price);
        }
        if (auction && price <= 0D) return "Açık artırma başlangıç fiyatı sıfırdan büyük olmalı.";

        ItemStack escrowItem = extractMatchingInventory(p, template, quantity);
        if (escrowItem.isEmpty() || escrowItem.getCount() < quantity) return "Seçili üründen istenen adet ayrıştırılamadı. Lütfen tekrar dene.";
        ItemStack listingTemplate = template.copy();
        listingTemplate.setCount(1);
        String escrowEncoded;
        try { escrowEncoded = ItemCodec.encode(listingTemplate.copy()); } catch (Exception e) {
            restoreToPlayerOrStorage(p, escrowItem, "İlan açma sırasında kodlama hatası; item iade edildi.");
            return "Seçili ürün paketlenemedi. Item iade edildi.";
        }
        String escrowHash = ItemCodec.sha256(escrowEncoded);

        if (!EconomyBridge.withdraw(p, fee)) {
            restoreToPlayerOrStorage(p, escrowItem, "İlan ücreti çekilemedi; item iade edildi.");
            return "İlan ücreti çekilemedi. Item iade edildi.";
        }
        if (!takeCredits(p.getUniqueID(), creditCost)) {
            EconomyBridge.deposit(p, fee);
            restoreToPlayerOrStorage(p, escrowItem, "Kredi çekilemedi; item iade edildi.");
            return "Kredi çekilemedi. Ücret ve item iade edildi.";
        }

        Listing l = new Listing();
        l.seller = p.getUniqueID();
        l.sellerName = p.getGameProfile().getName();
        l.itemBase64 = escrowEncoded;
        l.itemName = w.itemName == null ? escrowItem.getDisplayName().getString() : w.itemName;
        l.itemRegistryName = w.itemRegistryName == null ? ItemCodec.registryName(escrowItem) : w.itemRegistryName;
        l.itemHash = escrowHash;
        l.quantity = quantity;
        l.category = w.category == null ? Category.OTHER : w.category;
        l.type = auction ? ListingType.AUCTION : ListingType.FIXED;
        l.currency = auction ? auctionCurrency : CurrencyUnit.RC;
        l.state = ListingState.LISTED;
        l.legendary = w.legendarySeal;
        l.featuredUntil = w.featuredBoost ? System.currentTimeMillis() + RuasolMarket.CONFIG.featuredHours * 3600000L : 0L;
        l.announced = w.broadcastBoost;
        l.guildId = primaryGuildOf(p.getUniqueID());
        l.buyoutPrice = auction ? 0D : price;
        l.startPrice = auction ? price : 0D;
        l.createdAt = System.currentTimeMillis();
        l.expiresAt = l.createdAt + (auction ? hours * 3600000L : days * 86400000L);
        data.listings.put(l.id, l);
        stat(p.getUniqueID()).auctionsStarted += auction ? 1 : 0;
        tx(TransactionType.LISTING_CREATE, TransactionStatus.STARTED, l.id, null, p.getUniqueID(), null, price, 0, l.itemHash, auction ? "Auction create started" : "Fixed listing create started");
        if (!save()) {
            data.listings.remove(l.id);
            if (auction) stat(p.getUniqueID()).auctionsStarted = Math.max(0, stat(p.getUniqueID()).auctionsStarted - 1);
            if (fee > 0) EconomyBridge.deposit(p, fee);
            if (creditCost > 0) addCreditsNoSave(p.getUniqueID(), creditCost);
            restoreToPlayerOrStorage(p, escrowItem, "İlan veri dosyasına yazılamadı; item iade edildi.");
            tx(TransactionType.LISTING_CREATE, TransactionStatus.FAILED, l.id, null, p.getUniqueID(), null, price, 0, l.itemHash, "Create save failed; rolled back");
            save();
            return "İlan güvenli şekilde iptal edildi: veri dosyası yazılamadı, item/ücret iade edilmeye çalışıldı.";
        }
        tx(TransactionType.LISTING_CREATE, TransactionStatus.COMMITTED, l.id, null, p.getUniqueID(), null, price, 0, l.itemHash, auction ? "Auction created" : "Fixed listing created");
        if (fee > 0) tx(TransactionType.LISTING_FEE, TransactionStatus.COMMITTED, l.id, null, p.getUniqueID(), null, fee, 0, l.itemHash, "Listing duration fee");
        clearWizard(p);
        save();
        String baseLine = l.itemName + " x" + quantity + " pazara mühürlendi.";
        notifyPlayer(p.getUniqueID(), auction ? "Müzayede başladı" : "İlan açıldı", baseLine, l.id);
        if (auction) broadcast(p.getServer(), "§6[Pazar] §e" + p.getGameProfile().getName() + " §7kadim salonda açık artırma başlattı: §f" + l.itemName + " x" + quantity + " §8Birim: §6" + l.currency.title + " §8(/pazar)");
        else if (w.broadcastBoost) broadcast(p.getServer(), "§6[Pazar Duyurusu] §e" + p.getGameProfile().getName() + " §7yeni ilan açtı: §f" + l.itemName + " x" + quantity + " §8- §6" + fmt(price) + " RC §8(/pazar)");
        return (auction ? "Açık artırma başladı" : "İlan açıldı") + ": #" + l.id + " / " + l.itemName + " x" + quantity;
    }

    public synchronized String buy(ServerPlayerEntity buyer, String listingId) {

        remember(buyer);
        Listing l = data.listings.get(listingId);
        if (l == null || l.state != ListingState.LISTED) return "İlan bulunamadı veya aktif değil.";
        if (l.type != ListingType.FIXED) return "Bu ürün açık artırmada; teklif verilmeli.";
        if (!EconomyBridge.isReady()) return "Ekonomi sağlayıcısı kilitli. RC satın alma yapılamaz.";
        if (buyer.getUniqueID().equals(l.seller)) return "Kendi ilanını satın alamazsın.";
        l.state = ListingState.LOCKED_TRANSACTION;
        l.lockedAt = System.currentTimeMillis();
        tx(TransactionType.BUY, TransactionStatus.STARTED, l.id, null, buyer.getUniqueID(), l.seller, l.buyoutPrice, 0, l.itemHash, "Buy started");
        if (!save()) { unlock(l); return "Satın alma başlatılamadı: veri dosyası kilit kaydını yazamadı."; }
        try {
            ItemStack item = ItemCodec.decode(l.itemBase64);
            int quantity = listingQuantity(l, item);
            if (!canFitAll(buyer, item, quantity)) { unlock(l); return "Envanterinde bu alış için yeterli yer yok. Önce yer açmalısın."; }
            if (!EconomyBridge.withdraw(buyer, l.buyoutPrice)) { unlock(l); return "Yeterli bakiyen yok."; }
            if (!addSplitStacksToInventory(buyer, item, quantity)) {
                EconomyBridge.deposit(buyer, l.buyoutPrice);
                unlock(l);
                return "Item teslim edilemedi; para iade edildi.";
            }
            double tax = tax(l.buyoutPrice);
            collectTax(tax, l.currency, l.id);
            payOrQueue(l.seller, l.sellerName, l.buyoutPrice - tax, CurrencyUnit.RC, "Satış geliri: #" + l.id, l.id);
            l.state = ListingState.SOLD;
            l.closeReason = "sold_fixed";
            refundOpenOffers(l.id, "İlan satıldı; teklif iade edildi.");
            stat(buyer.getUniqueID()).purchases++; stat(buyer.getUniqueID()).spent += l.buyoutPrice;
            stat(l.seller).sales++; stat(l.seller).earned += l.buyoutPrice - tax;
            tx(TransactionType.BUY, TransactionStatus.COMMITTED, l.id, null, buyer.getUniqueID(), l.seller, l.buyoutPrice, tax, l.itemHash, "Buy committed");
            notifyPlayer(l.seller, "Ürünün satıldı", l.itemName + " satıldı. Satıcı payı: " + fmt(l.buyoutPrice - tax) + " RC", l.id);
            notifyPlayer(buyer.getUniqueID(), "Satın alma tamamlandı", l.itemName + " envanterine teslim edildi.", l.id);
            if (!criticalSave(l, "Buy final save failed after item/coin transfer")) return "Satın alma tamamlandı fakat recovery kaydı açıldı. OP panelde kontrol edilmeli. Vergi: " + fmt(tax);
            return "Satın alındı. Vergi: " + fmt(tax) + ". Satıcı payı: " + fmt(l.buyoutPrice - tax);
        } catch (Exception e) {
            RuasolMarket.LOG.error("Buy failed", e);
            unlock(l);
            tx(TransactionType.BUY, TransactionStatus.FAILED, l.id, null, buyer.getUniqueID(), l.seller, l.buyoutPrice, 0, l.itemHash, "Exception: " + e.getMessage());
            save();
            return "Satın alma güvenli şekilde iptal edildi.";
        }
    }

    public synchronized String offerOrBid(ServerPlayerEntity buyer, String listingId, double amount) {
        remember(buyer);
        Listing l = data.listings.get(listingId);
        if (l == null || l.state != ListingState.LISTED) return "İlan bulunamadı veya aktif değil.";
        if (buyer.getUniqueID().equals(l.seller)) return "Kendi ilanına teklif veremezsin.";
        if (!Double.isFinite(amount)) return "Teklif geçersiz.";
        if (amount <= 0) return "Teklif pozitif olmalı.";
        CurrencyUnit actionCurrency = l.type == ListingType.AUCTION ? l.currency : CurrencyUnit.RC;
        if (actionCurrency == CurrencyUnit.RC && amount > RuasolMarket.CONFIG.maxRcTransactionAmount) return "Teklif güvenlik sınırını aşıyor. Max: " + fmt(RuasolMarket.CONFIG.maxRcTransactionAmount);
        if (actionCurrency == CurrencyUnit.MARKET_CREDIT && amount > RuasolMarket.CONFIG.maxCreditTransactionAmount) return "Pazar Kredisi teklifi güvenlik sınırını aşıyor. Max: " + RuasolMarket.CONFIG.maxCreditTransactionAmount;
        if (l.type == ListingType.AUCTION && l.currency == CurrencyUnit.MARKET_CREDIT) {
            if (Math.abs(amount - Math.rint(amount)) > 0.0001D) return "Pazar Kredisi teklifleri tam sayı olmalı.";
            amount = asCreditAmount(amount);
            if (amount <= 0) return "Pazar Kredisi teklifleri pozitif tam sayı olmalı.";
        }
        return l.type == ListingType.AUCTION ? bidAuction(buyer, l, amount) : fixedOffer(buyer, l, amount);
    }

    private String fixedOffer(ServerPlayerEntity buyer, Listing l, double amount) {
        if (!EconomyBridge.isReady()) return "Ekonomi sağlayıcısı kilitli. RC teklif verilemez.";
        if (!EconomyBridge.withdraw(buyer, amount)) return "Bu teklif için yeterli bakiyen yok.";
        Offer o = new Offer();
        o.listingId = l.id;
        o.buyer = buyer.getUniqueID();
        o.buyerName = buyer.getGameProfile().getName();
        o.amount = amount;
        o.currency = CurrencyUnit.RC;
        o.state = OfferState.ESCROWED;
        o.escrowed = true;
        data.offers.put(o.id, o);
        stat(buyer.getUniqueID()).offersMade++;
        tx(TransactionType.FIXED_OFFER, TransactionStatus.STARTED, l.id, o.id, buyer.getUniqueID(), l.seller, amount, 0, l.itemHash, "Fixed offer escrow start");
        if (!save()) {
            data.offers.remove(o.id);
            stat(buyer.getUniqueID()).offersMade = Math.max(0, stat(buyer.getUniqueID()).offersMade - 1);
            EconomyBridge.deposit(buyer, amount);
            tx(TransactionType.FIXED_OFFER, TransactionStatus.FAILED, l.id, o.id, buyer.getUniqueID(), l.seller, amount, 0, l.itemHash, "Offer save failed; refunded");
            save();
            return "Teklif kaydedilemedi; para iade edilmeye çalışıldı.";
        }
        tx(TransactionType.FIXED_OFFER, TransactionStatus.COMMITTED, l.id, o.id, buyer.getUniqueID(), l.seller, amount, 0, l.itemHash, "Fixed offer escrowed");
        save();
        return "Teklif verildi ve para emanet kasaya alındı: #" + o.id + " / " + fmt(amount);
    }

    private String bidAuction(ServerPlayerEntity buyer, Listing l, double amount) {
        double min = minNextBid(l);
        if (amount < min) return "Bu açık artırma için minimum teklif: " + fmtAmount(min, l.currency);
        UUID oldBidder = l.highestBidder;
        String oldName = l.highestBidderName;
        double oldAmount = l.highestBid;
        long oldExpires = l.expiresAt;
        boolean sameBidder = oldBidder != null && oldBidder.equals(buyer.getUniqueID());
        double withdrawAmount = sameBidder ? amount - oldAmount : amount;
        if (!withdrawFunds(buyer, l.currency, withdrawAmount)) return "Bu teklif için yeterli " + l.currency.title + " yok.";
        l.highestBidder = buyer.getUniqueID();
        l.highestBidderName = buyer.getGameProfile().getName();
        l.highestBid = amount;
        long now = System.currentTimeMillis();
        if (RuasolMarket.CONFIG.antiSnipeWindowSeconds > 0 && l.expiresAt - now <= RuasolMarket.CONFIG.antiSnipeWindowSeconds * 1000L) {
            l.expiresAt += RuasolMarket.CONFIG.antiSnipeExtendSeconds * 1000L;
        }
        tx(TransactionType.AUCTION_BID, TransactionStatus.STARTED, l.id, null, buyer.getUniqueID(), l.seller, amount, 0, l.itemHash, "Auction bid escrow start");
        if (!save()) {
            l.highestBidder = oldBidder; l.highestBidderName = oldName; l.highestBid = oldAmount; l.expiresAt = oldExpires;
            refundFunds(buyer.getUniqueID(), buyer.getGameProfile().getName(), withdrawAmount, l.currency, "Teklif kaydedilemedi; iade edildi: #" + l.id, l.id);
            tx(TransactionType.AUCTION_BID, TransactionStatus.FAILED, l.id, null, buyer.getUniqueID(), l.seller, amount, 0, l.itemHash, "Bid save failed; refunded new bidder");
            save();
            return "Teklif kaydedilemedi; emanet para/kredi iade edilmeye çalışıldı.";
        }
        if (!sameBidder && oldBidder != null && oldAmount > 0) { refundFunds(oldBidder, oldName, oldAmount, l.currency, "Açık artırmada daha yüksek teklif geldi: #" + l.id, l.id); notifyPlayer(oldBidder, "Teklifin geçildi", l.itemName + " için teklifin geçildi; emanetin iade edildi.", l.id); }
        notifyPlayer(buyer.getUniqueID(), "Müzayede teklifi", l.itemName + " için lider teklif sende: " + fmtAmount(amount, l.currency), l.id);
        tx(TransactionType.AUCTION_BID, TransactionStatus.COMMITTED, l.id, null, buyer.getUniqueID(), l.seller, amount, 0, l.itemHash, "Auction bid escrowed");
        save();
        broadcastAuctionBid(buyer.getServer(), l, buyer.getGameProfile().getName(), amount);
        return "Açık artırma teklifin işlendi: " + fmtAmount(amount, l.currency);
    }

    public synchronized String acceptOffer(ServerPlayerEntity seller, String offerId) {
        remember(seller);
        Offer o = data.offers.get(offerId);
        if (o == null || o.state != OfferState.ESCROWED) return "Teklif bulunamadı veya aktif değil.";
        Listing l = data.listings.get(o.listingId);
        if (l == null || l.state != ListingState.LISTED) return "İlan artık aktif değil.";
        if (l.type != ListingType.FIXED) return "Açık artırma teklifleri süre sonunda otomatik sonuçlanır.";
        if (!seller.getUniqueID().equals(l.seller)) return "Bu ilan sana ait değil.";
        l.state = ListingState.LOCKED_TRANSACTION;
        l.lockedAt = System.currentTimeMillis();
        tx(TransactionType.OFFER_ACCEPT, TransactionStatus.STARTED, l.id, o.id, seller.getUniqueID(), o.buyer, o.amount, 0, l.itemHash, "Offer accept started");
        if (!save()) { unlock(l); return "Teklif kabulü başlatılamadı: veri dosyası kilit kaydını yazamadı."; }
        try {
            ItemStack item = ItemCodec.decode(l.itemBase64);
            ServerPlayerEntity buyer = seller.getServer().getPlayerList().getPlayerByUUID(o.buyer);
            if (!deliverOrStoreMany(buyer, o.buyer, o.buyerName, item, listingQuantity(l, item), "Kabul edilen teklif itemi: #" + l.id, l)) { save(); return "Alıcı deposu dolu; item admin recovery kuyruğuna alındı. OP panelde kontrol edilmeli."; }
            double tax = tax(o.amount);
            collectTax(tax, CurrencyUnit.RC, l.id);
            payOrQueue(l.seller, l.sellerName, o.amount - tax, CurrencyUnit.RC, "Teklif kabul geliri: #" + l.id, l.id);
            l.state = ListingState.SOLD; l.closeReason = "offer_accepted";
            o.state = OfferState.ACCEPTED;
            refundOpenOffersExcept(l.id, o.id, "Başka teklif kabul edildi; teklif iade edildi.");
            stat(o.buyer).purchases++; stat(o.buyer).spent += o.amount;
            stat(seller.getUniqueID()).sales++; stat(seller.getUniqueID()).earned += o.amount - tax; stat(seller.getUniqueID()).offersAccepted++;
            tx(TransactionType.OFFER_ACCEPT, TransactionStatus.COMMITTED, l.id, o.id, seller.getUniqueID(), o.buyer, o.amount, tax, l.itemHash, "Offer committed");
            notifyPlayer(o.buyer, "Teklif kabul edildi", l.itemName + " teklifin kabul edildi; item envanter/depona gider.", l.id);
            notifyPlayer(l.seller, "Teklif kabul edildi", l.itemName + " için " + fmt(o.amount) + " RC teklif kabul edildi.", l.id);
            if (!criticalSave(l, "Offer accept final save failed after transfer")) return "Teklif kabul edildi fakat recovery kaydı açıldı. OP panelde kontrol edilmeli.";
            return "Teklif kabul edildi. Alıcı offline/doluysa item deposuna gitti. Vergi: " + fmt(tax);
        } catch (Exception e) {
            RuasolMarket.LOG.error("Offer accept failed", e);
            unlock(l);
            tx(TransactionType.OFFER_ACCEPT, TransactionStatus.FAILED, l.id, o.id, seller.getUniqueID(), o.buyer, o.amount, 0, l.itemHash, "Exception: " + e.getMessage());
            save();
            return "Teklif güvenli şekilde iptal edildi.";
        }
    }

    public synchronized String cancelOffer(ServerPlayerEntity buyer, String offerId) {
        Offer o = data.offers.get(offerId);
        if (o == null || o.state != OfferState.ESCROWED) return "Aktif teklif bulunamadı.";
        if (!buyer.getUniqueID().equals(o.buyer)) return "Bu teklif sana ait değil.";
        refundOffer(o, "Teklif oyuncu tarafından iptal edildi.");
        save();
        return "Teklif iptal edildi ve para iade edildi.";
    }

    public synchronized String cancelListing(ServerPlayerEntity p, String listingId) {
        Listing l = data.listings.get(listingId);
        if (l == null || l.state != ListingState.LISTED) return "İlan bulunamadı veya aktif değil.";
        boolean admin = isAdmin(p);
        if (!admin && !p.getUniqueID().equals(l.seller)) return "Bu ilan sana ait değil.";
        if (l.type == ListingType.AUCTION && l.highestBidder != null) return "Teklif almış açık artırma iptal edilemez; süre bitişini beklemelisin.";
        try {
            ItemStack item = ItemCodec.decode(l.itemBase64);
            if (!deliverOrStoreMany(p.getUniqueID().equals(l.seller) ? p : null, l.seller, l.sellerName, item, listingQuantity(l, item), "İlan iptal iadesi: #" + l.id, l)) { save(); return "İade deposu dolu; item admin recovery kuyruğuna alındı."; }
            l.state = ListingState.CANCELLED; l.closeReason = admin ? "admin_cancel" : "seller_cancel";
            refundOpenOffers(l.id, "İlan iptal edildi; teklif iade edildi.");
            tx(TransactionType.CANCEL, TransactionStatus.COMMITTED, l.id, null, p.getUniqueID(), l.seller, 0, 0, l.itemHash, l.closeReason);
            if (!criticalSave(l, "Cancel final save failed after item return")) return "İlan iptal edildi fakat recovery kaydı açıldı. OP panelde kontrol edilmeli.";
            return "İlan iptal edildi. Item satıcıya/deposuna iade edildi.";
        } catch (Exception e) {
            RuasolMarket.LOG.error("Cancel failed", e);
            return "İlan iptali başarısız; item korunuyor.";
        }
    }

    public synchronized String feature(ServerPlayerEntity p, String id) {
        remember(p);
        Listing l = data.listings.get(id);
        if (l == null || l.state != ListingState.LISTED) return "İlan bulunamadı.";
        if (!p.getUniqueID().equals(l.seller)) return "Bu ilan sana ait değil.";
        int cost = Math.max(0, RuasolMarket.CONFIG.featuredCreditCost);
        if (!takeCredits(p.getUniqueID(), cost)) return "Yeterli Pazar Kredin yok.";
        long oldFeatured = l.featuredUntil;
        l.featuredUntil = Math.max(System.currentTimeMillis(), l.featuredUntil) + RuasolMarket.CONFIG.featuredHours * 3600000L;
        tx(TransactionType.FEATURE, TransactionStatus.COMMITTED, l.id, null, p.getUniqueID(), null, cost, 0, l.itemHash, "Featured listing");
        if (!save()) { addCreditsNoSave(p.getUniqueID(), cost); l.featuredUntil = oldFeatured; tx(TransactionType.FEATURE, TransactionStatus.FAILED, l.id, null, p.getUniqueID(), null, cost, 0, l.itemHash, "Feature save failed; credit refunded and timer rolled back"); save(); return "Öne çıkarma kaydedilemedi; kredi iade edildi."; }
        return "İlan öne çıkarıldı. Mühür süresi: " + RuasolMarket.CONFIG.featuredHours + " saat.";
    }

    public synchronized String like(ServerPlayerEntity p, String id) {
        remember(p);
        Listing l = data.listings.get(id);
        if (l == null || l.state != ListingState.LISTED) return "İlan bulunamadı.";
        if (p.getUniqueID().equals(l.seller)) return "Kendi ilanın için mühür bırakamazsın.";
        if (l.likes == null) l.likes = new LinkedHashSet<String>();
        String key = p.getUniqueID().toString();
        if (!l.likes.add(key)) return "Bu ürüne zaten mühür bırakmışsın.";
        stat(l.seller).likesReceived++;
        stat(p.getUniqueID()).likesGiven++;
        // Beğeni ekonomi işlemi değildir: WAL/transaction log şişirmemek için soft-save yeterlidir.
        softSave();
        return "Ürüne mühür bırakıldı.";
    }

    public synchronized String claimStorage(ServerPlayerEntity p, String id) {
        remember(p);
        Iterator<StorageEntry> it = data.storage.iterator();
        while (it.hasNext()) {
            StorageEntry s = it.next();
            if (s.id.equals(id) && p.getUniqueID().equals(s.owner)) {
                try {
                    ItemStack item = ItemCodec.decode(s.itemBase64);
                    int qty = Math.max(1, item.getCount());
                    ItemStack base = item.copy();
                    base.setCount(1);
                    if (!canFitAll(p, base, qty)) return "Envanterin dolu.";
                    if (!addSplitStacksToInventory(p, base, qty)) return "Item teslim edilemedi; depo kaydı korunuyor.";
                    it.remove();
                    tx(TransactionType.CLAIM_STORAGE, TransactionStatus.COMMITTED, "", null, p.getUniqueID(), null, 0, 0, "", s.reason);
                    save();
                    return "Depodan alındı: " + s.itemName;
                } catch (Exception e) { return "Depo itemi okunamadı; kayıt korunuyor."; }
            }
        }
        return "Depo kaydı bulunamadı.";
    }

    public synchronized String claimAllStorage(ServerPlayerEntity p) {
        remember(p);
        int count = 0;
        Iterator<StorageEntry> it = data.storage.iterator();
        while (it.hasNext()) {
            StorageEntry s = it.next();
            if (!p.getUniqueID().equals(s.owner)) continue;
            try {
                ItemStack item = ItemCodec.decode(s.itemBase64);
                int qty = Math.max(1, item.getCount());
                ItemStack base = item.copy();
                base.setCount(1);
                if (!canFitAll(p, base, qty)) break;
                if (!addSplitStacksToInventory(p, base, qty)) break;
                it.remove();
                count++;
                tx(TransactionType.CLAIM_STORAGE, TransactionStatus.COMMITTED, "", null, p.getUniqueID(), null, 0, 0, "", s.reason);
            } catch (Exception e) {
                RuasolMarket.LOG.warn("Claim-all skipped unreadable storage item", e);
                break;
            }
        }
        if (count > 0) save();
        return count == 0 ? "Alınabilecek depo itemi yok veya envanter dolu." : count + " depo itemi teslim edildi.";
    }

    public synchronized String processPayouts(ServerPlayerEntity p) {
        remember(p);
        double total = 0D;
        Iterator<PendingPayout> it = data.payouts.iterator();
        int processed = 0;
        int max = Math.max(1, RuasolMarket.CONFIG.maxPayoutsProcessedPerClick);
        while (it.hasNext() && processed < max) {
            PendingPayout pp = it.next();
            if (p.getUniqueID().equals(pp.owner) && pp.currency == CurrencyUnit.RC) {
                if (EconomyBridge.deposit(p, pp.amount)) {
                    total += pp.amount;
                    processed++;
                    tx(TransactionType.PAYOUT_DELIVER, TransactionStatus.COMMITTED, pp.listingId, null, null, p.getUniqueID(), pp.amount, 0, "", pp.reason);
                    it.remove();
                }
            }
        }
        if (total > 0) save();
        return total > 0 ? "Bekleyen pazar ödemen teslim edildi: " + fmt(total) : "Bekleyen ödeme yok.";
    }

    public synchronized void expireListings(MinecraftServer server) {
        long now = System.currentTimeMillis();
        boolean changed = false;
        int processedExpirations = 0;
        int maxExpirations = Math.max(1, RuasolMarket.CONFIG.maxExpirationsPerCheck);
        List<Listing> expired = new ArrayList<Listing>();
        for (Listing l : data.listings.values()) {
            if (l.state == ListingState.LISTED && l.expiresAt <= now) expired.add(l);
            if (l.state == ListingState.LOCKED_TRANSACTION && l.lockedAt > 0 && now - l.lockedAt > 30000L) {
                l.state = ListingState.LISTED;
                l.lockedAt = 0L;
                tx(TransactionType.RECOVERY, TransactionStatus.RECOVERED, l.id, null, null, l.seller, 0, 0, l.itemHash, "Stale lock recovered");
                changed = true;
            }
        }
        expired.sort(Comparator.comparingLong(l -> l.expiresAt));
        for (Listing l : expired) {
            if (processedExpirations >= maxExpirations) break;
            if (l.type == ListingType.AUCTION) settleAuction(server, l);
            else expireFixed(l);
            processedExpirations++;
            changed = true;
        }
        if (changed || softDirty) save();
    }

    private void expireFixed(Listing l) {
        try {
            ItemStack item = ItemCodec.decode(l.itemBase64);
            MinecraftServer server = net.minecraftforge.fml.server.ServerLifecycleHooks.getCurrentServer();
            ServerPlayerEntity seller = server == null ? null : server.getPlayerList().getPlayerByUUID(l.seller);
            if (!deliverOrStoreMany(seller, l.seller, l.sellerName, item, listingQuantity(l, item), "İlan süresi doldu: #" + l.id, l)) {
                tx(TransactionType.EXPIRE, TransactionStatus.FAILED, l.id, null, null, l.seller, 0, 0, l.itemHash, "Expire storage overflow; item moved to recovery queue");
                return;
            }
            l.state = ListingState.EXPIRED; l.closeReason = "expired";
            notifyPlayer(l.seller, "İlan süresi doldu", l.itemName + " satılmadı; item envanter/depona iade edildi.", l.id);
            refundOpenOffers(l.id, "İlan süresi doldu; teklif iade edildi.");
            tx(TransactionType.EXPIRE, TransactionStatus.COMMITTED, l.id, null, null, l.seller, 0, 0, l.itemHash, "Fixed listing expired");
        } catch (Exception e) {
            l.state = ListingState.FAILED_RECOVERY;
            tx(TransactionType.EXPIRE, TransactionStatus.FAILED, l.id, null, null, l.seller, 0, 0, l.itemHash, "Expire decode failed");
        }
    }

    private void settleAuction(MinecraftServer server, Listing l) {
        try {
            ItemStack item = ItemCodec.decode(l.itemBase64);
            int quantity = listingQuantity(l, item);
            if (l.highestBidder == null || l.highestBid <= 0D) {
                ServerPlayerEntity seller = server == null ? null : server.getPlayerList().getPlayerByUUID(l.seller);
                if (!deliverOrStoreMany(seller, l.seller, l.sellerName, item, quantity, "Teklif gelmeyen açık artırma iadesi: #" + l.id, l)) {
                    tx(TransactionType.AUCTION_SETTLE, TransactionStatus.FAILED, l.id, null, null, l.seller, 0, 0, l.itemHash, "No-bid auction storage overflow; item moved to recovery queue");
                    return;
                }
                l.state = ListingState.EXPIRED; l.closeReason = "auction_no_bid";
                tx(TransactionType.AUCTION_SETTLE, TransactionStatus.COMMITTED, l.id, null, null, l.seller, 0, 0, l.itemHash, "Auction expired without bid");
                return;
            }
            ServerPlayerEntity winner = server == null ? null : server.getPlayerList().getPlayerByUUID(l.highestBidder);
            if (!deliverOrStoreMany(winner, l.highestBidder, l.highestBidderName, item, quantity, "Kazanılan açık artırma itemi: #" + l.id, l)) {
                tx(TransactionType.AUCTION_SETTLE, TransactionStatus.FAILED, l.id, null, l.highestBidder, l.seller, l.highestBid, 0, l.itemHash, "Winner storage overflow; item moved to recovery queue");
                return;
            }
            double tax;
            double sellerAmount;
            if (l.currency == CurrencyUnit.MARKET_CREDIT) {
                int bidCredits = asCreditAmount(l.highestBid);
                int taxCredits = creditTax(bidCredits);
                tax = taxCredits;
                sellerAmount = Math.max(0, bidCredits - taxCredits);
            } else {
                tax = tax(l.highestBid);
                sellerAmount = Math.max(0D, l.highestBid - tax);
            }
            collectTax(tax, l.currency, l.id);
            payOrQueue(l.seller, l.sellerName, sellerAmount, l.currency, "Açık artırma geliri: #" + l.id, l.id);
            l.state = ListingState.SOLD; l.closeReason = "auction_settled";
            stat(l.highestBidder).purchases++; stat(l.highestBidder).spent += l.highestBid; stat(l.highestBidder).auctionsWon++;
            stat(l.seller).sales++; stat(l.seller).earned += sellerAmount;
            tx(TransactionType.AUCTION_SETTLE, TransactionStatus.COMMITTED, l.id, null, l.highestBidder, l.seller, l.highestBid, tax, l.itemHash, "Auction settled");
            notifyPlayer(l.highestBidder, "Müzayede kazanıldı", l.itemName + " açık artırmasını kazandın.", l.id);
            notifyPlayer(l.seller, "Müzayede sonuçlandı", l.itemName + " satıldı. Gelir: " + fmtAmount(sellerAmount, l.currency), l.id);
            broadcast(server, "§6[Pazar] §eMühür kırıldı. §f" + l.itemName + " §7açık artırmasını §b" + l.highestBidderName + " §7kazandı.");
        } catch (Exception e) {
            l.state = ListingState.FAILED_RECOVERY;
            tx(TransactionType.AUCTION_SETTLE, TransactionStatus.FAILED, l.id, null, l.highestBidder, l.seller, l.highestBid, 0, l.itemHash, "Auction settle failed");
        }
    }

    public synchronized void tryDeliverStorage(ServerPlayerEntity p) {
        int delivered = 0;
        for (StorageEntry s : new ArrayList<StorageEntry>(storage(p.getUniqueID()))) {
            try {
                ItemStack item = ItemCodec.decode(s.itemBase64);
                int qty = Math.max(1, item.getCount());
                ItemStack base = item.copy();
                base.setCount(1);
                if (canFitAll(p, base, qty) && addSplitStacksToInventory(p, base, qty)) {
                    data.storage.remove(s); delivered++;
                }
            } catch (Exception ignored) { }
        }
        if (delivered > 0) {
            p.sendMessage(msg("Depodan teslim edildi: " + delivered + " item.", TextFormatting.GOLD), p.getUniqueID());
            save();
        }
    }

    public double minNextBid(Listing l) {
        if (l == null || l.type != ListingType.AUCTION) return 0D;
        double base = l.highestBid > 0D ? l.highestBid : l.startPrice;
        double inc = Math.max(1D, base * RuasolMarket.CONFIG.minBidIncrementPercent / 100D);
        double next = l.highestBid > 0D ? base + inc : base;
        return l.currency == CurrencyUnit.MARKET_CREDIT ? asCreditAmount(next) : next;
    }

    public boolean isFeatured(Listing l) { return l != null && l.featuredUntil > System.currentTimeMillis(); }
    public double visiblePrice(Listing l) { return l.type == ListingType.AUCTION ? Math.max(l.startPrice, l.highestBid) : l.buyoutPrice; }
    public long remainingMillis(Listing l) { return Math.max(0L, l.expiresAt - System.currentTimeMillis()); }

    public Listing getListing(String id) { return data.listings.get(id); }

    private void unlock(Listing l) {
        if (l != null && l.state == ListingState.LOCKED_TRANSACTION) { l.state = ListingState.LISTED;
 l.lockedAt = 0L; save(); }
    }

    private void refundOpenOffers(String listingId, String reason) { refundOpenOffersExcept(listingId, null, reason); }

    private void refundOpenOffersExcept(String listingId, String exceptId, String reason) {
        for (Offer o : data.offers.values()) {
            if (listingId.equals(o.listingId) && o.state == OfferState.ESCROWED && (exceptId == null || !exceptId.equals(o.id))) refundOffer(o, reason);
        }
    }

    private void refundOffer(Offer o, String reason) {
        refundFunds(o.buyer, o.buyerName, o.amount, o.currency, reason, o.listingId);
        o.state = OfferState.REFUNDED;
        tx(TransactionType.OFFER_REFUND, TransactionStatus.COMMITTED, o.listingId, o.id, null, o.buyer, o.amount, 0, "", reason);
    }

    private boolean withdrawFunds(ServerPlayerEntity player, CurrencyUnit currency, double amount) {
        if (amount <= 0D) return true;
        if (currency == CurrencyUnit.MARKET_CREDIT) return takeCredits(player.getUniqueID(), asCreditAmount(amount));
        return EconomyBridge.withdraw(player, amount);
    }

    private void refundFunds(UUID owner, String name, double amount, CurrencyUnit currency, String reason, String listingId) {
        if (currency == CurrencyUnit.MARKET_CREDIT) {
            addCreditsNoSave(owner, (int)Math.ceil(amount));
            tx(TransactionType.AUCTION_REFUND, TransactionStatus.COMMITTED, listingId, null, null, owner, amount, 0, "", reason);
            return;
        }
        if (!EconomyBridge.depositOffline(owner, name, amount)) queuePayout(owner, name, amount, currency, reason, listingId);
    }

    private void payOrQueue(UUID owner, String name, double amount, CurrencyUnit currency, String reason, String listingId) {
        if (currency == CurrencyUnit.MARKET_CREDIT) {
            int creditAmount = asCreditAmount(amount);
            if (creditAmount > 0) addCreditsNoSave(owner, creditAmount);
            tx(TransactionType.PAYOUT_DELIVER, TransactionStatus.COMMITTED, listingId, null, null, owner, creditAmount, 0, "", reason + " (Pazar Kredisi)");
            return;
        }
        if (!EconomyBridge.depositOffline(owner, name, amount)) queuePayout(owner, name, amount, currency, reason, listingId);
    }

    private void queuePayout(UUID owner, String name, double amount, CurrencyUnit currency, String reason, String listingId) {
        PendingPayout pp = new PendingPayout();
        pp.owner = owner; pp.ownerName = name == null ? "?" : name; pp.amount = amount; pp.currency = currency == null ? CurrencyUnit.RC : currency; pp.reason = reason; pp.listingId = listingId == null ? "" : listingId;
        data.payouts.add(pp);
        tx(TransactionType.PAYOUT_QUEUE, TransactionStatus.COMMITTED, listingId, null, null, owner, amount, 0, "", reason);
    }

    private void addCreditsNoSave(UUID player, int amount) {
        data.credits.put(player.toString(), Math.max(0, credits(player)+amount));
    }


    public synchronized String snapshotJson(ServerPlayerEntity p, String view, int page, String selectedId, String message) {
        String json = gson.toJson(snapshot(p, view, page, selectedId, message));
        int warn = RuasolMarket.CONFIG == null ? 0 : RuasolMarket.CONFIG.snapshotDebugBytesWarn;
        if (warn > 0 && json.length() > warn) {
            RuasolMarket.LOG.warn("Large market snapshot for {} view={} bytes={}", p == null ? "?" : p.getGameProfile().getName(), view, json.length());
        }
        return json;
    }

    public synchronized MarketSnapshot snapshot(ServerPlayerEntity p, String view, int page, String selectedId, String message) {
        remember(p);
        MarketSnapshot snap = new MarketSnapshot();
        snap.view = view == null || view.isEmpty() ? "HOME" : view;
        snap.page = Math.max(0, page);
        snap.admin = isAdmin(p);
        snap.credits = credits(p.getUniqueID());
        snap.economyMode = EconomyBridge.mode();
        snap.economyReady = EconomyBridge.isReady();
        snap.storageCount = storage(p.getUniqueID()).size();
        snap.storageLimit = RuasolMarket.CONFIG.maxStorageItemsPerPlayer;
        snap.recoveryItems = recoveryItemCount();
        snap.auctionCreditCost = 0;
        snap.featuredCreditCost = RuasolMarket.CONFIG.featuredCreditCost;
        snap.legendarySealCreditCost = RuasolMarket.CONFIG.legendarySealCreditCost;
        snap.announcementCreditCost = RuasolMarket.CONFIG.announcementCreditCost;
        snap.auctionHour3CreditCost = RuasolMarket.CONFIG.auctionThreeHourCreditCost;
        snap.auctionHour5CreditCost = RuasolMarket.CONFIG.auctionFiveHourCreditCost;
        snap.fixed3DayFee = RuasolMarket.CONFIG.fixedThreeDayFee;
        snap.fixed5DayFee = RuasolMarket.CONFIG.fixedFiveDayFee;
        snap.taxPercent = RuasolMarket.CONFIG.taxPercent;
        snap.message = message == null ? "" : message;
        snap.searchQuery = selectedId == null ? "" : selectedId;
        snap.enableUiSounds = RuasolMarket.CONFIG == null || RuasolMarket.CONFIG.enableUiSounds;
        snap.treasuryLine = "Hazine: " + fmt(data.taxTreasuryRc) + " RC / " + data.taxTreasuryCredits + " PK";
        Stats ownStats = stat(p.getUniqueID());
        refreshReputation(ownStats);
        snap.profileSales = ownStats.sales;
        snap.profilePurchases = ownStats.purchases;
        snap.profileAuctionsWon = ownStats.auctionsWon;
        snap.profileLikesReceived = ownStats.likesReceived;
        snap.profileEarned = ownStats.earned;
        snap.profileSpent = ownStats.spent;
        snap.profileReputation = ownStats.reputation;
        snap.profileBadges = ownStats.badges == null || ownStats.badges.isEmpty() ? "Rozet yok" : String.join(", ", ownStats.badges);
        snap.profileActiveListings = mine(p.getUniqueID()).size();
        snap.profileNotificationCount = notifications(p.getUniqueID()).size();
        Wizard w = wizard(p);
        snap.wizard.hasItem = w.itemBase64 != null;
        snap.wizard.itemName = w.itemName == null ? "Ürün seçilmedi" : w.itemName;
        snap.wizard.registryName = w.itemRegistryName == null ? "minecraft:air" : w.itemRegistryName;
        snap.wizard.itemBase64 = w.itemBase64 == null ? "" : w.itemBase64;
        snap.wizard.category = w.category == null ? "Diğer" : w.category.title;
        snap.wizard.quantity = Math.max(1, w.quantity);
        snap.wizard.price = w.price;
        snap.wizard.mode = w.mode == ListingType.AUCTION ? "AUCTION" : "FIXED";
        snap.wizard.days = w.days;
        snap.wizard.auctionHours = w.auctionHours;
        snap.wizard.auctionCurrency = (w.auctionCurrency == null ? CurrencyUnit.RC : w.auctionCurrency).title;
        snap.wizard.legendarySeal = w.legendarySeal;
        snap.wizard.featuredBoost = w.featuredBoost;
        snap.wizard.broadcastBoost = w.broadcastBoost;

        if (selectedId != null && !selectedId.isEmpty()) {
            Listing l = data.listings.get(selectedId);
            if (l != null) snap.selected = viewOf(p, l, true);
        }

        List<Listing> src;
        SortMode sort = sortForView(snap.view);
        if ("INFO".equals(snap.view)) {
            snap.maxPages = 8;
            if (snap.page > 7) snap.page = 7;
            src = new ArrayList<Listing>();
        }
        else if ("PROFILE".equals(snap.view)) {
            src = new ArrayList<Listing>();
        }
        else if ("GUILDS".equals(snap.view)) { src = new ArrayList<Listing>(); fillGuildViews(snap, p); paginateGuilds(snap, 5); }
        else if (snap.view != null && snap.view.startsWith("GUILD_")) { String gid = snap.view.substring(6); snap.selectedGuildId = gid; fillGuildViews(snap, p); src = listingsForGuild(gid, sort); }
        else if ("LEGENDARY".equals(snap.view)) src = active(null, sort).stream().filter(l -> l.legendary).collect(Collectors.toList());
        else if ("AUCTIONS".equals(snap.view)) src = auctions();
        else if ("AUCTIONS_RC".equals(snap.view)) src = auctions().stream().filter(l -> l.currency == CurrencyUnit.RC).collect(Collectors.toList());
        else if ("AUCTIONS_CREDIT".equals(snap.view)) src = auctions().stream().filter(l -> l.currency == CurrencyUnit.MARKET_CREDIT).collect(Collectors.toList());
        else if ("AUCTIONS_SOON".equals(snap.view)) src = auctions().stream().sorted(Comparator.comparingLong(l -> l.expiresAt)).collect(Collectors.toList());
        else if ("FEATURED".equals(snap.view)) src = active(null, SortMode.FEATURED).stream().filter(this::isFeatured).collect(Collectors.toList());
        else if ("SEARCH".equals(snap.view)) {
            String q = selectedId == null ? "" : selectedId.toLowerCase(Locale.ROOT).trim();
            src = active(null, sort).stream().filter(l -> q.isEmpty()
                    || (l.itemName != null && l.itemName.toLowerCase(Locale.ROOT).contains(q))
                    || (l.sellerName != null && l.sellerName.toLowerCase(Locale.ROOT).contains(q))
                    || (l.category != null && l.category.title.toLowerCase(Locale.ROOT).contains(q))).collect(Collectors.toList());
        }
        else if ("FAVORITES".equals(snap.view)) {
            src = new ArrayList<Listing>();
            Set<String> fav = data.favorites.get(p.getUniqueID().toString());
            if (fav != null) for (String fid : fav) { Listing fl = data.listings.get(fid); if (fl != null && fl.state == ListingState.LISTED && fl.expiresAt > System.currentTimeMillis()) src.add(fl); }
            src.sort(Comparator.comparingLong((Listing l)->l.expiresAt));
        }
        else if ("MINE".equals(snap.view)) src = mine(p.getUniqueID());
        else if ("MY_BIDS".equals(snap.view)) src = auctions().stream().filter(l -> p.getUniqueID().equals(l.highestBidder)).collect(Collectors.toList());
        else if (snap.view.startsWith("CAT_")) {
            Category cat = parseCategory(snap.view.substring(4));
            src = active(cat, sort);
        } else src = active(null, sort);

        int pageSize = "DETAIL".equals(snap.view) || "INFO".equals(snap.view) || "PROFILE".equals(snap.view) || "GUILDS".equals(snap.view) || "MAIL".equals(snap.view) || "OFFERS".equals(snap.view) || "MY_OFFERS".equals(snap.view) || "STORAGE".equals(snap.view) || snap.view.startsWith("BOARD") || "ADMIN".equals(snap.view) ? 0 : ("FAVORITES".equals(snap.view) ? Math.max(1, RuasolMarket.CONFIG.maxFavoritesRowsPerSnapshot) : 5);
        if (pageSize > 0) {
            snap.maxPages = Math.max(1, (src.size() + pageSize - 1) / pageSize);
            if (snap.page >= snap.maxPages) snap.page = Math.max(0, snap.maxPages - 1);
            int from = Math.min(src.size(), snap.page * pageSize);
            int to = Math.min(src.size(), from + pageSize);
            for (Listing l : src.subList(from, to)) snap.listings.add(viewOf(p, l, false));
        }

        if (snap.guilds.isEmpty() && !"GUILDS".equals(snap.view) && !(snap.view != null && snap.view.startsWith("GUILD_"))) fillGuildViews(snap, p);

        if ("MAIL".equals(snap.view)) {
            List<NotificationEntry> rows = notifications(p.getUniqueID());
            int maxMail = Math.max(1, RuasolMarket.CONFIG.maxNotificationRowsPerSnapshot);
            snap.maxPages = Math.max(1, (rows.size() + maxMail - 1) / maxMail);
            if (snap.page >= snap.maxPages) snap.page = Math.max(0, snap.maxPages - 1);
            int from = Math.min(rows.size(), snap.page * maxMail);
            int to = Math.min(rows.size(), from + maxMail);
            for (NotificationEntry n : rows.subList(from, to)) { MarketSnapshot.NotificationView nv = new MarketSnapshot.NotificationView(); nv.id=n.id; nv.title=n.title; nv.body=n.body; nv.listingId=n.listingId; nv.read=n.read; nv.createdAt=n.createdAt; snap.notifications.add(nv); }
        }

        if ("OFFERS".equals(snap.view) || "MY_OFFERS".equals(snap.view)) {
            List<Offer> offerRows = "OFFERS".equals(snap.view) ? offersFor(p.getUniqueID()) : myOffers(p.getUniqueID());
            int maxOffers = Math.max(1, RuasolMarket.CONFIG.maxOffersRowsPerSnapshot);
            snap.maxPages = Math.max(1, (offerRows.size() + maxOffers - 1) / maxOffers);
            if (snap.page >= snap.maxPages) snap.page = Math.max(0, snap.maxPages - 1);
            int from = Math.min(offerRows.size(), snap.page * maxOffers);
            int to = Math.min(offerRows.size(), from + maxOffers);
            for (Offer o : offerRows.subList(from, to)) snap.offers.add(viewOf(o));
        }

        if ("STORAGE".equals(snap.view)) {
            List<StorageEntry> storageRows = storage(p.getUniqueID());
            int maxStorage = Math.max(1, RuasolMarket.CONFIG.maxStorageRowsPerSnapshot);
            snap.maxPages = Math.max(1, (storageRows.size() + maxStorage - 1) / maxStorage);
            if (snap.page >= snap.maxPages) snap.page = Math.max(0, snap.maxPages - 1);
            int from = Math.min(storageRows.size(), snap.page * maxStorage);
            int to = Math.min(storageRows.size(), from + maxStorage);
            for (StorageEntry se : storageRows.subList(from, to)) {
                MarketSnapshot.StorageView sv = new MarketSnapshot.StorageView();
                sv.id = se.id; sv.itemName = se.itemName; sv.itemBase64 = se.itemBase64 == null ? "" : se.itemBase64; sv.reason = se.reason; sv.createdAt = se.createdAt;
                snap.storage.add(sv);
            }
            List<PendingPayout> payoutRows = payouts(p.getUniqueID());
            int maxPayouts = Math.max(1, RuasolMarket.CONFIG.maxPayoutRowsPerSnapshot);
            for (PendingPayout pp : payoutRows.subList(0, Math.min(maxPayouts, payoutRows.size()))) {
                MarketSnapshot.PayoutView pv = new MarketSnapshot.PayoutView();
                pv.id = pp.id; pv.amount = pp.amount; pv.currency = pp.currency.title; pv.reason = pp.reason;
                snap.payouts.add(pv);
            }
        }

        if (snap.view.startsWith("BOARD")) {
            snap.boardMode = snap.view.contains("SPENT") ? "SPENT" : snap.view.contains("SALES") ? "SALES" : snap.view.contains("BUY") ? "BUY" : snap.view.contains("LIKE") ? "LIKE" : snap.view.contains("AUCTION") ? "AUCTION" : snap.view.contains("REP") ? "REP" : snap.view.contains("BADGES") ? "BADGES" : "EARNED";
            List<Map.Entry<String, Stats>> rows = new ArrayList<Map.Entry<String, Stats>>(data.stats.entrySet());
            if ("SPENT".equals(snap.boardMode)) rows.sort(Comparator.comparingDouble((Map.Entry<String, Stats> e) -> e.getValue().spent).reversed());
            else if ("SALES".equals(snap.boardMode)) rows.sort(Comparator.comparingInt((Map.Entry<String, Stats> e) -> e.getValue().sales).reversed());
            else if ("BUY".equals(snap.boardMode)) rows.sort(Comparator.comparingInt((Map.Entry<String, Stats> e) -> e.getValue().purchases).reversed());
            else if ("LIKE".equals(snap.boardMode)) rows.sort(Comparator.comparingInt((Map.Entry<String, Stats> e) -> e.getValue().likesReceived).reversed());
            else if ("AUCTION".equals(snap.boardMode)) rows.sort(Comparator.comparingInt((Map.Entry<String, Stats> e) -> e.getValue().auctionsWon).reversed());
            else if ("REP".equals(snap.boardMode)) rows.sort(Comparator.comparingInt((Map.Entry<String, Stats> e) -> { refreshReputation(e.getValue()); return e.getValue().reputation; }).reversed());
            else if ("BADGES".equals(snap.boardMode)) rows.sort(Comparator.comparingInt((Map.Entry<String, Stats> e) -> e.getValue().badges == null ? 0 : e.getValue().badges.size()).reversed());
            else rows.sort(Comparator.comparingDouble((Map.Entry<String, Stats> e) -> e.getValue().earned).reversed());
            for (Map.Entry<String, Stats> e : rows.subList(0, Math.min(12, rows.size()))) {
                Stats st = e.getValue();
                MarketSnapshot.BoardRow br = new MarketSnapshot.BoardRow();
                br.player = displayNameFor(e.getKey(), st);
                refreshReputation(st); br.sales = st.sales; br.purchases = st.purchases; br.auctionsWon = st.auctionsWon; br.likesReceived = st.likesReceived; br.earned = st.earned; br.spent = st.spent; br.reputation = st.reputation; br.badgeLine = st.badges == null || st.badges.isEmpty() ? "" : String.join(", ", st.badges);
                snap.board.add(br);
            }
        }

        if ("ADMIN".equals(snap.view) && snap.admin) {
            List<TransactionEntry> txs = new ArrayList<TransactionEntry>(data.transactions);
            Collections.reverse(txs);
            for (TransactionEntry te : txs.subList(0, Math.min(12, txs.size()))) {
                MarketSnapshot.TxRow tr = new MarketSnapshot.TxRow();
                tr.id = te.id; tr.type = String.valueOf(te.type); tr.status = String.valueOf(te.status); tr.listingId = te.listingId; tr.amount = te.amount; tr.note = te.note; tr.actor = nameOf(te.actor); tr.target = nameOf(te.target); tr.phase = String.valueOf(te.phase);
                snap.transactions.add(tr);
            }
        }
        return snap;
    }

    public synchronized String handleClientAction(ServerPlayerEntity p, String action, String id, double amount, int value, String text) {
        String limited = rateLimit(p, action);
        if (limited != null) return limited;
        try {
            if ("SELECT_HELD".equals(action)) return startWizardFromHand(p);
            if ("SET_DAYS".equals(action)) return setWizardDays(p, value);
            if ("SET_MODE_FIXED".equals(action)) return setWizardMode(p, ListingType.FIXED);
            if ("SET_MODE_AUCTION".equals(action)) return setWizardMode(p, ListingType.AUCTION);
            if ("SET_AUCTION_HOURS".equals(action)) return setWizardAuctionHours(p, value);
            if ("SET_AUCTION_CURRENCY_RC".equals(action)) return setWizardAuctionCurrency(p, CurrencyUnit.RC);
            if ("SET_AUCTION_CURRENCY_CREDIT".equals(action)) return setWizardAuctionCurrency(p, CurrencyUnit.MARKET_CREDIT);
            if ("TOGGLE_LEGENDARY".equals(action)) return toggleWizardLegendary(p);
            if ("TOGGLE_FEATURED_BOOST".equals(action)) return toggleWizardFeature(p);
            if ("TOGGLE_ANNOUNCEMENT_BOOST".equals(action)) return toggleWizardAnnouncement(p);
            if ("CREATE_FIXED".equals(action)) { setWizardPrice(p, amount); setWizardQuantity(p, value); setWizardMode(p, ListingType.FIXED); return createListing(p, wizard(p)); }
            if ("CREATE_AUCTION".equals(action)) { setWizardPrice(p, amount); setWizardQuantity(p, value); setWizardMode(p, ListingType.AUCTION); return createListing(p, wizard(p)); }
            if ("BUY".equals(action)) return buy(p, id);
            if ("OFFER".equals(action)) return offerOrBid(p, id, amount);
            if ("BID".equals(action)) return offerOrBid(p, id, amount);
            if ("LIKE".equals(action)) return like(p, id);
            if ("FAVORITE".equals(action)) return favorite(p, id);
            if ("CLEAR_MAIL".equals(action)) return clearMail(p);
            if ("FEATURE".equals(action)) return feature(p, id);
            if ("PRESTIGE_SEAL".equals(action)) return prestigeSeal(p, id);
            if ("CANCEL_LISTING".equals(action)) return cancelListing(p, id);
            if ("ACCEPT_OFFER".equals(action)) return acceptOffer(p, id);
            if ("CANCEL_OFFER".equals(action)) return cancelOffer(p, id);
            if ("CLAIM_STORAGE".equals(action)) return claimStorage(p, id);
            if ("CLAIM_ALL".equals(action)) return claimAllStorage(p);
            if ("PAYOUTS".equals(action)) return processPayouts(p);
            if ("ADMIN_RELOAD".equals(action) && isAdmin(p)) { RuasolMarket.CONFIG = MarketConfig.load(); return "Config yenilendi."; }
            if ("ADMIN_RECOVER".equals(action) && isAdmin(p)) return adminRecoverLocks(p);
            if ("ADMIN_ECON_TEST".equals(action) && isAdmin(p)) return adminEconomyTest(p);
            if ("ADMIN_RESOLVE_RECOVERY".equals(action) && isAdmin(p)) return adminResolveOneRecovery(p);
            if ("ADMIN_REPORT".equals(action) && isAdmin(p)) return adminReport(p);
            return "Bilinmeyen pazar işlemi: " + action;
        } catch (Exception e) {
            RuasolMarket.LOG.error("Client action failed: " + action, e);
            return "İşlem güvenli şekilde iptal edildi. Detay loga yazıldı.";
        }
    }

    private MarketSnapshot.ListingView viewOf(ServerPlayerEntity viewer, Listing l, boolean includeFullItemNbt) {
        MarketSnapshot.ListingView v = new MarketSnapshot.ListingView();
        v.id = l.id; v.seller = l.sellerName; v.itemName = l.itemName; v.registryName = l.itemRegistryName; v.itemBase64 = includeFullItemNbt && l.itemBase64 != null ? l.itemBase64 : ""; v.itemHash = l.itemHash == null ? "" : l.itemHash;
        v.category = l.category == null ? "Diğer" : l.category.title; v.type = l.type == null ? "FIXED" : l.type.name();
        v.currency = (l.currency == null ? CurrencyUnit.RC : l.currency).title;
        v.price = visiblePrice(l); v.startPrice = l.startPrice; v.highestBid = l.highestBid; v.minNextBid = minNextBid(l);
        v.highestBidder = l.highestBidderName == null ? "" : l.highestBidderName;
        v.quantity = Math.max(1, l.quantity);
        v.remainingMillis = remainingMillis(l); v.likes = l.likes == null ? 0 : l.likes.size(); v.likedByMe = l.likes != null && l.likes.contains(viewer.getUniqueID().toString()); v.featured = isFeatured(l); v.mine = viewer.getUniqueID().equals(l.seller); v.followedByMe = isFavorite(viewer.getUniqueID(), l.id); v.legendary = l.legendary; v.prestigeLevel = l.prestigeLevel; v.guildId = l.guildId == null ? "" : l.guildId;
        return v;
    }

    private MarketSnapshot.OfferView viewOf(Offer o) {
        MarketSnapshot.OfferView v = new MarketSnapshot.OfferView();
        v.id = o.id; v.listingId = o.listingId; v.buyer = o.buyerName; v.amount = o.amount; v.currency = o.currency == null ? "RC" : o.currency.title; v.createdAt = o.createdAt;
        Listing l = data.listings.get(o.listingId); v.itemName = l == null ? "Bilinmeyen ürün" : l.itemName; v.itemBase64 = l == null || l.itemBase64 == null ? "" : l.itemBase64;
        return v;
    }


    private SortMode sortForView(String view) {
        if (view == null) return SortMode.FEATURED;
        if (view.contains("PRICE_LOW")) return SortMode.PRICE_LOW;
        if (view.contains("PRICE_HIGH")) return SortMode.PRICE_HIGH;
        if (view.contains("NEWEST")) return SortMode.NEWEST;
        if (view.contains("ENDING")) return SortMode.ENDING_SOON;
        if (view.contains("LIKES")) return SortMode.LIKES;
        return SortMode.FEATURED;
    }

    private String rateLimit(ServerPlayerEntity p, String action) {
        if (p == null || action == null || action.isEmpty()) return null;
        long cooldown = 250L;
        if ("NAV".equals(action)) cooldown = 300L;
        else if ("LIKE".equals(action)) cooldown = 1000L;
        else if ("BUY".equals(action) || "OFFER".equals(action) || "BID".equals(action) || "CREATE_FIXED".equals(action) || "CREATE_AUCTION".equals(action) || "ACCEPT_OFFER".equals(action)) cooldown = 700L;
        else if (action.startsWith("ADMIN")) cooldown = 1000L;
        String key = p.getUniqueID().toString() + ":" + action;
        long now = System.currentTimeMillis();
        Long last = actionRateLimit.get(key);
        if (last != null && now - last < cooldown) return "Pazar mühürleri henüz soğumadı. Çok hızlı işlem yapıyorsun.";
        actionRateLimit.put(key, now);
        return null;
    }

    private Category parseCategory(String raw) {
        if (raw == null) return null;
        String r = raw.replace("_PRICE_LOW", "").replace("_PRICE_HIGH", "").replace("_NEWEST", "").replace("_ENDING", "").replace("_LIKES", "");
        try { return Category.valueOf(r); } catch (Exception e) { return null; }
    }

    private boolean storeItem(UUID owner, String ownerName, ItemStack item, String reason) throws IOException {
        StorageEntry s = new StorageEntry();
        s.owner = owner;
        s.ownerName = ownerName == null ? "?" : ownerName;
        s.itemBase64 = ItemCodec.encode(item.copy());
        s.itemName = item.getDisplayName().getString();
        s.reason = reason;
        if (ownerStorageCount(owner) >= RuasolMarket.CONFIG.maxStorageItemsPerPlayer) {
            data.recoveryStorage.add(s);
            tx(TransactionType.STORAGE_OVERFLOW, TransactionStatus.FAILED, "", null, null, owner, 0, 0, "", "Storage full; item moved to admin recovery queue: " + reason);
            return false;
        }
        data.storage.add(s);
        return true;
    }

    private int ownerStorageCount(UUID owner) {
        if (owner == null) return Integer.MAX_VALUE;
        int count = 0;
        for (StorageEntry s : data.storage) if (owner.equals(s.owner)) count++;
        return count;
    }

    private boolean deliverOrStore(ServerPlayerEntity player, UUID owner, String ownerName, ItemStack item, String reason, Listing l) throws IOException {
        if (item == null || item.isEmpty()) return false;
        int qty = Math.max(1, item.getCount());
        ItemStack base = item.copy();
        base.setCount(1);
        return deliverOrStoreMany(player, owner, ownerName, base, qty, reason, l);
    }


    private boolean deliverOrStoreMany(ServerPlayerEntity player, UUID owner, String ownerName, ItemStack template, int quantity, String reason, Listing l) throws IOException {
        if (template == null || template.isEmpty()) return false;
        int q = Math.max(1, quantity);
        ItemStack base = template.copy();
        base.setCount(1);
        if (player != null && canFitAll(player, base, q) && addSplitStacksToInventory(player, base, q)) return true;
        boolean allStored = true;
        for (ItemStack part : splitStacks(base, q)) {
            if (!storeItem(owner, ownerName, part, reason)) allStored = false;
        }
        if (!allStored && l != null) {
            l.state = ListingState.FAILED_RECOVERY;
            l.closeReason = "storage_overflow";
        }
        return allStored;
    }

    private boolean criticalSave(Listing l, String note) {
        if (save()) return true;
        TransactionEntry emergency = new TransactionEntry();
        emergency.type = TransactionType.RECOVERY;
        emergency.status = TransactionStatus.FAILED;
        emergency.listingId = l == null ? "" : l.id;
        emergency.target = l == null ? null : l.seller;
        emergency.itemHash = l == null ? "" : l.itemHash;
        emergency.note = note == null ? "critical_save_failed" : note;
        emergency.phase = TransactionPhase.NONE;
        appendEmergency(emergency);
        if (l != null) {
            l.state = ListingState.FAILED_RECOVERY;
            l.closeReason = "critical_save_failed";
            tx(TransactionType.RECOVERY, TransactionStatus.FAILED, l.id, null, null, l.seller, 0, 0, l.itemHash, note + " / emergency log appended");
        } else {
            tx(TransactionType.RECOVERY, TransactionStatus.FAILED, "", null, null, null, 0, 0, "", note + " / emergency log appended");
        }
        if (!save()) appendEmergency(emergency);
        return false;
    }

    private void softSave() {
        softDirty = true;
        long now = System.currentTimeMillis();
        if (now >= nextSoftSaveAt) save();
    }

    private String displayNameFor(String uuidKey, Stats st) {
        if (st != null && st.playerName != null && !"?".equals(st.playerName)) return st.playerName;
        String n = data.playerNames == null ? null : data.playerNames.get(uuidKey);
        if (n != null && !n.isEmpty()) return n;
        return uuidKey == null ? "?" : uuidKey.substring(0, Math.min(8, uuidKey.length()));
    }

    private String nameOf(UUID uuid) {
        if (uuid == null) return "";
        String key = uuid.toString();
        String n = data.playerNames == null ? null : data.playerNames.get(key);
        return n == null || n.isEmpty() ? key.substring(0, Math.min(8, key.length())) : n;
    }

    private void recoverFromWalAndData() {
        int recovered = recoverLockedListings("startup");
        if (recovered > 0) {
            tx(TransactionType.RECOVERY, TransactionStatus.RECOVERED, "", null, null, null, recovered, 0, "", "Startup recovered stale locked listings");
            save();
        }
        scanWalForUnclosedTransactions();
    }

    private int recoverLockedListings(String source) {
        int recovered = 0;
        long now = System.currentTimeMillis();
        for (Listing l : data.listings.values()) {
            if (l.state == ListingState.LOCKED_TRANSACTION && (l.lockedAt <= 0 || now - l.lockedAt > 30000L)) {
                l.state = ListingState.LISTED;
                l.lockedAt = 0L;
                l.closeReason = "recovered_" + source;
                tx(TransactionType.RECOVERY, TransactionStatus.RECOVERED, l.id, null, null, l.seller, 0, 0, l.itemHash, "Recovered stale lock: " + source);
                recovered++;
            }
        }
        return recovered;
    }

    private void scanWalForUnclosedTransactions() {
        if (!walFile.exists()) return;
        try {
            Map<String, TransactionStatus> last = new HashMap<String, TransactionStatus>();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(walFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    TransactionEntry t = gson.fromJson(line, TransactionEntry.class);
                    if (t == null || t.type == null) continue;
                    String key = t.type + ":" + (t.listingId == null ? "" : t.listingId) + ":" + (t.offerId == null ? "" : t.offerId);
                    last.put(key, t.status);
                }
            }
            int suspicious = 0;
            for (Map.Entry<String, TransactionStatus> e : last.entrySet()) if (e.getValue() == TransactionStatus.STARTED) suspicious++;
            if (suspicious > 0) {
                int locks = recoverLockedListings("wal_startup");
                tx(TransactionType.RECOVERY, locks > 0 ? TransactionStatus.RECOVERED : TransactionStatus.FAILED, "", null, null, null, suspicious, 0, "", "WAL scan found unclosed STARTED transaction groups. Stale locks recovered: " + locks + ". Manual admin review still advised for money/item phases.");
                save();
            }
        } catch (Exception e) {
            RuasolMarket.LOG.warn("WAL recovery scan failed; data file remains authoritative", e);
        }
    }

    private void restoreToPlayerOrStorage(ServerPlayerEntity p, ItemStack item, String reason) {
        try {
            if (p == null || item == null || item.isEmpty()) return;
            deliverOrStoreMany(p, p.getUniqueID(), p.getGameProfile().getName(), item.copy(), Math.max(1, item.getCount()), reason, null);
        } catch (Exception e) { RuasolMarket.LOG.error("Restore failed; manual recovery may be required", e); }
    }

    private boolean canFit(ServerPlayerEntity p, ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (ItemStack slot : p.inventory.mainInventory) {
            if (remaining.isEmpty()) return true;
            if (slot.isEmpty()) return true;
            if (ItemStack.areItemsEqual(slot, remaining) && ItemStack.areItemStackTagsEqual(slot, remaining)) {
                int room = Math.min(slot.getMaxStackSize(), p.inventory.getInventoryStackLimit()) - slot.getCount();
                if (room > 0) remaining.shrink(room);
            }
        }
        return remaining.isEmpty();
    }

    private double tax(double amount) { return Math.max(0D, amount * RuasolMarket.CONFIG.taxPercent / 100D); }

    private int creditTax(int amount) {
        int safe = Math.max(0, amount);
        int tax = (int)Math.floor(safe * RuasolMarket.CONFIG.taxPercent / 100D);
        return Math.max(0, Math.min(safe, tax));
    }
    private Stats stat(UUID u) {
        Stats st = data.stats.computeIfAbsent(u.toString(), k -> new Stats());
        String n = data.playerNames == null ? null : data.playerNames.get(u.toString());
        if (n != null && !n.isEmpty()) st.playerName = n;
        refreshReputation(st);
        return st;
    }
    private boolean isAdmin(ServerPlayerEntity p) { return p.getServer().getPlayerList().canSendCommands(p.getGameProfile()); }

    private void tx(TransactionType type, TransactionStatus status, String listingId, String offerId, UUID actor, UUID target, double amount, double tax, String itemHash, String note) {
        TransactionEntry t = new TransactionEntry();
        t.type = type; t.status = status; t.listingId = listingId == null ? "" : listingId; t.offerId = offerId == null ? "" : offerId;
        t.actor = actor; t.target = target; t.amount = amount; t.tax = tax; t.itemHash = itemHash == null ? "" : itemHash; t.note = note == null ? "" : note; t.phase = inferPhase(t);
        data.transactions.add(t); trimTransactions();
        appendWal(t);
    }




    private TransactionPhase inferPhase(TransactionEntry t) {
        if (t == null || t.status == TransactionStatus.FAILED) return TransactionPhase.NONE;
        String n = t.note == null ? "" : t.note.toLowerCase(Locale.ROOT);
        if (n.contains("lock")) return TransactionPhase.LISTING_LOCKED;
        if (n.contains("withdraw") || n.contains("escrow")) return TransactionPhase.MONEY_WITHDRAWN;
        if (n.contains("deliver") || n.contains("depo") || n.contains("item")) return TransactionPhase.ITEM_DELIVERED;
        if (n.contains("payout") || n.contains("gelir") || n.contains("paid")) return TransactionPhase.SELLER_PAID_OR_PENDING;
        if (t.status == TransactionStatus.COMMITTED) return TransactionPhase.DATA_SAVED;
        return TransactionPhase.NONE;
    }

    public synchronized String favorite(ServerPlayerEntity p, String id) {
        remember(p);
        Listing l = data.listings.get(id);
        if (l == null || l.state != ListingState.LISTED) return "Takip edilecek aktif ilan bulunamadı.";
        Set<String> set = data.favorites.computeIfAbsent(p.getUniqueID().toString(), k -> new LinkedHashSet<String>());
        boolean already = set.contains(id);
        if (!already && set.size() >= RuasolMarket.CONFIG.maxFavoritesPerPlayer) return "Takip sınırına ulaştın. Bazı ilanları takipten çıkarıp tekrar dene.";
        boolean added = already ? false : set.add(id);
        if (added) { stat(p.getUniqueID()).followsGiven++; notifyPlayer(p.getUniqueID(), "Takibe alındı", l.itemName + " artık Takip bölümünde görünecek.", id); }
        else { set.remove(id); notifyPlayer(p.getUniqueID(), "Takipten çıkarıldı", l.itemName + " takiplerinden kaldırıldı.", id); }
        softSave();
        return added ? "Ürün takip listene eklendi." : "Ürün takipten çıkarıldı.";
    }

    public synchronized String clearMail(ServerPlayerEntity p) {
        int cleared = 0;
        Iterator<NotificationEntry> it = data.notifications.iterator();
        while (it.hasNext()) {
            NotificationEntry n = it.next();
            if (p.getUniqueID().equals(n.owner)) { it.remove(); cleared++; }
        }
        if (cleared > 0) save();
        return cleared == 0 ? "Pazar postanda temizlenecek kayıt yok." : cleared + " pazar postası temizlendi.";
    }

    private void notifyPlayer(UUID owner, String title, String body, String listingId) {
        if (owner == null) return;
        if (data.notifications == null) data.notifications = new ArrayList<NotificationEntry>();
        NotificationEntry n = new NotificationEntry();
        n.owner = owner; n.title = title == null ? "Pazar" : title; n.body = body == null ? "" : body; n.listingId = listingId == null ? "" : listingId;
        data.notifications.add(n);
        int perPlayerLimit = RuasolMarket.CONFIG == null ? 60 : RuasolMarket.CONFIG.maxNotificationsPerPlayer;
        int owned = 0;
        for (int i = data.notifications.size() - 1; i >= 0; i--) {
            NotificationEntry e = data.notifications.get(i);
            if (owner.equals(e.owner)) {
                owned++;
                if (owned > perPlayerLimit) data.notifications.remove(i);
            }
        }
        while (data.notifications.size() > 2000) data.notifications.remove(0);
    }

    private void writeDataShards() {
        try {
            if (RuasolMarket.CONFIG == null || !RuasolMarket.CONFIG.writeDataShards) return;
            if (!shardDir.exists()) shardDir.mkdirs();
            writeShard("listings.json", data.listings);
            writeShard("offers.json", data.offers);
            writeShard("storage.json", data.storage);
            writeShard("recovery_storage.json", data.recoveryStorage);
            writeShard("payouts.json", data.payouts);
            writeShard("stats.json", data.stats);
            writeShard("notifications.json", data.notifications);
        } catch (Exception e) { RuasolMarket.LOG.warn("Market shard write failed; main data file remains authoritative", e); }
    }

    private void writeShard(String name, Object obj) throws IOException {
        File f = new File(shardDir, name);
        File tmp = new File(shardDir, name + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) { gson.toJson(obj, w); }
        try { Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING); }
    }

    private void appendWal(TransactionEntry t) {
        try {
            if (!walFile.getParentFile().exists()) walFile.getParentFile().mkdirs();
            try (Writer w = new OutputStreamWriter(new FileOutputStream(walFile, true), StandardCharsets.UTF_8)) {
                w.write(gson.toJson(t));
                w.write("\n");
            }
        } catch (Exception e) {
            RuasolMarket.LOG.error("Market WAL write failed. Transaction stayed in memory log but external recovery log was not updated.", e);
        }
    }

    private void appendEmergency(TransactionEntry t) {
        try {
            if (!emergencyFile.getParentFile().exists()) emergencyFile.getParentFile().mkdirs();
            try (Writer w = new OutputStreamWriter(new FileOutputStream(emergencyFile, true), StandardCharsets.UTF_8)) {
                w.write(gson.toJson(t));
                w.write("\n");
            }
        } catch (Exception e) {
            RuasolMarket.LOG.error("Emergency recovery write failed. Manual file-system investigation required.", e);
        }
    }

    private void trimTransactions() {
        int max = RuasolMarket.CONFIG == null ? 1500 : RuasolMarket.CONFIG.maxTransactionLogEntries;
        while (data.transactions.size() > max) data.transactions.remove(0);
    }

    private void broadcastAuctionBid(MinecraftServer server, Listing l, String bidderName, double amount) {
        if (server == null || l == null) return;
        long now = System.currentTimeMillis();
        long cooldown = Math.max(0, RuasolMarket.CONFIG.auctionBidBroadcastCooldownSeconds) * 1000L;
        Long last = auctionBidBroadcasts.get(l.id);
        if (last != null && now - last < cooldown) return;
        auctionBidBroadcasts.put(l.id, now);
        broadcast(server, "§6[Pazar] §f" + l.itemName + " §7için yeni teklif: §a" + fmtAmount(amount, l.currency) + " §8(" + bidderName + ")");
    }

    private void broadcast(MinecraftServer server, String message) {
        if (server == null) return;
        String plain = message == null ? "" : message.replaceAll("§.", "");
        for (ServerPlayerEntity sp : server.getPlayerList().getPlayers()) {
            sp.sendMessage(new StringTextComponent(message), sp.getUniqueID());
            NetworkHandler.sendAnnouncement(sp, "Kadim Duyuru", plain);
        }
    }

    public static String fmt(double d) {
        if (Math.abs(d - Math.round(d)) < 0.0001D) return String.valueOf((long)Math.round(d));
        return String.format(Locale.US, "%.2f", d);
    }

    public static String fmtAmount(double d, CurrencyUnit unit) {
        CurrencyUnit u = unit == null ? CurrencyUnit.RC : unit;
        return (u == CurrencyUnit.MARKET_CREDIT ? String.valueOf((int)Math.round(d)) : fmt(d)) + " " + u.title;
    }

    public static String timeLeft(long millis) {
        long s = Math.max(0L, millis / 1000L);
        long h = s / 3600L;
        long m = (s % 3600L) / 60L;
        if (h >= 24) return (h / 24L) + "g " + (h % 24L) + "s";
        if (h > 0) return h + "s " + m + "d";
        return m + "d";
    }

    public static StringTextComponent msg(String s, TextFormatting f) { StringTextComponent t = new StringTextComponent(s); t.mergeStyle(f); return t; }

    public synchronized String toggleWizardLegendary(ServerPlayerEntity p) {
        Wizard w = wizard(p);
        w.legendarySeal = !w.legendarySeal;
        return w.legendarySeal ? "Efsanevi ilan mührü seçildi. Bedel: " + RuasolMarket.CONFIG.legendarySealCreditCost + " Pazar Kredisi." : "Efsanevi ilan mührü kaldırıldı.";
    }

    public synchronized String prestigeSeal(ServerPlayerEntity p, String listingId) {
        Listing l = data.listings.get(listingId);
        if (l == null || l.state != ListingState.LISTED) return "İlan aktif değil.";
        if (!p.getUniqueID().equals(l.seller) && !isAdmin(p)) return "Bu ilan sana ait değil.";
        int maxLevel = Math.max(1, RuasolMarket.CONFIG.maxPrestigeLevel);
        if (l.prestigeLevel >= maxLevel) return "Bu ilan zaten en yüksek prestij seviyesinde.";
        int cost = Math.max(0, RuasolMarket.CONFIG.prestigeSealCreditCost);
        if (!takeCredits(p.getUniqueID(), cost)) return "Prestij mührü için yeterli Pazar Kredin yok. Gerekli: " + cost;
        int oldLevel = l.prestigeLevel;
        boolean oldLegendary = l.legendary;
        l.prestigeLevel = Math.min(maxLevel, l.prestigeLevel + 1);
        if (l.prestigeLevel >= 3) l.legendary = true;
        tx(TransactionType.PRESTIGE_SPEND, TransactionStatus.COMMITTED, l.id, null, p.getUniqueID(), l.seller, cost, 0, l.itemHash, "Prestij mührü basıldı. Seviye=" + l.prestigeLevel);
        notifyPlayer(l.seller, "Prestij Mührü", l.itemName + " ilanına prestij mührü basıldı. Seviye: " + l.prestigeLevel, l.id);
        if (!save()) {
            l.prestigeLevel = oldLevel;
            l.legendary = oldLegendary;
            addCreditsNoSave(p.getUniqueID(), cost);
            tx(TransactionType.PRESTIGE_SPEND, TransactionStatus.FAILED, l.id, null, p.getUniqueID(), l.seller, cost, 0, l.itemHash, "Prestige save failed; credit refunded and prestige rolled back");
            save();
            return "Prestij mührü kaydedilemedi; kredi iade edildi.";
        }
        return "Prestij mührü basıldı. Seviye: " + l.prestigeLevel;
    }

    public synchronized String adminGuildCreate(ServerPlayerEntity op, String id, String name) {
        if (!isAdmin(op)) return "Bu işlem için OP yetkisi gerekli.";
        String gid = cleanId(id);
        if (gid.isEmpty()) return "Lonca id boş olamaz.";
        if (data.guilds.containsKey(gid)) return "Bu id ile lonca zaten var: " + gid;
        if (data.guilds.size() >= RuasolMarket.CONFIG.maxGuilds) return "Lonca sınırına ulaşıldı. Max: " + RuasolMarket.CONFIG.maxGuilds;
        TradeGuild g = new TradeGuild();
        String display = trimText(name, 40);
        g.id = gid; g.displayName = display.isEmpty() ? gid : display;
        g.createdBy = op.getUniqueID(); g.createdByName = op.getGameProfile().getName();
        data.guilds.put(gid, g);
        tx(TransactionType.GUILD_ADMIN, TransactionStatus.COMMITTED, "", null, op.getUniqueID(), null, 0, 0, "", "Lonca oluşturuldu: " + gid + " / " + g.displayName);
        save();
        return "Ticaret loncası kuruldu: " + g.displayName + " (#" + gid + ")";
    }

    public synchronized String adminGuildDelete(ServerPlayerEntity op, String id) {
        if (!isAdmin(op)) return "Bu işlem için OP yetkisi gerekli.";
        String gid = cleanId(id);
        TradeGuild g = data.guilds.remove(gid);
        if (g == null) return "Lonca bulunamadı: " + gid;
        for (Listing l : data.listings.values()) if (gid.equals(l.guildId)) l.guildId = "";
        tx(TransactionType.GUILD_ADMIN, TransactionStatus.COMMITTED, "", null, op.getUniqueID(), null, 0, 0, "", "Lonca silindi: " + gid);
        save();
        return "Lonca silindi: " + g.displayName;
    }

    public synchronized String adminGuildAdd(ServerPlayerEntity op, ServerPlayerEntity player, String id) {
        if (!isAdmin(op)) return "Bu işlem için OP yetkisi gerekli.";
        String gid = cleanId(id);
        TradeGuild g = data.guilds.get(gid);
        if (g == null) return "Lonca bulunamadı: " + gid;
        remember(player);
        String pid = player.getUniqueID().toString();
        if (g.members.contains(pid)) return player.getGameProfile().getName() + " zaten bu loncanın üyesi.";
        if (g.members.size() >= RuasolMarket.CONFIG.maxGuildMembersPerGuild) return "Bu lonca üye sınırına ulaştı. Max: " + RuasolMarket.CONFIG.maxGuildMembersPerGuild;
        for (TradeGuild other : data.guilds.values()) {
            if (other != g && other.members != null && other.members.contains(pid)) return "Oyuncu zaten başka bir ticaret loncasında: " + other.displayName;
        }
        g.members.add(pid);
        notifyPlayer(player.getUniqueID(), "Ticaret Loncası", g.displayName + " loncasına kabul edildin. Pazar sekmesinde lonca vitrinini görebilirsin.", "");
        tx(TransactionType.GUILD_ADMIN, TransactionStatus.COMMITTED, "", null, op.getUniqueID(), player.getUniqueID(), 0, 0, "", "Lonca üyesi eklendi: " + gid);
        save();
        return player.getGameProfile().getName() + " artık " + g.displayName + " üyesi.";
    }

    public synchronized String adminGuildRemove(ServerPlayerEntity op, ServerPlayerEntity player, String id) {
        if (!isAdmin(op)) return "Bu işlem için OP yetkisi gerekli.";
        String gid = cleanId(id);
        TradeGuild g = data.guilds.get(gid);
        if (g == null) return "Lonca bulunamadı: " + gid;
        g.members.remove(player.getUniqueID().toString());
        tx(TransactionType.GUILD_ADMIN, TransactionStatus.COMMITTED, "", null, op.getUniqueID(), player.getUniqueID(), 0, 0, "", "Lonca üyesi çıkarıldı: " + gid);
        save();
        return player.getGameProfile().getName() + " loncadan çıkarıldı: " + g.displayName;
    }

    public synchronized String adminSeasonReset(ServerPlayerEntity op, String title) {
        if (!isAdmin(op)) return "Bu işlem için OP yetkisi gerekli.";
        SeasonArchive a = new SeasonArchive();
        a.title = title == null || title.trim().isEmpty() ? "Pazar Sezonu" : title.trim();
        a.treasuryRc = data.taxTreasuryRc;
        a.treasuryCredits = data.taxTreasuryCredits;
        List<Map.Entry<String, Stats>> rows = new ArrayList<Map.Entry<String, Stats>>(data.stats.entrySet());
        rows.sort(Comparator.comparingDouble((Map.Entry<String, Stats> e) -> e.getValue().earned).reversed());
        for (Map.Entry<String, Stats> e : rows.subList(0, Math.min(10, rows.size()))) {
            Stats st = e.getValue();
            a.topRows.add(displayNameFor(e.getKey(), st) + " | satış=" + st.sales + " | kazanç=" + fmt(st.earned) + " | itibar=" + st.reputation);
        }
        data.seasonArchives.add(a);
        data.stats.clear();
        data.taxTreasuryRc = 0D; data.taxTreasuryCredits = 0;
        tx(TransactionType.SEASON_RESET, TransactionStatus.COMMITTED, "", null, op.getUniqueID(), null, 0, 0, "", "Sezon arşivlendi: " + a.title);
        save();
        broadcast(op.getServer(), "§6[Pazar] §e" + a.title + " arşive alındı. Pazar tabloları yeni sezon için temizlendi.");
        return "Sezon sıfırlandı ve arşivlendi: " + a.title;
    }

    private void paginateGuilds(MarketSnapshot snap, int pageSize) {
        List<MarketSnapshot.GuildView> all = new ArrayList<MarketSnapshot.GuildView>(snap.guilds);
        int safeSize = Math.max(1, pageSize);
        snap.maxPages = Math.max(1, (all.size() + safeSize - 1) / safeSize);
        if (snap.page >= snap.maxPages) snap.page = Math.max(0, snap.maxPages - 1);
        int from = Math.min(all.size(), snap.page * safeSize);
        int to = Math.min(all.size(), from + safeSize);
        snap.guilds.clear();
        snap.guilds.addAll(all.subList(from, to));
    }

    private void fillGuildViews(MarketSnapshot snap, ServerPlayerEntity viewer) {
        if (data.guilds == null) return;
        long now = System.currentTimeMillis();
        Map<String, Integer> activeByGuild = new HashMap<String, Integer>();
        Map<String, String> memberToGuild = new HashMap<String, String>();
        for (TradeGuild g : data.guilds.values()) if (g.members != null) for (String m : g.members) memberToGuild.put(m, g.id);
        for (Listing l : data.listings.values()) {
            if (l.state != ListingState.LISTED || l.expiresAt <= now) continue;
            String gid = l.guildId != null && !l.guildId.isEmpty() ? l.guildId : memberToGuild.get(l.seller == null ? "" : l.seller.toString());
            if (gid != null && data.guilds.containsKey(gid)) activeByGuild.put(gid, activeByGuild.getOrDefault(gid, 0) + 1);
        }
        for (TradeGuild g : data.guilds.values()) {
            MarketSnapshot.GuildView gv = new MarketSnapshot.GuildView();
            gv.id = g.id; gv.displayName = g.displayName; gv.motto = g.displayName + " Loncasının güzidelerine bak.";
            gv.members = g.members == null ? 0 : g.members.size();
            gv.mine = viewer != null && g.members != null && g.members.contains(viewer.getUniqueID().toString());
            gv.activeListings = activeByGuild.getOrDefault(g.id, 0);
            snap.guilds.add(gv);
        }
    }

    private List<Listing> listingsForGuild(String guildId, SortMode sort) {
        TradeGuild g = data.guilds.get(cleanId(guildId));
        if (g == null) return new ArrayList<Listing>();
        Set<String> members = g.members == null ? Collections.emptySet() : g.members;
        List<Listing> out = new ArrayList<Listing>();
        long now = System.currentTimeMillis();
        for (Listing l : data.listings.values()) if (l.state == ListingState.LISTED && l.expiresAt > now && (members.contains(l.seller.toString()) || g.id.equals(l.guildId))) out.add(l);
        out.sort(Comparator.comparing((Listing l) -> !l.legendary).thenComparing((Listing l) -> !isFeatured(l)).thenComparingLong(l -> l.expiresAt));
        return out;
    }

    private String primaryGuildOf(UUID player) {
        if (player == null || data.guilds == null) return "";
        String pid = player.toString();
        for (TradeGuild g : data.guilds.values()) if (g.members != null && g.members.contains(pid)) return g.id;
        return "";
    }

    private String cleanId(String id) {
        if (id == null) return "";
        return id.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "");
    }

    private void collectTax(double amount, CurrencyUnit currency, String listingId) {
        if (amount <= 0) return;
        if (currency == CurrencyUnit.MARKET_CREDIT) data.taxTreasuryCredits += Math.max(0, asCreditAmount(amount));
        else data.taxTreasuryRc += amount;
        tx(TransactionType.TAX_TREASURY, TransactionStatus.COMMITTED, listingId, null, null, null, amount, 0, "", "Vergi Kraliyet Hazinesine aktarıldı");
    }

    private void refreshReputation(Stats st) {
        if (st == null) return;
        int rep = st.sales * 4 + st.purchases * 2 + st.auctionsWon * 5 + st.likesReceived * 2 + (int)Math.min(100, st.earned / 1000D);
        st.reputation = Math.max(st.reputation, rep);
        if (st.badges == null) st.badges = new LinkedHashSet<String>();
        if (st.sales >= 1) st.badges.add("İlk Satış");
        if (st.earned >= 100000D) st.badges.add("100.000 RC Ciro");
        if (st.auctionsWon >= 1) st.badges.add("Müzayede Fatihi");
        if (st.likesReceived >= 25) st.badges.add("Altın Eller");
        if (st.reputation >= 100) st.badges.add("Kadim Tüccar");
    }


}

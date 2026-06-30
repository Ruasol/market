package com.ruasol.market.data;

import java.util.*;

public class MarketModels {
    public enum Category {
        BLOCK("Bloklar"), FOOD("Yemekler"), ARMOR("Zırhlar"), WEAPON("Silahlar"), TOOL("Aletler"),
        POTION("İksirler"), BOOK("Kitaplar"), MATERIAL("Malzemeler"), OTHER("Diğer");
        public final String title;
        Category(String title) { this.title = title; }
    }

    public enum ListingType { FIXED, AUCTION }

    public enum CurrencyUnit {
        RC("RC"), MARKET_CREDIT("Pazar Kredisi");
        public final String title;
        CurrencyUnit(String title) { this.title = title; }
    }

    public enum ListingState {
        DRAFT, LISTED, LOCKED_TRANSACTION, SOLD, EXPIRED, CANCELLED, STORAGE_PENDING, CLAIMED, FAILED_RECOVERY
    }

    public enum OfferState { ESCROWED, ACCEPTED, REFUNDED, CANCELLED, FAILED_RECOVERY }

    public enum TransactionType {
        LISTING_CREATE, LISTING_FEE, BUY, FIXED_OFFER, OFFER_ACCEPT, OFFER_REFUND,
        AUCTION_BID, AUCTION_REFUND, AUCTION_SETTLE, EXPIRE, CANCEL, CLAIM_STORAGE,
        PAYOUT_QUEUE, PAYOUT_DELIVER, FEATURE, CREDIT_ADMIN, RECOVERY,
        STORAGE_OVERFLOW, ADMIN_ACTION, ECONOMY_TEST, SEARCH, RECOVERY_RESOLVE,
        GUILD_ADMIN, SEASON_RESET, LEGENDARY_SEAL, PRESTIGE_SPEND, TAX_TREASURY
    }

    public enum TransactionStatus { STARTED, COMMITTED, FAILED, RECOVERED }

    public enum TransactionPhase { NONE, LISTING_LOCKED, MONEY_WITHDRAWN, ITEM_RESERVED, ITEM_DELIVERED, SELLER_PAID_OR_PENDING, DATA_SAVED }

    public enum SortMode { FEATURED, PRICE_LOW, PRICE_HIGH, NEWEST, ENDING_SOON, LIKES }

    public static class Listing {
        public String id = Ids.next("L");
        public UUID seller;
        public String sellerName = "?";
        public String itemBase64;
        public String itemName = "?";
        public String itemRegistryName = "?";
        public String itemHash = "";
        public int quantity = 1;
        public Category category = Category.OTHER;
        public ListingType type = ListingType.FIXED;
        public CurrencyUnit currency = CurrencyUnit.RC;
        public ListingState state = ListingState.LISTED;

        public double buyoutPrice;
        public double startPrice;
        public double highestBid;
        public UUID highestBidder;
        public String highestBidderName;

        public long createdAt = System.currentTimeMillis();
        public long expiresAt;
        public long featuredUntil;
        public boolean legendary = false;
        public int prestigeLevel = 0;
        public boolean announced = false;
        public String guildId = "";
        public Set<String> likes = new LinkedHashSet<>();
        public String closeReason = "";
        public long lockedAt;
    }

    public static class Offer {
        public String id = Ids.next("O");
        public String listingId;
        public UUID buyer;
        public String buyerName = "?";
        public double amount;
        public CurrencyUnit currency = CurrencyUnit.RC;
        public long createdAt = System.currentTimeMillis();
        public OfferState state = OfferState.ESCROWED;
        public boolean escrowed = true;
    }

    public static class StorageEntry {
        public String id = Ids.next("S");
        public UUID owner;
        public String ownerName = "?";
        public String itemBase64;
        public String itemName = "?";
        public String reason = "";
        public long createdAt = System.currentTimeMillis();
        public boolean systemOnly = true;
    }

    public static class PendingPayout {
        public String id = Ids.next("P");
        public UUID owner;
        public String ownerName = "?";
        public double amount;
        public CurrencyUnit currency = CurrencyUnit.RC;
        public String reason = "";
        public String listingId = "";
        public long createdAt = System.currentTimeMillis();
    }

    public static class TransactionEntry {
        public String id = Ids.next("T");
        public TransactionType type;
        public TransactionStatus status = TransactionStatus.STARTED;
        public String listingId = "";
        public String offerId = "";
        public UUID actor;
        public UUID target;
        public double amount;
        public double tax;
        public CurrencyUnit currency = CurrencyUnit.RC;
        public String itemHash = "";
        public String note = "";
        public TransactionPhase phase = TransactionPhase.NONE;
        public long createdAt = System.currentTimeMillis();
    }

    public static class Stats {
        public String playerName = "?";
        public int purchases;
        public int sales;
        public int auctionsWon;
        public int auctionsStarted;
        public int offersMade;
        public int offersAccepted;
        public double spent;
        public double earned;
        public int likesReceived;
        public int likesGiven;
        public int followsGiven;
        public int notificationsRead;
        public int reputation;
        public Set<String> badges = new LinkedHashSet<>();
    }


    public static class TradeGuild {
        public String id = "";
        public String displayName = "";
        public String motto = "Loncanın güzidelerine bak.";
        public UUID createdBy;
        public String createdByName = "?";
        public long createdAt = System.currentTimeMillis();
        public Set<String> members = new LinkedHashSet<>();
    }

    public static class SeasonArchive {
        public String id = Ids.next("SEASON");
        public String title = "Pazar Sezonu";
        public long archivedAt = System.currentTimeMillis();
        public List<String> topRows = new ArrayList<>();
        public double treasuryRc;
        public int treasuryCredits;
    }

    public static class NotificationEntry {
        public String id = Ids.next("N");
        public UUID owner;
        public String title = "";
        public String body = "";
        public String listingId = "";
        public boolean read = false;
        public long createdAt = System.currentTimeMillis();
    }

    public static class MarketData {
        public Map<String, Listing> listings = new LinkedHashMap<>();
        public Map<String, Offer> offers = new LinkedHashMap<>();
        public List<StorageEntry> storage = new ArrayList<>();
        public List<StorageEntry> recoveryStorage = new ArrayList<>();
        public List<PendingPayout> payouts = new ArrayList<>();
        public Map<String, Integer> credits = new HashMap<>();
        public Map<String, String> playerNames = new HashMap<>();
        public Map<String, Stats> stats = new HashMap<>();
        public List<TransactionEntry> transactions = new ArrayList<>();
        public List<NotificationEntry> notifications = new ArrayList<>();
        public Map<String, Set<String>> favorites = new HashMap<>();
        public Map<String, TradeGuild> guilds = new LinkedHashMap<>();
        public List<SeasonArchive> seasonArchives = new ArrayList<>();
        public double taxTreasuryRc = 0D;
        public int taxTreasuryCredits = 0;
        public int dataVersion = 405;
    }

    public static class Wizard {
        public String itemBase64;
        public String itemName;
        public String itemRegistryName;
        public String itemHash;
        public Category category = Category.OTHER;
        public int quantity = 1;
        public double price = 100.0;
        public int days = 1;
        public ListingType mode = ListingType.FIXED;
        public int auctionHours = 1;
        public CurrencyUnit auctionCurrency = CurrencyUnit.RC;
        public boolean legendarySeal = false;
        public boolean featuredBoost = false;
        public boolean broadcastBoost = false;

        public void clearItem() {
            itemBase64 = null;
            itemName = null;
            itemRegistryName = null;
            itemHash = null;
            category = Category.OTHER;
            quantity = 1;
        }
    }

    private static class Ids {
        static String next(String prefix) {
            return prefix + Long.toString(System.currentTimeMillis(), 36).toUpperCase(Locale.ROOT)
                    + Integer.toString(new Random().nextInt(1296), 36).toUpperCase(Locale.ROOT);
        }
    }
}

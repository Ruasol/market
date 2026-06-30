package com.ruasol.market.data;

import java.util.ArrayList;
import java.util.List;

public class MarketSnapshot {
    public String view = "HOME";
    public int page = 0;
    public int maxPages = 1;
    public boolean admin = false;
    public int credits = 0;
    public String economyMode = "?";
    public boolean economyReady = false;
    public double taxPercent = 5.0D;
    public int storageCount = 0;
    public int storageLimit = 100;
    public int recoveryItems = 0;
    public int auctionCreditCost = 0;
    public int featuredCreditCost = 15;
    public int legendarySealCreditCost = 30;
    public int announcementCreditCost = 30;
    public int auctionHour3CreditCost = 5;
    public int auctionHour5CreditCost = 10;
    public double fixed3DayFee = 200D;
    public double fixed5DayFee = 400D;
    public String boardMode = "EARNED";
    public String message = "";
    public String searchQuery = "";
    public boolean enableUiSounds = true;
    public String selectedGuildId = "";
    public String treasuryLine = "";
    public int profileSales = 0;
    public int profilePurchases = 0;
    public int profileAuctionsWon = 0;
    public int profileLikesReceived = 0;
    public double profileEarned = 0D;
    public double profileSpent = 0D;
    public int profileReputation = 0;
    public int profileActiveListings = 0;
    public int profileNotificationCount = 0;
    public String profileBadges = "";
    public WizardView wizard = new WizardView();
    public ListingView selected;
    public List<ListingView> listings = new ArrayList<ListingView>();
    public List<OfferView> offers = new ArrayList<OfferView>();
    public List<StorageView> storage = new ArrayList<StorageView>();
    public List<PayoutView> payouts = new ArrayList<PayoutView>();
    public List<BoardRow> board = new ArrayList<BoardRow>();
    public List<TxRow> transactions = new ArrayList<TxRow>();
    public List<NotificationView> notifications = new ArrayList<NotificationView>();
    public List<GuildView> guilds = new ArrayList<GuildView>();
    public List<BadgeView> badges = new ArrayList<BadgeView>();

    public static class WizardView {
        public boolean hasItem;
        public String itemName = "Ürün seçilmedi";
        public String registryName = "minecraft:air";
        public String itemBase64 = "";
        public String category = "Diğer";
        public int quantity = 1;
        public double price = 100.0D;
        public String mode = "FIXED";
        public int days = 1;
        public int auctionHours = 1;
        public String auctionCurrency = "RC";
        public boolean legendarySeal = false;
        public boolean featuredBoost = false;
        public boolean broadcastBoost = false;
    }

    public static class ListingView {
        public String id;
        public String seller;
        public String itemName;
        public String registryName;
        public String itemBase64 = "";
        public String itemHash = "";
        public String category;
        public int quantity = 1;
        public String type;
        public String currency;
        public double price;
        public double startPrice;
        public double highestBid;
        public double minNextBid;
        public String highestBidder = "";
        public long remainingMillis;
        public int likes;
        public boolean likedByMe;
        public boolean featured;
        public boolean mine;
        public boolean followedByMe;
        public boolean legendary;
        public int prestigeLevel;
        public String guildId = "";
    }

    public static class OfferView {
        public String id;
        public String listingId;
        public String buyer;
        public String itemName;
        public String itemBase64 = "";
        public double amount;
        public String currency;
        public long createdAt;
    }

    public static class StorageView {
        public String id;
        public String itemName;
        public String itemBase64 = "";
        public String reason;
        public long createdAt;
    }

    public static class PayoutView {
        public String id;
        public double amount;
        public String currency;
        public String reason;
    }

    public static class BoardRow {
        public String player;
        public int sales;
        public int purchases;
        public int auctionsWon;
        public int likesReceived;
        public double earned;
        public double spent;
        public int reputation;
        public String badgeLine = "";
    }

    public static class TxRow {
        public String id;
        public String type;
        public String status;
        public String listingId;
        public double amount;
        public String note;
        public String actor;
        public String target;
        public String phase;
    }

    public static class NotificationView {
        public String id;
        public String title;
        public String body;
        public String listingId;
        public boolean read;
        public long createdAt;
    }
    public static class GuildView {
        public String id;
        public String displayName;
        public String motto;
        public int members;
        public int activeListings;
        public boolean mine;
    }

    public static class BadgeView {
        public String name;
        public String description;
    }

}

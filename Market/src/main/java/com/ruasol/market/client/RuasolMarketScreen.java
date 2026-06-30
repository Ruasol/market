package com.ruasol.market.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.ruasol.market.RuasolMarket;
import com.ruasol.market.data.MarketSnapshot;
import com.ruasol.market.network.MarketActionPacket;
import com.ruasol.market.network.NetworkHandler;
import com.ruasol.market.util.ItemCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class RuasolMarketScreen extends Screen {
    private static final ResourceLocation ITEM_CARD = new ResourceLocation(RuasolMarket.MODID, "textures/gui/item_card.png");
    private static final int PANEL_W = 672;
    private static final int PANEL_H = 378;
    private static final int HEADER_H = 64;
    private static final int NAV_X = 16;
    private static final int NAV_Y = 78;
    private static final int NAV_W = 150;
    private static final int CONTENT_X = 180;
    private static final int CONTENT_Y = 78;
    private static final int CONTENT_W = 476;
    private static final int CONTENT_H = 252;
    private static final int FOOTER_Y = 338;
    private static final int INFO_PAGES = 8;

    private final MarketSnapshot snap;
    private final Map<String, ItemStack> previewCache = new HashMap<String, ItemStack>();
    private int left;
    private int top;
    private TextFieldWidget priceField;
    private TextFieldWidget quantityField;
    private TextFieldWidget searchField;
    private Pending pending;
    private ItemStack hoveredStack = ItemStack.EMPTY;
    private static String externalAnnouncementTitle = "";
    private static String externalAnnouncementBody = "";
    private static long externalAnnouncementUntil = 0L;

    public static void setExternalAnnouncement(String title, String body) {
        externalAnnouncementTitle = title == null || title.trim().isEmpty() ? "Kadim Duyuru" : title.trim();
        externalAnnouncementBody = body == null ? "" : body.trim();
        externalAnnouncementUntil = System.currentTimeMillis() + 5500L;
    }

    public RuasolMarketScreen(MarketSnapshot snapshot) {
        super(new StringTextComponent("Ruasol's Tavern Pazarı"));
        this.snap = snapshot == null ? new MarketSnapshot() : snapshot;
        if (this.snap.view == null || this.snap.view.isEmpty()) this.snap.view = "HOME";
        if (this.snap.wizard == null) this.snap.wizard = new MarketSnapshot.WizardView();
    }

    @Override
    protected void init() {
        this.left = (this.width - PANEL_W) / 2;
        this.top = (this.height - PANEL_H) / 2;
        this.buttons.clear();
        this.children.clear();
        this.priceField = null;
        this.quantityField = null;
        this.searchField = null;
        if (pending != null) {
            addRtButton(left + 245, top + 240, 86, 24, "Onayla", () -> { Pending p = pending; pending = null; if (p != null) p.run(); });
            addRtButton(left + 342, top + 240, 86, 24, "Vazgeç", () -> { pending = null; this.init(); });
            return;
        }
        sideNav();
        if (is("SELL")) initSell();
        else if (is("DETAIL")) initDetail();
        else if (is("STORAGE")) initStorage();
        else if (is("MAIL")) initMail();
        else if (is("OFFERS") || is("MY_OFFERS")) initOffers();
        else if (snap.view.startsWith("BOARD")) initBoard();
        else if (is("INFO")) initInfo();
        else if (is("ADMIN")) initAdmin();
        else if (is("PROFILE")) initProfile();
        else initListings();
    }

    private boolean is(String v) { return v.equals(snap.view); }
    private boolean isHomeView() { return is("HOME"); }
    private boolean isAuctionView() { return snap.view != null && (snap.view.startsWith("AUCTIONS") || is("MY_BIDS")); }
    private boolean isGuildMarketView() { return snap.view != null && snap.view.startsWith("GUILD_"); }
    private boolean isPersonalView() { return is("PROFILE") || is("SELL") || is("MINE") || is("OFFERS") || is("MY_OFFERS") || is("STORAGE") || is("MAIL") || is("FAVORITES"); }
    private boolean isFixedListView() { return snap.view.startsWith("PRODUCTS") || snap.view.startsWith("CAT_") || is("SEARCH") || is("FAVORITES") || is("LEGENDARY") || is("FEATURED"); }

    private int cx(int x) { return left + CONTENT_X + x; }
    private int cy(int y) { return top + CONTENT_Y + y; }
    private int nx(int x) { return left + NAV_X + x; }
    private int ny(int y) { return top + NAV_Y + y; }

    private void sideNav() {
        int x = nx(14);
        int y = ny(8);
        int w = 122;
        int h = 22;
        int gap = 27;
        addRtButton(x, y, w, h, "Ana Salon", isHomeView(), () -> nav("HOME", 0, "")); y += gap;
        addRtButton(x, y, w, h, "Kadim Pazar", isFixedListView() && !is("FAVORITES") && !is("LEGENDARY") && !is("FEATURED"), () -> nav("PRODUCTS", 0, "")); y += gap;
        addRtButton(x, y, w, h, "Öne Çıkan", is("FEATURED"), () -> nav("FEATURED", 0, "")); y += gap;
        addRtButton(x, y, w, h, "Efsanevi", is("LEGENDARY"), () -> nav("LEGENDARY", 0, "")); y += gap;
        addRtButton(x, y, w, h, "Loncalar", is("GUILDS") || snap.view.startsWith("GUILD_"), () -> nav("GUILDS", 0, "")); y += gap;
        addRtButton(x, y, w, h, "Müzayede", isAuctionView(), () -> nav("AUCTIONS", 0, "")); y += gap;
        addRtButton(x, y, w, h, "Tablo", snap.view.startsWith("BOARD"), () -> nav("BOARD", 0, "")); y += gap;
        addRtButton(x, y, w, h, "Profilim", isPersonalView(), () -> nav("PROFILE", 0, "")); y += gap;
        addRtButton(x, y, w, h, "Rehber", is("INFO"), () -> nav("INFO", 0, ""));
        if (snap.admin) addRtButton(x, top + PANEL_H - 36, w, h, "OP Mührü", is("ADMIN"), () -> nav("ADMIN", 0, ""));
    }

    private void initListings() {
        if (isHomeView()) {
            addRtButton(cx(24), cy(224), 104, 24, "Kadim Pazar", () -> nav("PRODUCTS", 0, ""));
            addRtButton(cx(140), cy(224), 104, 24, "Müzayede", () -> nav("AUCTIONS", 0, ""));
            addRtButton(cx(256), cy(224), 94, 24, "Profilim", () -> nav("PROFILE", 0, ""));
            addRtButton(cx(362), cy(224), 84, 24, "Rehber", () -> nav("INFO", 0, ""));
            return;
        }
        if (is("GUILDS")) {
            int y = cy(62);
            for (int i = 0; i < snap.guilds.size(); i++) {
                final MarketSnapshot.GuildView g = snap.guilds.get(i);
                addRtButton(cx(360), y + i * 38, 96, 22, "Lonca Pazarı", () -> nav("GUILD_" + g.id, 0, ""));
            }
            addPaging("GUILDS");
            return;
        }
        if (isFixedListView()) {
            int x = cx(14);
            int y = cy(48);
            addRtButton(x, y, 52, 20, "Blok", snap.view.startsWith("CAT_BLOCK"), () -> nav("CAT_BLOCK", 0, ""));
            addRtButton(x + 60, y, 60, 20, "Yemek", snap.view.startsWith("CAT_FOOD"), () -> nav("CAT_FOOD", 0, ""));
            addRtButton(x + 128, y, 54, 20, "Zırh", snap.view.startsWith("CAT_ARMOR"), () -> nav("CAT_ARMOR", 0, ""));
            addRtButton(x + 190, y, 58, 20, "Silah", snap.view.startsWith("CAT_WEAPON"), () -> nav("CAT_WEAPON", 0, ""));
            addRtButton(x + 256, y, 54, 20, "Alet", snap.view.startsWith("CAT_TOOL"), () -> nav("CAT_TOOL", 0, ""));
            addRtButton(x + 318, y, 54, 20, "İksir", snap.view.startsWith("CAT_POTION"), () -> nav("CAT_POTION", 0, ""));
            addRtButton(x + 380, y, 60, 20, "Kitap", snap.view.startsWith("CAT_BOOK"), () -> nav("CAT_BOOK", 0, ""));
            y += 27;
            addRtButton(x, y, 82, 20, "Malzeme", snap.view.startsWith("CAT_MATERIAL"), () -> nav("CAT_MATERIAL", 0, ""));
            addRtButton(x + 90, y, 60, 20, "Diğer", snap.view.startsWith("CAT_OTHER"), () -> nav("CAT_OTHER", 0, ""));
            addRtButton(x + 230, y, 58, 20, "Yeni", !snap.view.endsWith("PRICE_LOW") && !snap.view.endsWith("PRICE_HIGH"), () -> nav(sortView("NEWEST"), 0, ""));
            addRtButton(x + 296, y, 58, 20, "Ucuz", snap.view.endsWith("PRICE_LOW"), () -> nav(sortView("PRICE_LOW"), 0, ""));
            addRtButton(x + 362, y, 70, 20, "Pahalı", snap.view.endsWith("PRICE_HIGH"), () -> nav(sortView("PRICE_HIGH"), 0, ""));
            searchField = new TextFieldWidget(this.font, x, cy(104), 330, 20, new StringTextComponent("Ara"));
            searchField.setEnableBackgroundDrawing(false);
            searchField.setMaxStringLength(32);
            searchField.setText(snap.searchQuery == null ? "" : snap.searchQuery);
            addButton(searchField);
            addRtButton(x + 342, cy(103), 82, 22, "Ara", () -> nav("SEARCH", 0, searchField == null ? "" : searchField.getText()));
        }
        if (isAuctionView()) {
            int x = cx(14);
            int y = cy(58);
            addRtButton(x, y, 64, 20, "Tümü", is("AUCTIONS"), () -> nav("AUCTIONS", 0, ""));
            addRtButton(x + 72, y, 54, 20, "RC", is("AUCTIONS_RC"), () -> nav("AUCTIONS_RC", 0, ""));
            addRtButton(x + 134, y, 92, 20, "P. Kredi", is("AUCTIONS_CREDIT"), () -> nav("AUCTIONS_CREDIT", 0, ""));
            addRtButton(x + 234, y, 104, 20, "Son Dakika", is("AUCTIONS_SOON"), () -> nav("AUCTIONS_SOON", 0, ""));
            addRtButton(x + 346, y, 90, 20, "Teklifim", is("MY_BIDS"), () -> nav("MY_BIDS", 0, ""));
        }
        int listY = isAuctionView() ? cy(104) : (isGuildMarketView() ? cy(72) : cy(136));
        for (int i = 0; i < snap.listings.size(); i++) {
            final MarketSnapshot.ListingView l = snap.listings.get(i);
            int rowY = listY + i * 44;
            addRtButton(cx(378), rowY + 7, 78, 24, l.type.equals("AUCTION") ? "Teklif" : "Detay", () -> nav("DETAIL", 0, l.id));
        }
        addPaging(snap.view);
    }

    private String sortView(String sort) {
        if (snap.view.startsWith("CAT_")) return snap.view.replace("_PRICE_LOW", "").replace("_PRICE_HIGH", "").replace("_NEWEST", "") + "_" + sort;
        return "PRODUCTS_" + sort;
    }

    private void initProfile() {
        int y = cy(194);
        addRtButton(cx(24), y, 106, 24, "Ürün Ekle", () -> nav("SELL", 0, ""));
        addRtButton(cx(138), y, 106, 24, "İlanlarım", () -> nav("MINE", 0, ""));
        addRtButton(cx(252), y, 112, 24, "Tekliflerim", () -> nav("OFFERS", 0, ""));
        addRtButton(cx(372), y, 84, 24, "Depom", () -> nav("STORAGE", 0, ""));
        y += 36;
        addRtButton(cx(24), y, 88, 24, "Postam", () -> nav("MAIL", 0, ""));
        addRtButton(cx(120), y, 120, 24, "Takip Listem", () -> nav("FAVORITES", 0, ""));
        addRtButton(cx(248), y, 100, 24, "Rozetlerim", () -> nav("BOARD_BADGES", 0, ""));
        addRtButton(cx(356), y, 108, 24, "İstatistikler", () -> nav("BOARD", 0, ""));
    }

    private void initSell() {
        boolean auctionMode = "AUCTION".equals(snap.wizard.mode);
        addRtButton(cx(24), cy(36), 126, 22, "Eldeki Ürünü Seç", () -> action("SELECT_HELD", "", 0, 0, "SELL", ""));
        quantityField = new TextFieldWidget(this.font, cx(368), cy(36), 78, 20, new StringTextComponent("Adet"));
        quantityField.setEnableBackgroundDrawing(false);
        quantityField.setText(String.valueOf(Math.max(1, snap.wizard.quantity)));
        quantityField.setMaxStringLength(6);
        addButton(quantityField);

        addRtButton(cx(24), cy(96), 112, 22, "İlan Panosu", !auctionMode, () -> action("SET_MODE_FIXED", "", 0, 0, "SELL", ""));
        addRtButton(cx(146), cy(96), 126, 22, "Açık Artırma", auctionMode, () -> action("SET_MODE_AUCTION", "", 0, 0, "SELL", ""));
        priceField = new TextFieldWidget(this.font, cx(368), cy(96), 78, 20, new StringTextComponent("Fiyat"));
        priceField.setEnableBackgroundDrawing(false);
        priceField.setText(String.valueOf((int)Math.max(1, snap.wizard.price)));
        priceField.setMaxStringLength(12);
        addButton(priceField);

        if (!auctionMode) {
            addRtButton(cx(24), cy(156), 100, 22, "1 Gün 0RC", snap.wizard.days == 1, () -> action("SET_DAYS", "", 0, 1, "SELL", ""));
            addRtButton(cx(132), cy(156), 106, 22, "3 Gün " + fmt(snap.fixed3DayFee), snap.wizard.days == 3, () -> action("SET_DAYS", "", 0, 3, "SELL", ""));
            addRtButton(cx(246), cy(156), 106, 22, "5 Gün " + fmt(snap.fixed5DayFee), snap.wizard.days == 5, () -> action("SET_DAYS", "", 0, 5, "SELL", ""));
        } else {
            addRtButton(cx(24), cy(156), 70, 22, "1 Saat", snap.wizard.auctionHours == 1, () -> action("SET_AUCTION_HOURS", "", 0, 1, "SELL", ""));
            addRtButton(cx(102), cy(156), 70, 22, "2 Saat", snap.wizard.auctionHours == 2, () -> action("SET_AUCTION_HOURS", "", 0, 2, "SELL", ""));
            addRtButton(cx(180), cy(156), 76, 22, "3s " + snap.auctionHour3CreditCost + "PK", snap.wizard.auctionHours == 3, () -> action("SET_AUCTION_HOURS", "", 0, 3, "SELL", ""));
            addRtButton(cx(264), cy(156), 76, 22, "5s " + snap.auctionHour5CreditCost + "PK", snap.wizard.auctionHours == 5, () -> action("SET_AUCTION_HOURS", "", 0, 5, "SELL", ""));
            addRtButton(cx(348), cy(156), 44, 22, "RC", "RC".equals(snap.wizard.auctionCurrency), () -> action("SET_AUCTION_CURRENCY_RC", "", 0, 0, "SELL", ""));
            addRtButton(cx(400), cy(156), 44, 22, "PK", "Pazar Kredisi".equals(snap.wizard.auctionCurrency), () -> action("SET_AUCTION_CURRENCY_CREDIT", "", 0, 0, "SELL", ""));
        }
        addRtButton(cx(24), cy(218), 124, 22, "Efsanevi 30PK", snap.wizard.legendarySeal, () -> action("TOGGLE_LEGENDARY", "", 0, 0, "SELL", ""));
        addRtButton(cx(158), cy(218), 118, 22, "Öne Çıkar 15PK", snap.wizard.featuredBoost, () -> action("TOGGLE_FEATURED_BOOST", "", 0, 0, "SELL", ""));
        addRtButton(cx(286), cy(218), 134, 22, "Duyuru 30PK", snap.wizard.broadcastBoost, () -> action("TOGGLE_ANNOUNCEMENT_BOOST", "", 0, 0, "SELL", ""));
        addRtButton(cx(300), top + FOOTER_Y, 158, 22, auctionMode ? "Müzayedeyi Başlat" : "İlanı Mühürle", () -> { if ("AUCTION".equals(snap.wizard.mode)) openAuctionConfirm(); else openFixedListingConfirm(); });
    }

    private void initDetail() {
        if (snap.selected == null) return;
        priceField = new TextFieldWidget(this.font, cx(292), cy(86), 110, 20, new StringTextComponent("Teklif"));
        priceField.setEnableBackgroundDrawing(false);
        double d = snap.selected.type.equals("AUCTION") ? Math.max(snap.selected.minNextBid, snap.selected.startPrice) : Math.max(1, snap.selected.price * 0.9D);
        priceField.setText(String.valueOf((int)Math.ceil(d)));
        priceField.setMaxStringLength(12);
        addButton(priceField);
        if (snap.selected.type.equals("FIXED")) {
            addRtButton(cx(292), cy(120), 86, 22, "Satın Al", () -> confirm("Satın alma onayı", snap.selected.itemName + " için " + fmt(snap.selected.price) + " RC ödeyeceksin.", () -> action("BUY", snap.selected.id, 0, 0, "PRODUCTS", "")));
            addRtButton(cx(386), cy(120), 86, 22, "Teklif Ver", () -> confirm("Teklif emanet kasaya alınacak", "Teklif: " + fmt(amount()) + " RC. Satıcı kabul etmezse para iade edilir.", () -> action("OFFER", snap.selected.id, amount(), 0, "DETAIL", snap.selected.id)));
        } else {
            addRtButton(cx(292), cy(120), 138, 22, "Müzayede Teklifi", () -> confirm("Müzayede teklif onayı", "Teklif: " + fmt(amount()) + " " + snap.selected.currency + ". Eski en yüksek teklif otomatik iade edilir.", () -> action("BID", snap.selected.id, amount(), 0, "DETAIL", snap.selected.id)));
        }
        addRtButton(cx(292), cy(154), 86, 22, snap.selected.likedByMe ? "Mühürlü" : "Beğen", () -> action("LIKE", snap.selected.id, 0, 0, "DETAIL", snap.selected.id));
        addRtButton(cx(386), cy(154), 86, 22, snap.selected.followedByMe ? "Takipte" : "Takip", () -> action("FAVORITE", snap.selected.id, 0, 0, "DETAIL", snap.selected.id));
        if (snap.selected.mine) {
            addRtButton(cx(292), cy(188), 86, 22, "Prestij", () -> confirm("Prestij mührü", "Bu ilana ek prestij mührü basılacak ve Pazar Kredisi harcanacak.", () -> action("PRESTIGE_SEAL", snap.selected.id, 0, 0, "DETAIL", snap.selected.id)));
            addRtButton(cx(386), cy(188), 86, 22, "Öne Çıkar", () -> confirm("Öne çıkarma", "Bu ilan vitrine " + snap.featuredCreditCost + " Pazar Kredisi karşılığı mühürlenecek.", () -> action("FEATURE", snap.selected.id, 0, 0, "DETAIL", snap.selected.id)));
            addRtButton(cx(292), cy(222), 86, 22, "İptal", () -> confirm("İlan iptali", "Aktif teklif yoksa item sana/depona iade edilir.", () -> action("CANCEL_LISTING", snap.selected.id, 0, 0, "MINE", "")));
        }
    }

    private void initStorage() {
        int y = cy(54);
        for (MarketSnapshot.StorageView s : snap.storage) {
            final MarketSnapshot.StorageView entry = s;
            addRtButton(cx(360), y, 94, 22, "Teslim Al", () -> action("CLAIM_STORAGE", entry.id, 0, 0, "STORAGE", ""));
            y += 36;
        }
        addRtButton(cx(0), top + FOOTER_Y, 88, 22, "Hepsini Al", () -> action("CLAIM_ALL", "", 0, 0, "STORAGE", ""));
        addRtButton(cx(98), top + FOOTER_Y, 94, 22, "Ödeme Al", () -> action("PAYOUTS", "", 0, 0, "STORAGE", ""));
        addPaging("STORAGE");
    }

    private void initOffers() {
        int y = cy(64);
        for (MarketSnapshot.OfferView o : snap.offers) {
            final MarketSnapshot.OfferView offer = o;
            if (is("OFFERS")) addRtButton(cx(360), y, 94, 22, "Kabul", () -> confirm("Teklif kabul", offer.itemName + " için " + fmt(offer.amount) + " " + offer.currency + " kabul edilecek.", () -> action("ACCEPT_OFFER", offer.id, 0, 0, "OFFERS", "")));
            else addRtButton(cx(360), y, 94, 22, "İptal", () -> action("CANCEL_OFFER", offer.id, 0, 0, "MY_OFFERS", ""));
            y += 36;
        }
        addRtButton(cx(0), cy(26), 90, 22, "Gelen", is("OFFERS"), () -> nav("OFFERS", 0, ""));
        addRtButton(cx(100), cy(26), 110, 22, "Verdiklerim", is("MY_OFFERS"), () -> nav("MY_OFFERS", 0, ""));
        addPaging(snap.view);
    }

    private void initBoard() {
        int x = cx(18);
        addRtButton(x, cy(40), 90, 22, "Kazanç", is("BOARD"), () -> nav("BOARD", 0, ""));
        addRtButton(x + 100, cy(40), 94, 22, "Harcama", is("BOARD_SPENT"), () -> nav("BOARD_SPENT", 0, ""));
        addRtButton(x + 204, cy(40), 72, 22, "Satış", is("BOARD_SALES"), () -> nav("BOARD_SALES", 0, ""));
        addRtButton(x + 286, cy(40), 66, 22, "Alış", is("BOARD_BUY"), () -> nav("BOARD_BUY", 0, ""));
        addRtButton(x, cy(68), 84, 22, "Beğeni", is("BOARD_LIKE"), () -> nav("BOARD_LIKE", 0, ""));
        addRtButton(x + 94, cy(68), 108, 22, "Müzayede", is("BOARD_AUCTION"), () -> nav("BOARD_AUCTION", 0, ""));
        addRtButton(x + 212, cy(68), 78, 22, "İtibar", is("BOARD_REP"), () -> nav("BOARD_REP", 0, ""));
        addRtButton(x + 300, cy(68), 74, 22, "Rozet", is("BOARD_BADGES"), () -> nav("BOARD_BADGES", 0, ""));
    }

    private void initInfo() {
        String[] tabs = {"Amaç", "İlan", "Açık Artırma", "Teklif", "Lonca", "Efsanevi", "Rozet", "Hazine"};
        int page = Math.max(0, Math.min(INFO_PAGES - 1, snap.page));
        for (int i = 0; i < tabs.length; i++) {
            final int idx = i;
            addRtButton(cx(24), cy(42 + i * 24), 112, 20, tabs[i], page == i, () -> nav("INFO", idx, ""));
        }
        addPaging("INFO");
    }

    private void initMail() {
        addRtButton(cx(0), top + FOOTER_Y, 82, 22, "Temizle", () -> action("CLEAR_MAIL", "", 0, 0, "MAIL", ""));
        addPaging("MAIL");
    }

    private void initAdmin() {
        addRtButton(cx(28), cy(184), 88, 22, "Yenile", () -> action("ADMIN_RELOAD", "", 0, 0, "ADMIN", ""));
        addRtButton(cx(124), cy(184), 88, 22, "Ekonomi", () -> action("ADMIN_ECON_TEST", "", 0, 0, "ADMIN", ""));
        addRtButton(cx(28), cy(212), 88, 22, "Tara", () -> action("ADMIN_RECOVER", "", 0, 0, "ADMIN", ""));
        addRtButton(cx(124), cy(212), 88, 22, "Çöz", () -> action("ADMIN_RESOLVE_RECOVERY", "", 0, 0, "ADMIN", ""));
        addRtButton(cx(28), cy(240), 184, 22, "Pazar Raporu", () -> action("ADMIN_REPORT", "", 0, 0, "ADMIN", ""));
    }

    private void addPaging(String view) {
        addRtButton(cx(330), top + FOOTER_Y, 62, 22, "Önce", () -> nav(view, Math.max(0, snap.page - 1), carrySearch()));
        addRtButton(cx(402), top + FOOTER_Y, 62, 22, "Sonra", () -> nav(view, snap.page + 1, carrySearch()));
    }

    @Override
    public void render(MatrixStack ms, int mouseX, int mouseY, float partialTicks) {
        this.hoveredStack = ItemStack.EMPTY;
        this.renderBackground(ms);
        drawPanel(ms);
        drawHeader(ms);
        drawSide(ms);
        if (pending != null) drawConfirm(ms); else drawContent(ms, mouseX, mouseY);
        if (pending == null) drawAnnouncementPopup(ms);
        super.render(ms, mouseX, mouseY, partialTicks);
        if (!hoveredStack.isEmpty() && pending == null) this.renderTooltip(ms, hoveredStack, mouseX, mouseY);
    }

    private void drawPanel(MatrixStack ms) {
        fill(ms, left, top, left + PANEL_W, top + PANEL_H, 0xF20D0906);
        fill(ms, left + 3, top + 3, left + PANEL_W - 3, top + PANEL_H - 3, 0xEE21160D);
        fill(ms, left + 9, top + 9, left + PANEL_W - 9, top + PANEL_H - 9, 0xF315100B);
        for (int sx = left + 14; sx < left + PANEL_W - 14; sx += 18) fill(ms, sx, top + 10, sx + 2, top + PANEL_H - 10, 0x181F160F);
        for (int sy = top + 18; sy < top + PANEL_H - 18; sy += 20) fill(ms, left + 10, sy, left + PANEL_W - 10, sy + 1, 0x181F160F);
        stroke(ms, left, top, PANEL_W, PANEL_H, 0xFFD6A950);
        stroke(ms, left + 4, top + 4, PANEL_W - 8, PANEL_H - 8, 0xFF6F471F);
        stroke(ms, left + 8, top + 8, PANEL_W - 16, PANEL_H - 16, 0xAA2F2215);
        fill(ms, left + 14, top + 12, left + PANEL_W - 14, top + HEADER_H, 0x99402A17);
        fill(ms, left + 18, top + 16, left + PANEL_W - 18, top + HEADER_H - 4, 0x55180F08);
        stroke(ms, left + 14, top + 12, PANEL_W - 28, HEADER_H - 12, 0xCC9E6D32);
        fill(ms, nx(0), ny(0), nx(NAV_W), top + PANEL_H - 16, 0x77100C08);
        stroke(ms, nx(0), ny(0), NAV_W, PANEL_H - NAV_Y - 16, 0xBB8F6532);
        fill(ms, cx(0), cy(0), cx(CONTENT_W), cy(CONTENT_H), 0x66100C08);
        stroke(ms, cx(0), cy(0), CONTENT_W, CONTENT_H, 0xAA8F6532);
        fill(ms, cx(0), top + FOOTER_Y, cx(CONTENT_W), top + FOOTER_Y + 28, 0x55100C08);
        stroke(ms, cx(0), top + FOOTER_Y, CONTENT_W, 28, 0x998F6532);
        corner(ms, left + 10, top + 10, true, true); corner(ms, left + PANEL_W - 26, top + 10, false, true);
        corner(ms, left + 10, top + PANEL_H - 26, true, false); corner(ms, left + PANEL_W - 26, top + PANEL_H - 26, false, false);
    }

    private void drawHeader(MatrixStack ms) {
        drawCenteredString(ms, this.font, new StringTextComponent("RUASOL'S TAVERN"), left + PANEL_W / 2, top + 21, 0xFFF1DCA6);
        drawCenteredString(ms, this.font, new StringTextComponent("PAZAR"), left + PANEL_W / 2, top + 36, 0xFFD5A957);
        text(ms, trim(titleForView(), 24), left + 26, top + 27, 0xFFE7C878);
        text(ms, "Pazar Kredisi: " + snap.credits, left + PANEL_W - 142, top + 27, 0xFFD5B56B);
        if (snap.message != null && !snap.message.trim().isEmpty()) {
            int color = isErrorMessage(snap.message) ? 0xFFFF9B86 : (isSuccessMessage(snap.message) ? 0xFFA8E6A3 : 0xFFECD9A8);
            fill(ms, cx(8), top + 52, cx(CONTENT_W - 8), top + 66, 0xAA23170E);
            stroke(ms, cx(8), top + 52, CONTENT_W - 16, 14, isErrorMessage(snap.message) ? 0xAA9E3A2E : 0xAA8F6532);
            text(ms, trim(messagePrefix(snap.message), 70), cx(16), top + 55, color);
        }
    }

    private boolean isErrorMessage(String s) {
        String m = s.toLowerCase(Locale.ROOT);
        return m.contains("yok") || m.contains("yeterli") || m.contains("başarısız") || m.contains("redded") || m.contains("geçersiz") || m.contains("bulunamadı") || m.contains("hata") || m.contains("iptal edildi");
    }
    private boolean isSuccessMessage(String s) {
        String m = s.toLowerCase(Locale.ROOT);
        return m.contains("açıldı") || m.contains("başladı") || m.contains("seçildi") || m.contains("başarılı") || m.contains("alındı") || m.contains("mühürlendi");
    }
    private String messagePrefix(String s) {
        if (s == null || s.isEmpty()) return "";
        if (isErrorMessage(s)) return "İşlem Başarısız: " + s;
        if (isSuccessMessage(s)) return "İşlem Başarılı: " + s;
        return "Bilgi: " + s;
    }

    private void drawSide(MatrixStack ms) {
        text(ms, "ANA MENÜ", nx(42), ny(-12), 0xFFB99B63);
    }

    private void drawContent(MatrixStack ms, int mouseX, int mouseY) {
        if (is("SELL")) drawSell(ms, mouseX, mouseY);
        else if (is("DETAIL")) drawDetail(ms, mouseX, mouseY);
        else if (is("STORAGE")) drawStorage(ms, mouseX, mouseY);
        else if (is("MAIL")) drawMail(ms);
        else if (is("OFFERS") || is("MY_OFFERS")) drawOffers(ms, mouseX, mouseY);
        else if (snap.view.startsWith("BOARD")) drawBoard(ms);
        else if (is("INFO")) drawInfo(ms);
        else if (is("ADMIN")) drawAdmin(ms);
        else if (is("PROFILE")) drawProfile(ms);
        else drawListings(ms, mouseX, mouseY);
    }

    private void drawHome(MatrixStack ms) {
        box(ms, cx(16), cy(18), 444, 72);
        title(ms, "Ana Salon", cx(32), cy(30));
        text(ms, "Ruasol's Tavern Pazarı'na hoş geldin.", cx(32), cy(46), 0xFFE8DEC4);
        text(ms, "Güvenli ticaret, açık artırma ve prestij salonu.", cx(32), cy(60), 0xFFB9AC91);
        box(ms, cx(16), cy(104), 210, 104);
        title(ms, "Pazar Özeti", cx(30), cy(116));
        text(ms, "Pazar Kredisi: " + snap.credits, cx(30), cy(138), 0xFFD2B46A);
        text(ms, "Depo: " + snap.storageCount + "/" + snap.storageLimit, cx(30), cy(154), 0xFFE8DEC4);
        text(ms, "Kurtarma: " + snap.recoveryItems, cx(30), cy(170), snap.recoveryItems > 0 ? 0xFFFFC96A : 0xFFB9AC91);
        box(ms, cx(246), cy(104), 214, 104);
        title(ms, "Öne Çıkanlar", cx(260), cy(116));
        if (snap.listings.isEmpty()) text(ms, "Vitrin henüz boş.", cx(260), cy(138), 0xFFB9AC91);
        for (int i = 0; i < Math.min(3, snap.listings.size()); i++) {
            MarketSnapshot.ListingView l = snap.listings.get(i);
            text(ms, "• " + trim(l.itemName, 24) + " — " + fmt(l.price) + " " + l.currency, cx(260), cy(138 + i * 16), 0xFFE8DEC4);
        }
    }

    private void drawListings(MatrixStack ms, int mouseX, int mouseY) {
        if (isHomeView()) { drawHome(ms); return; }
        if (is("GUILDS")) { drawGuilds(ms); return; }
        if (isFixedListView()) {
            title(ms, titleForView(), cx(18), cy(18));
            text(ms, "Kategoriler, sıralama ve mühürlü ilanlar.", cx(18), cy(32), 0xFFB9AC91);
            inputBox(ms, cx(14), cy(104), 330, 20);
        } else if (isAuctionView()) {
            title(ms, "Kadim Açık Artırma Salonu", cx(18), cy(22));
            text(ms, "Rekabetli tekliflerin toplandığı salon.", cx(18), cy(38), 0xFFB9AC91);
        } else if (isGuildMarketView()) {
            title(ms, "Lonca Pazarı", cx(18), cy(18));
            text(ms, "Seçili ticaret loncasının mühürlü vitrini.", cx(18), cy(34), 0xFFB9AC91);
        } else if (is("MINE")) {
            title(ms, "İlanlarım", cx(18), cy(18));
            text(ms, "Aktif kişisel ilanların burada listelenir.", cx(18), cy(34), 0xFFB9AC91);
        } else if (is("FAVORITES")) {
            title(ms, "Takip Listem", cx(18), cy(18));
            text(ms, "Takibe aldığın pazar ürünleri.", cx(18), cy(34), 0xFFB9AC91);
        }
        int listY = isAuctionView() ? cy(104) : (isGuildMarketView() || is("MINE") || is("FAVORITES") ? cy(72) : cy(136));
        if (snap.listings.isEmpty()) text(ms, "Bu salonda şimdilik mühürlü ürün yok.", cx(8), listY + 8, 0xFFB9AC91);
        for (int i = 0; i < snap.listings.size(); i++) {
            MarketSnapshot.ListingView l = snap.listings.get(i);
            int rowY = listY + i * 44;
            listingCard(ms, l, cx(8), rowY, mouseX, mouseY);
        }
        text(ms, "Sayfa " + (snap.page + 1) + "/" + Math.max(1, snap.maxPages), cx(206), top + FOOTER_Y + 7, 0xFF8F826B);
    }

    private void listingCard(MatrixStack ms, MarketSnapshot.ListingView l, int x, int y, int mouseX, int mouseY) {
        int w = 462;
        int h = 38;
        fill(ms, x + 2, y + 3, x + w + 2, y + h + 3, 0x66100A05);
        fill(ms, x, y, x + w, y + h, l.legendary ? 0x66442F12 : 0x5518130D);
        fill(ms, x + 2, y + 2, x + w - 2, y + h - 2, l.legendary ? 0x44331F0B : 0x33100B07);
        stroke(ms, x, y, w, h, l.legendary ? 0xDDE6B85E : 0xAA8F6532);
        fill(ms, x + 2, y + 2, x + w - 2, y + 3, l.legendary ? 0x88FFE6A3 : 0x555C3B1C);
        ItemStack stack = stackOf(l.itemBase64, l.registryName, l.itemHash);
        drawItem(ms, stack, x + 10, y + 10, mouseX, mouseY);
        String name = (l.legendary ? "✦ " : "") + l.itemName + (l.quantity > 1 ? " x" + l.quantity : "");
        text(ms, trim(name, 25), x + 36, y + 6, (l.legendary || l.featured) ? 0xFFFFD875 : 0xFFE8DEC4);
        text(ms, "Satıcı: " + trim(l.seller, 12) + " | " + trim(l.category, 12), x + 36, y + 21, 0xFFB9AC91);
        text(ms, trim(fmt(l.price) + " " + l.currency, 10), x + 224, y + 8, 0xFFC9A861);
        text(ms, time(l.remainingMillis), x + 296, y + 8, 0xFFB9AC91);
        text(ms, "♥ " + l.likes, x + 346, y + 8, l.likedByMe ? 0xFFFFB3B3 : 0xFF9F9685);
        fill(ms, x + 370, y + 4, x + 371, y + h - 4, 0x665C3B1C);
    }

    private void drawGuilds(MatrixStack ms) {
        title(ms, "Ticaret Loncaları", cx(12), cy(18));
        text(ms, "Loncaların özel vitrinlerine gir ve üyelerin ilanlarına bak.", cx(12), cy(36), 0xFFB9AC91);
        int y = cy(74);
        for (MarketSnapshot.GuildView g : snap.guilds) {
            fill(ms, cx(8), y, cx(456), y + 32, g.mine ? 0x553D2B10 : 0x4418130D);
            stroke(ms, cx(8), y, 448, 32, g.mine ? 0xBBD5A957 : 0x778F6532);
            text(ms, trim(g.displayName, 34), cx(20), y + 6, g.mine ? 0xFFFFD875 : 0xFFE8DEC4);
            text(ms, trim(g.motto, 48), cx(20), y + 20, 0xFFB9AC91);
            text(ms, g.members + " üye / " + g.activeListings + " ilan", cx(284), y + 6, 0xFFC9A861);
            y += 38;
        }
        if (snap.guilds.isEmpty()) text(ms, "Henüz ticaret loncası kurulmamış.", cx(18), cy(86), 0xFFB9AC91);
    }

    private void drawProfile(MatrixStack ms) {
        box(ms, cx(14), cy(18), 214, 130);
        title(ms, "Profilim", cx(30), cy(32));
        String name = Minecraft.getInstance().player == null ? "Oyuncu" : Minecraft.getInstance().player.getGameProfile().getName();
        drawItem(ms, skullOf(name), cx(32), cy(58), -1, -1);
        text(ms, name, cx(60), cy(58), 0xFFE8DEC4);
        text(ms, "Unvan: Kadim Tüccar", cx(30), cy(86), 0xFFB9AC91);
        text(ms, "Pazar Kredisi: " + snap.credits, cx(30), cy(102), 0xFFD2B46A);
        text(ms, "İtibar: " + snap.profileReputation, cx(30), cy(118), 0xFF9F9685);
        text(ms, "Rozet: " + trim(snap.profileBadges, 20), cx(30), cy(134), 0xFFC9A861);
        box(ms, cx(248), cy(18), 214, 130);
        title(ms, "Tüccar İstatistikleri", cx(264), cy(32));
        text(ms, "Satış: " + snap.profileSales + " | Alış: " + snap.profilePurchases, cx(264), cy(58), 0xFFE8DEC4);
        text(ms, "Müzayede: " + snap.profileAuctionsWon + " | Beğeni: " + snap.profileLikesReceived, cx(264), cy(74), 0xFFB9AC91);
        text(ms, "Kazanç: " + fmt(snap.profileEarned) + " RC", cx(264), cy(90), 0xFFC9A861);
        text(ms, "Aktif ilan: " + snap.profileActiveListings, cx(264), cy(106), 0xFFE8DEC4);
        text(ms, "Depo: " + snap.storageCount + " | Posta: " + snap.profileNotificationCount, cx(264), cy(122), 0xFFB9AC91);
        box(ms, cx(14), cy(162), 448, 104);
        title(ms, "Kişisel Alanlar", cx(30), cy(176));
        text(ms, "Kişisel pazar işlemlerin aşağıdaki mühürlerden açılır.", cx(30), cy(188), 0xFF8F826B);
    }

    private void drawSell(MatrixStack ms, int mouseX, int mouseY) {
        boolean auctionMode = "AUCTION".equals(snap.wizard.mode);
        box(ms, cx(10), cy(10), 456, 52);
        title(ms, "1. Hangi ürünü satmak istiyorsun?", cx(24), cy(22));
        title(ms, "2. Adet", cx(334), cy(22));
        ItemStack stack = stackOf(snap.wizard.itemBase64, snap.wizard.registryName);
        drawItem(ms, stack, cx(174), cy(36), mouseX, mouseY);
        text(ms, trim(snap.wizard.itemName, 22), cx(198), cy(33), 0xFFE8DEC4);
        text(ms, "Kategori: " + trim(snap.wizard.category, 12), cx(198), cy(47), 0xFFB9AC91);
        inputBox(ms, cx(368), cy(36), 78, 20);

        box(ms, cx(10), cy(70), 456, 52);
        title(ms, "3. Bu ürünü nereye koymak istiyorsun?", cx(24), cy(82));
        text(ms, auctionMode ? "Seçili: Açık Artırma" : "Seçili: İlan Panosu", cx(24), cy(108), 0xFFD2B46A);
        text(ms, auctionMode ? "Başlangıç fiyatı" : "Sabit ilan fiyatı", cx(344), cy(82), 0xFFB9AC91);
        inputBox(ms, cx(368), cy(96), 78, 20);

        box(ms, cx(10), cy(130), 456, 52);
        title(ms, auctionMode ? "4. Açık artırma süresi" : "4. İlan süresi ve fiyatlandırma", cx(24), cy(142));

        box(ms, cx(10), cy(192), 456, 58);
        title(ms, "5. Ürününü boostlamak istiyor musun?", cx(24), cy(204));
    }

    private void drawDetail(MatrixStack ms, int mouseX, int mouseY) {
        MarketSnapshot.ListingView l = snap.selected;
        if (l == null) { text(ms, "İlan bulunamadı.", cx(16), cy(16), 0xFFFF8888); return; }
        box(ms, cx(14), cy(18), 246, 196);
        title(ms, "Ürün Kartı", cx(28), cy(30));
        ItemStack stack = stackOf(l.itemBase64, l.registryName, l.itemHash);
        drawItem(ms, stack, cx(36), cy(58), mouseX, mouseY);
        text(ms, trim(l.itemName + (l.quantity > 1 ? " x" + l.quantity : ""), 28), cx(64), cy(58), 0xFFE8DEC4);
        text(ms, "Kategori: " + trim(l.category, 16), cx(28), cy(88), 0xFFB9AC91);
        text(ms, "Satıcı: " + trim(l.seller, 18), cx(28), cy(104), 0xFFB9AC91);
        text(ms, "NBT: Korunur", cx(28), cy(120), 0xFF9F9685);
        text(ms, "Süre: " + time(l.remainingMillis), cx(28), cy(150), 0xFFC9A861);
        text(ms, "Beğeni: " + l.likes + " | Prestij: " + l.prestigeLevel, cx(28), cy(166), 0xFFB9AC91);
        box(ms, cx(280), cy(18), 182, 196);
        title(ms, l.type.equals("AUCTION") ? "Teklif" : "Satın Alma", cx(294), cy(30));
        if (l.type.equals("AUCTION")) {
            text(ms, "Birim: " + l.currency, cx(294), cy(58), 0xFFD2B46A);
            text(ms, "Başlangıç: " + fmt(l.startPrice), cx(294), cy(74), 0xFFB9AC91);
            text(ms, "En yüksek: " + fmt(l.highestBid), cx(294), cy(90), 0xFFC9A861);
            text(ms, "Lider: " + trim(l.highestBidder, 14), cx(294), cy(106), 0xFFB9AC91);
            inputBox(ms, cx(292), cy(86), 110, 20);
        } else {
            text(ms, "Fiyat: " + fmt(l.price) + " RC", cx(294), cy(58), 0xFFC9A861);
            text(ms, "Teklif miktarı", cx(294), cy(74), 0xFFB9AC91);
            inputBox(ms, cx(292), cy(86), 110, 20);
            text(ms, "Vergi satıcı payından kesilir.", cx(294), cy(106), 0xFF9F9685);
        }
    }

    private void drawStorage(MatrixStack ms, int mouseX, int mouseY) {
        title(ms, "Depo ve Emanet", cx(0), cy(12));
        text(ms, "Depo: " + snap.storageCount + "/" + snap.storageLimit + " | Kurtarma: " + snap.recoveryItems, cx(0), cy(28), snap.recoveryItems > 0 ? 0xFFFFC96A : 0xFFB9AC91);
        int y = cy(54);
        for (MarketSnapshot.StorageView s : snap.storage) {
            fill(ms, cx(0), y - 4, cx(456), y + 28, 0x4418130D);
            drawItem(ms, stackOf(s.itemBase64, "minecraft:chest"), cx(8), y + 4, mouseX, mouseY);
            text(ms, trim(s.itemName, 34), cx(34), y + 4, 0xFFE8DEC4);
            text(ms, trim(s.reason, 48), cx(34), y + 18, 0xFF9F9685);
            y += 36;
        }
        if (snap.storage.isEmpty()) text(ms, "Depoda bekleyen item yok.", cx(0), y, 0xFFB9AC91);
    }

    private void drawOffers(MatrixStack ms, int mouseX, int mouseY) {
        title(ms, is("OFFERS") ? "Gelen Teklifler" : "Verdiğim Teklifler", cx(0), cy(12));
        int y = cy(64);
        for (MarketSnapshot.OfferView o : snap.offers) {
            fill(ms, cx(0), y - 4, cx(456), y + 28, 0x4418130D);
            drawItem(ms, stackOf(o.itemBase64, "minecraft:paper"), cx(8), y + 4, mouseX, mouseY);
            text(ms, trim(o.itemName, 30), cx(34), y + 4, 0xFFE8DEC4);
            text(ms, trim(o.buyer + " — " + fmt(o.amount) + " " + o.currency, 42), cx(34), y + 18, 0xFFC9A861);
            y += 36;
        }
        if (snap.offers.isEmpty()) text(ms, "Aktif teklif yok.", cx(0), y, 0xFFB9AC91);
    }

    private void drawMail(MatrixStack ms) {
        title(ms, "Pazar Postası", cx(0), cy(12));
        int y = cy(48);
        if (snap.notifications.isEmpty()) text(ms, "Henüz pazar bildirimin yok.", cx(0), y, 0xFFB9AC91);
        for (MarketSnapshot.NotificationView n : snap.notifications) {
            box(ms, cx(0), y - 6, 456, 34);
            text(ms, trim(n.title, 40), cx(10), y, 0xFFFFD875);
            text(ms, trim(n.body, 58), cx(10), y + 14, 0xFFE8DEC4);
            y += 40;
        }
    }

    private void drawBoard(MatrixStack ms) {
        title(ms, "Pazar Tablosu - " + boardTitle(), cx(0), cy(8));
        int y = cy(82);
        int rank = 1;
        for (MarketSnapshot.BoardRow b : snap.board) {
            fill(ms, cx(0), y - 6, cx(456), y + 42, 0x4418130D);
            stroke(ms, cx(0), y - 6, 456, 48, 0x778F6532);
            drawItem(ms, skullOf(b.player), cx(8), y, -1, -1);
            text(ms, "#" + rank + " " + trim(b.player, 16), cx(32), y, 0xFFE8DEC4);
            text(ms, "Satış: " + b.sales + " | Alış: " + b.purchases + " | Müzayede: " + b.auctionsWon, cx(32), y + 14, 0xFFB9AC91);
            text(ms, "İtibar: " + b.reputation + " | Beğeni: " + b.likesReceived + " | Kazanç: " + fmt(b.earned) + " RC", cx(32), y + 28, 0xFFC9A861);
            rank++; y += 54;
            if (y > cy(230)) break;
        }
        if (snap.board.isEmpty()) text(ms, "Henüz tablo verisi oluşmadı.", cx(0), y, 0xFFB9AC91);
    }

    private void drawInfo(MatrixStack ms) {
        int page = Math.max(0, Math.min(INFO_PAGES - 1, snap.page));
        box(ms, cx(12), cy(20), 140, 230);
        box(ms, cx(166), cy(20), 294, 230);
        title(ms, "Konular", cx(28), cy(32));
        title(ms, infoTitle(page), cx(184), cy(34));
        int y = cy(62);
        for (String line : infoLines(page)) {
            int color = line.startsWith("•") ? 0xFFE8DEC4 : line.startsWith("!") ? 0xFFFFC96A : 0xFFB9AC91;
            for (String wrapped : wrapLines(line.replaceFirst("^!", ""), 260)) {
                text(ms, wrapped, cx(184), y, color);
                y += 12;
                if (y > cy(220)) break;
            }
            y += 4;
            if (y > cy(220)) break;
        }
    }

    private void drawAdmin(MatrixStack ms) {
        title(ms, "OP Yönetim Mührü", cx(0), cy(12));
        box(ms, cx(14), cy(44), 212, 94);
        box(ms, cx(246), cy(44), 212, 94);
        title(ms, "Ekonomi Durumu", cx(28), cy(56));
        text(ms, snap.economyReady ? "Ekonomi: Hazır" : "Ekonomi: Kilitli", cx(28), cy(80), economyColor());
        text(ms, "Vergi: %" + fmt(snap.taxPercent), cx(28), cy(96), 0xFFE8DEC4);
        text(ms, snap.treasuryLine == null ? "Hazine: -" : trim(snap.treasuryLine, 30), cx(28), cy(112), 0xFFC9A861);
        title(ms, "Sistem Durumu", cx(260), cy(56));
        text(ms, "Kurtarma: " + snap.recoveryItems, cx(260), cy(80), snap.recoveryItems > 0 ? 0xFFFFC96A : 0xFFE8DEC4);
        text(ms, "Durum: " + (snap.economyReady ? "Hazır" : "Kilitli"), cx(260), cy(96), snap.economyReady ? 0xFFA8E6A3 : 0xFFFF9B86);
        text(ms, "WAL: Aktif", cx(260), cy(112), 0xFFB9AC91);
        box(ms, cx(14), cy(150), 212, 112);
        box(ms, cx(246), cy(150), 212, 112);
        title(ms, "Yönetim İşlemleri", cx(28), cy(164));
        title(ms, "Son Kayıtlar", cx(260), cy(164));
        int ty = cy(184);
        for (int i = 0; i < Math.min(5, snap.transactions.size()); i++) {
            MarketSnapshot.TxRow tr = snap.transactions.get(i);
            text(ms, trim(tr.type + " " + tr.status + " " + fmt(tr.amount), 26), cx(260), ty + i * 14, 0xFFB9AC91);
        }
        if (snap.transactions.isEmpty()) text(ms, "Kayıt yok.", cx(260), ty, 0xFF8F826B);
    }


    private void drawAnnouncementPopup(MatrixStack ms) {
        String title = "";
        String body = "";
        if (System.currentTimeMillis() < externalAnnouncementUntil) {
            title = externalAnnouncementTitle;
            body = externalAnnouncementBody;
        }
        if (snap.message != null) {
            String m = snap.message.toLowerCase(Locale.ROOT);
            if (m.contains("duyuru") || m.contains("müzayede") || m.contains("efsanevi") || m.contains("açık artırma")) {
                title = "Kadim Duyuru";
                body = trim(snap.message, 42);
            }
        }
        if (title.isEmpty() && snap.notifications != null && !snap.notifications.isEmpty()) {
            MarketSnapshot.NotificationView n = snap.notifications.get(0);
            String t = (n.title == null ? "" : n.title).toLowerCase(Locale.ROOT);
            String b = (n.body == null ? "" : n.body).toLowerCase(Locale.ROOT);
            if (t.contains("duyuru") || t.contains("müzayede") || t.contains("efsanevi") || b.contains("duyuru") || b.contains("müzayede") || b.contains("efsanevi")) {
                title = n.title == null || n.title.isEmpty() ? "Kadim Duyuru" : trim(n.title, 22);
                body = trim(n.body, 42);
            }
        }
        if (title.isEmpty()) return;
        int x = Math.max(8, left - 178);
        int y = top + PANEL_H / 2 - 34;
        fill(ms, x + 2, y + 2, x + 174, y + 64, 0x66000000);
        fill(ms, x, y, x + 172, y + 62, 0xDD17100A);
        stroke(ms, x, y, 172, 62, 0xFFD6A950);
        fill(ms, x + 4, y + 4, x + 168, y + 18, 0x55351F0E);
        text(ms, title, x + 12, y + 7, 0xFFFFD875);
        drawWrapped(ms, body, x + 12, y + 26, 146, 0xFFE8DEC4);
    }

    private void drawConfirm(MatrixStack ms) {
        box(ms, left + 210, top + 128, 252, 128);
        title(ms, pending.title, left + 230, top + 148);
        drawWrapped(ms, pending.body, left + 230, top + 170, 210, 0xFFE8DEC4);
    }

    private String boardTitle() {
        if ("SPENT".equals(snap.boardMode)) return "Harcama";
        if ("SALES".equals(snap.boardMode)) return "Satış";
        if ("BUY".equals(snap.boardMode)) return "Alış";
        if ("LIKE".equals(snap.boardMode)) return "Beğeni";
        if ("AUCTION".equals(snap.boardMode)) return "Müzayede";
        if ("REP".equals(snap.boardMode)) return "İtibar";
        if ("BADGES".equals(snap.boardMode)) return "Rozet";
        return "Kazanç";
    }

    private int economyColor() { return snap.economyReady ? 0xFFA8E6A3 : 0xFFFF9B86; }

    private String titleForView() {
        if (is("MY_BIDS")) return "Müzayede Tekliflerim";
        if (isAuctionView()) return "Açık Artırma";
        if (is("FEATURED")) return "Öne Çıkan";
        if (is("LEGENDARY")) return "Efsanevi";
        if (is("GUILDS")) return "Loncalar";
        if (snap.view.startsWith("GUILD_")) return "Lonca Pazarı";
        if (is("SELL")) return "Ürün Ekle";
        if (is("PROFILE")) return "Profilim";
        if (is("MINE")) return "İlanlarım";
        if (is("OFFERS")) return "Gelen Teklifler";
        if (is("MY_OFFERS")) return "Verdiğim Teklifler";
        if (is("STORAGE")) return "Depo";
        if (snap.view.startsWith("BOARD")) return "Pazar Tablosu";
        if (is("INFO")) return "Tüccar Rehberi";
        if (is("MAIL")) return "Posta";
        if (is("FAVORITES")) return "Takip Listem";
        if (is("ADMIN")) return "OP Mührü";
        if (is("DETAIL")) return "İlan Detayı";
        if (snap.view.startsWith("CAT_")) return "Kadim Pazar";
        if (is("SEARCH")) return "Arama";
        if (snap.view.startsWith("PRODUCTS")) return "Kadim Pazar";
        return "Ana Salon";
    }

    private void openFixedListingConfirm() {
        final double capturedAmount = amount();
        final int capturedQty = quantity();
        confirm("İlanı mühürle", "Ürün: " + trim(snap.wizard.itemName, 22) + " | Adet: " + capturedQty + " | Fiyat: " + fmt(capturedAmount) + " RC", () -> action("CREATE_FIXED", "", capturedAmount, capturedQty, "MINE", ""));
    }

    private void openAuctionConfirm() {
        final double capturedAmount = amount();
        final int capturedQty = quantity();
        int creditFee = (snap.wizard.auctionHours >= 5 ? snap.auctionHour5CreditCost : (snap.wizard.auctionHours >= 3 ? snap.auctionHour3CreditCost : 0));
        confirm("Müzayedeyi başlat", "Adet: " + capturedQty + " | Birim: " + snap.wizard.auctionCurrency + " | Başlangıç: " + fmt(capturedAmount) + " | Ücret: " + creditFee + " PK", () -> action("CREATE_AUCTION", "", capturedAmount, capturedQty, "AUCTIONS", ""));
    }

    private void confirm(String title, String body, Runnable run) { this.pending = new Pending(title, body, run); this.init(); }
    private double amount() { try { return Double.parseDouble(priceField == null ? "0" : priceField.getText().replace(',', '.')); } catch (Exception e) { return Math.max(1, snap.wizard.price); } }
    private int quantity() { try { return Math.max(1, Integer.parseInt(quantityField == null ? "1" : quantityField.getText().trim())); } catch (Exception e) { return Math.max(1, snap.wizard.quantity); } }
    private String carrySearch() { return "SEARCH".equals(snap.view) ? (snap.searchQuery == null ? "" : snap.searchQuery) : ""; }
    private void nav(String view, int page, String selectedId) { NetworkHandler.CHANNEL.sendToServer(new MarketActionPacket("NAV", "", 0, 0, snap.view, snap.page, selectedId, view + "|" + page + "|" + selectedId)); }
    private void action(String action, String id, double amount, int value, String nextView, String selectedId) { NetworkHandler.CHANNEL.sendToServer(new MarketActionPacket(action, id, amount, value, nextView, 0, selectedId, "")); }
    private void playUiClick() { try { if (Minecraft.getInstance().player != null && snap.enableUiSounds) Minecraft.getInstance().player.playSound(SoundEvents.UI_BUTTON_CLICK, 0.22F, 0.92F); } catch (Exception ignored) {} }

    private ItemStack stackOf(String base64, String registry) { return stackOf(base64, registry, ""); }
    private ItemStack stackOf(String base64, String registry, String hash) {
        String key = (hash == null ? "" : hash) + "|" + (base64 == null ? "" : base64) + "|" + registry;
        ItemStack cached = previewCache.get(key);
        if (cached != null) return cached;
        ItemStack stack = ItemStack.EMPTY;
        if (base64 != null && !base64.isEmpty()) {
            try { stack = ItemCodec.decode(base64); } catch (Exception ignored) {}
        }
        if (stack.isEmpty()) {
            try {
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(registry == null || registry.isEmpty() ? "minecraft:barrier" : registry));
                stack = new ItemStack(item == null ? Items.BARRIER : item);
            } catch (Exception e) { stack = new ItemStack(Items.BARRIER); }
        }
        previewCache.put(key, stack);
        return stack;
    }

    private ItemStack skullOf(String player) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        try { CompoundNBT tag = head.getOrCreateTag(); tag.putString("SkullOwner", player == null ? "Steve" : player); } catch (Exception ignored) {}
        return head;
    }

    private void drawItem(MatrixStack ms, ItemStack stack, int x, int y, int mouseX, int mouseY) {
        if (stack == null || stack.isEmpty()) return;
        this.itemRenderer.renderItemAndEffectIntoGUI(stack, x, y);
        this.itemRenderer.renderItemOverlayIntoGUI(this.font, stack, x, y, null);
        if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) hoveredStack = stack;
    }

    private void title(MatrixStack ms, String s, int x, int y) { text(ms, s, x, y, 0xFFFFD875); }
    private void text(MatrixStack ms, String s, int x, int y, int color) { this.font.drawString(ms, s == null ? "" : s, x, y, color); }
    private String trim(String s, int max) { if (s == null) return ""; return s.length() <= max ? s : s.substring(0, Math.max(0, max - 3)) + "..."; }
    private String fmt(double d) { if (Math.abs(d - Math.round(d)) < 0.01D) return String.valueOf((long)Math.round(d)); return String.format(Locale.US, "%.1f", d); }
    private String time(long ms) { if (ms <= 0) return "bitti"; long s = ms / 1000L; long d = s / 86400L; s %= 86400L; long h = s / 3600L; s %= 3600L; long m = s / 60L; if (d > 0) return d + "g " + h + "s"; if (h > 0) return h + "s " + m + "dk"; return Math.max(1, m) + "dk"; }

    private void box(MatrixStack ms, int x, int y, int w, int h) {
        fill(ms, x + 2, y + 2, x + w + 2, y + h + 2, 0x55100904);
        fill(ms, x, y, x + w, y + h, 0x5D251A10);
        fill(ms, x + 3, y + 3, x + w - 3, y + h - 3, 0x33100B07);
        stroke(ms, x, y, w, h, 0x998F6532);
        fill(ms, x + 4, y + 4, x + w - 4, y + 5, 0x335C3B1C);
        corner(ms, x + 6, y + 6, true, true); corner(ms, x + w - 16, y + 6, false, true);
        corner(ms, x + 6, y + h - 16, true, false); corner(ms, x + w - 16, y + h - 16, false, false);
    }
    private void inputBox(MatrixStack ms, int x, int y, int w, int h) {
        fill(ms, x, y, x + w, y + h, 0xCC0E0A06);
        fill(ms, x + 1, y + 1, x + w - 1, y + h - 1, 0xAA17100A);
        stroke(ms, x, y, w, h, 0xAA9E7140);
    }
    private void corner(MatrixStack ms, int x, int y, boolean leftSide, boolean topSide) {
        int c = 0xFFD9B05A;
        int len = 10;
        int thick = 2;
        int hx1 = x;
        int hx2 = x + len;
        int hy = topSide ? y : y + len - thick;
        int vx = leftSide ? x : x + len - thick;
        int vy1 = y;
        int vy2 = y + len;
        fill(ms, hx1, hy, hx2, hy + thick, c);
        fill(ms, vx, vy1, vx + thick, vy2, c);
    }
    private void stroke(MatrixStack ms, int x, int y, int w, int h, int color) { fill(ms, x, y, x + w, y + 1, color); fill(ms, x, y + h - 1, x + w, y + h, color); fill(ms, x, y, x + 1, y + h, color); fill(ms, x + w - 1, y, x + w, y + h, color); }
    private void addRtButton(int x, int y, int w, int h, String label, Runnable press) { addButton(new RTButton(x, y, w, h, label, false, b -> { playUiClick(); press.run(); })); }
    private void addRtButton(int x, int y, int w, int h, String label, boolean selected, Runnable press) { addButton(new RTButton(x, y, w, h, label, selected, b -> { playUiClick(); press.run(); })); }

    private List<String> wrapLines(String body, int maxWidth) {
        List<String> lines = new ArrayList<String>();
        if (body == null || body.isEmpty()) return lines;
        String[] words = body.split(" ");
        String line = "";
        for (String w : words) {
            String next = line.isEmpty() ? w : line + " " + w;
            if (this.font.getStringWidth(next) > maxWidth && !line.isEmpty()) { lines.add(line); line = w; }
            else line = next;
        }
        if (!line.isEmpty()) lines.add(line);
        return lines;
    }
    private void drawWrapped(MatrixStack ms, String body, int x, int y, int maxWidth, int color) { for (String line : wrapLines(body, maxWidth)) { text(ms, line, x, y, color); y += 12; } }

    private String infoTitle(int page) {
        switch (page) {
            case 1: return "İlan Panosu";
            case 2: return "Açık Artırma";
            case 3: return "Teklifler";
            case 4: return "Loncalar";
            case 5: return "Efsanevi";
            case 6: return "Rozetler";
            case 7: return "Hazine";
            default: return "Amaç";
        }
    }
    private String[] infoLines(int page) {
        switch (page) {
            case 1: return new String[]{"• İlan Panosu sabit fiyatlı ürünler içindir.", "• Ürün NBT verisiyle kasaya alınır.", "• Süre bitince item depo sistemine düşer."};
            case 2: return new String[]{"• Açık artırma rekabetli teklif salonudur.", "• Birim RC veya Pazar Kredisi olabilir.", "• Eski liderin ödemesi iade edilir."};
            case 3: return new String[]{"• Teklifler emanet mantığıyla çalışır.", "• Satıcı kabul ederse item alıcıya gider.", "• Reddedilen veya kapanan teklif iade edilir."};
            case 4: return new String[]{"• Loncalar OP komutuyla kurulur.", "• Lonca vitrini üyelerin ilanlarını gösterir.", "• Prestij ve ekonomi rekabeti sağlar."};
            case 5: return new String[]{"• Efsanevi mühür Pazar Kredisi harcar.", "• Altın vitrin görünümü verir.", "• Öne çıkarma ve duyuru boostları görünürlüğü artırır."};
            case 6: return new String[]{"• Rozetler pazar davranışlarından kazanılır.", "• Satış, beğeni, müzayede ve itibar izlenir.", "• Profil ve Tablo ekranlarında görünür."};
            case 7: return new String[]{"• Vergiler kraliyet hazinesine yazılır.", "• OP sezon arşivi ve rapor alabilir.", "• Hazine ileride etkinlik bütçesi olabilir."};
            default: return new String[]{"• Ruasol's Tavern Pazarı güvenli ticaret salonudur.", "• Amaç itemleri NBT bozmadan el değiştirmektir.", "• Tüm para ve item kararları sunucuda doğrulanır."};
        }
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return pending == null && ((priceField != null && priceField.charTyped(codePoint, modifiers)) || (quantityField != null && quantityField.charTyped(codePoint, modifiers)) || (searchField != null && searchField.charTyped(codePoint, modifiers))) || super.charTyped(codePoint, modifiers);
    }
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return pending == null && ((priceField != null && priceField.keyPressed(keyCode, scanCode, modifiers)) || (quantityField != null && quantityField.keyPressed(keyCode, scanCode, modifiers)) || (searchField != null && searchField.keyPressed(keyCode, scanCode, modifiers))) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static class Pending {
        final String title; final String body; final Runnable run;
        Pending(String title, String body, Runnable run) { this.title = title; this.body = body; this.run = run; }
        void run() { if (run != null) run.run(); }
    }

    @OnlyIn(Dist.CLIENT)
    private static class RTButton extends Button {
        private final boolean selected;
        public RTButton(int x, int y, int w, int h, String label, boolean selected, IPressable press) { super(x, y, w, h, new StringTextComponent(label), press); this.selected = selected; }
        @Override
        public void render(MatrixStack ms, int mouseX, int mouseY, float partialTicks) {
            Minecraft mc = Minecraft.getInstance();
            boolean hover = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
            int shadow = 0x66080502;
            int outer = selected ? 0xFFE0B35A : (hover ? 0xFFC39249 : 0xFF7A5127);
            int mid = selected ? 0xFF73481E : (hover ? 0xFF53341A : 0xFF2A1B10);
            int core = selected ? 0xFF4E3116 : (hover ? 0xFF352114 : 0xFF160F09);
            AbstractGui.fill(ms, this.x + 2, this.y + 2, this.x + this.width + 2, this.y + this.height + 2, shadow);
            AbstractGui.fill(ms, this.x, this.y, this.x + this.width, this.y + this.height, outer);
            AbstractGui.fill(ms, this.x + 1, this.y + 1, this.x + this.width - 1, this.y + this.height - 1, mid);
            AbstractGui.fill(ms, this.x + 3, this.y + 3, this.x + this.width - 3, this.y + this.height - 3, core);
            AbstractGui.fill(ms, this.x + 4, this.y + 4, this.x + this.width - 4, this.y + 5, hover || selected ? 0x55795227 : 0x332C1D10);
            AbstractGui.fill(ms, this.x + 4, this.y + this.height - 5, this.x + this.width - 4, this.y + this.height - 4, selected ? 0x66FFD875 : 0x33422A14);
            AbstractGui.fill(ms, this.x + 4, this.y + 4, this.x + 5, this.y + this.height - 4, 0x33422A14);
            AbstractGui.fill(ms, this.x + this.width - 5, this.y + 4, this.x + this.width - 4, this.y + this.height - 4, 0x33422A14);
            if (selected) AbstractGui.fill(ms, this.x + 6, this.y + 6, this.x + 8, this.y + this.height - 6, 0x99FFD875);
            String label = this.getMessage().getString();
            if ("Önce".equals(label)) label = "‹ " + label;
            else if ("Sonra".equals(label)) label = label + " ›";
            if (selected && !label.startsWith("✓")) label = "✓ " + label;
            while (mc.fontRenderer.getStringWidth(label) > this.width - 12 && label.length() > 3) label = label.substring(0, label.length() - 4) + "...";
            drawCenteredString(ms, mc.fontRenderer, new StringTextComponent(label), this.x + this.width / 2, this.y + (this.height - 8) / 2, selected ? 0xFFFFE9AA : (hover ? 0xFFFFE2A0 : 0xFFE3C894));
        }
    }
}


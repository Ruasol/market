RUASOL MARKET v4.0.5 GRAND ECONOMY CUSTOM UI - FORGE 1.16.5 / 36.2.39
================================================================

Bu paket Ruasol's Tavern için hazırlanmış özel pazar ve açık artırma modudur.
Bu sürüm chest GUI değildir. /pazar komutu client tarafında tam custom çizilmiş bir pazar ekranı açar.

ÖNEMLİ KURULUM NOTU
-------------------
Bu sürüm hem SERVER hem CLIENT tarafına kurulmalıdır.
Sebep: Özel pazar ekranı, packet sistemi ve özel texture dosyaları client modundan çalışır.
Server tarafı ise item/para/escrow/transaction güvenliğini yönetir.

HEDEF SÜRÜM
-----------
Minecraft: 1.16.5
Forge: 36.2.39
Build Java: Java 17
Gradle mantığı: 7.6.4

TEK TIK BUILD
-------------
1) Zipi bir klasöre çıkar.
2) Java 17 kurulu olduğundan emin ol.
3) BUILD_MOD.bat dosyasına çift tıkla.
4) Build başarılı olursa jar şurada oluşur:
   build/libs/ruasol-market-forge-1.16.5-4.0.5.jar
5) Jar dosyasını hem server mods klasörüne hem client/modpack mods klasörüne koy.

BUILD HATASI
------------
Build hata verirse aynı klasörde BUILD_LOG.txt oluşur.
Logu bana atarsan doğrudan build hatasına göre düzeltirim.

EKONOMİ
-------
Normal sabit fiyatlı pazar RC ile çalışır.
RC tarafı Vault/Essentials ekonomisine reflection ile bağlanmaya çalışır.
Saf Forge sunucuda Vault/Essentials yoksa para işlemleri varsayılan olarak kilitlenir. Test için configten allow_internal_test_economy=true yapılabilir. Production için Mohist/Arclight/Magma gibi Bukkit köprülü Forge + Vault + EssentialsX önerilir.

AÇIK ARTIRMA BİRİMİ
-------------------
Açık artırma başlatırken oyuncuya iki birim sunulur:
- RC: klasik Essentials/Vault bakiyesi
- Pazar Kredisi: modun kendi premium kredi sistemi

Pazar Kredisi ile ürün koyma/bid sistemi normal sabit pazarda yoktur; yalnızca açık artırma salonuna özeldir.
Açık artırma başlatma bedeli configte auction_credit_cost ile ayarlanır.

CONFIG
------
İlk açılışta şurada oluşur:
config/ruasol_market.properties

Önemli ayarlar:
tax_percent=5.0
extra_day_fee=100.0
featured_credit_cost=10
auction_credit_cost=20
allow_market_credit_auctions=true
max_auction_hours=3
min_bid_increment_percent=5.0
anti_snipe_window_seconds=30
anti_snipe_extend_seconds=60
max_active_listings_per_player=25
max_storage_items_per_player=100
max_item_nbt_bytes=65536
allow_shulker_boxes=false
allow_written_books=false
allow_internal_test_economy=false
blacklist_item_ids=minecraft:command_block,minecraft:barrier,minecraft:structure_block,minecraft:jigsaw

KOMUTLAR
--------
/pazar
/pazar fiyat <miktar>
/pazar teklif <ilanId> <miktar>
/pazar kabul <teklifId>
/pazar teklifiptal <teklifId>
/pazar iptal <ilanId>
/pazar kredi
/pazar kredi ver <oyuncu> <miktar>
/pazar reload
/pazar admin

CUSTOM UI
---------
Texture dosyaları:
src/main/resources/assets/ruasolmarket/textures/gui/panel_main.png
src/main/resources/assets/ruasolmarket/textures/gui/panel_auction.png
src/main/resources/assets/ruasolmarket/textures/gui/panel_storage.png
src/main/resources/assets/ruasolmarket/textures/gui/panel_board.png
src/main/resources/assets/ruasolmarket/textures/gui/panel_admin.png
src/main/resources/assets/ruasolmarket/textures/gui/button_states.png
src/main/resources/assets/ruasolmarket/textures/gui/icons.png

Tasarım dili:
- dark fantasy
- medieval RPG
- koyu taş/demir zemin
- soluk altın çerçeve
- premium pazar salonu hissi
- neon/generic vanilla görünümden uzak

GÜVENLİK MİMARİSİ
-----------------
- Item seçilince oyuncudan hemen silinmez.
- Item yalnızca ilan onayında, aynı item hâlâ ana eldeyse escrow'a alınır.
- Item Base64 sıkıştırılmış NBT olarak saklanır.
- Item ID değil, itemin NBT datası korunur.
- Satın alma, teklif, açık artırma ve depo işlemleri server tarafında doğrulanır.
- Client yalnızca buton/ekran/packet katmanıdır; para ve item konusunda client'a güvenilmez.
- Açık artırma teklifleri escrow mantığıyla yönetilir.
- Önceki en yüksek teklif sahibine iade yapılır.
- RC ödeme offline satıcıya verilemezse pending payout'a düşer.
- Pazar Kredisi ödemeleri internal veri dosyasında offline işlenebilir.
- Envanter doluysa item depoya gider veya işlem reddedilir.
- Transaction log tutulur.
- Kritik işlemler ayrıca world/serverconfig/ruasol_market_wal.jsonl dosyasına JSONL olarak yazılır.
- Client action packetleri işlem türüne göre rate-limit edilir; v4.0.5 ile NAV/page spam de rate-limit kapsamına alınmıştır.
- Liste ekranları hafif item preview kullanır; detay/teklif/depo ekranında tam NBT preview gönderilir. Böylece packet şişmesi azaltılır.
- WAL dosyası açılışta taranır; şüpheli STARTED transaction grupları OP panelde recovery uyarısı oluşturur.
- Depo limiti dolarsa item kaybolmaz; admin recovery kuyruğuna alınır ve STORAGE_OVERFLOW transactionı yazılır.
- Pazar Kredisi müzayedeleri tam sayı mantığıyla çalışır; küsuratlı kredi teklifleri reddedilir.
- Ürünler client UI'da registry ID ile değil, detay ekranında gerçek NBT preview verisiyle gösterilir.

TEST SIRASI
-----------
1) Server ve client mods klasörüne jarı koy.
2) Oyuna girip /pazar aç.
3) Custom ekran açılıyorsa client/server packet bağlantısı tamamdır.
4) Eline NBT'li bir item al, Ürün Ekle > Ürün Seç yap.
5) Sabit ilan aç, başka oyuncuyla satın al.
6) Açık artırma başlat, birim olarak RC seçip teklif ver.
7) Açık artırma başlat, birim olarak Pazar Kredisi seçip teklif ver.
8) /pazar kredi ver <oyuncu> <miktar> ile test kredisi ver.
9) Envanter doluyken item teslimi/depo akışını test et.
10) /pazar admin ekranında transaction kayıtlarını kontrol et.

BİLİNÇLİ SINIRLAR
-----------------
Bu mod entity, mob AI, worldgen, block sistemi veya sürekli ağır tick sistemi kullanmaz.
Süre dolumu düşük aralıklı server tick kontrolü ile yapılır.
Görsel UI client screen ile çalışır; chest GUI kullanılmaz.


V4.0.5 HARDENING NOTLARI
----------------------
Bu sürüm v3.1 üzerine canlı sunucu güvenliği için çıkarılmış hardening/polish sürümüdür.
Eklenen başlıca noktalar:
- WAL açılış taraması ve stale lock recovery.
- Admin panelde Recovery Tara ve Ekonomi Test butonları.
- Ekonomi hazır değilse headerda kırmızı uyarı.
- Depo sayacı ve recovery item sayacı.
- Player leaderboard satırlarında UUID yerine bilinen oyuncu adı.
- Configteki featured/auction kredi bedellerinin UI onay metinlerine dinamik yansıması.
- Kritik final save hatalarında FAILED_RECOVERY state.
- Açık artırma ve teklif kabul teslimlerinde addItemStackToInventory dönüş kontrolü.
- Daha süslü panel/button/icon texture seti.

V4.0.5 PERFORMANS NOTLARI
-------------------------
190+ modlu sunucu için varsayılanlar daha güvenli hale getirildi:
- max_expirations_per_check=25: tek expire döngüsünde sınırsız item/auction sonuçlanmaz.
- max_storage_rows_per_snapshot=7: depo ekranı tek pakette 100 item NBT göndermez.
- max_offers_rows_per_snapshot=7: teklif ekranı sayfalıdır.
- soft_save_interval_seconds=30: beğeni gibi kritik olmayan işlemler disk spam yapmaz.
- auction_bid_broadcast_cooldown_seconds=10: hararetli açık artırmada chat spam azalır.
- NAV rate limit 300ms: menü spamı snapshot üretimini boğmaz.

Canlı sunucuda öneri:
- allow_internal_test_economy=false kalsın.
- allow_shulker_boxes=false kalsın.
- max_item_nbt_bytes değerini 32768-65536 aralığında tut.
- max_active_listings_per_player değerini 15-25 arası tut.


V3.5 TASARIM + REHBER NOTLARI
-----------------------------
- /pazar içindeki sol menüye "Rehber" bölümü eklendi.
- Rehber 5 sayfadan oluşur: sistem amacı, ilan ekleme, teklif/beğeni/öne çıkarma, açık artırma, depo/tablo/güvenlik.
- Rehber client custom screen içinde çalışır; komuta veya dış wikiye bağımlı değildir.
- panel_info.png eklendi; bilgi ekranı ayrı kadim kayıt defteri/pazar kitabı havası taşır.
- Ana panel setine daha belirgin rune/separator polish uygulandı.
- Sol menü 10 bölümü taşır hale sıkıştırıldı; Rehber ve OP Mührü çakışmasın diye yan dekor sadeleştirildi.

TASARIM DİLİ
------------
Ruasol Market arayüzü koyu taş/demir zemin, soluk altın çizgiler, mühür ve kayıt defteri hissi üzerine kuruludur.
RC klasik ekonomi birimi, Pazar Kredisi ise prestij/müzayede birimi olarak ayrıştırılmıştır.
Rehber metinleri kısa tutulmuştur; uzun açıklamalar wrap edilir ve menü taşmasını engelleyecek şekilde sayfalara bölünür.


V3.5 HARDENING NOTLARI
======================
Bu sürümde NAV spam gerçek anlamda kesilir: limit dolduğunda server snapshot üretmez.
Arama, item-card texture, aktif liste cache, logout session cleanup, snapshot byte warning ve ödeme satırı limitleri eklendi.
Yeni önerilen canlı ayarlar:
active_list_cache_seconds=5
snapshot_debug_bytes_warn=750000
max_payout_rows_per_snapshot=5
max_expirations_per_check=15-25
allow_internal_test_economy=false

V4.0.5 GRAND ECONOMY NOTLARI
----------------------------
Bu sürümde Pazar Postası, Takip Ettiklerim, admin self-test, recovery çözümü ve data shard yazımı eklendi.

Yeni komutlar:
/pazar admintest
/pazar admintest economy
/pazar admintest storage
/pazar admintest auction
/pazar admintest snapshot

Yeni configler:
max_notification_rows_per_snapshot=7
max_favorites_rows_per_snapshot=7
write_data_shards=true
enable_ui_sounds=true

Data shard dosyaları:
world/serverconfig/ruasol_market_shards/listings.json
world/serverconfig/ruasol_market_shards/offers.json
world/serverconfig/ruasol_market_shards/storage.json
world/serverconfig/ruasol_market_shards/recovery_storage.json
world/serverconfig/ruasol_market_shards/payouts.json
world/serverconfig/ruasol_market_shards/stats.json
world/serverconfig/ruasol_market_shards/notifications.json

Not: Ana otoriter data dosyası hâlâ world/serverconfig/ruasol_market_data.json dosyasıdır. Shard dosyaları debug, yedek ve analiz kolaylığı için yazılır.

V4.0.5 HOTFIX NOTLARI
---------------------
- Sol menü taşması ve SEARCH/FAVORITES hitbox uyuşmazlığı düzeltildi.
- Takip durumu artık UI'da doğru görünür.
- Pazar Kredisi admin komutları ayrıldı: /pazar kredi ver ve /pazar kredi al.
- Recovery çözme işlemi depo limitini delmez.
- Kritik save hatasında ayrıca ruasol_market_emergency_recovery.jsonl dosyasına kayıt düşer.
- Data dosyaları varsayılan olarak server.properties level-name değerindeki dünya klasörünün serverconfig dizinine yazılır.
- Özel data yolu istenirse Java parametresiyle kullanılabilir: -Druasolmarket.dataDir=/tam/yol/serverconfig

V4.0.5 SECURITY + LAG HARDENING NOTLARI
----------------------------------------
Bu sürüm yeni prestige/lonca/efsanevi/vergi/sezon sistemleri için exploit ve lag risklerini azaltır.

Yeni/önemli configler:
- max_prestige_level: İlan başına maksimum prestij seviyesi.
- max_guilds: Toplam ticaret loncası sınırı.
- max_guild_members_per_guild: Lonca başı üye sınırı.
- max_favorites_per_player: Oyuncu başı takip/favori sınırı.
- max_notifications_per_player: Oyuncu başı Pazar Postası sınırı.
- max_payouts_processed_per_click: Tek tıkta işlenecek pending payout sınırı.
- write_data_shards: Production için varsayılan false. Debug/backup gerektiğinde true yapılabilir.

Güvenlik notu:
Pazar Kredisi açık artırmalarında küsuratlı teklif kabul edilmez. Efsanevi ilan, öne çıkarma ve prestij mührü kredi harcamaları save başarısız olursa rollback/refund dener.
- max_rc_transaction_amount: NaN/Infinity/aşırı büyük packet girişlerine karşı tek RC işlem sınırı.
- max_credit_transaction_amount: Tek Pazar Kredisi işlem sınırı.

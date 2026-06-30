package com.ruasol.market.economy;

import com.ruasol.market.RuasolMarket;
import net.minecraft.entity.player.ServerPlayerEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EconomyBridge {
    private static Object vaultEconomy;
    private static Class<?> vaultEconomyClass;
    private static Class<?> offlinePlayerClass;
    private static Object essentialsPlugin;
    private static Class<?> essentialsEconomyApi;
    private static long lastProbe;
    private static String lastError = "not probed";
    private static final Map<UUID, Double> TEST_BALANCES = new ConcurrentHashMap<UUID, Double>();

    public static boolean isReady() {
        return vault() != null || essentialsApi() != null || internalAllowed();
    }

    public static boolean forceReconnect() {
        vaultEconomy = null;
        vaultEconomyClass = null;
        offlinePlayerClass = null;
        essentialsPlugin = null;
        essentialsEconomyApi = null;
        lastProbe = 0L;
        lastError = "forced reconnect";
        return isReady();
    }

    public static String mode() {
        Object v = vault();
        if (v != null) return "Vault/Essentials";
        if (essentialsApi() != null) return "Essentials Direct";
        if (internalAllowed()) return "Internal test economy";
        return "No economy provider";
    }

    public static String debugStatus() {
        return mode() + " | " + lastError;
    }

    public static boolean has(ServerPlayerEntity player, double amount) {
        return has(player.getUniqueID(), player.getGameProfile().getName(), amount);
    }

    public static boolean withdraw(ServerPlayerEntity player, double amount) {
        return withdraw(player.getUniqueID(), player.getGameProfile().getName(), amount);
    }

    public static boolean deposit(ServerPlayerEntity player, double amount) {
        return depositOffline(player.getUniqueID(), player.getGameProfile().getName(), amount);
    }

    public static boolean has(UUID uuid, String name, double amount) {
        if (amount <= 0) return true;
        if (!Double.isFinite(amount)) return false;
        Object v = vault();
        if (v != null) {
            try {
                Object res = invokeVaultMoney(v, "has", uuid, name, amount);
                if (res instanceof Boolean) return Boolean.TRUE.equals(res);
            } catch (Throwable t) { remember("Vault has failed: " + simple(t)); }
        }
        if (essentialsApi() != null) {
            try { return essentialsHas(name, amount); } catch (Throwable t) { remember("Essentials has failed: " + simple(t)); }
        }
        if (internalAllowed()) return TEST_BALANCES.getOrDefault(uuid, 1000000D) >= amount;
        return false;
    }

    public static boolean withdraw(UUID uuid, String name, double amount) {
        if (amount <= 0) return true;
        if (!Double.isFinite(amount)) return false;
        Object v = vault();
        if (v != null) {
            try {
                Object response = invokeVaultMoney(v, "withdrawPlayer", uuid, name, amount);
                return responseSuccess(response);
            } catch (Throwable t) { remember("Vault withdraw failed: " + simple(t)); }
        }
        if (essentialsApi() != null) {
            try { return essentialsSubtract(name, amount); } catch (Throwable t) { remember("Essentials subtract failed: " + simple(t)); }
        }
        if (internalAllowed()) {
            double bal = TEST_BALANCES.getOrDefault(uuid, 1000000D);
            if (bal < amount) return false;
            TEST_BALANCES.put(uuid, bal - amount);
            return true;
        }
        return false;
    }

    public static boolean depositOffline(UUID uuid, String name, double amount) {
        if (amount <= 0) return true;
        if (!Double.isFinite(amount)) return false;
        Object v = vault();
        if (v != null) {
            try {
                Object response = invokeVaultMoney(v, "depositPlayer", uuid, name, amount);
                return responseSuccess(response);
            } catch (Throwable t) { remember("Vault deposit failed: " + simple(t)); }
        }
        if (essentialsApi() != null) {
            try { return essentialsAdd(name, amount); } catch (Throwable t) { remember("Essentials add failed: " + simple(t)); }
        }
        if (internalAllowed()) {
            TEST_BALANCES.put(uuid, TEST_BALANCES.getOrDefault(uuid, 1000000D) + amount);
            return true;
        }
        return false;
    }

    public static double testBalance(ServerPlayerEntity player) {
        return TEST_BALANCES.getOrDefault(player.getUniqueID(), 1000000D);
    }

    private static boolean internalAllowed() {
        return RuasolMarket.CONFIG != null && RuasolMarket.CONFIG.allowInternalTestEconomy;
    }

    private static Object vault() {
        long now = System.currentTimeMillis();
        if (vaultEconomy != null) return vaultEconomy;
        if (now - lastProbe < 2500L) return null;
        lastProbe = now;
        try {
            Class<?> bukkit = loadAny("org.bukkit.Bukkit");
            if (bukkit == null) { remember("Bukkit class not visible from mod classloader"); return null; }
            Object services = bukkit.getMethod("getServicesManager").invoke(null);
            Class<?> economyClass = loadPluginClass("Vault", "net.milkbowl.vault.economy.Economy");
            if (economyClass == null) { remember("Vault Economy API not visible yet"); return null; }
            Object registration = services.getClass().getMethod("getRegistration", Class.class).invoke(services, economyClass);
            if (registration == null) { remember("Vault service registration is null; Vault may still be waiting for Essentials"); return null; }
            Object provider = registration.getClass().getMethod("getProvider").invoke(registration);
            if (provider == null) { remember("Vault provider is null"); return null; }
            Class<?> op = loadPluginClass("Vault", "org.bukkit.OfflinePlayer");
            if (op == null) op = loadAny("org.bukkit.OfflinePlayer");
            if (op == null) { remember("OfflinePlayer class not visible"); return null; }
            vaultEconomy = provider;
            vaultEconomyClass = economyClass;
            offlinePlayerClass = op;
            remember("connected to Vault provider " + provider.getClass().getName());
            RuasolMarket.LOG.info("Ruasol Market economy bridge connected to Vault provider: {}", provider.getClass().getName());
            return vaultEconomy;
        } catch (Throwable t) {
            remember("Vault probe failed: " + simple(t));
            return null;
        }
    }

    private static Class<?> essentialsApi() {
        if (essentialsEconomyApi != null) return essentialsEconomyApi;
        try {
            Class<?> api = loadPluginClass("Essentials", "com.earth2me.essentials.api.Economy");
            if (api == null) return null;
            essentialsEconomyApi = api;
            remember("Essentials direct API visible");
            return api;
        } catch (Throwable t) {
            remember("Essentials direct probe failed: " + simple(t));
            return null;
        }
    }

    private static Object invokeVaultMoney(Object provider, String methodName, UUID uuid, String name, double amount) throws Exception {
        Object offline = offlinePlayer(uuid, name);
        Method best = null;
        Object firstArg = null;
        for (Method m : provider.getClass().getMethods()) {
            if (!m.getName().equals(methodName) || m.getParameterTypes().length != 2) continue;
            Class<?>[] p = m.getParameterTypes();
            if (!(p[1] == double.class || p[1] == Double.class)) continue;
            if (offline != null && p[0].isAssignableFrom(offline.getClass())) { best = m; firstArg = offline; break; }
            if (p[0] == String.class) { best = m; firstArg = name; }
        }
        if (best == null) throw new NoSuchMethodException("Vault method not found: " + methodName);
        best.setAccessible(true);
        return best.invoke(provider, firstArg, amount);
    }

    private static Object offlinePlayer(UUID uuid, String name) throws Exception {
        Class<?> bukkit = loadAny("org.bukkit.Bukkit");
        if (bukkit == null) return null;
        if (uuid != null) {
            try { return bukkit.getMethod("getOfflinePlayer", UUID.class).invoke(null, uuid); } catch (Throwable ignored) {}
        }
        if (name != null && !name.isEmpty()) {
            try { return bukkit.getMethod("getOfflinePlayer", String.class).invoke(null, name); } catch (Throwable ignored) {}
        }
        return null;
    }

    private static boolean essentialsHas(String name, double amount) throws Exception {
        Class<?> api = essentialsApi();
        if (api == null || name == null || name.isEmpty()) return false;
        try {
            Method m = api.getMethod("hasEnough", String.class, BigDecimal.class);
            return Boolean.TRUE.equals(m.invoke(null, name, BigDecimal.valueOf(amount)));
        } catch (NoSuchMethodException ignored) {
            BigDecimal money = essentialsMoney(name);
            return money != null && money.compareTo(BigDecimal.valueOf(amount)) >= 0;
        }
    }

    private static boolean essentialsSubtract(String name, double amount) throws Exception {
        if (!essentialsHas(name, amount)) return false;
        Method m = essentialsApi().getMethod("subtract", String.class, BigDecimal.class);
        m.invoke(null, name, BigDecimal.valueOf(amount));
        return true;
    }

    private static boolean essentialsAdd(String name, double amount) throws Exception {
        if (name == null || name.isEmpty()) return false;
        Method m = essentialsApi().getMethod("add", String.class, BigDecimal.class);
        m.invoke(null, name, BigDecimal.valueOf(amount));
        return true;
    }

    private static BigDecimal essentialsMoney(String name) throws Exception {
        Method m = essentialsApi().getMethod("getMoneyExact", String.class);
        Object o = m.invoke(null, name);
        if (o instanceof BigDecimal) return (BigDecimal)o;
        if (o instanceof Number) return BigDecimal.valueOf(((Number)o).doubleValue());
        return null;
    }

    private static boolean responseSuccess(Object response) {
        if (response == null) return false;
        if (response instanceof Boolean) return Boolean.TRUE.equals(response);
        try {
            Method m = response.getClass().getMethod("transactionSuccess");
            m.setAccessible(true);
            return Boolean.TRUE.equals(m.invoke(response));
        } catch (Throwable t) {
            try {
                Field f = response.getClass().getField("type");
                f.setAccessible(true);
                Object type = f.get(response);
                return type != null && type.toString().equalsIgnoreCase("SUCCESS");
            } catch (Throwable ignored) { return false; }
        }
    }

    private static Class<?> loadAny(String name) {
        ClassLoader[] loaders = new ClassLoader[]{
            Thread.currentThread().getContextClassLoader(),
            EconomyBridge.class.getClassLoader(),
            ClassLoader.getSystemClassLoader()
        };
        for (ClassLoader cl : loaders) {
            if (cl == null) continue;
            try { return Class.forName(name, false, cl); } catch (Throwable ignored) {}
        }
        try { return Class.forName(name); } catch (Throwable ignored) { return null; }
    }

    private static Class<?> loadPluginClass(String pluginName, String className) {
        Class<?> direct = loadAny(className);
        if (direct != null) return direct;
        try {
            Class<?> bukkit = loadAny("org.bukkit.Bukkit");
            if (bukkit == null) return null;
            Object pm = bukkit.getMethod("getPluginManager").invoke(null);
            Object plugin = pm.getClass().getMethod("getPlugin", String.class).invoke(pm, pluginName);
            if (plugin == null) return null;
            ClassLoader cl = plugin.getClass().getClassLoader();
            return Class.forName(className, false, cl);
        } catch (Throwable ignored) { return null; }
    }

    private static void remember(String msg) { lastError = msg == null ? "" : msg; }
    private static String simple(Throwable t) {
        if (t == null) return "unknown";
        Throwable c = t.getCause() != null ? t.getCause() : t;
        return c.getClass().getSimpleName() + (c.getMessage() == null ? "" : ": " + c.getMessage());
    }
}

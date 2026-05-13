package net.trueog.horsetpog;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class ProtectedActivity {

    private static Method duelsIsInMatch;
    private static Method spleefIsInSpleef;

    private static boolean duelsLookupComplete;
    private static boolean spleefLookupComplete;

    private ProtectedActivity() {

    }

    public static boolean isActive(Player player) {

        return player != null && (isInDuels(player) || isInSpleef(player));

    }

    private static boolean isInDuels(Player player) {

        if (!isPluginEnabled("Duels-OG", "Duels")) {

            return false;

        }

        return invoke(getDuelsMethod(), player);

    }

    private static boolean isInSpleef(Player player) {

        if (!isPluginEnabled("Spleef-OG", "ArenaSpleef")) {

            return false;

        }

        return invoke(getSpleefMethod(), player);

    }

    private static Method getDuelsMethod() {

        if (!duelsLookupComplete) {

            duelsIsInMatch = findMethod("me.realized.duels.api.DuelsAPI", "isInMatch");
            duelsLookupComplete = true;

        }

        return duelsIsInMatch;

    }

    private static Method getSpleefMethod() {

        if (!spleefLookupComplete) {

            spleefIsInSpleef = findMethod("org.battleplugins.arena.spleef.api.SpleefAPI", "isInSpleef");
            spleefLookupComplete = true;

        }

        return spleefIsInSpleef;

    }

    private static Method findMethod(String className, String methodName) {

        try {

            Method method = Class.forName(className).getMethod(methodName, Player.class);
            method.setAccessible(true);
            return method;

        } catch (ClassNotFoundException | NoSuchMethodException ignored) {

            return null;

        }

    }

    private static boolean invoke(Method method, Player player) {

        if (method == null) {

            return false;

        }

        try {

            return Boolean.TRUE.equals(method.invoke(null, player));

        } catch (IllegalAccessException | InvocationTargetException ignored) {

            return false;

        }

    }

    private static boolean isPluginEnabled(String... names) {

        for (String name : names) {

            Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
            if (plugin != null && plugin.isEnabled()) {

                return true;

            }

        }

        return false;

    }

}

package io.nightbeam.LPCF.util;

import net.luckperms.api.cacheddata.CachedMetaData;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.NavigableMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LuckPermsUtilTest {

    @Test
    void primaryGroupFallsBackToDefaultWhenMissing() {
        assertEquals("default", LuckPermsUtil.primaryGroup(meta(null, null, null, new TreeMap<>(), new TreeMap<>(), null)));
        assertEquals("admin", LuckPermsUtil.primaryGroup(meta("admin", "&cA", null, new TreeMap<>(), new TreeMap<>(), null)));
    }

    @Test
    void prefixAndSuffixTreatNullAsEmpty() {
        CachedMetaData meta = meta("default", null, null, new TreeMap<>(), new TreeMap<>(), null);
        assertEquals("", LuckPermsUtil.prefix(meta));
        assertEquals("", LuckPermsUtil.suffix(meta));
    }

    @Test
    void joinedPrefixesAndSuffixesPreserveOrder() {
        NavigableMap<Integer, String> prefixes = new TreeMap<>();
        prefixes.put(10, "&aA");
        prefixes.put(20, "&bB");
        NavigableMap<Integer, String> suffixes = new TreeMap<>();
        suffixes.put(5, "[1]");
        suffixes.put(15, "[2]");

        CachedMetaData meta = meta("default", "&bB", "[2]", prefixes, suffixes, null);
        assertEquals("&aA &bB", LuckPermsUtil.joinedPrefixes(meta));
        assertEquals("[1] [2]", LuckPermsUtil.joinedSuffixes(meta));
    }

    @Test
    void sortPriorityUsesHighestPrefixWeight() {
        NavigableMap<Integer, String> prefixes = new TreeMap<>();
        prefixes.put(100, "&cAdmin");
        prefixes.put(10, "&7User");
        assertEquals(900, LuckPermsUtil.sortPriority(meta("admin", "&cAdmin", "", prefixes, new TreeMap<>(), null)));
        assertEquals(100, LuckPermsUtil.sortPriority(meta("default", null, null, new TreeMap<>(), new TreeMap<>(), null)));
    }

    @Test
    void metaValueFallsBackToEmptyString() {
        CachedMetaData missing = meta("default", null, null, new TreeMap<>(), new TreeMap<>(), null);
        assertEquals("", LuckPermsUtil.metaValue(missing, "username-color"));

        CachedMetaData present = meta("default", null, null, new TreeMap<>(), new TreeMap<>(), "<red>");
        assertEquals("<red>", LuckPermsUtil.metaValue(present, "username-color"));
    }

    private static CachedMetaData meta(
            String primaryGroup,
            String prefix,
            String suffix,
            NavigableMap<Integer, String> prefixes,
            NavigableMap<Integer, String> suffixes,
            String usernameColor
    ) {
        return (CachedMetaData) Proxy.newProxyInstance(
                CachedMetaData.class.getClassLoader(),
                new Class<?>[]{CachedMetaData.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getPrimaryGroup" -> primaryGroup;
                    case "getPrefix" -> prefix;
                    case "getSuffix" -> suffix;
                    case "getPrefixes" -> prefixes;
                    case "getSuffixes" -> suffixes;
                    case "getMetaValue" -> "username-color".equals(args[0]) ? usernameColor : null;
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == float.class) {
            return 0f;
        }
        return 0;
    }
}

package io.nightbeam.LPCF.util;

import net.luckperms.api.cacheddata.CachedMetaData;

import java.util.Collection;
import java.util.stream.Collectors;

public final class LuckPermsUtil {

    private LuckPermsUtil() {
    }

    public static String primaryGroup(CachedMetaData metaData) {
        String primaryGroup = metaData.getPrimaryGroup();
        return primaryGroup == null ? "default" : primaryGroup;
    }

    public static String prefix(CachedMetaData metaData) {
        String prefix = metaData.getPrefix();
        return prefix == null ? "" : prefix;
    }

    public static String suffix(CachedMetaData metaData) {
        String suffix = metaData.getSuffix();
        return suffix == null ? "" : suffix;
    }

    public static String joinedPrefixes(CachedMetaData metaData) {
        return joinValues(metaData.getPrefixes().values());
    }

    public static String joinedSuffixes(CachedMetaData metaData) {
        return joinValues(metaData.getSuffixes().values());
    }

    public static String metaValue(CachedMetaData metaData, String key) {
        String value = metaData.getMetaValue(key);
        return value == null ? "" : value;
    }

    private static String joinValues(Collection<String> values) {
        return values.stream().collect(Collectors.joining(" "));
    }
}

package com.jjenus.qliina_management.common;

import java.time.LocalDateTime;
import java.time.ZoneId;

public final class TimezoneContext {

    private static final ThreadLocal<ZoneId> ZONE = new ThreadLocal<>();

    public static void set(ZoneId zone) {
        ZONE.set(zone);
    }

    public static ZoneId getZone() {
        return ZONE.get();
    }

    public static void clear() {
        ZONE.remove();
    }

    public static LocalDateTime now() {
        ZoneId zone = ZONE.get();
        return zone != null ? LocalDateTime.now(zone) : LocalDateTime.now(ZoneId.systemDefault());
    }
}

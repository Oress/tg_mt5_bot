package org.ipan.hedging.telegram;

import lombok.Getter;

@Getter
public class TgMessageMetaHolder {
    private static final ThreadLocal<TgMessageMeta> requestAttributesHolder = new ThreadLocal<>();

    public static void setTgMessageMeta(TgMessageMeta meta) {
        requestAttributesHolder.set(meta);
    }

    public static TgMessageMeta getTgMessageMeta() {
        return requestAttributesHolder.get();
    }
}

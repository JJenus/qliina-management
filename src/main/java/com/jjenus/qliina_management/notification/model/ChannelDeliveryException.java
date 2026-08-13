package com.jjenus.qliina_management.notification.model;

import com.jjenus.qliina_management.common.BusinessException;
import lombok.Getter;

/**
 * Typed channel send failure. The {@code permanent} flag drives the retry
 * policy (doc §4): permanent failures (hard bounce, invalid push token) are
 * exhausted immediately instead of burning retries on a cause that will never
 * succeed.
 */
@Getter
public class ChannelDeliveryException extends BusinessException {

    private final boolean permanent;

    public ChannelDeliveryException(String message, String errorCode, boolean permanent) {
        super(message, errorCode);
        this.permanent = permanent;
    }

    public ChannelDeliveryException(String message, String errorCode, boolean permanent, Throwable cause) {
        super(message, errorCode, cause);
        this.permanent = permanent;
    }
}
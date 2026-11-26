package org.codeflow.veterineryproject.notification.dto;

import java.util.List;

/** Bir bildirim türünün kullanıcıya uygun kanallardaki açık/kapalı durumu. */
public record PreferenceResponse(String type, String label, List<ChannelPreference> channels) {

    public record ChannelPreference(String channel, boolean enabled) {
    }
}

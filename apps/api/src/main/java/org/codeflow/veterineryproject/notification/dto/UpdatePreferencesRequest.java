package org.codeflow.veterineryproject.notification.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Değişen tür × kanal tercihleri; gönderilmeyen tercihler olduğu gibi kalır. */
public record UpdatePreferencesRequest(
        @NotNull(message = "Tercih listesi zorunludur.")
        @Size(max = 100, message = "En fazla 100 tercih gönderilebilir.")
        List<@Valid Item> preferences) {

    public record Item(
            @NotBlank(message = "Bildirim türü zorunludur.") String type,
            @NotBlank(message = "Kanal zorunludur.") String channel,
            @NotNull(message = "Açık/kapalı bilgisi zorunludur.") Boolean enabled) {
    }
}

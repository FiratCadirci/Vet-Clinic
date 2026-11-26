package org.codeflow.veterineryproject.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Mobil cihaz kaydı. Expo token biçimi: ExponentPushToken[...] (yeni SDK'larda ExpoPushToken[...]). */
public record RegisterPushDeviceRequest(
        @NotBlank(message = "Push token zorunludur.")
        @Size(max = 255, message = "Push token en fazla 255 karakter olabilir.")
        @Pattern(regexp = "^Expo(nent)?PushToken\\[[^\\[\\]\\s]+\\]$", message = "Geçersiz Expo push token (ExponentPushToken[...] bekleniyor).")
        String expoPushToken,
        @NotBlank(message = "Platform zorunludur.")
        @Pattern(regexp = "(?i)^(ios|android)$", message = "Platform 'IOS' veya 'ANDROID' olmalıdır.")
        String platform) {
}

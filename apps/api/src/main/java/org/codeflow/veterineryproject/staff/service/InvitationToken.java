package org.codeflow.veterineryproject.staff.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Davet token'ı (değer nesnesi, fabrika metodu {@link #generate()}). Ham değer yalnızca e-posta bağlantısı için olayda
 * taşınır; veritabanında SHA-256 özeti saklanır, böylece tablo sızsa bile davet bağlantısı üretilemez.
 */
public record InvitationToken(String raw) {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int BYTES = 32;

    public InvitationToken {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Davet kodu geçersiz.");
        }
        raw = raw.trim();
    }

    public static InvitationToken generate() {
        byte[] bytes = new byte[BYTES];
        RANDOM.nextBytes(bytes);
        return new InvitationToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    public String hash() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Ham token log/hata mesajlarına sızmasın. */
    @Override
    public String toString() {
        return "InvitationToken[***]";
    }
}

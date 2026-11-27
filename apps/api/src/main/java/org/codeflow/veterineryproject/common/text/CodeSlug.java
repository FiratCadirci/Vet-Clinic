package org.codeflow.veterineryproject.common.text;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Addan kısa kod üretimi (şube ve hizmet kodları): "Kadıköy Şubesi" → "KADIKOY-SUBESI".
 * Türkçe karakterler ASCII karşılıklarına çevrilir; kod alanları varchar(40/80) olduğu için 30 karakterle sınırlanır.
 */
public final class CodeSlug {

    private static final int MAX_LENGTH = 30;
    private static final SecureRandom RANDOM = new SecureRandom();

    private CodeSlug() {
    }

    public static String of(String name, String fallback) {
        String folded = name == null ? "" : name.toLowerCase(Locale.forLanguageTag("tr-TR"))
                .replace('ç', 'c').replace('ğ', 'g').replace('ı', 'i').replace('ö', 'o').replace('ş', 's').replace('ü', 'u');
        String ascii = Normalizer.normalize(folded, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        String slug = ascii.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > MAX_LENGTH) {
            slug = slug.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? fallback : slug;
    }

    /** Görünmeyen (başka klinik grubundaki) kayıtlarla çakışmayı önlemek için 4 haneli rastgele son ek ekler. */
    public static String withRandomSuffix(String name, String fallback) {
        byte[] suffix = new byte[2];
        RANDOM.nextBytes(suffix);
        return of(name, fallback) + "-" + HexFormat.of().withUpperCase().formatHex(suffix);
    }
}

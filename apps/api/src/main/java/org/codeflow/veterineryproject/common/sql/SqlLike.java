package org.codeflow.veterineryproject.common.sql;

import java.util.Locale;

public final class SqlLike {

    private SqlLike() {
    }

    /** LIKE ... ESCAPE '\' ile kullanılmak üzere küçük harfli "içerir" deseni üretir. */
    public static String contains(String value) {
        String escaped = value.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}

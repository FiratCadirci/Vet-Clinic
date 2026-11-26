package org.codeflow.veterineryproject.common.api;

import java.time.Instant;
import java.util.Map;

public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        Map<String, Object> details
) {
}


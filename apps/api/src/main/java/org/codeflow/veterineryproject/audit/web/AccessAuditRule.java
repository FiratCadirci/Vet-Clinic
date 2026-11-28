package org.codeflow.veterineryproject.audit.web;

import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/**
 * Görüntülenmesi denetim izine yazılan kritik kayıt uçları (FG-J4, FOG-13). Eşleşme istek yolu üzerinden yapılır;
 * böylece ilgili modüllerin controller'larına dokunmadan uygulanır. Yalnız başarılı GET istekleri kaydedilir.
 */
public enum AccessAuditRule {

    PATIENT_PROFILE("/api/v1/patients/{id}", "ANIMAL_VIEW", "ANIMAL"),
    ANIMAL_PROFILE("/api/v1/animals/{id}", "ANIMAL_VIEW", "ANIMAL"),
    PATIENT_HISTORY("/api/v1/patients/{id}/history", "ANIMAL_HISTORY_VIEW", "ANIMAL"),
    ANIMAL_HISTORY("/api/v1/animals/{id}/history", "ANIMAL_HISTORY_VIEW", "ANIMAL"),
    CLIENT_PROFILE("/api/v1/clients/{id}", "CLIENT_VIEW", "CLIENT"),
    ENCOUNTER_DETAIL("/api/v1/encounters/{id}", "ENCOUNTER_VIEW", "ENCOUNTER"),
    ATTACHMENT_DOWNLOAD("/api/v1/attachments/{id}/content", "CLINICAL_DOCUMENT_DOWNLOAD", "CLINICAL_DOCUMENT");

    private static final String ID = "id";

    private final PathPattern pattern;
    private final String action;
    private final String entityType;

    AccessAuditRule(String pattern, String action, String entityType) {
        this.pattern = PathPatternParser.defaultInstance.parse(pattern);
        this.action = action;
        this.entityType = entityType;
    }

    public String action() {
        return action;
    }

    public String entityType() {
        return entityType;
    }

    /** Yolu eşleşen kural ve kayıt kimliği; kimlik UUID değilse (ör. /animals/by-chip/...) eşleşme yoktur. */
    public static Optional<Match> match(String path) {
        PathContainer container = PathContainer.parsePath(path);
        return Arrays.stream(values())
                .map(rule -> rule.extract(container))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private Optional<Match> extract(PathContainer path) {
        PathPattern.PathMatchInfo info = pattern.matchAndExtract(path);
        if (info == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Match(this, UUID.fromString(info.getUriVariables().get(ID))));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public record Match(AccessAuditRule rule, UUID entityId) {
    }
}

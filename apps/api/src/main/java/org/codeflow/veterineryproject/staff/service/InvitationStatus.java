package org.codeflow.veterineryproject.staff.service;

import org.codeflow.veterineryproject.common.api.ConflictException;

import java.time.OffsetDateTime;
import java.util.Map;

/** Davetin durumu kayıttaki zaman damgalarından türetilir (ayrı bir durum sütunu tutulmaz). */
public enum InvitationStatus {
    PENDING(null, null),
    ACCEPTED("INVITATION_ALREADY_ACCEPTED", "Bu davet zaten kabul edilmiş."),
    REVOKED("INVITATION_REVOKED", "Bu davet iptal edilmiş."),
    EXPIRED("INVITATION_EXPIRED", "Davetin süresi dolmuş; yöneticinizden yeni davet isteyin.");

    private final String errorCode;
    private final String message;

    InvitationStatus(String errorCode, String message) {
        this.errorCode = errorCode;
        this.message = message;
    }

    public static InvitationStatus of(OffsetDateTime acceptedAt, OffsetDateTime revokedAt, OffsetDateTime expiresAt,
                                      OffsetDateTime now) {
        if (revokedAt != null) {
            return REVOKED;
        }
        if (acceptedAt != null) {
            return ACCEPTED;
        }
        return expiresAt.isAfter(now) ? PENDING : EXPIRED;
    }

    /** Bekleyen olmayan davet üzerinde işlem yapılamaz (409). */
    public void requirePending() {
        if (this != PENDING) {
            throw conflict();
        }
    }

    public ConflictException conflict() {
        return new ConflictException(message, errorCode, Map.of("status", name()));
    }
}

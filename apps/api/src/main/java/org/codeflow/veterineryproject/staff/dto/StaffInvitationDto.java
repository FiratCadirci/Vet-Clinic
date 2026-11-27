package org.codeflow.veterineryproject.staff.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Personel daveti DTO'ları. Ham davet kodu hiçbir yanıtta dönmez; yalnızca StaffInvitedEvent ile e-postaya gider. */
public final class StaffInvitationDto {

    private StaffInvitationDto() {
    }

    public record CreateRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotNull UUID roleId,
            @NotEmpty @Size(max = 100) List<@NotNull UUID> clinicIds
    ) {
    }

    public record InvitationResponse(UUID id, String email, UUID roleId, String roleCode, String roleName,
                                     List<UUID> clinicIds, String status, UUID invitedByUserId, String invitedByName,
                                     OffsetDateTime expiresAt, OffsetDateTime acceptedAt, OffsetDateTime revokedAt,
                                     OffsetDateTime createdAt) {
    }

    public record PreviewRequest(@NotBlank @Size(max = 200) String token) {
    }

    public record PreviewResponse(String organizationName, String email, String roleCode, String roleName,
                                  OffsetDateTime expiresAt) {
    }

    public record AcceptRequest(
            @NotBlank @Size(max = 200) String token,
            @NotBlank @Size(min = 2, max = 200) String fullName,
            @NotBlank @Size(min = 10, max = 100) String password
    ) {
    }
}

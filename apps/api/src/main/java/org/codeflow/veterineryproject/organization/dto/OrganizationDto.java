package org.codeflow.veterineryproject.organization.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.codeflow.veterineryproject.subscription.dto.PlanDto.PlanUsage;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Klinik kaydı ve klinik (organizasyon) profili DTO'ları. */
public final class OrganizationDto {

    private OrganizationDto() {
    }

    /** FG-A1 kayıt formu. Şube adı verilmezse "Merkez Şube"; paket kodu verilmezse en uygun aylık paket. */
    public record SignupRequest(
            @NotBlank @Size(min = 2, max = 200) String clinicName,
            @Size(max = 40) String phone,
            @Email @Size(max = 254) String email,
            @Size(max = 500) String address,
            @Size(max = 30) String taxNumber,
            @Size(min = 2, max = 200) String branchName,
            @NotBlank @Size(min = 2, max = 200) String adminFullName,
            @NotBlank @Email @Size(max = 254) String adminEmail,
            @NotBlank @Size(min = 10, max = 100) String password,
            @Size(max = 50) String planCode
    ) {
    }

    /** register_clinic sonucu. */
    public record Registration(String result, UUID organizationId, Integer organizationCode, UUID userId, UUID clinicId) {
    }

    public record SubscriptionSummary(String status, String planCode, String planName, String billingPeriod,
                                      OffsetDateTime currentPeriodEnd, int maxBranches, int maxUsers) {
    }

    public record ProfileResponse(UUID id, Integer code, String name, String legalName, String taxOffice, String taxNumber,
                                  String phone, String email, String addressLine,
                                  SubscriptionSummary subscription, PlanUsage usage) {
        public ProfileResponse with(SubscriptionSummary s, PlanUsage u) {
            return new ProfileResponse(id, code, name, legalName, taxOffice, taxNumber, phone, email, addressLine, s, u);
        }
    }

    /** Profilin tamamı gönderilir; boş alanlar temizlenir. */
    public record UpdateProfileRequest(
            @NotBlank @Size(min = 2, max = 200) String name,
            @Size(max = 250) String legalName,
            @Size(max = 120) String taxOffice,
            @Size(max = 30) String taxNumber,
            @Size(max = 40) String phone,
            @Email @Size(max = 254) String email,
            @Size(max = 500) String addressLine
    ) {
    }
}

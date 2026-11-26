package org.codeflow.veterineryproject.notification;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.notification.dto.PushDeviceResponse;
import org.codeflow.veterineryproject.notification.dto.RegisterPushDeviceRequest;
import org.codeflow.veterineryproject.notification.service.PushDeviceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.codeflow.veterineryproject.common.test.TenantTestData.*;
import static org.codeflow.veterineryproject.notification.NotificationTestData.*;

/** Push cihaz kaydı servisi (hayvan sahibi uç noktası portal modülünde): idempotent kayıt, taşıma, silme, doğrulama. */
class PushDeviceServiceIT extends NotificationItSupport {

    @Autowired
    private PushDeviceService devices;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void register_isIdempotent_andMovesTokenToTheNewUser() {
        String token = "ExponentPushToken[" + UUID.randomUUID() + "]";

        PushDeviceResponse first = as(OWNER_USER_A, () -> devices.register(new RegisterPushDeviceRequest(token, "android")));
        PushDeviceResponse again = as(OWNER_USER_A, () -> devices.register(new RegisterPushDeviceRequest(token, "ANDROID")));
        assertThat(again.id()).isEqualTo(first.id());
        assertThat(again.platform()).isEqualTo("ANDROID");
        assertThat(count("SELECT count(*) FROM vet.push_devices WHERE expo_push_token = ?", token)).isEqualTo(1);

        as(STOCK_A1, () -> devices.register(new RegisterPushDeviceRequest(token, "ios")));
        assertThat(text("SELECT user_id::text FROM vet.push_devices WHERE expo_push_token = ?", token)).isEqualTo(STOCK_A1.toString());
        assertThat(as(OWNER_USER_A, () -> devices.hasDevice(OWNER_USER_A) && devices.tokensOf(OWNER_USER_A).contains(token))).isFalse();
        assertThat(as(STOCK_A1, () -> devices.tokensOf(STOCK_A1))).contains(token);
    }

    @Test
    void unregister_onlyOwnDevice_otherwise404() {
        String token = "ExpoPushToken[" + UUID.randomUUID() + "]";
        as(OWNER_USER_A, () -> devices.register(new RegisterPushDeviceRequest(token, "ios")));

        assertThatThrownBy(() -> as(STOCK_A1, () -> {
            devices.unregister(token);
            return null;
        })).isInstanceOf(EntityNotFoundException.class);
        as(OWNER_USER_A, () -> {
            devices.unregister(token);
            return null;
        });
        assertThat(count("SELECT count(*) FROM vet.push_devices WHERE expo_push_token = ?", token)).isZero();
    }

    @Test
    void otherOrganizationCannotSeeOrRemoveTheDevice() {
        String token = "ExponentPushToken[" + UUID.randomUUID() + "]";
        as(OWNER_USER_A, () -> devices.register(new RegisterPushDeviceRequest(token, "android")));

        try (TenantContext.Scope ignored = TenantContext.withTenant(ORG_B, ADMIN_B1, BRANCH_B1_S1)) {
            assertThat(devices.hasDevice(OWNER_USER_A)).isFalse();
            assertThat(devices.removeUnregistered(java.util.List.of(token))).isZero();
        }
        assertThat(count("SELECT count(*) FROM vet.push_devices WHERE expo_push_token = ?", token)).isEqualTo(1);
    }

    @Test
    void request_validatesExpoTokenFormatAndPlatform() {
        assertThat(validator.validate(new RegisterPushDeviceRequest("ExponentPushToken[abc123]", "ios"))).isEmpty();
        assertThat(validator.validate(new RegisterPushDeviceRequest("abc123", "ios"))).isNotEmpty();
        assertThat(validator.validate(new RegisterPushDeviceRequest("ExponentPushToken[abc]", "windows"))).isNotEmpty();
        assertThat(validator.validate(new RegisterPushDeviceRequest(" ", null))).hasSizeGreaterThanOrEqualTo(2);
    }

    private static <T> T as(UUID user, java.util.function.Supplier<T> action) {
        UUID org = user.equals(OWNER_USER_A) || user.equals(STOCK_A1) ? ORG_A : ORG_B;
        try (TenantContext.Scope ignored = TenantContext.withTenant(org, user, null)) {
            return action.get();
        }
    }
}

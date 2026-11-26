package org.codeflow.veterineryproject.encounter.workflow;

import org.codeflow.veterineryproject.common.api.BusinessRuleException;
import org.codeflow.veterineryproject.common.api.ConflictException;
import org.codeflow.veterineryproject.encounter.dto.EncounterResponse;
import org.codeflow.veterineryproject.encounter.dto.EncounterStatus;

/**
 * Template Method: muayene adımlarının ortak iskeleti. execute() sırası sabittir
 * (kapalı mı? geçiş geçerli mi? doğrulama, uygulama, yeni durum); alt sınıflar yalnızca
 * geçiş tanımını, doğrulamayı ve uygulamayı sağlar.
 */
public abstract class EncounterStep<I> {

    public final EncounterResponse execute(EncounterResponse current, I input) {
        guardNotClosed(current);
        guardTransition(current);
        validate(current, input);
        return apply(current, input).withStatus(targetStatus());
    }

    /** Adımın başlayabileceği durum; kabul adımı için null (henüz kayıt yok). */
    protected abstract EncounterStatus requiredStatus();

    protected abstract EncounterStatus targetStatus();

    protected abstract String stepName();

    protected abstract void validate(EncounterResponse current, I input);

    protected abstract EncounterResponse apply(EncounterResponse current, I input);

    private void guardNotClosed(EncounterResponse current) {
        if (current.status() == EncounterStatus.CLOSED) {
            throw new ConflictException("Kapatılmış muayene değiştirilemez; düzeltme için addendum ekleyin.");
        }
    }

    private void guardTransition(EncounterResponse current) {
        if (current.status() != requiredStatus()) {
            throw new ConflictException("Muayene '%s' durumundayken '%s' adımı uygulanamaz."
                    .formatted(current.status(), stepName()));
        }
    }

    protected static void require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(message);
        }
    }

    protected static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

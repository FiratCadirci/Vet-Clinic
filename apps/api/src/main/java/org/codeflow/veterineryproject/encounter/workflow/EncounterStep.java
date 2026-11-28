package org.codeflow.veterineryproject.encounter.workflow;

import org.codeflow.veterineryproject.common.api.ConflictException;
import org.codeflow.veterineryproject.encounter.dto.EncounterResponse;
import org.codeflow.veterineryproject.encounter.dto.EncounterStatus;

import java.util.List;

/**
 * Template Method: muayene adımlarının ortak iskeleti. execute() sırası sabittir
 * (kapalı mı? geçiş muayene türünün akışına göre geçerli mi? doğrulama, uygulama, türün zorunlu alanları ve
 * uyarı kancası, yeni durum); alt sınıflar yalnızca hedef durumu, biçim doğrulamasını ve uygulamayı sağlar.
 * Hangi adımın hangisinden sonra geldiği ve hangi alanların zorunlu olduğu {@link EncounterFlow}'a aittir.
 */
public abstract class EncounterStep<I> {

    private static final EncounterFlow DEFAULT_FLOW = new GeneralEncounterFlow();

    /** Genel muayene akışıyla yürütür. */
    public final EncounterResponse execute(EncounterResponse current, I input) {
        return execute(current, input, DEFAULT_FLOW);
    }

    public final EncounterResponse execute(EncounterResponse current, I input, EncounterFlow flow) {
        guardNotClosed(current);
        guardTransition(current, flow);
        validate(current, input);
        EncounterResponse next = apply(current, input).withStatus(targetStatus());
        List<String> warnings = targetStatus() == EncounterStatus.CLOSED
                ? flow.close(next)
                : flow.completeStep(targetStatus(), next);
        return next.withWarnings(warnings);
    }

    protected abstract EncounterStatus targetStatus();

    protected abstract String stepName();

    /** Adıma özgü biçim kuralları (zorunlu alanlar akışta denetlenir). */
    protected void validate(EncounterResponse current, I input) {
    }

    protected abstract EncounterResponse apply(EncounterResponse current, I input);

    private void guardNotClosed(EncounterResponse current) {
        if (current.status() == EncounterStatus.CLOSED) {
            throw new ConflictException("Kapatılmış muayene değiştirilemez; düzeltme için addendum ekleyin.");
        }
    }

    private void guardTransition(EncounterResponse current, EncounterFlow flow) {
        if (current.status() != flow.previousOf(targetStatus())) {
            throw new ConflictException("Muayene '%s' durumundayken '%s' adımı uygulanamaz."
                    .formatted(current.status(), stepName()));
        }
    }

    protected static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

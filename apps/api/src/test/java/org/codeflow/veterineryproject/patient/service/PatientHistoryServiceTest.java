package org.codeflow.veterineryproject.patient.service;

import jakarta.persistence.EntityNotFoundException;
import org.codeflow.veterineryproject.patient.dto.PatientHistoryResponse;
import org.codeflow.veterineryproject.patient.dto.PatientHistoryResponse.HiddenRecordCounts;
import org.codeflow.veterineryproject.patient.repository.PatientJdbcRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** FG-C5 gizlenen kayıt sayısı hesabı (veritabanı olmadan). */
class PatientHistoryServiceTest {

    private final PatientJdbcRepository repository = mock(PatientJdbcRepository.class);
    private final PatientHistoryService service = new PatientHistoryService(repository);
    private final UUID animal = UUID.randomUUID();

    @Test
    void hiddenIsTotalMinusVisiblePerType() {
        when(repository.animalVisible(animal)).thenReturn(true);
        when(repository.visibleCounts(animal, true)).thenReturn(new HiddenRecordCounts(1, 0, 2));
        when(repository.totalHistoryCounts(animal)).thenReturn(new HiddenRecordCounts(3, 1, 2));
        when(repository.history(eq(animal), eq(true), anyInt(), anyLong())).thenReturn(List.of());

        PatientHistoryResponse response = service.history(animal, true, PageRequest.of(0, 20));

        assertThat(response.hiddenByType()).isEqualTo(new HiddenRecordCounts(2, 1, 0));
        assertThat(response.hiddenCount()).isEqualTo(3);
        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.clinicalAccess()).isTrue();
    }

    @Test
    void withoutClinicalAccessClinicalRowsAreNotQueriedButCounted() {
        when(repository.animalVisible(animal)).thenReturn(true);
        when(repository.visibleCounts(animal, false)).thenReturn(new HiddenRecordCounts(0, 0, 1));
        when(repository.totalHistoryCounts(animal)).thenReturn(new HiddenRecordCounts(2, 1, 1));
        when(repository.history(eq(animal), eq(false), anyInt(), anyLong())).thenReturn(List.of());

        PatientHistoryResponse response = service.history(animal, false, PageRequest.of(0, 20));

        verify(repository, never()).history(eq(animal), eq(true), anyInt(), anyLong());
        assertThat(response.hiddenByType()).isEqualTo(new HiddenRecordCounts(2, 1, 0));
        assertThat(response.clinicalAccess()).isFalse();
    }

    @Test
    void hiddenNeverGoesNegative_andInvisibleAnimalIs404() {
        assertThat(PatientHistoryService.hidden(new HiddenRecordCounts(0, 0, 0), new HiddenRecordCounts(1, 1, 1)))
                .isEqualTo(new HiddenRecordCounts(0, 0, 0));
        when(repository.animalVisible(animal)).thenReturn(false);
        assertThatThrownBy(() -> service.history(animal, true, PageRequest.of(0, 20)))
                .isInstanceOf(EntityNotFoundException.class);
        verify(repository, never()).totalHistoryCounts(animal);
    }
}

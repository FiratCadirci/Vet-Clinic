package org.codeflow.veterineryproject.appointment;

import org.codeflow.veterineryproject.appointment.repository.AppointmentJdbcRepository;
import org.codeflow.veterineryproject.appointment.repository.AppointmentJdbcRepository.Filter;
import org.codeflow.veterineryproject.appointment.service.AppointmentService;
import org.codeflow.veterineryproject.common.audit.AuditRecorder;
import org.codeflow.veterineryproject.common.sequence.SequenceCounterRepository;
import org.codeflow.veterineryproject.encounter.repository.AppointmentLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.context.ApplicationEventPublisher;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AppointmentServiceTest {

    private AppointmentJdbcRepository repository;
    private AppointmentService service;

    @BeforeEach
    void setUp() {
        repository = mock(AppointmentJdbcRepository.class);
        service = new AppointmentService(repository, mock(AppointmentLinkRepository.class),
                mock(SequenceCounterRepository.class), mock(AuditRecorder.class), mock(ApplicationEventPublisher.class));
        when(repository.search(any(), any())).thenReturn(List.of());
        when(repository.count(any())).thenReturn(0L);
    }

    @Test
    void listNormalizesMultipleStatusesAndIgnoresBlankFilters() {
        OffsetDateTime from = OffsetDateTime.parse("2032-05-03T00:00:00Z");
        OffsetDateTime to = from.plusDays(1);
        Filter filter = new Filter(from, to, null, null, List.of("BOOKED, CONFIRMED", ""),
                "EXAM", "STAFF", null, UUID.randomUUID());

        service.list(filter, PageRequest.of(0, 20, Sort.by(Sort.Order.asc("startsAt"))));

        verify(repository).search(any(Filter.class), any());
        verify(repository).count(argThat(normalized ->
                normalized.statuses().equals(List.of("BOOKED", "CONFIRMED"))
                        && normalized.type().equals("EXAM")
                        && normalized.bookingChannel().equals("STAFF")));
    }

    @Test
    void listRejectsAnInvalidTimeWindowBeforeQuerying() {
        OffsetDateTime start = OffsetDateTime.parse("2032-05-03T00:00:00Z");
        Filter filter = new Filter(start, start, null, null, List.of(), null, null, null, null);

        assertThatThrownBy(() -> service.list(filter, PageRequest.of(0, 20)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endDateTime");
        verifyNoInteractions(repository);
    }

    @Test
    void listRejectsUnknownAppointmentStatus() {
        Filter filter = new Filter(null, null, null, null, List.of("NOT_A_STATUS"),
                null, null, null, null);

        assertThatThrownBy(() -> service.list(filter, PageRequest.of(0, 20)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Geçersiz randevu durumu");
        verifyNoInteractions(repository);
    }
}

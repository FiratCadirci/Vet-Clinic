package org.codeflow.veterineryproject.animal.service;

import org.codeflow.veterineryproject.animal.dto.AnimalOwnerRequest;
import org.codeflow.veterineryproject.animal.dto.AnimalResponse;
import org.codeflow.veterineryproject.animal.dto.CreateAnimalRequest;
import org.codeflow.veterineryproject.animal.dto.DeceasedRequest;
import org.codeflow.veterineryproject.animal.repository.AnimalJdbcRepository;
import org.codeflow.veterineryproject.common.api.BusinessRuleException;
import org.codeflow.veterineryproject.common.api.ConflictException;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnimalServiceTest {

    private final UUID org = UUID.randomUUID();
    private final UUID species = UUID.randomUUID();
    private final UUID client = UUID.randomUUID();
    private final AnimalJdbcRepository repository = mock(AnimalJdbcRepository.class);
    private final AnimalService service = new AnimalService(repository);

    @BeforeEach
    void setUp() {
        TenantContext.set(org, UUID.randomUUID(), UUID.randomUUID());
        when(repository.speciesActive(species)).thenReturn(true);
        when(repository.clientActive(any())).thenReturn(Optional.of(true));
        when(repository.nextPatientSequence(org)).thenReturn(123L);
        when(repository.findById(any())).thenAnswer(i -> Optional.of(animal(i.getArgument(0), null)));
        when(repository.findOwners(any())).thenReturn(java.util.Map.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void create_formatsPatientNumberFromCounter() {
        service.create(request(null, List.of(owner(client, true))));

        verify(repository).insert(any(), eq(org), eq("P-000123"), any());
        verify(repository).insertOwner(eq(org), any(), eq(client), eq("OWNER"), eq(true), eq(true));
    }

    @Test
    void create_requiresExactlyOnePrimaryContact() {
        assertThatThrownBy(() -> service.create(request(null, List.of(owner(client, false)))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(request(null, List.of(owner(client, true), owner(UUID.randomUUID(), true)))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).insert(any(), any(), any(), any());
    }

    @Test
    void create_breedMustBelongToSpecies() {
        UUID breed = UUID.randomUUID();
        when(repository.breedBelongsToSpecies(breed, species)).thenReturn(false);

        assertThatThrownBy(() -> service.create(request(breed, List.of(owner(client, true)))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void create_inactiveClientIsRejected() {
        when(repository.clientActive(client)).thenReturn(Optional.of(false));

        assertThatThrownBy(() -> service.create(request(null, List.of(owner(client, true)))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void markDeceased_rejectsFutureAndRepeatedDates() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> service.markDeceased(id, new DeceasedRequest(LocalDate.now().plusDays(1))))
                .isInstanceOf(IllegalArgumentException.class);

        when(repository.findById(id)).thenReturn(Optional.of(animal(id, LocalDate.now().minusDays(1))));
        assertThatThrownBy(() -> service.markDeceased(id, new DeceasedRequest(LocalDate.now())))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).markDeceased(any(), any());
        assertThat(true).isTrue();
    }

    private CreateAnimalRequest request(UUID breed, List<AnimalOwnerRequest> owners) {
        return new CreateAnimalRequest("Pamuk", species, breed, null, null, null, null, null, null, null, null, owners);
    }

    private AnimalOwnerRequest owner(UUID clientId, boolean primary) {
        return new AnimalOwnerRequest(clientId, null, primary, null);
    }

    private AnimalResponse animal(UUID id, LocalDate deceasedAt) {
        return new AnimalResponse(id, "P-000001", "Pamuk", species, "Köpek", null, null, "UNKNOWN", null, false, null, null,
                false, null, deceasedAt, null, null, null, List.of());
    }
}

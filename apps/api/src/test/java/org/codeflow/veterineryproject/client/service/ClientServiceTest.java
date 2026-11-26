package org.codeflow.veterineryproject.client.service;

import org.codeflow.veterineryproject.client.dto.ClientAnimalSummary;
import org.codeflow.veterineryproject.client.dto.DuplicateClientMatch;
import org.codeflow.veterineryproject.client.dto.ClientContactRequest;
import org.codeflow.veterineryproject.client.dto.ClientResponse;
import org.codeflow.veterineryproject.client.dto.CreateClientRequest;
import org.codeflow.veterineryproject.client.repository.ClientJdbcRepository;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

class ClientServiceTest {

    private final UUID orgId = UUID.randomUUID();
    private final ClientJdbcRepository repository = mock(ClientJdbcRepository.class);
    private final ClientService service = new ClientService(repository);

    @BeforeEach
    void setUp() {
        TenantContext.set(orgId, UUID.randomUUID(), UUID.randomUUID());
        when(repository.findById(any())).thenAnswer(i -> Optional.of(stub(i.getArgument(0))));
        when(repository.findDuplicates(any(), any(), any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void create_personRequiresFirstAndLastName() {
        assertThatThrownBy(() -> service.create(request("PERSON", "Ayşe", null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).insert(any(), any(), any());
    }

    @Test
    void create_businessRequiresCompanyName() {
        assertThatThrownBy(() -> service.create(request("BUSINESS", null, null, " ", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_normalizesPhoneAndEmail() {
        service.create(request("PERSON", "Ayşe", "Yılmaz", null, new ClientContactRequest("+90 (532) 111-22.33", " A@B.COM ")));

        verify(repository).upsertPrimaryContact(eq(orgId), any(), eq("PHONE"), eq("+905321112233"));
        verify(repository).upsertPrimaryContact(eq(orgId), any(), eq("EMAIL"), eq("a@b.com"));
        verify(repository, org.mockito.Mockito.atLeastOnce()).findDuplicates(any(), eq("+905321112233"), eq("a@b.com"));
    }

    private List<DuplicateClientMatch> oneMatch() {
        return List.of(new DuplicateClientMatch(UUID.randomUUID(), "Ayşe Yılmaz", "+905321112233", "PHONE", 2));
    }

    private CreateClientRequest withConfirm(Boolean confirm) {
        return new CreateClientRequest("PERSON", "Ayşe", "Yılmaz", null, null, null, null, null, null,
                new ClientContactRequest("0532 111 22 33", null), null, confirm);
    }

    @Test
    void create_duplicateWithoutConfirm_throws409AndDoesNotInsert() {
        when(repository.findDuplicates(any(), any(), any())).thenReturn(oneMatch());

        assertThatThrownBy(() -> service.create(withConfirm(null)))
                .isInstanceOf(org.codeflow.veterineryproject.common.api.ConflictException.class);
        verify(repository, never()).insert(any(), any(), any());
    }

    @Test
    void create_duplicateWithConfirm_inserts() {
        when(repository.findDuplicates(any(), any(), any())).thenReturn(oneMatch());

        service.create(withConfirm(true));

        verify(repository).insert(any(), eq(orgId), any());
    }

    @Test
    void list_phoneVariantsNormalizeToSameCore() {
        for (String q : List.of("+90 (532) 111-22-33", "0532 111 22 33", "5321112233", "90 532 111 22 33")) {
            when(repository.search(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyLong()))
                    .thenReturn(List.of());
            service.list(q, null, org.springframework.data.domain.PageRequest.of(0, 20));
        }
        org.mockito.ArgumentCaptor<String> cap = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(repository, org.mockito.Mockito.times(4)).search(any(), cap.capture(), any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyLong());
        assertThat(cap.getAllValues()).containsOnly("5321112233");
    }

    @Test
    void list_attachesAnimalSummaryAndCount() {
        UUID cid = UUID.randomUUID();
        UUID aid = UUID.randomUUID();
        when(repository.search(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(List.of(stub(cid)));
        when(repository.findAnimalSummaries(any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(new ClientJdbcRepository.AnimalRow(cid,
                        new ClientAnimalSummary(aid, "Pamuk", UUID.randomUUID(), "Kedi", false, true), 7, true)));

        var page = service.list("pamuk", null, org.springframework.data.domain.PageRequest.of(0, 20));

        var item = page.content().get(0);
        assertThat(item.animalCount()).isEqualTo(7);
        assertThat(item.animals()).hasSize(1);
        assertThat(item.matchedAnimalIds()).containsExactly(aid);
    }

    @Test
    void create_rejectsMalformedPhone() {
        assertThatThrownBy(() -> service.create(request("PERSON", "A", "B", null, new ClientContactRequest("12ab", null))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_businessDisplayNameIsCompanyName() {
        ClientResponse response = service.create(request("BUSINESS", null, null, "Patili Ltd.", null));

        assertThat(response.displayName()).isEqualTo("Patili Ltd.");
    }

    private CreateClientRequest request(String type, String first, String last, String company, ClientContactRequest contact) {
        return new CreateClientRequest(type, first, last, company, null, null, null, null, null, contact, null, null);
    }

    private ClientResponse stub(UUID id) {
        return new ClientResponse(id, "BUSINESS", "Patili Ltd.", null, null, "Patili Ltd.", null, null, null,
                false, null, true, null, null, null, null, null, null);
    }
}

package org.codeflow.veterineryproject.client.service;

import jakarta.persistence.EntityNotFoundException;
import org.codeflow.veterineryproject.client.dto.ClientContactRequest;
import org.codeflow.veterineryproject.client.dto.ClientListItemResponse;
import org.codeflow.veterineryproject.common.api.ConflictException;
import org.codeflow.veterineryproject.client.dto.ClientResponse;
import org.codeflow.veterineryproject.client.dto.CreateClientRequest;
import org.codeflow.veterineryproject.client.dto.DuplicateClientMatch;
import org.codeflow.veterineryproject.client.dto.UpdateClientRequest;
import org.codeflow.veterineryproject.client.repository.ClientJdbcRepository;
import org.codeflow.veterineryproject.common.pagination.PageResponse;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ClientService {

    private static final int LIST_ANIMAL_CAP = 5;
    private static final String NOT_FOUND = "Hayvan sahibi bulunamadı.";
    private static final Pattern PHONE_FORMAT = Pattern.compile("\\+?[0-9]{7,15}");
    private static final Pattern PHONE_NOISE = Pattern.compile("[\\s\\-().]");

    private final ClientJdbcRepository repository;

    public ClientService(ClientJdbcRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ClientResponse create(CreateClientRequest request) {
        UUID organizationId = TenantContext.require().organizationId();
        String phone = request.contact() == null ? null : normalizePhone(request.contact().phone());
        String email = request.contact() == null ? null : normalizeEmail(request.contact().email());

        String firstName = blankToNull(request.firstName());
        String lastName = blankToNull(request.lastName());
        String companyName = blankToNull(request.companyName());
        boolean person = "PERSON".equals(request.clientType());
        String displayName = displayName(person, firstName, lastName, companyName);

        List<DuplicateClientMatch> candidates = duplicates(UUID.randomUUID(), phone, email);
        if (!candidates.isEmpty() && !Boolean.TRUE.equals(request.confirmDuplicate())) {
            throw new ConflictException("Aynı telefon veya e-posta ile kayıtlı müşteri bulundu.",
                    "DUPLICATE_CLIENT_CANDIDATE", Map.of("possibleDuplicates", candidates));
        }

        ClientResponse values = new ClientResponse(null, request.clientType(), displayName,
                firstName, lastName, person ? null : companyName,
                blankToNull(request.taxOffice()), blankToNull(request.taxNumber()),
                request.preferredChannel(), Boolean.TRUE.equals(request.marketingConsent()),
                blankToNull(request.communicationNotes()), true, null, null, null, null, null, null);

        UUID id = UUID.randomUUID();
        repository.insert(id, organizationId, values);
        if (phone != null) {
            repository.upsertPrimaryContact(organizationId, id, "PHONE", phone);
        }
        if (email != null) {
            repository.upsertPrimaryContact(organizationId, id, "EMAIL", email);
        }
        if (request.address() != null) {
            repository.upsertPrimaryAddress(organizationId, id, request.address());
        }
        return load(id).withPossibleDuplicates(duplicates(id, phone, email));
    }

    @Transactional(readOnly = true)
    public PageResponse<ClientListItemResponse> list(String query, Boolean active, Pageable pageable) {
        String phoneQuery = query == null ? null : normalizeSearchPhone(query);
        List<ClientResponse> clients = repository.search(query, phoneQuery, active, pageable.getPageSize(), pageable.getOffset());
        Map<UUID, List<ClientJdbcRepository.AnimalRow>> byClient = repository
                .findAnimalSummaries(clients.stream().map(ClientResponse::id).toList(), query, LIST_ANIMAL_CAP)
                .stream().collect(Collectors.groupingBy(ClientJdbcRepository.AnimalRow::clientId));
        List<ClientListItemResponse> content = clients.stream().map(c -> {
            List<ClientJdbcRepository.AnimalRow> rows = byClient.getOrDefault(c.id(), List.of());
            return new ClientListItemResponse(c,
                    rows.isEmpty() ? 0 : rows.get(0).total(),
                    rows.stream().map(ClientJdbcRepository.AnimalRow::animal).toList(),
                    rows.stream().filter(ClientJdbcRepository.AnimalRow::matched).map(r -> r.animal().id()).toList());
        }).toList();
        return PageResponse.from(new PageImpl<>(content, pageable, repository.count(query, phoneQuery, active)));
    }

    @Transactional(readOnly = true)
    public ClientResponse get(UUID id) {
        return load(id);
    }

    @Transactional
    public ClientResponse update(UUID id, UpdateClientRequest request) {
        UUID organizationId = TenantContext.require().organizationId();
        ClientResponse current = load(id);
        boolean person = "PERSON".equals(current.clientType());

        String firstName = merge(request.firstName(), current.firstName());
        String lastName = merge(request.lastName(), current.lastName());
        String companyName = merge(request.companyName(), current.companyName());
        String displayName = displayName(person, firstName, lastName, companyName);

        ClientContactRequest contact = request.contact();
        boolean phoneProvided = contact != null && contact.phone() != null;
        boolean emailProvided = contact != null && contact.email() != null;
        String phone = phoneProvided ? normalizePhone(contact.phone()) : null;
        String email = emailProvided ? normalizeEmail(contact.email()) : null;

        repository.update(id, new ClientResponse(id, current.clientType(), displayName, firstName, lastName,
                person ? null : companyName,
                merge(request.taxOffice(), current.taxOffice()), merge(request.taxNumber(), current.taxNumber()),
                request.preferredChannel() != null ? request.preferredChannel() : current.preferredChannel(),
                request.marketingConsent() != null ? request.marketingConsent() : current.marketingConsent(),
                merge(request.communicationNotes(), current.communicationNotes()),
                current.active(), null, null, null, null, null, null));
        if (phoneProvided) {
            repository.upsertPrimaryContact(organizationId, id, "PHONE", phone);
        }
        if (emailProvided) {
            repository.upsertPrimaryContact(organizationId, id, "EMAIL", email);
        }
        if (request.address() != null) {
            repository.upsertPrimaryAddress(organizationId, id, request.address());
        }
        ClientResponse updated = load(id);
        return phone == null && email == null ? updated : updated.withPossibleDuplicates(duplicates(id, phone, email));
    }

    @Transactional
    public ClientResponse deactivate(UUID id) {
        TenantContext.require();
        load(id);
        repository.setActive(id, false);
        return load(id);
    }

    @Transactional
    public ClientResponse activate(UUID id) {
        TenantContext.require();
        load(id);
        repository.setActive(id, true);
        return load(id);
    }

    private ClientResponse load(UUID id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException(NOT_FOUND));
    }

    private List<DuplicateClientMatch> duplicates(UUID id, String phone, String email) {
        return repository.findDuplicates(id, phone, email);
    }

    private String displayName(boolean person, String firstName, String lastName, String companyName) {
        if (person) {
            if (firstName == null || lastName == null) {
                throw new IllegalArgumentException("Bireysel müşteri için ad ve soyad zorunludur.");
            }
            return firstName + " " + lastName;
        }
        if (companyName == null) {
            throw new IllegalArgumentException("Kurumsal müşteri için şirket adı zorunludur.");
        }
        return companyName;
    }

    private String normalizePhone(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String phone = PHONE_NOISE.matcher(raw.trim()).replaceAll("");
        if (!PHONE_FORMAT.matcher(phone).matches()) {
            throw new IllegalArgumentException("Telefon numarası geçersiz.");
        }
        return phone;
    }

    /** Telefon aramasını +90/90/0 öneklerinden ve biçim karakterlerinden arındırıp en az 3 haneli çekirdek rakam dizisi döndürür. */
    private String normalizeSearchPhone(String query) {
        String trimmed = query.trim();
        String digits = PHONE_NOISE.matcher(trimmed).replaceAll("");
        if (!digits.matches("\\+?[0-9]{3,15}")) {
            return null;
        }
        boolean international = digits.startsWith("+");
        digits = digits.replace("+", "");
        if ((international || digits.length() >= 12) && digits.startsWith("90")) {
            digits = digits.substring(2);
        }
        if (digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        return digits.length() >= 3 ? digits : null;
    }

    private String normalizeEmail(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim().toLowerCase(Locale.ROOT);
    }

    private String merge(String incoming, String existing) {
        return incoming == null ? existing : blankToNull(incoming);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

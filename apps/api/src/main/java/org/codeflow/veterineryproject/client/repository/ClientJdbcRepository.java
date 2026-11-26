package org.codeflow.veterineryproject.client.repository;

import org.codeflow.veterineryproject.client.dto.ClientAddressRequest;
import org.codeflow.veterineryproject.client.dto.ClientAddressResponse;
import org.codeflow.veterineryproject.client.dto.ClientAnimalSummary;
import org.codeflow.veterineryproject.client.dto.ClientResponse;
import org.codeflow.veterineryproject.client.dto.DuplicateClientMatch;
import org.codeflow.veterineryproject.common.sql.SqlLike;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tüm sorgular RLS altında çalışır: yalnızca çağıranın klinik grubuna açılmış müşteriler görünür. */
@Repository
public class ClientJdbcRepository {

    private static final String SELECT = """
            select c.id, c.client_type, c.display_name, c.first_name, c.last_name, c.company_name, c.tax_office, c.tax_number,
                   c.preferred_channel, c.marketing_consent, c.communication_notes, c.is_active, c.created_at, c.updated_at,
                   (select cc.contact_value from vet.client_contacts cc
                     where cc.client_id = c.id and cc.contact_type = 'PHONE' and cc.is_primary) as phone,
                   (select cc.contact_value from vet.client_contacts cc
                     where cc.client_id = c.id and cc.contact_type = 'EMAIL' and cc.is_primary) as email,
                   a.address_line, a.city, a.district, a.neighborhood, a.postal_code, a.country_code
            from vet.clients c
            left join vet.client_addresses a on a.client_id = c.id and a.is_primary
            """;

    private static final RowMapper<ClientResponse> MAPPER = (rs, i) -> {
        String addressLine = rs.getString("address_line");
        ClientAddressResponse address = addressLine == null ? null : new ClientAddressResponse(
                addressLine, rs.getString("city"), rs.getString("district"), rs.getString("neighborhood"),
                rs.getString("postal_code"), rs.getString("country_code"));
        return new ClientResponse(
                rs.getObject("id", UUID.class),
                rs.getString("client_type"),
                rs.getString("display_name"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("company_name"),
                rs.getString("tax_office"),
                rs.getString("tax_number"),
                rs.getString("preferred_channel"),
                rs.getBoolean("marketing_consent"),
                rs.getString("communication_notes"),
                rs.getBoolean("is_active"),
                rs.getString("phone"),
                rs.getString("email"),
                address,
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                null
        );
    };

    private final JdbcTemplate jdbc;

    public ClientJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, UUID organizationId, ClientResponse values) {
        jdbc.update("""
                insert into vet.clients (id, organization_id, client_type, display_name, first_name, last_name, company_name,
                                         tax_office, tax_number, preferred_channel, marketing_consent, communication_notes)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, organizationId, values.clientType(), values.displayName(), values.firstName(), values.lastName(),
                values.companyName(), values.taxOffice(), values.taxNumber(), values.preferredChannel(),
                values.marketingConsent(), values.communicationNotes());
    }

    public void update(UUID id, ClientResponse values) {
        jdbc.update("""
                update vet.clients
                set display_name = ?, first_name = ?, last_name = ?, company_name = ?, tax_office = ?, tax_number = ?,
                    preferred_channel = ?, marketing_consent = ?, communication_notes = ?, updated_at = now()
                where id = ?
                """, values.displayName(), values.firstName(), values.lastName(), values.companyName(), values.taxOffice(),
                values.taxNumber(), values.preferredChannel(), values.marketingConsent(), values.communicationNotes(), id);
    }

    public int setActive(UUID id, boolean active) {
        return jdbc.update("update vet.clients set is_active = ?, updated_at = now() where id = ?", active, id);
    }

    public Optional<ClientResponse> findById(UUID id) {
        return jdbc.query(SELECT + " where c.id = ?", MAPPER, id).stream().findFirst();
    }

    public List<ClientResponse> search(String query, String phoneQuery, Boolean active, int limit, long offset) {
        List<Object> args = new ArrayList<>();
        String where = searchWhere(query, phoneQuery, active, args);
        args.add(limit);
        args.add(offset);
        return jdbc.query(SELECT + where + " order by lower(c.display_name), c.id limit ? offset ?", MAPPER, args.toArray());
    }

    public long count(String query, String phoneQuery, Boolean active) {
        List<Object> args = new ArrayList<>();
        String where = searchWhere(query, phoneQuery, active, args);
        Long total = jdbc.queryForObject("select count(*) from vet.clients c" + where, Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    /** Birincil iletişim satırını ekler/günceller; değer null ise satır silinir. */
    public void upsertPrimaryContact(UUID organizationId, UUID clientId, String contactType, String value) {
        if (value == null) {
            jdbc.update("delete from vet.client_contacts where client_id = ? and contact_type = ? and is_primary",
                    clientId, contactType);
            return;
        }
        int updated = jdbc.update("""
                update vet.client_contacts set contact_value = ?, is_verified = false, verified_at = null
                where client_id = ? and contact_type = ? and is_primary
                """, value, clientId, contactType);
        if (updated == 0) {
            jdbc.update("""
                    insert into vet.client_contacts (organization_id, client_id, contact_type, contact_value, is_primary)
                    values (?, ?, ?, ?, true)
                    """, organizationId, clientId, contactType, value);
        }
    }

    public void upsertPrimaryAddress(UUID organizationId, UUID clientId, ClientAddressRequest address) {
        String country = address.countryCode() == null ? "TR" : address.countryCode().toUpperCase();
        int updated = jdbc.update("""
                update vet.client_addresses
                set address_line = ?, city = ?, district = ?, neighborhood = ?, postal_code = ?, country_code = ?
                where client_id = ? and is_primary
                """, address.addressLine().trim(), address.city(), address.district(), address.neighborhood(),
                address.postalCode(), country, clientId);
        if (updated == 0) {
            jdbc.update("""
                    insert into vet.client_addresses (organization_id, client_id, address_type, country_code, city, district,
                                                      neighborhood, postal_code, address_line, is_primary)
                    values (?, ?, 'HOME', ?, ?, ?, ?, ?, ?, true)
                    """, organizationId, clientId, country, address.city(), address.district(), address.neighborhood(),
                    address.postalCode(), address.addressLine().trim());
        }
    }

    public List<DuplicateClientMatch> findDuplicates(UUID excludeClientId, String phone, String email) {
        if (phone == null && email == null) {
            return List.of();
        }
        String phoneTail = phone == null ? null : tail10(phone);
        return jdbc.query("""
                select c.id, c.display_name,
                       string_agg(distinct cc.contact_type, ',' order by cc.contact_type) as matched_on,
                       (select p.contact_value from vet.client_contacts p
                         where p.client_id = c.id and p.contact_type = 'PHONE' and p.is_primary) as phone,
                       (select count(*) from vet.animal_owners ao
                         where ao.client_id = c.id and ao.valid_to is null) as animal_count
                from vet.client_contacts cc
                join vet.clients c on c.organization_id = cc.organization_id and c.id = cc.client_id
                where cc.client_id <> ?
                  and ((cast(? as text) is not null and cc.contact_type = 'PHONE'
                        and right(regexp_replace(cc.contact_value, '[^0-9]', '', 'g'), 10) = ?)
                    or (cast(? as text) is not null and cc.contact_type = 'EMAIL' and lower(cc.contact_value) = ?))
                group by c.id, c.display_name
                order by lower(c.display_name), c.id
                """, (rs, i) -> new DuplicateClientMatch(
                        rs.getObject("id", UUID.class), rs.getString("display_name"), rs.getString("phone"),
                        rs.getString("matched_on"), rs.getLong("animal_count")),
                excludeClientId, phoneTail, phoneTail, email, email);
    }

    private static String tail10(String phone) {
        String digits = phone.replaceAll("[^0-9]", "");
        return digits.length() > 10 ? digits.substring(digits.length() - 10) : digits;
    }

    /** Verilen müşterilerin güncel hayvanları (tek sorgu); her müşteri için en çok {@code cap} satır, eşleşenler önde. */
    public List<AnimalRow> findAnimalSummaries(List<UUID> clientIds, String query, int cap) {
        if (clientIds.isEmpty()) {
            return List.of();
        }
        boolean hasQuery = query != null && !query.isBlank();
        String like = hasQuery ? SqlLike.contains(query) : null;
        String matched = hasQuery
                ? "(lower(a.name) like ? escape '\\' or exists (select 1 from vet.animal_identifiers ai"
                        + " where ai.animal_id = a.id and lower(ai.identifier_value) like ? escape '\\'))"
                : "false";
        List<Object> args = new ArrayList<>();
        if (hasQuery) {
            args.add(like);
            args.add(like);
        }
        args.add(clientIds.toArray(UUID[]::new));
        args.add(cap);
        return jdbc.query("""
                select * from (
                  select x.*, count(*) over (partition by x.client_id) as total,
                         row_number() over (partition by x.client_id order by x.matched desc, lower(x.name), x.id) as rn
                  from (
                    select ao.client_id, a.id, a.name, a.species_id, s.name as species_name,
                           a.deceased_at is not null as deceased, ao.is_primary_contact, %s as matched
                    from vet.animal_owners ao
                    join vet.animals a on a.organization_id = ao.organization_id and a.id = ao.animal_id
                    join vet.species s on s.id = a.species_id
                    where ao.valid_to is null and ao.client_id = any (?)
                  ) x
                ) y where y.rn <= ?
                order by y.client_id, y.rn
                """.formatted(matched), (rs, i) -> new AnimalRow(
                        rs.getObject("client_id", UUID.class),
                        new ClientAnimalSummary(rs.getObject("id", UUID.class), rs.getString("name"),
                                rs.getObject("species_id", UUID.class), rs.getString("species_name"),
                                rs.getBoolean("deceased"), rs.getBoolean("is_primary_contact")),
                        rs.getLong("total"), rs.getBoolean("matched")),
                args.toArray());
    }

    /** Listede bir müşteri için döndürülen en fazla hayvan sayısı (matched olanlar öncelikli). */
    public record AnimalRow(UUID clientId, ClientAnimalSummary animal, long total, boolean matched) {
    }

    private String searchWhere(String query, String phoneQuery, Boolean active, List<Object> args) {
        StringBuilder where = new StringBuilder(" where true");
        if (query != null && !query.isBlank()) {
            String like = SqlLike.contains(query);
            where.append(" and (lower(c.display_name) like ? escape '\\'")
                    .append(" or lower(coalesce(c.first_name, '')) like ? escape '\\'")
                    .append(" or lower(coalesce(c.last_name, '')) like ? escape '\\'")
                    .append(" or lower(coalesce(c.company_name, '')) like ? escape '\\'")
                    .append(" or exists (select 1 from vet.client_contacts ce where ce.client_id = c.id")
                    .append(" and ce.contact_type = 'EMAIL' and lower(ce.contact_value) like ? escape '\\')")
                    .append(" or exists (select 1 from vet.animal_owners ao join vet.animals an")
                    .append(" on an.organization_id = ao.organization_id and an.id = ao.animal_id")
                    .append(" where ao.client_id = c.id and ao.valid_to is null and (lower(an.name) like ? escape '\\'")
                    .append(" or exists (select 1 from vet.animal_identifiers ai where ai.animal_id = an.id")
                    .append(" and lower(ai.identifier_value) like ? escape '\\')))");
            for (int i = 0; i < 7; i++) {
                args.add(like);
            }
            if (phoneQuery != null) {
                where.append(" or exists (select 1 from vet.client_contacts cc where cc.client_id = c.id")
                        .append(" and cc.contact_type = 'PHONE'")
                        .append(" and regexp_replace(cc.contact_value, '[^0-9]', '', 'g') like ?)");
                args.add("%" + phoneQuery + "%");
            }
            where.append(")");
        }
        if (active != null) {
            where.append(" and c.is_active = ?");
            args.add(active);
        }
        return where.toString();
    }
}

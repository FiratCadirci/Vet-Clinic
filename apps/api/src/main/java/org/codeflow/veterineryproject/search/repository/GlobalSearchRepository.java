package org.codeflow.veterineryproject.search.repository;

import org.codeflow.veterineryproject.common.sql.SqlLike;
import org.codeflow.veterineryproject.search.dto.GlobalSearchResponse.AnimalHit;
import org.codeflow.veterineryproject.search.dto.GlobalSearchResponse.ClientHit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** RLS altında çalışır: yalnızca çağıranın görebildiği müşteri ve hastalar döner. */
@Repository
public class GlobalSearchRepository {

    private final JdbcTemplate jdbc;

    public GlobalSearchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ClientHit> clients(String query, String phoneQuery, int limit) {
        String like = SqlLike.contains(query);
        String phoneLike = SqlLike.contains(phoneQuery == null ? "" : phoneQuery);
        return jdbc.query("""
                select c.id, c.display_name, c.is_active,
                       (select cc.contact_value from vet.client_contacts cc
                        where cc.client_id = c.id and cc.contact_type = 'PHONE'
                        order by cc.is_primary desc, cc.created_at limit 1) as phone
                from vet.clients c
                where lower(c.display_name) like ? escape '\\'
                   or (cast(? as text) is not null and exists (select 1 from vet.client_contacts cc
                        where cc.client_id = c.id and cc.contact_type = 'PHONE' and cc.contact_value like ? escape '\\'))
                order by lower(c.display_name), c.id
                limit ?
                """, (rs, i) -> new ClientHit(rs.getObject("id", UUID.class),
                rs.getString("display_name"), rs.getString("phone"), rs.getBoolean("is_active")),
                like, phoneQuery, phoneLike, limit);
    }

    public List<AnimalHit> animals(String query, String phoneQuery, int limit) {
        String like = SqlLike.contains(query);
        String phoneLike = SqlLike.contains(phoneQuery == null ? "" : phoneQuery);
        return jdbc.query("""
                select a.id, a.patient_no, a.name, s.name as species_name,
                       (select ai.identifier_value from vet.animal_identifiers ai
                        where ai.animal_id = a.id and ai.identifier_type = 'MICROCHIP' limit 1) as chip_no,
                       o.client_id as owner_id, c.display_name as owner_name,
                       (select cc.contact_value from vet.client_contacts cc
                        where cc.client_id = o.client_id and cc.contact_type = 'PHONE'
                        order by cc.is_primary desc, cc.created_at limit 1) as owner_phone
                from vet.animals a
                join vet.species s on s.id = a.species_id
                left join lateral (select ao.client_id from vet.animal_owners ao
                                   where ao.animal_id = a.id and ao.valid_to is null
                                   order by ao.is_primary_contact desc, ao.created_at limit 1) o on true
                left join vet.clients c on c.id = o.client_id
                where lower(a.name) like ? escape '\\'
                   or lower(a.patient_no) like ? escape '\\'
                   or exists (select 1 from vet.animal_identifiers ai
                              where ai.animal_id = a.id and lower(ai.identifier_value) like ? escape '\\')
                   or lower(c.display_name) like ? escape '\\'
                   or (cast(? as text) is not null and exists (select 1 from vet.client_contacts cc
                        where cc.client_id = o.client_id and cc.contact_type = 'PHONE' and cc.contact_value like ? escape '\\'))
                order by lower(a.name), a.id
                limit ?
                """, (rs, i) -> new AnimalHit(rs.getObject("id", UUID.class), rs.getString("patient_no"),
                rs.getString("name"), rs.getString("species_name"), rs.getString("chip_no"),
                rs.getObject("owner_id", UUID.class), rs.getString("owner_name"), rs.getString("owner_phone")),
                like, like, like, like, phoneQuery, phoneLike, limit);
    }
}

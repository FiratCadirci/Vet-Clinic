package org.codeflow.veterineryproject.encounter.repository;

import org.codeflow.veterineryproject.encounter.dto.AttachmentResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AttachmentRepository {

    public record Stored(AttachmentResponse meta, String objectKey) {
    }

    private static final String COLUMNS = """
            id, encounter_id, clinic_id, document_type, title, original_filename, mime_type, size_bytes, uploaded_by,
            created_at, object_key
            """;

    private static final RowMapper<Stored> MAPPER = (rs, i) -> new Stored(new AttachmentResponse(
            rs.getObject("id", UUID.class), rs.getObject("encounter_id", UUID.class), rs.getObject("clinic_id", UUID.class),
            rs.getString("document_type"), rs.getString("title"), rs.getString("original_filename"),
            rs.getString("mime_type"), rs.getObject("size_bytes", Long.class), rs.getObject("uploaded_by", UUID.class),
            rs.getObject("created_at", OffsetDateTime.class)), rs.getString("object_key"));

    private final JdbcTemplate jdbc;

    public AttachmentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, UUID organizationId, UUID clinicId, UUID animalId, UUID encounterId, String documentType,
                       String title, String provider, String objectKey, String filename, String mimeType, long size,
                       String sha256, UUID uploadedBy) {
        jdbc.update("""
                insert into vet.clinical_documents (id, organization_id, clinic_id, animal_id, encounter_id, document_type, title,
                    storage_provider, object_key, original_filename, mime_type, size_bytes, sha256_checksum, uploaded_by)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, organizationId, clinicId, animalId, encounterId, documentType, title, provider, objectKey,
                filename, mimeType, size, sha256, uploadedBy);
    }

    public Optional<Stored> findById(UUID id) {
        return jdbc.query("select " + COLUMNS + " from vet.clinical_documents where id = ? and encounter_id is not null", MAPPER, id)
                .stream().findFirst();
    }

    public List<Stored> findByEncounter(UUID encounterId) {
        return jdbc.query("select " + COLUMNS + " from vet.clinical_documents where encounter_id = ? order by created_at, id",
                MAPPER, encounterId);
    }

    public void delete(UUID id) {
        jdbc.update("delete from vet.clinical_documents where id = ?", id);
    }
}

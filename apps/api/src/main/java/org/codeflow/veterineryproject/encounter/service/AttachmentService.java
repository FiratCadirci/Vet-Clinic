package org.codeflow.veterineryproject.encounter.service;

import jakarta.persistence.EntityNotFoundException;
import org.codeflow.veterineryproject.common.api.BusinessRuleException;
import org.codeflow.veterineryproject.common.api.ConflictException;
import org.codeflow.veterineryproject.common.audit.AuditRecorder;
import org.codeflow.veterineryproject.common.storage.FileStorage;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.encounter.dto.AttachmentResponse;
import org.codeflow.veterineryproject.encounter.dto.EncounterResponse;
import org.codeflow.veterineryproject.encounter.dto.EncounterStatus;
import org.codeflow.veterineryproject.encounter.repository.AttachmentRepository;
import org.codeflow.veterineryproject.encounter.repository.AttachmentRepository.Stored;
import org.codeflow.veterineryproject.user.service.VeterinarianService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AttachmentService {

    public record Download(AttachmentResponse meta, byte[] content) {
    }

    private static final Set<String> DOCUMENT_TYPES = Set.of("CONSENT", "PRESCRIPTION", "LAB_REPORT", "IMAGING_REPORT",
            "VACCINE_CERTIFICATE", "PHOTO", "REFERRAL", "OTHER");
    private static final Map<String, String> EXTENSION_TO_MIME = Map.of(
            "pdf", "application/pdf", "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png");

    private final AttachmentRepository repository;
    private final EncounterService encounterService;
    private final VeterinarianService veterinarians;
    private final FileStorage storage;
    private final AuditRecorder audit;
    private final long maxBytes;

    public AttachmentService(AttachmentRepository repository, EncounterService encounterService, VeterinarianService veterinarians,
                             FileStorage storage, AuditRecorder audit,
                             @Value("${app.storage.max-attachment-bytes:10485760}") long maxBytes) {
        this.repository = repository;
        this.encounterService = encounterService;
        this.veterinarians = veterinarians;
        this.storage = storage;
        this.audit = audit;
        this.maxBytes = maxBytes;
    }

    @Transactional
    public AttachmentResponse upload(UUID encounterId, MultipartFile file, String documentType, String title) {
        TenantContext.ContextValues ctx = TenantContext.require();
        EncounterResponse encounter = encounterService.loadForWrite(encounterId);
        if (encounter.status() == EncounterStatus.CLOSED) {
            throw new ConflictException("Kapatılmış muayeneye ek dosya eklenemez; addendum kullanın.");
        }
        veterinarians.requireCurrentVeterinarianId();

        String type = documentType == null || documentType.isBlank() ? "OTHER" : documentType.trim().toUpperCase(Locale.ROOT);
        if (!DOCUMENT_TYPES.contains(type)) {
            throw new BusinessRuleException("Geçersiz doküman türü.");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Dosya boş olamaz.");
        }
        if (file.getSize() > maxBytes) {
            throw new BusinessRuleException("Dosya boyutu en fazla %d MB olabilir.".formatted(maxBytes / (1024 * 1024)));
        }
        String filename = sanitize(file.getOriginalFilename());
        String extension = extension(filename);
        String mime = EXTENSION_TO_MIME.get(extension);
        if (mime == null) {
            throw new BusinessRuleException("Desteklenmeyen dosya türü; yalnızca PDF, JPEG ve PNG kabul edilir.");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        if (!matchesSignature(mime, content)) {
            throw new BusinessRuleException("Dosya içeriği uzantısıyla uyuşmuyor.");
        }

        UUID id = UUID.randomUUID();
        String key = "%s/encounters/%s/%s.%s".formatted(ctx.organizationId(), encounterId, id, extension);
        try {
            storage.store(key, new ByteArrayInputStream(content));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        String resolvedTitle = title == null || title.isBlank() ? filename : title.trim();
        repository.insert(id, ctx.organizationId(), encounter.clinicId(), encounter.animalId(), encounterId, type,
                resolvedTitle, storage.provider(), key, filename, mime, content.length, sha256(content), ctx.userId());
        return repository.findById(id).orElseThrow().meta();
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> list(UUID encounterId) {
        encounterService.get(encounterId);
        return repository.findByEncounter(encounterId).stream().map(Stored::meta).toList();
    }

    @Transactional(readOnly = true)
    public Download download(UUID id) {
        Stored stored = repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Ek dosya bulunamadı."));
        try (var in = storage.open(stored.objectKey())) {
            return new Download(stored.meta(), in.readAllBytes());
        } catch (IOException ex) {
            throw new EntityNotFoundException("Ek dosya bulunamadı.");
        }
    }

    @Transactional
    public void delete(UUID id) {
        Stored stored = repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Ek dosya bulunamadı."));
        EncounterResponse encounter = encounterService.loadForWrite(stored.meta().encounterId());
        if (encounter.status() == EncounterStatus.CLOSED) {
            throw new ConflictException("Kapatılmış muayenenin ek dosyası silinemez.");
        }
        repository.delete(id);
        audit.record("ATTACHMENT_DELETE", "CLINICAL_DOCUMENT", id, encounter.clinicId());
        try {
            storage.delete(stored.objectKey());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static String sanitize(String name) {
        String value = name == null ? "file" : name.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        return value.isEmpty() ? "file" : value;
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean matchesSignature(String mime, byte[] c) {
        return switch (mime) {
            case "application/pdf" -> c.length > 4 && c[0] == '%' && c[1] == 'P' && c[2] == 'D' && c[3] == 'F';
            case "image/jpeg" -> c.length > 3 && (c[0] & 0xFF) == 0xFF && (c[1] & 0xFF) == 0xD8 && (c[2] & 0xFF) == 0xFF;
            case "image/png" -> c.length > 8 && (c[0] & 0xFF) == 0x89 && c[1] == 'P' && c[2] == 'N' && c[3] == 'G';
            default -> false;
        };
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}

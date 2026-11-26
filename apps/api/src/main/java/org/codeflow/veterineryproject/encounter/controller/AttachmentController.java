package org.codeflow.veterineryproject.encounter.controller;

import org.codeflow.veterineryproject.auth.security.Permission;
import org.codeflow.veterineryproject.encounter.dto.AttachmentResponse;
import org.codeflow.veterineryproject.encounter.service.AttachmentService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AttachmentController {

    private static final String READ = "hasAuthority('" + Permission.CLINICAL_READ + "')";
    private static final String WRITE = "hasAuthority('" + Permission.CLINICAL_WRITE + "')";
    private static final String DELETE = "hasAuthority('" + Permission.CLINICAL_DELETE + "')";

    private final AttachmentService service;

    public AttachmentController(AttachmentService service) {
        this.service = service;
    }

    @PostMapping(value = "/encounters/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    public AttachmentResponse upload(
            @RequestHeader("X-Clinic-Id") String clinicId,
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) String title
    ) {
        return service.upload(id, file, documentType, title);
    }

    @GetMapping("/encounters/{id}/attachments")
    @PreAuthorize(READ)
    public List<AttachmentResponse> list(@PathVariable UUID id) {
        return service.list(id);
    }

    @GetMapping("/attachments/{id}/content")
    @PreAuthorize(READ)
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        AttachmentService.Download download = service.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.meta().mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.meta().originalFilename()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(download.content());
    }

    @DeleteMapping("/attachments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(DELETE)
    public void delete(@RequestHeader("X-Clinic-Id") String clinicId, @PathVariable UUID id) {
        service.delete(id);
    }
}

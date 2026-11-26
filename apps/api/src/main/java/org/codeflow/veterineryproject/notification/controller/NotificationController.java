package org.codeflow.veterineryproject.notification.controller;

import jakarta.validation.Valid;
import org.codeflow.veterineryproject.auth.security.Permission;
import org.codeflow.veterineryproject.common.pagination.PageResponse;
import org.codeflow.veterineryproject.notification.dto.DeliveryResponse;
import org.codeflow.veterineryproject.notification.dto.DeliverySummaryRow;
import org.codeflow.veterineryproject.notification.dto.MarkAllReadResponse;
import org.codeflow.veterineryproject.notification.dto.NotificationResponse;
import org.codeflow.veterineryproject.notification.dto.PreferenceResponse;
import org.codeflow.veterineryproject.notification.dto.ReminderRunResponse;
import org.codeflow.veterineryproject.notification.dto.UnreadCountResponse;
import org.codeflow.veterineryproject.notification.dto.UpdatePreferencesRequest;
import org.codeflow.veterineryproject.notification.service.DeliveryLogService;
import org.codeflow.veterineryproject.notification.service.NotificationInboxService;
import org.codeflow.veterineryproject.notification.service.NotificationPreferenceService;
import org.codeflow.veterineryproject.notification.service.VaccineReminderJob;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Bildirimler: oturumdaki kullanıcının gelen kutusu ve tercihleri (her oturum), gönderim kayıtları (audit.read)
 * ve demo için hatırlatma görevini elle çalıştırma (users.manage). Kullanıcıya özel uçlar şubeden bağımsızdır.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private static final String AUDIT_READ = "hasAuthority('" + Permission.AUDIT_READ + "')";
    private static final String ADMIN = "hasAuthority('" + Permission.USERS_MANAGE + "')";

    private final NotificationInboxService inbox;
    private final NotificationPreferenceService preferences;
    private final DeliveryLogService deliveries;
    private final VaccineReminderJob reminders;

    public NotificationController(NotificationInboxService inbox, NotificationPreferenceService preferences,
                                  DeliveryLogService deliveries, VaccineReminderJob reminders) {
        this.inbox = inbox;
        this.preferences = preferences;
        this.deliveries = deliveries;
        this.reminders = reminders;
    }

    @GetMapping
    public PageResponse<NotificationResponse> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return inbox.list(unreadOnly, page, size);
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount() {
        return inbox.unreadCount();
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable UUID id) {
        return inbox.markRead(id);
    }

    @PostMapping("/read-all")
    public MarkAllReadResponse markAllRead() {
        return inbox.markAllRead();
    }

    @GetMapping("/preferences")
    public List<PreferenceResponse> preferences() {
        return preferences.list();
    }

    @PutMapping("/preferences")
    public List<PreferenceResponse> updatePreferences(@Valid @RequestBody UpdatePreferencesRequest request) {
        return preferences.update(request);
    }

    @GetMapping("/deliveries")
    @PreAuthorize(AUDIT_READ)
    public PageResponse<DeliveryResponse> deliveries(@RequestParam(required = false) String channel,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(required = false) String type,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return deliveries.list(new DeliveryLogService.Query(channel, status, type, from, to), page, size);
    }

    @GetMapping("/deliveries/summary")
    @PreAuthorize(AUDIT_READ)
    public List<DeliverySummaryRow> deliverySummary(@RequestParam(required = false) String channel,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) String type,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return deliveries.summary(new DeliveryLogService.Query(channel, status, type, from, to));
    }

    @PostMapping("/run-vaccine-reminders")
    @PreAuthorize(ADMIN)
    public ReminderRunResponse runVaccineReminders() {
        return reminders.runForCurrentOrganization();
    }
}

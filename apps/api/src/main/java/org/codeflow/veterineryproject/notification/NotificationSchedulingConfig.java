package org.codeflow.veterineryproject.notification;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Zamanlanmış görevler (aşı hatırlatma). Kapanışta zamanlayıcının çalışan görevi bitirmesi
 * spring.task.scheduling.shutdown ayarlarıyla beklenir (application.yml).
 */
@Configuration
@EnableScheduling
public class NotificationSchedulingConfig {
}

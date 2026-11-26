package org.codeflow.veterineryproject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class VeterineryProjectApplication {

    /** Klinik saati: "bugün" ve gün sınırları sunucunun (Docker/CI'da UTC) değil İstanbul'un saatine göre hesaplanır. */
    public static final String CLINIC_TIME_ZONE = "Europe/Istanbul";

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone(CLINIC_TIME_ZONE));
        SpringApplication.run(VeterineryProjectApplication.class, args);
    }

}

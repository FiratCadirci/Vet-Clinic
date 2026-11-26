package org.codeflow.veterineryproject.notification.push;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ADAPTER: Expo Push HTTP API'sini ({@code POST https://exp.host/--/api/v2/push/send}) uygulamanın
 * {@link PushGateway} arayüzüne uyarlar. Yanıttaki iki hata biçimini ayrıştırır: istek düzeyinde
 * {@code errors[]} (tümü başarısız → istisna) ve mesaj düzeyinde {@code status: "error"} ticket'ı.
 */
public class ExpoPushGateway implements PushGateway {

    public static final String DEFAULT_URL = "https://exp.host/--/api/v2/push/send";

    private final RestClient http;
    private final String url;
    private final String accessToken;

    /**
     * @param accessToken Expo hesabında "enhanced push security" açıksa gereken erişim belirteci (boş olabilir)
     */
    public ExpoPushGateway(RestClient http, String url, String accessToken) {
        this.http = http;
        this.url = url == null || url.isBlank() ? DEFAULT_URL : url;
        this.accessToken = accessToken == null || accessToken.isBlank() ? null : accessToken;
    }

    @Override
    public List<PushTicket> send(List<PushMessage> messages) {
        if (messages.isEmpty()) {
            return List.of();
        }
        if (messages.size() > BATCH_LIMIT) {
            throw new IllegalArgumentException("Expo tek istekte en fazla " + BATCH_LIMIT + " mesaj kabul eder.");
        }
        ExpoResponse response;
        try {
            response = http.post().uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(h -> {
                        if (accessToken != null) {
                            h.setBearerAuth(accessToken);
                        }
                    })
                    .body(messages)
                    .retrieve()
                    .body(ExpoResponse.class);
        } catch (RestClientResponseException ex) {
            throw new PushGatewayException("Expo push isteği başarısız (HTTP " + ex.getStatusCode().value() + "): "
                    + ex.getResponseBodyAsString(), ex);
        } catch (RestClientException ex) {
            throw new PushGatewayException("Expo push isteği başarısız: " + ex.getMessage(), ex);
        }
        if (response == null) {
            throw new PushGatewayException("Expo push yanıtı boş.");
        }
        if (response.errors() != null && !response.errors().isEmpty()) {
            String detail = response.errors().stream()
                    .map(e -> (e.code() == null ? "ERROR" : e.code()) + ": " + (e.message() == null ? "" : e.message()))
                    .collect(Collectors.joining("; "));
            throw new PushGatewayException("Expo push isteği reddedildi: " + detail);
        }
        List<ExpoTicket> data = response.data() == null ? List.of() : response.data();
        if (data.size() != messages.size()) {
            throw new PushGatewayException("Expo push yanıtı beklenmedik: " + data.size() + " ticket / "
                    + messages.size() + " mesaj");
        }
        return data.stream().map(ExpoTicket::toTicket).toList();
    }

    /** Expo yanıt gövdesi: {@code {"data": [...], "errors": [...]}}. */
    record ExpoResponse(List<ExpoTicket> data, List<ExpoError> errors) {
    }

    record ExpoTicket(String status, String id, String message, Map<String, Object> details) {
        PushTicket toTicket() {
            if ("ok".equals(status)) {
                return PushTicket.ok(id);
            }
            Object error = details == null ? null : details.get("error");
            return PushTicket.error(message, error == null ? null : error.toString());
        }
    }

    record ExpoError(String code, String message) {
    }
}

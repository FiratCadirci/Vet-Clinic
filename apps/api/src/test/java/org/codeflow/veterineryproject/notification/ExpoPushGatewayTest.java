package org.codeflow.veterineryproject.notification;

import org.codeflow.veterineryproject.notification.push.ExpoPushGateway;
import org.codeflow.veterineryproject.notification.push.PushGatewayException;
import org.codeflow.veterineryproject.notification.push.PushMessage;
import org.codeflow.veterineryproject.notification.push.PushTicket;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Adapter: Expo Push HTTP API yanıtlarının PushTicket'a çevrilmesi (ağ yok, MockRestServiceServer). */
class ExpoPushGatewayTest {

    private static final String URL = ExpoPushGateway.DEFAULT_URL;

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final ExpoPushGateway gateway = new ExpoPushGateway(builder.build(), URL, "expo-secret");

    @Test
    void postsMessagesWithBearerToken_andMapsOkAndErrorTicketsInOrder() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer expo-secret"))
                .andExpect(content().json("[{\"to\":\"ExponentPushToken[a]\",\"title\":\"T\",\"body\":\"B\",\"sound\":\"default\",\"channelId\":\"default\",\"priority\":\"high\"},"
                        + "{\"to\":\"ExponentPushToken[b]\"}]"))
                .andRespond(withSuccess("""
                        {"data":[{"status":"ok","id":"XXXX-1"},
                                 {"status":"error","message":"not registered","details":{"error":"DeviceNotRegistered"}}]}
                        """, MediaType.APPLICATION_JSON));

        List<PushTicket> tickets = gateway.send(List.of(message("ExponentPushToken[a]"), message("ExponentPushToken[b]")));

        assertThat(tickets).hasSize(2);
        assertThat(tickets.get(0).ok()).isTrue();
        assertThat(tickets.get(0).id()).isEqualTo("XXXX-1");
        assertThat(tickets.get(1).ok()).isFalse();
        assertThat(tickets.get(1).deviceNotRegistered()).isTrue();
        server.verify();
    }

    @Test
    void requestLevelErrorsAndHttpFailuresBecomeGatewayExceptions() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"errors\":[{\"code\":\"PUSH_TOO_MANY_EXPERIENCE_IDS\",\"message\":\"x\"}]}",
                MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> gateway.send(List.of(message("ExponentPushToken[a]"))))
                .isInstanceOf(PushGatewayException.class).hasMessageContaining("PUSH_TOO_MANY_EXPERIENCE_IDS");

        server.reset();
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("boom"));
        assertThatThrownBy(() -> gateway.send(List.of(message("ExponentPushToken[a]"))))
                .isInstanceOf(PushGatewayException.class).hasMessageContaining("HTTP 500");

        server.reset();
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> gateway.send(List.of(message("ExponentPushToken[a]"))))
                .isInstanceOf(PushGatewayException.class).hasMessageContaining("beklenmedik");
    }

    @Test
    void emptyInputSendsNothing_andMoreThan100IsRejected() {
        assertThat(gateway.send(List.of())).isEmpty();
        List<PushMessage> tooMany = java.util.stream.IntStream.range(0, 101).mapToObj(i -> message("ExponentPushToken[" + i + "]")).toList();
        assertThatThrownBy(() -> gateway.send(tooMany)).isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    private static PushMessage message(String to) {
        return new PushMessage(to, "T", "B", null, "default", "default", "high");
    }
}

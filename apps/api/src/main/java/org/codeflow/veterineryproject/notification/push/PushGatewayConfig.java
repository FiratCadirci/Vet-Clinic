package org.codeflow.veterineryproject.notification.push;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/** Push gateway seçimi: {@code app.push.enabled=true} ise Expo, değilse dışarıya istek atmayan gateway. */
@Configuration
public class PushGatewayConfig {

    @Bean
    public PushGateway pushGateway(@Value("${app.push.enabled:false}") boolean enabled,
                                   @Value("${app.push.url:" + ExpoPushGateway.DEFAULT_URL + "}") String url,
                                   @Value("${app.push.access-token:}") String accessToken,
                                   RestClient.Builder builder) {
        if (!enabled) {
            return new DisabledPushGateway();
        }
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        return new ExpoPushGateway(builder.requestFactory(factory).build(), url, accessToken);
    }
}

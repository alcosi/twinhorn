package org.twins.horn.service.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.twins.horn.service.auth.dto.TokenIntrospectRsDTOv1;
import org.twins.horn.service.auth.session.ClientSessionService;
import org.twins.horn.exception.TwinhornException;
import org.twins.horn.exception.TwinhornException.TwinhornErrorType;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class TwinsTokenIntrospectService {
    private final RestTemplate restTemplate = new RestTemplate(); //todo - correct initialization, use @Bean in config
    @Value("${twins.introspection.url}")
    private String introspectUrl;

    private final ClientSessionService clientSessionService;

    /**
     * Sends the provided access token to the Twins introspection endpoint and
     * returns the parsed response.
     * <p>
     * A JSON body {@code {"token": "<authToken>"}} is POSTed to the URL specified
     * by the {@code twins.introspection.url} property. If the endpoint responds
     * with a 2xx status and a non-null body, the payload is deserialized into
     * {@link TokenIntrospectRsDTOv1} and returned. Any error (non-2xx status,
     * null body, networking issues, etc.) results in {@code null}.
     * <p>
     * The broad exception handling is temporary and will be migrated to an aspect.
     *
     * @param authToken raw access token (without the "Bearer " prefix)
     * @return the introspection result, or {@code null} when validation fails
     */
    public TokenIntrospectRsDTOv1 validateToken(String authToken) throws TwinhornException {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, String> body = new HashMap<>();
            body.put("token", authToken);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

            ResponseEntity<TokenIntrospectRsDTOv1> response = restTemplate.postForEntity(introspectUrl, request, TokenIntrospectRsDTOv1.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new TwinhornException(TwinhornErrorType.UNAUTHORIZED, "Invalid response from introspection service");
            }
            TokenIntrospectRsDTOv1 introspectRsDTOv1 = response.getBody();
            clientSessionService.saveClientSession(
                    UUID.fromString(introspectRsDTOv1.getClientId()),
                    Instant.ofEpochSecond(introspectRsDTOv1.getExp()));
            return response.getBody();
        } catch (Exception e) {
            throw new TwinhornException(TwinhornErrorType.INTROSPECT_SERVICE_CONNECTION_ERROR, "Failed to introspect token", e);
        }
    }
}

package org.twins.horn.service.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.twins.horn.service.auth.dto.TokenIntrospectRsDTOv1;

import java.util.HashMap;
import java.util.Map;


@Service
public class TwinsTokenIntrospectService {
    @Value("${twins.introspection.url}")
    private String introspectUrl;

    private final RestTemplate restTemplate = new RestTemplate();

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
    public TokenIntrospectRsDTOv1 validateToken(String authToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, String> body = new HashMap<>();
            body.put("token", authToken);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

            ResponseEntity<TokenIntrospectRsDTOv1> response = restTemplate.postForEntity(introspectUrl, request, TokenIntrospectRsDTOv1.class);
// TODO: Adjust logic based on the actual introspection response structure if necessary
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new Exception("Invalid response from introspection service");
            }
            return response.getBody();
        } catch (Exception e) {
            return null;
        }
    }
}

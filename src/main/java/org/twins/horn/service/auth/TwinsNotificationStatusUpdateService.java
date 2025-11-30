package org.twins.horn.service.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.twins.horn.exception.TwinhornException;
import org.twins.horn.exception.TwinhornException.TwinhornErrorType;
import org.twins.horn.service.auth.dto.TwinsResponseDTOv1;

import org.springframework.web.util.UriComponentsBuilder;

/**
 * Service responsible for informing the Twins platform that the current
 * client wants to start or stop receiving notification messages. The decision
 * is transferred by the {@code needStart} flag.
 */
@RequiredArgsConstructor
@Service
public class TwinsNotificationStatusUpdateService {

    public static final String HEADER_AUTH_TOKEN = "AuthToken";
    public static final String HEADER_DOMAIN_ID = "DomainId";

    // TODO: move RestTemplate instantiation to a @Configuration class and inject it
    private final RestTemplate restTemplate = new RestTemplate();

    public enum SubscriptionAction {init, stop}

    /**
     * Target endpoint configured in {@code application.properties}
     * (e.g. twins.notification.status.update.url=http://localhost:8080/private/notify/subscribe/v1)
     */
    @Value("${twins.notification.status.update.url}")
    private String updateStatusUrl;

    /**
     * Calls Twins REST API responsible for changing the notification subscription state.
     * <p>
     * The request is executed via HTTP POST with JSON body {@code {"needStart": <boolean>}} and
     * two mandatory headers:
     * <ul>
     *     <li>{@value #HEADER_DOMAIN_ID} – identifies the customer domain</li>
     *     <li>{@value #HEADER_AUTH_TOKEN} – bearer token obtained from auth service</li>
     * </ul>
     * <p>
     * The method validates that the response has a 2xx status and contains a non-null body that can be
     * deserialized into {@link TwinsResponseDTOv1}. Otherwise or on any network/serialization error a
     * {@link TwinhornException} is thrown.
     *
     * @param domainId  Twins domain identifier
     * @param authToken Access token for authorization
     * @param needStart {@code true} to start subscription, {@code false} to stop
     * @throws TwinhornException if the request fails or the response is invalid
     */
    public void updateSubscriptionStatus(String domainId, String authToken, boolean needStart) throws TwinhornException {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(HEADER_DOMAIN_ID, domainId);
            headers.set(HEADER_AUTH_TOKEN, authToken);

            String action = (needStart ? SubscriptionAction.init : SubscriptionAction.stop).name();
            String requestUrl = UriComponentsBuilder.fromUriString(updateStatusUrl)
                    .queryParam("subscriptionAction", action)
                    .toUriString();

            HttpEntity<Void> requestEntity = new HttpEntity<>(null, headers);

            ResponseEntity<TwinsResponseDTOv1> response = restTemplate.postForEntity(requestUrl, requestEntity, TwinsResponseDTOv1.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new TwinhornException(TwinhornErrorType.GENERAL_ERROR, "Invalid response from Twins notification status service");
            }
        } catch (Exception e) {
            // Wrap any exception into application-specific runtime exception
            throw new TwinhornException(TwinhornErrorType.GENERAL_ERROR, "Failed to update Twins notification subscription status", e);
        }
    }
}

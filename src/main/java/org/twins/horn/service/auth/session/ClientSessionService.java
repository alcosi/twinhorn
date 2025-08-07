package org.twins.horn.service.auth.session;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.twins.horn.db.ClientSessionEntity;
import org.twins.horn.db.ClientSessionRepository;
import org.twins.horn.service.grpc.ConnectionRegistry;
import org.twins.horn.subscribe.TwinfaceSubscribeProto;
import org.twins.horn.exception.TwinhornException;
import org.twins.horn.exception.TwinhornException.TwinhornErrorType;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ClientSessionService {

    private final ClientSessionRepository clientSessionRepository;

    @Autowired
    public ClientSessionService(ClientSessionRepository clientSessionRepository) {
        this.clientSessionRepository = clientSessionRepository;
    }

    /**
     * Stores a new or updated client session in the database.
     * <p>
     * The {@code createdAt} field is automatically set to the current instant
     * when this method is invoked.
     *
     * @param clientId         unique identifier of the client
     * @param tokenExpiryDate  instant when the token should expire (nullable)
     */
    public void saveClientSession(UUID clientId, Instant tokenExpiryDate) throws TwinhornException {
        try {
            ClientSessionEntity entity = new ClientSessionEntity();
            entity.setClientId(clientId);
            entity.setCreatedAt(Instant.now());
            entity.setExpiresAt(tokenExpiryDate);
            entity.setStatus("ACTIVE");
            clientSessionRepository.save(entity);
        } catch (Exception e) {
            throw new TwinhornException(TwinhornErrorType.DB_DATA_PROCESSING_ERROR,
                    "Failed to save client session", e);
        }
    }

    @Value("${session.expiry.grace:PT5M}")
    private Duration gracePeriod;

    /**
     * Scans for sessions with expired tokens and triggers reminders / disconnects.
     * <p>
     * Runs at a fixed delay configured by {@code session.scan.interval.ms} (default 60 s).
     */
    @Scheduled(fixedDelayString = "${session.scan.interval.ms:60000}")
    public void handleExpiredSessions() throws TwinhornException {
        try {
            Instant now = Instant.now();

            // 1) Send reminder for tokens that have just expired and are still ACTIVE
            List<ClientSessionEntity> justExpired = clientSessionRepository.findByStatusAndExpiresAtBefore("ACTIVE", now);
            if (!justExpired.isEmpty()) {
                for (ClientSessionEntity sess : justExpired) {
                    TwinfaceSubscribeProto.TwinfaceSubscribeUpdate warning = TwinfaceSubscribeProto.TwinfaceSubscribeUpdate.newBuilder()
                            .setUpdateId(UUID.randomUUID().toString())
                            .setTimestamp(now.toString())
                            .setEventType(TwinfaceSubscribeProto.TwinEventType.TOKEN_EXPIRED_WARNING)
                            .setStatus(TwinfaceSubscribeProto.UpdateStatus.GENERAL_ERROR)
                            .build();
                    ConnectionRegistry.broadcast(sess.getClientId().toString(), warning);
                    sess.setStatus("WARNING");
                }
                clientSessionRepository.saveAll(justExpired);
            }

            // 2) Close connections that ignored the warning for longer than gracePeriod
            Instant deadline = now.minus(gracePeriod);
            List<ClientSessionEntity> stale = clientSessionRepository.findByStatusAndExpiresAtBefore("WARNING", deadline);
            if (!stale.isEmpty()) {
                for (ClientSessionEntity sess : stale) {
                    TwinfaceSubscribeProto.TwinfaceSubscribeUpdate closeMsg = TwinfaceSubscribeProto.TwinfaceSubscribeUpdate.newBuilder()
                            .setUpdateId(UUID.randomUUID().toString())
                            .setTimestamp(now.toString())
                            .setEventType(TwinfaceSubscribeProto.TwinEventType.CONNECTION_CLOSED)
                            .setStatus(TwinfaceSubscribeProto.UpdateStatus.GENERAL_ERROR)
                            .build();
                    ConnectionRegistry.broadcast(sess.getClientId().toString(), closeMsg);
                    sess.setStatus("CLOSED");
                }
                clientSessionRepository.saveAll(stale);
            }
        } catch (Exception e) {
            throw new TwinhornException(TwinhornErrorType.DB_DATA_PROCESSING_ERROR,
                    "Failed to process expired sessions", e);
        }
    }
}

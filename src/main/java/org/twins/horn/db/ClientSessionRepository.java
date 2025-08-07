package org.twins.horn.db;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ClientSessionRepository extends CrudRepository<ClientSessionEntity, UUID> {
    /**
     * Returns all sessions whose {@code expiresAt} is before the supplied deadline.
     * <p>
     * Spring Data derives the query automatically from the method name.
     */
    java.util.List<ClientSessionEntity> findByExpiresAtBefore(java.time.Instant deadline);

    /**
     * Variant that also filters by status. Helps to determine whether a reminder has already been sent.
     */
    java.util.List<ClientSessionEntity> findByStatusAndExpiresAtBefore(String status, java.time.Instant deadline);
}

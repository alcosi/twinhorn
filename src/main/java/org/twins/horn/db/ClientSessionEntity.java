package org.twins.horn.db;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a client session.
 *
 * Table structure:
 * <pre>
 * client_session(
 *   client_id   UUID      PRIMARY KEY NOT NULL,
 *   token       VARCHAR   NOT NULL,
 *   created_at  TIMESTAMP NOT NULL,
 *   expires_at  TIMESTAMP,
 *   status      VARCHAR   NOT NULL
 * )
 * </pre>
 */
@Data
@NoArgsConstructor // required by JPA
@AllArgsConstructor
@Entity
@Table(name = "client_session")
public class ClientSessionEntity {

    @Id
    @Column(name = "client_id", nullable = false, updatable = false)
    private UUID clientId;

    @Column(name = "token", nullable = false)
    private String token;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "status", nullable = false)
    private String status;
}
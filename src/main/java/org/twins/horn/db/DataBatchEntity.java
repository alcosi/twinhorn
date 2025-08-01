package org.twins.horn.db;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity that represents a batch of data uploaded by a client session.
 *
 * Corresponding PostgreSQL table (jsonb used for batch_data):
 * <pre>
 * data_batch(
 *   client_session_id UUID REFERENCES client_session(client_id),
 *   batch_id          UUID      PRIMARY KEY NOT NULL,
 *   created_at        TIMESTAMP NOT NULL,
 *   batch_data        JSONB,
 *   status            VARCHAR   NOT NULL DEFAULT 'PENDING'
 * )
 * </pre>
 */
@Data
@NoArgsConstructor // required by JPA
@AllArgsConstructor
@Entity
@Table(name = "data_batch")
public class DataBatchEntity {

    @Id
    @Column(name = "batch_id", nullable = false, updatable = false)
    private UUID batchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_session_id", nullable = false)
    private ClientSessionEntity clientSession;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Raw JSON of the batch.  Column is stored as PostgreSQL jsonb.
     */
    @Column(name = "batch_data", columnDefinition = "jsonb")
    private String batchData;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status = Status.PENDING;

    public enum Status {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }
}
package org.twins.horn.service.batch;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.twins.horn.db.ClientSessionEntity;
import org.twins.horn.db.ClientSessionRepository;
import org.twins.horn.db.DataBatchEntity;
import org.twins.horn.db.DataBatchRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DataBatchHandlingService {
    @Autowired
    private DataBatchRepository dataBatchRepository;
    @Autowired
    private ClientSessionRepository clientSessionRepository;

    /**
     * Persists a batch for the given twinId and clientIds.
     * Each clientId will result in a DataBatchEntity persisted with the twinId and all clientIds in batchData.
     */
    public void saveBatch(UUID twinId, List<UUID> clientIds) {
        for (UUID clientId : clientIds) {
            Optional<ClientSessionEntity> clientSessionOpt = clientSessionRepository.findById(clientId);
            if (clientSessionOpt.isEmpty()) {
                // Optionally handle missing client session (log, throw, etc.)
                continue;
            }
            ClientSessionEntity clientSession = clientSessionOpt.get();
            DataBatchEntity entity = new DataBatchEntity();
            entity.setBatchId(UUID.randomUUID());
            entity.setClientSession(clientSession);
            entity.setCreatedAt(Instant.now());
            // Store twinId and clientIds as JSON in batchData
            entity.setBatchData(String.format("{\"twinId\":\"%s\",\"clientIds\":%s}", twinId, clientIds.toString()));
            entity.setStatus(DataBatchEntity.Status.PENDING);
            dataBatchRepository.save(entity);
        }
    }
}

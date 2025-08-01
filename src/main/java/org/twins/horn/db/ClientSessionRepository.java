package org.twins.horn.db;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ClientSessionRepository extends CrudRepository<ClientSessionEntity, UUID> {
}

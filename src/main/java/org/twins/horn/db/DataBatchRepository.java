package org.twins.horn.db;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
@Repository
public interface DataBatchRepository extends CrudRepository<DataBatchEntity, UUID> {
}

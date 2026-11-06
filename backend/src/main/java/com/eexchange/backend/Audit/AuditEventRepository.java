package com.eexchange.backend.Audit;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditEventRepository
        extends MongoRepository<AuditEventDocument, String>, AuditEventRepositoryCustom {
}

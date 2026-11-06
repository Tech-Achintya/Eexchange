package com.eexchange.backend.Audit;

import com.eexchange.backend.Entity.Enums.AuditAction;
import com.eexchange.backend.Entity.Enums.AuditTargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.List;

@RequiredArgsConstructor
public class AuditEventRepositoryImpl implements AuditEventRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public Page<AuditEventDocument> search(AuditTargetType targetType,
                                           Long targetId,
                                           Long actorId,
                                           AuditAction action,
                                           Instant from,
                                           Instant to,
                                           Pageable pageable) {

        Query query = filterQuery(targetType, targetId, actorId, action, from, to);

        // Count before applying paging so skip/limit does not affect the total.
        long total = mongoTemplate.count(query, AuditEventDocument.class);

        query.with(pageable);
        List<AuditEventDocument> content =
                mongoTemplate.find(query, AuditEventDocument.class);

        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public List<AuditEventDocument> findRecentByTarget(AuditTargetType targetType,
                                                       Long targetId,
                                                       int limit) {
        Query query = new Query(Criteria.where("targetType").is(targetType)
                .and("targetId").is(targetId));
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"));
        query.limit(limit);
        return mongoTemplate.find(query, AuditEventDocument.class);
    }

    private Query filterQuery(AuditTargetType targetType,
                              Long targetId,
                              Long actorId,
                              AuditAction action,
                              Instant from,
                              Instant to) {

        Query query = new Query();

        if (targetType != null) {
            query.addCriteria(Criteria.where("targetType").is(targetType));
        }
        if (targetId != null) {
            query.addCriteria(Criteria.where("targetId").is(targetId));
        }
        if (actorId != null) {
            query.addCriteria(Criteria.where("actorId").is(actorId));
        }
        if (action != null) {
            query.addCriteria(Criteria.where("action").is(action));
        }
        if (from != null || to != null) {
            Criteria createdAt = Criteria.where("createdAt");
            if (from != null) {
                createdAt = createdAt.gte(from);
            }
            if (to != null) {
                createdAt = createdAt.lte(to);
            }
            query.addCriteria(createdAt);
        }
        return query;
    }
}

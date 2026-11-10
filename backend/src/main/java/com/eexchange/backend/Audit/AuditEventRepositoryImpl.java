package com.eexchange.backend.Audit;

import com.eexchange.backend.Entity.Enums.AuditAction;
import com.eexchange.backend.Entity.Enums.AuditTargetType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class AuditEventRepositoryImpl implements AuditEventRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<AuditEvent> search(AuditTargetType targetType,
                                   Long targetId,
                                   Long actorId,
                                   AuditAction action,
                                   Instant from,
                                   Instant to,
                                   Pageable pageable) {

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<AuditEvent> query = cb.createQuery(AuditEvent.class);
        Root<AuditEvent> root = query.from(AuditEvent.class);
        query.where(predicates(cb, root, targetType, targetId, actorId, action, from, to)
                .toArray(Predicate[]::new));
        applySort(cb, root, query, pageable.getSort());

        TypedQuery<AuditEvent> contentQuery = entityManager.createQuery(query);
        contentQuery.setFirstResult((int) pageable.getOffset());
        contentQuery.setMaxResults(pageable.getPageSize());
        List<AuditEvent> content = contentQuery.getResultList();

        long total = count(targetType, targetId, actorId, action, from, to);

        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public List<AuditEvent> findRecentByTarget(AuditTargetType targetType,
                                               Long targetId,
                                               int limit) {

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<AuditEvent> query = cb.createQuery(AuditEvent.class);
        Root<AuditEvent> root = query.from(AuditEvent.class);
        query.where(
                cb.equal(root.get("targetType"), targetType),
                cb.equal(root.get("targetId"), targetId));
        query.orderBy(cb.desc(root.get("createdAt")));

        return entityManager.createQuery(query)
                .setMaxResults(Math.max(limit, 0))
                .getResultList();
    }

    private long count(AuditTargetType targetType,
                       Long targetId,
                       Long actorId,
                       AuditAction action,
                       Instant from,
                       Instant to) {

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<AuditEvent> root = query.from(AuditEvent.class);
        query.select(cb.count(root));
        query.where(predicates(cb, root, targetType, targetId, actorId, action, from, to)
                .toArray(Predicate[]::new));

        return entityManager.createQuery(query).getSingleResult();
    }

    private List<Predicate> predicates(CriteriaBuilder cb,
                                       Root<AuditEvent> root,
                                       AuditTargetType targetType,
                                       Long targetId,
                                       Long actorId,
                                       AuditAction action,
                                       Instant from,
                                       Instant to) {

        List<Predicate> predicates = new ArrayList<>();

        if (targetType != null) {
            predicates.add(cb.equal(root.get("targetType"), targetType));
        }
        if (targetId != null) {
            predicates.add(cb.equal(root.get("targetId"), targetId));
        }
        if (actorId != null) {
            predicates.add(cb.equal(root.get("actorId"), actorId));
        }
        if (action != null) {
            predicates.add(cb.equal(root.get("action"), action));
        }
        if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
        }
        return predicates;
    }

    private void applySort(CriteriaBuilder cb,
                           Root<AuditEvent> root,
                           CriteriaQuery<AuditEvent> query,
                           Sort sort) {

        List<Order> orders = new ArrayList<>();
        for (Sort.Order order : sort) {
            Path<Object> path = root.get(order.getProperty());
            orders.add(order.isAscending() ? cb.asc(path) : cb.desc(path));
        }
        if (!orders.isEmpty()) {
            query.orderBy(orders);
        }
    }
}

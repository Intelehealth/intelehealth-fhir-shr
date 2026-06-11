package org.openmrs.module.ihshr.synclog;

import java.util.Date;
import java.util.List;

import org.hibernate.Query;
import org.openmrs.module.ihshr.utils.IhshrDbSessionFactory;
import org.springframework.stereotype.Repository;

@Repository
public class ShrSyncLogRepository {
	
	public void save(IntelehealthShrSyncLog row) {
		IhshrDbSessionFactory.get().getCurrentSession().saveOrUpdate(row);
	}
	
	public IntelehealthShrSyncLog findById(Long id) {
		return (IntelehealthShrSyncLog) IhshrDbSessionFactory.get().getCurrentSession()
		        .get(IntelehealthShrSyncLog.class, id);
	}
	
	public int nextAttemptNumberForVisit(String visitUuid) {
		String sql = "SELECT COALESCE(MAX(attempt_number), 0) + 1 FROM intelehealth_shr_sync_log WHERE visit_uuid = :visitUuid";
		Number result = (Number) IhshrDbSessionFactory.get().getCurrentSession().createSQLQuery(sql)
		        .setString("visitUuid", visitUuid).uniqueResult();
		return result == null ? 1 : result.intValue();
	}
	
	@SuppressWarnings("unchecked")
	public List<IntelehealthShrSyncLog> findFailedDueForRetry(int limit) {
		String hql = "FROM IntelehealthShrSyncLog l WHERE l.status = :status AND l.nextRetryAt IS NOT NULL "
		        + "AND l.nextRetryAt <= :now ORDER BY l.nextRetryAt ASC";
		Query query = IhshrDbSessionFactory.get().getCurrentSession().createQuery(hql);
		query.setParameter("status", ShrSyncLogStatus.FAILED);
		query.setTimestamp("now", new Date());
		query.setMaxResults(Math.max(1, limit));
		return query.list();
	}
	
	@SuppressWarnings("unchecked")
	public List<IntelehealthShrSyncLog> findPendingAwaitingPush(int limit) {
		String hql = "FROM IntelehealthShrSyncLog l WHERE l.status = :status AND l.completedAt IS NULL "
		        + "AND l.httpStatusCode IS NULL ORDER BY l.startedAt ASC";
		Query query = IhshrDbSessionFactory.get().getCurrentSession().createQuery(hql);
		query.setParameter("status", ShrSyncLogStatus.PENDING);
		query.setMaxResults(Math.max(1, limit));
		return query.list();
	}
	
	@SuppressWarnings("unchecked")
	public List<IntelehealthShrSyncLog> findFailedPermanentEligibleForRetry(int limit) {
		String hql = "FROM IntelehealthShrSyncLog l WHERE l.status = :status AND l.attemptNumber < :maxAttempts "
		        + "ORDER BY l.completedAt ASC";
		Query query = IhshrDbSessionFactory.get().getCurrentSession().createQuery(hql);
		query.setParameter("status", ShrSyncLogStatus.FAILED_PERMANENT);
		query.setParameter("maxAttempts", ShrSyncRetryPolicy.MAX_ATTEMPTS);
		query.setMaxResults(Math.max(1, limit));
		return query.list();
	}
}

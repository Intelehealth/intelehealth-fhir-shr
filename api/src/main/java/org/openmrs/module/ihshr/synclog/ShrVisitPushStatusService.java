package org.openmrs.module.ihshr.synclog;

import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.Query;
import org.openmrs.module.ihshr.utils.IhshrDbSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Updates visit attributes for SHR sync badge lookup (doc §13.2).
 */
@Service("shrVisitPushStatusService")
public class ShrVisitPushStatusService implements ShrVisitPushStatusServiceContract {
	
	private static final Logger LOG = LoggerFactory.getLogger(ShrVisitPushStatusService.class);
	
	private static final String ATTR_STATUS = "shr_last_push_status";
	
	private static final String ATTR_PUSH_AT = "shr_last_push_at";
	
	@Override
	@Transactional
	public void recordSuccess(String visitUuid) {
		if (StringUtils.isBlank(visitUuid)) {
			return;
		}
		try {
			Integer visitId = resolveVisitId(visitUuid.trim());
			if (visitId == null) {
				return;
			}
			upsertVisitAttribute(visitId, ATTR_STATUS, "SUCCESS");
			upsertVisitAttribute(visitId, ATTR_PUSH_AT, String.valueOf(new Date().getTime()));
		}
		catch (Exception ex) {
			LOG.warn("Unable to update visit SHR push attributes for visit {}: {}", visitUuid, ex.getMessage());
		}
	}
	
	@Override
	@Transactional
	public void recordPermanentFailure(String visitUuid, String failureReason) {
		if (StringUtils.isBlank(visitUuid)) {
			return;
		}
		try {
			Integer visitId = resolveVisitId(visitUuid.trim());
			if (visitId == null) {
				return;
			}
			upsertVisitAttribute(visitId, ATTR_STATUS, "FAILED_PERMANENT");
			upsertVisitAttribute(visitId, ATTR_PUSH_AT, String.valueOf(new Date().getTime()));
			LOG.warn("Visit {} marked FAILED_PERMANENT for SHR sync badge: {}", visitUuid, failureReason);
		}
		catch (Exception ex) {
			LOG.warn("Unable to update visit SHR permanent-failure attributes for visit {}: {}", visitUuid, ex.getMessage());
		}
	}
	
	private Integer resolveVisitId(String visitUuid) {
		Number id = (Number) IhshrDbSessionFactory.get().getCurrentSession()
		        .createSQLQuery("SELECT visit_id FROM visit WHERE uuid = :uuid AND voided = 0").setString("uuid", visitUuid)
		        .uniqueResult();
		return id == null ? null : id.intValue();
	}
	
	private void upsertVisitAttribute(Integer visitId, String attributeName, String value) {
		Number typeId = (Number) IhshrDbSessionFactory.get().getCurrentSession()
		        .createSQLQuery("SELECT visit_attribute_type_id FROM visit_attribute_type WHERE name = :name LIMIT 1")
		        .setString("name", attributeName).uniqueResult();
		if (typeId == null) {
			LOG.debug("Visit attribute type {} not configured; skipping badge update", attributeName);
			return;
		}
		Number existingId = (Number) IhshrDbSessionFactory
		        .get()
		        .getCurrentSession()
		        .createSQLQuery(
		            "SELECT visit_attribute_id FROM visit_attribute WHERE visit_id = :visitId AND attribute_type_id = :typeId AND voided = 0")
		        .setInteger("visitId", visitId).setInteger("typeId", typeId.intValue()).uniqueResult();
		if (existingId != null) {
			Query update = IhshrDbSessionFactory
			        .get()
			        .getCurrentSession()
			        .createSQLQuery(
			            "UPDATE visit_attribute SET value_reference = :value, date_changed = NOW() WHERE visit_attribute_id = :id");
			update.setString("value", value);
			update.setInteger("id", existingId.intValue());
			update.executeUpdate();
			return;
		}
		Query insert = IhshrDbSessionFactory
		        .get()
		        .getCurrentSession()
		        .createSQLQuery(
		            "INSERT INTO visit_attribute (visit_id, value_reference, attribute_type_id, uuid, date_created, voided, creator) "
		                    + "VALUES (:visitId, :value, :typeId, UUID(), NOW(), 0, 1)");
		insert.setInteger("visitId", visitId);
		insert.setString("value", value);
		insert.setInteger("typeId", typeId.intValue());
		insert.executeUpdate();
	}
}

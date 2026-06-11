package org.openmrs.module.ihshr.backlog;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.Query;
import org.openmrs.module.ihshr.utils.IhshrDbSessionFactory;
import org.springframework.stereotype.Repository;

@Repository
public class IhshrUnmappedTermRepository {
	
	public void recordOccurrence(IhshrUnmappedTerm row) {
		String sql = "INSERT INTO ihshr_unmapped_term "
		        + "(record_uuid, artifact, term_text, lookup_file, lookup_type, obs_uuid, encounter_uuid, patient_uuid, concept_id, "
		        + "first_seen, last_seen, occurrences, resolved) "
		        + "VALUES (:recordUuid, :artifact, :termText, :lookupFile, :lookupType, :obsUuid, :encounterUuid, :patientUuid, :conceptId, "
		        + ":firstSeen, :lastSeen, 1, 0) "
		        + "ON DUPLICATE KEY UPDATE last_seen = :lastSeenUpdate, occurrences = occurrences + 1";
		Query query = IhshrDbSessionFactory.get().getCurrentSession().createSQLQuery(sql);
		Date now = new Date();
		query.setString("recordUuid", row.getRecordUuid());
		query.setString("artifact", row.getArtifact());
		query.setString("termText", truncateTerm(row.getTermText()));
		query.setString("lookupFile", row.getLookupFile());
		query.setString("lookupType", row.getLookupType());
		query.setString("obsUuid", row.getObsUuid());
		query.setString("encounterUuid", row.getEncounterUuid());
		query.setString("patientUuid", row.getPatientUuid());
		query.setParameter("conceptId", row.getConceptId());
		query.setTimestamp("firstSeen", now);
		query.setTimestamp("lastSeen", now);
		query.setTimestamp("lastSeenUpdate", now);
		query.executeUpdate();
	}
	
	@SuppressWarnings("unchecked")
	public List<UnmappedTermAggregate> findTopUnmapped(String artifactCode, int limit, boolean unresolvedOnly) {
		StringBuilder sql = new StringBuilder();
		sql.append("SELECT artifact, term_text, lookup_file, lookup_type, ");
		sql.append("SUM(occurrences) AS total_occurrences, COUNT(DISTINCT obs_uuid) AS distinct_obs, ");
		sql.append("MIN(first_seen) AS first_seen, MAX(last_seen) AS last_seen ");
		sql.append("FROM ihshr_unmapped_term WHERE 1=1 ");
		if (unresolvedOnly) {
			sql.append("AND resolved = 0 ");
		}
		if (StringUtils.isNotBlank(artifactCode)) {
			sql.append("AND artifact = :artifact ");
		}
		sql.append("GROUP BY artifact, term_text, lookup_file, lookup_type ");
		sql.append("ORDER BY total_occurrences DESC ");
		sql.append("LIMIT :limit");
		
		Query query = IhshrDbSessionFactory.get().getCurrentSession().createSQLQuery(sql.toString());
		if (StringUtils.isNotBlank(artifactCode)) {
			query.setString("artifact", artifactCode.trim());
		}
		query.setInteger("limit", Math.max(1, limit));
		
		List<Object[]> rows = query.list();
		List<UnmappedTermAggregate> results = new ArrayList<UnmappedTermAggregate>();
		for (Object[] row : rows) {
			UnmappedTermAggregate aggregate = new UnmappedTermAggregate();
			aggregate.setArtifact(stringAt(row, 0));
			aggregate.setTermText(stringAt(row, 1));
			aggregate.setLookupFile(stringAt(row, 2));
			aggregate.setLookupType(stringAt(row, 3));
			aggregate.setTotalOccurrences(longAt(row, 4));
			aggregate.setDistinctObsCount(longAt(row, 5));
			aggregate.setFirstSeen(dateAt(row, 6));
			aggregate.setLastSeen(dateAt(row, 7));
			results.add(aggregate);
		}
		return results;
	}
	
	public int markResolved(String artifactCode, String termText) {
		if (StringUtils.isBlank(artifactCode) || StringUtils.isBlank(termText)) {
			return 0;
		}
		String sql = "UPDATE ihshr_unmapped_term SET resolved = 1, date_resolved = :now "
		        + "WHERE artifact = :artifact AND term_text = :termText AND resolved = 0";
		Query query = IhshrDbSessionFactory.get().getCurrentSession().createSQLQuery(sql);
		query.setTimestamp("now", new Date());
		query.setString("artifact", artifactCode.trim());
		query.setString("termText", truncateTerm(termText));
		return query.executeUpdate();
	}
	
	private static String truncateTerm(String term) {
		if (term == null) {
			return "";
		}
		String trimmed = term.trim();
		return trimmed.length() <= 512 ? trimmed : trimmed.substring(0, 512);
	}
	
	private static String stringAt(Object[] row, int index) {
		return row[index] == null ? null : row[index].toString();
	}
	
	private static long longAt(Object[] row, int index) {
		Object value = row[index];
		if (value == null) {
			return 0L;
		}
		if (value instanceof BigInteger) {
			return ((BigInteger) value).longValue();
		}
		if (value instanceof Number) {
			return ((Number) value).longValue();
		}
		return Long.parseLong(value.toString());
	}
	
	private static Date dateAt(Object[] row, int index) {
		Object value = row[index];
		if (value instanceof Date) {
			return (Date) value;
		}
		return null;
	}
	
}

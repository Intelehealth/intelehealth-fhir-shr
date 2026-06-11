package org.openmrs.module.ihshr.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.openmrs.module.ihshr.config.StructuredObsConceptSettings;
import org.hibernate.Query;
import org.openmrs.module.ihshr.utils.IhshrDbSessionFactory;
import org.openmrs.module.ihmodule.api.patientexchange.domain.CompeletdVisit;
import org.openmrs.module.ihshr.domain.CompletedRecord;
import org.openmrs.module.ihshr.domain.LocationInfo;
import org.openmrs.module.ihshr.synclog.ObsPushContext;
import org.springframework.stereotype.Service;

@Service("ihshrCommonOperationService")
public class CommonOperationService {
	
	private volatile Set<Integer> physicalExamConceptIds;
	
	private volatile Set<Integer> chiefComplaintConceptIds;
	
	private volatile Set<Integer> familyHistoryConceptIds;
	
	private volatile Set<Integer> medicalHistoryConceptIds;
	
	private volatile Set<Integer> diagnosisConceptIds;
	
	private Query createNativeQuery(String sqlText) {
		return IhshrDbSessionFactory.get().getCurrentSession().createSQLQuery(sqlText);
	}
	
	public List<CompeletdVisit> getCompletedVisit(String date, int encounterType) {
		String sql = "SELECT " + "    v.uuid AS visit, " + "    p.uuid AS person, "
		        + "    COALESCE(e.date_changed, e.date_created) AS updated_date, " + "    v.visit_id AS visit_id " + "FROM "
		        + "    encounter e " + "JOIN " + "    visit v ON e.visit_id = v.visit_id " + "JOIN "
		        + "    person p ON p.person_id = e.patient_id " + "JOIN "
		        + "    patient_identifier pi2 ON pi2.patient_id = p.person_id " + "WHERE "
		        + "    e.encounter_type = :encounterType " + "    AND (e.date_created > :date OR e.date_changed > :date) "
		        + "    AND pi2.identifier_type = ( " + "        SELECT " + "            patient_identifier_type_id "
		        + "        FROM " + "            patient_identifier_type pit " + "        WHERE "
		        + "            pit.name = 'MPI' " + "    )";
		
		List<CompeletdVisit> visits = new ArrayList<CompeletdVisit>();
		
		Query q = createNativeQuery(sql).setParameter("date", date).setParameter("encounterType", encounterType);
		
		List resultList = q.list();
		
		for (Iterator iter = resultList.iterator(); iter.hasNext();) {
			Object[] resultArray = (Object[]) iter.next();
			CompeletdVisit theVisit = new CompeletdVisit();
			theVisit.setVisit(resultArray[0].toString());
			theVisit.setPatient(resultArray[1].toString());
			theVisit.setDate(resultArray[2].toString());
			theVisit.setVisitId(Integer.parseInt(resultArray[3].toString()));
			visits.add(theVisit);
		}
		return visits;
	}
	
	/**
	 * Same visit projection as {@link #getCompletedVisit(String, int)} for one encounter row.
	 * 
	 * @return the visit for {@code encounterId}, or {@code null} if not found / MPI filter excludes
	 *         it
	 */
	public CompeletdVisit getCompletedVisitByEncounterId(int encounterId, int encounterType) {
		return mapCompletedVisitRow(queryCompletedVisitByEncounterId(encounterId, encounterType, true));
	}
	
	/**
	 * Event-driven visit push: resolves visit linkage without requiring MPI upfront (CRUID/MPI is
	 * enforced later when building the FHIR bundle).
	 */
	public CompeletdVisit getCompletedVisitByEncounterIdForPush(int encounterId, int encounterType) {
		return mapCompletedVisitRow(queryCompletedVisitByEncounterId(encounterId, encounterType, false));
	}
	
	/**
	 * Resolves a completed visit for SHR replay using the visit uuid stored on
	 * {@code intelehealth_shr_sync_log}.
	 */
	public CompeletdVisit getCompletedVisitByVisitUuid(String visitUuid, int encounterType) {
		if (visitUuid == null || visitUuid.trim().isEmpty()) {
			return null;
		}
		String sql = "SELECT " + "    v.uuid AS visit, " + "    p.uuid AS person, "
		        + "    COALESCE(e.date_changed, e.date_created) AS updated_date, " + "    v.visit_id AS visit_id "
		        + "FROM encounter e " + "JOIN visit v ON e.visit_id = v.visit_id "
		        + "JOIN person p ON p.person_id = e.patient_id " + "WHERE v.uuid = :visitUuid "
		        + "AND e.encounter_type = :encounterType " + "AND e.voided = 0 " + "ORDER BY e.encounter_id ASC LIMIT 1";
		Object[] row = (Object[]) createNativeQuery(sql).setString("visitUuid", visitUuid.trim())
		        .setInteger("encounterType", encounterType).uniqueResult();
		return mapCompletedVisitRow(row);
	}
	
	private Object[] queryCompletedVisitByEncounterId(int encounterId, int encounterType, boolean requireMpi) {
		String sql = "SELECT " + "    v.uuid AS visit, " + "    p.uuid AS person, "
		        + "    COALESCE(e.date_changed, e.date_created) AS updated_date, " + "    v.visit_id AS visit_id " + "FROM "
		        + "    encounter e " + "JOIN " + "    visit v ON e.visit_id = v.visit_id " + "JOIN "
		        + "    person p ON p.person_id = e.patient_id ";
		if (requireMpi) {
			sql += "JOIN " + "    patient_identifier pi2 ON pi2.patient_id = p.person_id ";
		}
		sql += "WHERE " + "    e.encounter_id = :encounterId " + "    AND e.encounter_type = :encounterType "
		        + "    AND e.voided = 0 ";
		if (requireMpi) {
			sql += "    AND pi2.identifier_type = ( " + "        SELECT " + "            patient_identifier_type_id "
			        + "        FROM " + "            patient_identifier_type pit " + "        WHERE "
			        + "            pit.name = 'MPI' " + "    )";
		}
		return (Object[]) createNativeQuery(sql).setInteger("encounterId", encounterId)
		        .setInteger("encounterType", encounterType).uniqueResult();
	}
	
	private CompeletdVisit mapCompletedVisitRow(Object[] resultArray) {
		if (resultArray == null) {
			return null;
		}
		CompeletdVisit theVisit = new CompeletdVisit();
		theVisit.setVisit(resultArray[0].toString());
		theVisit.setPatient(resultArray[1].toString());
		theVisit.setDate(resultArray[2].toString());
		theVisit.setVisitId(Integer.parseInt(resultArray[3].toString()));
		return theVisit;
	}
	
	public List<CompletedRecord> getCompletedEncounters(List<Integer> ids) {
		String sql = "SELECT e.uuid ,e.encounter_id  from encounter e  WHERE e.visit_id in :ids";
		
		List<CompletedRecord> records = new ArrayList<CompletedRecord>();
		
		List resultList = createNativeQuery(sql).setParameterList("ids", ids).list();
		Iterator iter = null;
		for (iter = resultList.iterator(); iter.hasNext();) {
			CompletedRecord theRecord = new CompletedRecord();
			Object[] resultArray = (Object[]) iter.next();
			theRecord.setId(Integer.parseInt(resultArray[1].toString()));
			theRecord.setUuid(resultArray[0].toString());
			
			records.add(theRecord);
			
		}
		return records;
	}
	
	public List<CompletedRecord> getCompletedEncounter(Integer id) {
		String sql = "SELECT e.uuid ,e.encounter_id  from encounter e  WHERE e.visit_id = :id";
		
		List<CompletedRecord> records = new ArrayList<CompletedRecord>();
		
		List resultList = createNativeQuery(sql).setParameter("id", id).list();
		Iterator iter = null;
		for (iter = resultList.iterator(); iter.hasNext();) {
			CompletedRecord theRecord = new CompletedRecord();
			Object[] resultArray = (Object[]) iter.next();
			theRecord.setId(Integer.parseInt(resultArray[1].toString()));
			theRecord.setUuid(resultArray[0].toString());
			
			records.add(theRecord);
			
		}
		return records;
	}
	
	public List<CompletedRecord> getCompletedObs(List<Integer> ids) {
		
	    String encounterIds = ids.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
	    
		String sql = "SELECT o.uuid, o.obs_id, o.encounter_id, o.concept_id, o.value_text, o.value_complex, o.comments "
		        + "FROM obs o WHERE o.encounter_id IN ("
		        + encounterIds + ") AND o.voided = false";

		return mapCompletedObs(createNativeQuery(sql).list());
	}
	
	public List<CompletedRecord> getCompletedObs(Integer id) {
		String sql = "SELECT o.uuid, o.obs_id, o.encounter_id, o.concept_id, o.value_text, o.value_complex, o.comments "
		        + "FROM obs o WHERE o.encounter_id = :id AND o.voided = false";
		
		return mapCompletedObs(createNativeQuery(sql).setParameter("id", id).list());
	}
	
	private List<CompletedRecord> mapCompletedObs(List resultList) {
		List<CompletedRecord> records = new ArrayList<CompletedRecord>();
		for (Iterator iter = resultList.iterator(); iter.hasNext();) {
			Object[] resultArray = (Object[]) iter.next();
			CompletedRecord theRecord = new CompletedRecord();
			theRecord.setUuid(resultArray[0].toString());
			theRecord.setId(toInteger(resultArray[1]));
			theRecord.setEncounterId(toInteger(resultArray[2]));
			theRecord.setConceptId(toInteger(resultArray[3]));
			theRecord.setValueText(resultArray[4] != null ? resultArray[4].toString() : null);
			theRecord.setValueComplex(resultArray[5] != null ? resultArray[5].toString() : null);
			theRecord.setComments(resultArray[6] != null ? resultArray[6].toString() : null);
			records.add(theRecord);
		}
		return records;
	}
	
	private static Integer toInteger(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Number) {
			return ((Number) value).intValue();
		}
		return Integer.parseInt(value.toString());
	}
	
	public boolean isPhysicalExamConceptId(Integer conceptId) {
		if (conceptId == null) {
			return false;
		}
		return getPhysicalExamConceptIds().contains(conceptId);
	}
	
	public boolean isChiefComplaintConceptId(Integer conceptId) {
		if (conceptId == null) {
			return false;
		}
		return getChiefComplaintConceptIds().contains(conceptId);
	}
	
	public Set<Integer> getResolvedPhysicalExamConceptIds() {
		return getPhysicalExamConceptIds();
	}
	
	public Set<Integer> getResolvedChiefComplaintConceptIds() {
		return getChiefComplaintConceptIds();
	}
	
	public boolean isFamilyHistoryConceptId(Integer conceptId) {
		if (conceptId == null) {
			return false;
		}
		return getFamilyHistoryConceptIds().contains(conceptId);
	}
	
	public boolean isMedicalHistoryConceptId(Integer conceptId) {
		if (conceptId == null) {
			return false;
		}
		return getMedicalHistoryConceptIds().contains(conceptId);
	}
	
	public Set<Integer> getResolvedFamilyHistoryConceptIds() {
		return getFamilyHistoryConceptIds();
	}
	
	public Set<Integer> getResolvedMedicalHistoryConceptIds() {
		return getMedicalHistoryConceptIds();
	}
	
	public boolean isDiagnosisConceptId(Integer conceptId) {
		if (conceptId == null) {
			return false;
		}
		return getDiagnosisConceptIds().contains(conceptId);
	}
	
	public Set<Integer> getResolvedDiagnosisConceptIds() {
		return getDiagnosisConceptIds();
	}
	
	public void logObsDiagnosticsForEncounters(List<Integer> encounterIds) {
		if (encounterIds == null || encounterIds.isEmpty()) {
			return;
		}
		String encounterIdList = encounterIds.stream().map(String::valueOf).collect(Collectors.joining(","));
		String sql = "SELECT o.obs_id, o.uuid, o.concept_id, o.voided, COALESCE(LENGTH(o.value_text), 0), "
		        + "LEFT(o.value_text, 50) FROM obs o WHERE o.encounter_id IN (" + encounterIdList
		        + ") ORDER BY o.obs_id";
		List<?> rows = createNativeQuery(sql).list();
		System.err.println("[Observation] All obs rows for encounter_id(s) " + encounterIds + " (incl. voided), count="
		        + rows.size());
		for (Object row : rows) {
			Object[] r = (Object[]) row;
			System.err.println("[Observation]   obs_id=" + r[0] + " uuid=" + r[1] + " concept_id=" + r[2] + " voided="
			        + r[3] + " value_text_len=" + r[4] + " preview=" + r[5]);
		}
	}
	
	private Set<Integer> getPhysicalExamConceptIds() {
		Set<Integer> cached = physicalExamConceptIds;
		if (cached != null) {
			return cached;
		}
		synchronized (this) {
			if (physicalExamConceptIds == null) {
				physicalExamConceptIds = loadPhysicalExamConceptIds();
			}
			return physicalExamConceptIds;
		}
	}
	
	private Set<Integer> loadPhysicalExamConceptIds() {
		Set<Integer> ids = new HashSet<Integer>();
		ids.addAll(StructuredObsConceptSettings.physicalExamConceptIds());
		String sql = "SELECT DISTINCT c.concept_id FROM concept c "
		        + "INNER JOIN concept_name cn ON cn.concept_id = c.concept_id AND cn.voided = 0 "
		        + "WHERE c.retired = 0 AND (UPPER(cn.name) LIKE '%PHYSICAL EXAMINATION%' OR UPPER(cn.name) LIKE '%PHYSICAL EXAM%')";
		List<?> rows = createNativeQuery(sql).list();
		for (Object row : rows) {
			if (row != null) {
				ids.add(toInteger(row));
			}
		}
		String uuidSql = "SELECT concept_id FROM concept WHERE uuid = (SELECT uuid FROM concept WHERE concept_id = "
		        + StructuredObsConceptSettings.primaryPhysicalExamConceptId() + " LIMIT 1)";
		List<?> uuidRows = createNativeQuery(uuidSql).list();
		for (Object row : uuidRows) {
			if (row != null) {
				ids.add(toInteger(row));
			}
		}
		return ids;
	}
	
	private Set<Integer> getChiefComplaintConceptIds() {
		Set<Integer> cached = chiefComplaintConceptIds;
		if (cached != null) {
			return cached;
		}
		synchronized (this) {
			if (chiefComplaintConceptIds == null) {
				chiefComplaintConceptIds = loadChiefComplaintConceptIds();
			}
			return chiefComplaintConceptIds;
		}
	}
	
	private Set<Integer> loadChiefComplaintConceptIds() {
		Set<Integer> ids = new HashSet<Integer>();
		ids.addAll(StructuredObsConceptSettings.chiefComplaintConceptIds());
		String sql = "SELECT DISTINCT c.concept_id FROM concept c "
		        + "INNER JOIN concept_name cn ON cn.concept_id = c.concept_id AND cn.voided = 0 "
		        + "WHERE c.retired = 0 AND (UPPER(cn.name) LIKE '%CHIEF COMPLAINT%' OR UPPER(cn.name) LIKE '%CURRENT COMPLAINT%')";
		List<?> rows = createNativeQuery(sql).list();
		for (Object row : rows) {
			if (row != null) {
				ids.add(toInteger(row));
			}
		}
		String uuidSql = "SELECT concept_id FROM concept WHERE uuid = (SELECT uuid FROM concept WHERE concept_id = "
		        + StructuredObsConceptSettings.primaryChiefComplaintConceptId() + " LIMIT 1)";
		List<?> uuidRows = createNativeQuery(uuidSql).list();
		for (Object row : uuidRows) {
			if (row != null) {
				ids.add(toInteger(row));
			}
		}
		return ids;
	}
	
	private Set<Integer> getFamilyHistoryConceptIds() {
		Set<Integer> cached = familyHistoryConceptIds;
		if (cached != null) {
			return cached;
		}
		synchronized (this) {
			if (familyHistoryConceptIds == null) {
				familyHistoryConceptIds = loadFamilyHistoryConceptIds();
			}
			return familyHistoryConceptIds;
		}
	}
	
	private Set<Integer> loadFamilyHistoryConceptIds() {
		Set<Integer> ids = new HashSet<Integer>();
		ids.addAll(StructuredObsConceptSettings.familyHistoryConceptIds());
		String sql = "SELECT DISTINCT c.concept_id FROM concept c "
		        + "INNER JOIN concept_name cn ON cn.concept_id = c.concept_id AND cn.voided = 0 "
		        + "WHERE c.retired = 0 AND UPPER(cn.name) LIKE '%FAMILY HISTORY%'";
		List<?> rows = createNativeQuery(sql).list();
		for (Object row : rows) {
			if (row != null) {
				ids.add(toInteger(row));
			}
		}
		String uuidSql = "SELECT concept_id FROM concept WHERE uuid = (SELECT uuid FROM concept WHERE concept_id = "
		        + StructuredObsConceptSettings.primaryFamilyHistoryConceptId() + " LIMIT 1)";
		List<?> uuidRows = createNativeQuery(uuidSql).list();
		for (Object row : uuidRows) {
			if (row != null) {
				ids.add(toInteger(row));
			}
		}
		return ids;
	}
	
	private Set<Integer> getMedicalHistoryConceptIds() {
		Set<Integer> cached = medicalHistoryConceptIds;
		if (cached != null) {
			return cached;
		}
		synchronized (this) {
			if (medicalHistoryConceptIds == null) {
				medicalHistoryConceptIds = loadMedicalHistoryConceptIds();
			}
			return medicalHistoryConceptIds;
		}
	}
	
	private Set<Integer> loadMedicalHistoryConceptIds() {
		Set<Integer> ids = new HashSet<Integer>();
		ids.addAll(StructuredObsConceptSettings.medicalHistoryConceptIds());
		String sql = "SELECT DISTINCT c.concept_id FROM concept c "
		        + "INNER JOIN concept_name cn ON cn.concept_id = c.concept_id AND cn.voided = 0 "
		        + "WHERE c.retired = 0 AND (UPPER(cn.name) LIKE '%MEDICAL HISTORY%' OR UPPER(cn.name) LIKE '%PATIENT MEDICAL HISTORY%')";
		List<?> rows = createNativeQuery(sql).list();
		for (Object row : rows) {
			if (row != null) {
				ids.add(toInteger(row));
			}
		}
		String uuidSql = "SELECT concept_id FROM concept WHERE uuid = (SELECT uuid FROM concept WHERE concept_id = "
		        + StructuredObsConceptSettings.primaryMedicalHistoryConceptId() + " LIMIT 1)";
		List<?> uuidRows = createNativeQuery(uuidSql).list();
		for (Object row : uuidRows) {
			if (row != null) {
				ids.add(toInteger(row));
			}
		}
		return ids;
	}
	
	private Set<Integer> getDiagnosisConceptIds() {
		Set<Integer> cached = diagnosisConceptIds;
		if (cached != null) {
			return cached;
		}
		synchronized (this) {
			if (diagnosisConceptIds == null) {
				diagnosisConceptIds = loadDiagnosisConceptIds();
			}
			return diagnosisConceptIds;
		}
	}
	
	private Set<Integer> loadDiagnosisConceptIds() {
		Set<Integer> ids = new HashSet<Integer>();
		ids.addAll(StructuredObsConceptSettings.diagnosisConceptIds());
		String sql = "SELECT DISTINCT c.concept_id FROM concept c "
		        + "INNER JOIN concept_name cn ON cn.concept_id = c.concept_id AND cn.voided = 0 "
		        + "WHERE c.retired = 0 AND UPPER(cn.name) LIKE '%DIAGNOSIS%'";
		List<?> rows = createNativeQuery(sql).list();
		for (Object row : rows) {
			if (row != null) {
				ids.add(toInteger(row));
			}
		}
		String uuidSql = "SELECT concept_id FROM concept WHERE uuid = (SELECT uuid FROM concept WHERE concept_id = "
		        + StructuredObsConceptSettings.primaryDiagnosisConceptId() + " LIMIT 1)";
		List<?> uuidRows = createNativeQuery(uuidSql).list();
		for (Object row : uuidRows) {
			if (row != null) {
				ids.add(toInteger(row));
			}
		}
		return ids;
	}
	
	public List<CompletedRecord> getCompletedServiceRequest(List<Integer> ids, int type) {
	    String encounterIds = ids.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
		
		String sql = "SELECT o.uuid, o.order_id, o.date_created  from orders o  WHERE  o.encounter_id IN ("+encounterIds+") and "
				+ " o.order_type_id=:type and o.voided=false";

		List<CompletedRecord> records = new ArrayList<CompletedRecord>();

		List resultList = createNativeQuery(sql)
				.setParameter("type", type)
				.list();

		Iterator iter = null;
		for (iter = resultList.iterator(); iter.hasNext();) {
			CompletedRecord theRecord = new CompletedRecord();
			Object[] resultArray = (Object[]) iter.next();

			theRecord.setUuid(resultArray[0].toString());
			theRecord.setId(Integer.parseInt(resultArray[1].toString()));
			theRecord.setDateCreated(resultArray[2].toString());

			records.add(theRecord);
		}
		return records;
	}
	
	public List<CompletedRecord> getCompletedServiceRequest(Integer id, int type) {
		String sql = "SELECT o.uuid, o.order_id  from orders o  WHERE o.encounter_id=:id and order_type_id=:type and voided=false";
		
		List<CompletedRecord> records = new ArrayList<CompletedRecord>();
		
		List resultList = createNativeQuery(sql).setParameter("id", id).setParameter("type", type).list();
		Iterator iter = null;
		for (iter = resultList.iterator(); iter.hasNext();) {
			CompletedRecord theRecord = new CompletedRecord();
			Object[] resultArray = (Object[]) iter.next();
			theRecord.setId(Integer.parseInt(resultArray[1].toString()));
			theRecord.setUuid(resultArray[0].toString());
			records.add(theRecord);
		}
		return records;
	}
	
	public List<CompletedRecord> getCompletedMedication(String date_created) {
		String sql = "SELECT d.uuid ,d.drug_id ,coalesce(d.date_changed , d.date_created) date_created  from drug d "
		        + " where  d.date_created > :date_created or d.date_changed > :date_created "
		        + " order by  coalesce(d.date_changed , d.date_created) asc";
		
		List<CompletedRecord> records = new ArrayList<CompletedRecord>();
		
		List resultList = createNativeQuery(sql).setParameter("date_created", date_created).list();
		Iterator iter = null;
		for (iter = resultList.iterator(); iter.hasNext();) {
			CompletedRecord theRecord = new CompletedRecord();
			Object[] resultArray = (Object[]) iter.next();
			theRecord.setId(Integer.parseInt(resultArray[1].toString()));
			theRecord.setUuid(resultArray[0].toString());
			theRecord.setDateCreated(resultArray[2].toString());
			
			records.add(theRecord);
			
		}
		return records;
	}
	
	public String getObsValueText(String obsUuid) {
		String sql = "SELECT o.value_text FROM obs o WHERE o.uuid = :uuid AND o.voided = false";
		Object value = createNativeQuery(sql).setParameter("uuid", obsUuid).uniqueResult();
		return value != null ? value.toString() : null;
	}
	
	public String getMPIUsingPatientReference(String reference) {
		String sql = "SELECT " + " identifier as mpi " + "FROM " + " patient_identifier pi2 "
		        + "JOIN patient_identifier_type pit ON " + " pi2.identifier_type = pit.patient_identifier_type_id "
		        + "JOIN person p ON " + " p.person_id = pi2.patient_id " + "WHERE " + " pit.name = 'MPI' "
		        + " AND p.uuid = :reference";
		
		Object mpiId = createNativeQuery(sql).setParameter("reference", reference).uniqueResult();
		return (mpiId != null) ? mpiId.toString() : null;
	}
	
	public LocationInfo getLocationInfo(String mpi) {
		String sql = "SELECT " + "	pi.patient_id," + "	pi.identifier," + "	pi.identifier_type," + "	l.uuid locationUUID,"
		        + "	l.name," + "	l.location_id" + " from" + "	patient_identifier pi" + " join location l on"
		        + "	pi.location_id = l.location_id" + " where" + "	pi.identifier = :mpi";
		
		List resultList = createNativeQuery(sql).setParameter("mpi", mpi).list();
		Iterator iter = null;
		for (iter = resultList.iterator(); iter.hasNext();) {
			LocationInfo location = new LocationInfo();
			Object[] resultArray = (Object[]) iter.next();
			location.setPatientId(resultArray[0].toString());
			location.setIdentifier(resultArray[1].toString());
			location.setIdentifierType(resultArray[2].toString());
			location.setLocationUUID(resultArray[3].toString());
			location.setLocationName(resultArray[4].toString());
			location.setLocationId(resultArray[5].toString());
			return location;
		}
		return null;
	}
	
	public HashSet<String> getEncountersByMpi(String mpi) {
	    String sql = " select"
	    		+ "	e.uuid"
	    		+ " from"
	    		+ "	encounter e"
	    		+ " join patient_identifier pi on"
	    		+ "	pi.patient_id = e.patient_id"
	    		+ " where"
	    		+ "	pi.identifier = :mpi"
	    		+ " union "
	    		+ " select"
	    		+ "	v.uuid"
	    		+ " from"
	    		+ "	patient_identifier pi"
	    		+ " join visit v on"
	    		+ "	pi.patient_id = v.patient_id"
	    		+ " where"
	    		+ "	pi.identifier = :mpi";

		List resultList = createNativeQuery(sql).setParameter("mpi", mpi).list();
		
		HashSet<String> hashSets = new HashSet<>();
		
		Iterator iter = null;
		for (iter = resultList.iterator(); iter.hasNext();) {
			String id = (String) iter.next();
			hashSets.add(id);
		}
		return hashSets;
	}
	
	public HashSet<String> getObservationsByMpi(String mpi) {
	    String sql = " SELECT"
	    		+ "	o.uuid"
	    		+ " from"
	    		+ "	obs o"
	    		+ " join patient_identifier pi "
	    		+ " on"
	    		+ "	o.person_id = pi.patient_id"
	    		+ " WHERE"
	    		+ "	pi.identifier = :mpi";

		List resultList = createNativeQuery(sql).setParameter("mpi", mpi).list();
		
		HashSet<String> hashSets = new HashSet<>();
		
		Iterator iter = null;
		for (iter = resultList.iterator(); iter.hasNext();) {
			String id = (String) iter.next();
			hashSets.add(id);
		}
		return hashSets;
	}
	
	public HashSet<String> getServiceRequestByMpiAndOrderType(String mpi, String type){
		 String sql = " SELECT"
		 		+ "	o.uuid"
		 		+ " from"
		 		+ "	orders o"
		 		+ " join patient_identifier pi on"
		 		+ "	o.patient_id = pi.patient_id"
		 		+ " WHERE"
		 		+ "	o.order_type_id = :type"
		 		+ "	and pi.identifier =:mpi";

			List resultList = createNativeQuery(sql)
					.setParameter("mpi", mpi)
					.setParameter("type", type)
					.list();
			
			HashSet<String> hashSets = new HashSet<>();
			
			Iterator iter = null;
			for (iter = resultList.iterator(); iter.hasNext();) {
				String id = (String) iter.next();
				hashSets.add(id);
			}
			return hashSets;
	}
	
	/**
	 * Visit-level SHR push context (sync log step 9) using the visit-complete encounter as trigger.
	 */
	public ObsPushContext findVisitPushContext(int visitId, String visitUuid) {
		String triggerEncounterUuid = getVisitCompleteEncounterUuid(visitId);
		if (triggerEncounterUuid == null) {
			throw new IllegalStateException("No visit-complete encounter for visit_id=" + visitId);
		}
		return new ObsPushContext(visitUuid.trim(), triggerEncounterUuid, null);
	}
	
	public String getVisitCompleteEncounterUuid(int visitId) {
		String sql = "SELECT e.uuid FROM encounter e WHERE e.visit_id = :visitId AND e.encounter_type = :encounterType "
		        + "AND e.voided = 0 ORDER BY e.encounter_id ASC LIMIT 1";
		Object value = createNativeQuery(sql).setInteger("visitId", visitId)
		        .setInteger("encounterType", org.openmrs.module.ihshr.datatype.EncounterType.VISIT_COMPLETE.getValue())
		        .uniqueResult();
		return value != null ? value.toString() : null;
	}
	
	public ObsPushContext findObsPushContext(Integer obsId, String obsUuid) {
		String sql = "SELECT v.uuid AS visit_uuid, e.uuid AS encounter_uuid, o.uuid AS obs_uuid " + "FROM obs o "
		        + "JOIN encounter e ON o.encounter_id = e.encounter_id " + "JOIN visit v ON e.visit_id = v.visit_id "
		        + "WHERE o.voided = 0 AND (o.obs_id = :obsId OR o.uuid = :obsUuid) LIMIT 1";
		Query query = createNativeQuery(sql);
		if (obsId != null) {
			query.setInteger("obsId", obsId);
		} else {
			query.setInteger("obsId", -1);
		}
		query.setString("obsUuid", obsUuid == null ? "" : obsUuid);
		Object[] row = (Object[]) query.uniqueResult();
		if (row == null || row.length < 2) {
			throw new IllegalStateException("Unable to resolve visit/encounter for obs uuid=" + obsUuid);
		}
		return new ObsPushContext(String.valueOf(row[0]), String.valueOf(row[1]),
		        row.length > 2 && row[2] != null ? String.valueOf(row[2]) : obsUuid);
	}
	
	/**
	 * OpenMRS patient person uuid for a visit (for CRUID resolution when flushing deferred
	 * Provenance).
	 */
	public String getPatientUuidForVisit(String visitUuid) {
		if (visitUuid == null || visitUuid.trim().isEmpty()) {
			return null;
		}
		String sql = "SELECT p.uuid FROM visit v " + "JOIN encounter e ON e.visit_id = v.visit_id AND e.voided = 0 "
		        + "JOIN person p ON p.person_id = e.patient_id AND p.voided = 0 " + "WHERE v.uuid = :visitUuid " + "LIMIT 1";
		Object value = createNativeQuery(sql).setString("visitUuid", visitUuid.trim()).uniqueResult();
		return value != null ? value.toString() : null;
	}
	
	public String getVisitUuidForEncounter(String encounterUuid) {
		if (encounterUuid == null || encounterUuid.trim().isEmpty()) {
			return null;
		}
		String sql = "SELECT v.uuid FROM encounter e JOIN visit v ON e.visit_id = v.visit_id "
		        + "WHERE e.uuid = :encounterUuid AND e.voided = 0 LIMIT 1";
		Object value = createNativeQuery(sql).setString("encounterUuid", encounterUuid.trim()).uniqueResult();
		return value != null ? value.toString() : null;
	}
}

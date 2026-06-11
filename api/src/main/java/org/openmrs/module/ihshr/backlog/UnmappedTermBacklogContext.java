package org.openmrs.module.ihshr.backlog;

/**
 * Thread-local context for structured-obs FHIR build (obs / visit / patient).
 */
public final class UnmappedTermBacklogContext {
	
	private static final ThreadLocal<Holder> CURRENT = new ThreadLocal<Holder>();
	
	private UnmappedTermBacklogContext() {
	}
	
	public static void set(String obsUuid, String encounterUuid, String patientUuid, Integer conceptId) {
		Holder holder = new Holder();
		holder.obsUuid = obsUuid;
		holder.encounterUuid = encounterUuid;
		holder.patientUuid = patientUuid;
		holder.conceptId = conceptId;
		CURRENT.set(holder);
	}
	
	public static Holder get() {
		return CURRENT.get();
	}
	
	public static void clear() {
		CURRENT.remove();
	}
	
	public static final class Holder {
		
		private String obsUuid;
		
		private String encounterUuid;
		
		private String patientUuid;
		
		private Integer conceptId;
		
		public String getObsUuid() {
			return obsUuid;
		}
		
		public String getEncounterUuid() {
			return encounterUuid;
		}
		
		public String getPatientUuid() {
			return patientUuid;
		}
		
		public Integer getConceptId() {
			return conceptId;
		}
		
	}
	
}

package org.openmrs.module.ihshr.backlog;

import java.util.Date;
import java.util.UUID;

/**
 * Row in {@code ihshr_unmapped_term} — one occurrence bucket per (artifact, term, obs).
 */
public class IhshrUnmappedTerm {
	
	private Long id;
	
	private String recordUuid = UUID.randomUUID().toString();
	
	private String artifact;
	
	private String termText;
	
	private String lookupFile;
	
	private String lookupType;
	
	private String obsUuid;
	
	private String encounterUuid;
	
	private String patientUuid;
	
	private Integer conceptId;
	
	private Date firstSeen = new Date();
	
	private Date lastSeen = new Date();
	
	private int occurrences = 1;
	
	private boolean resolved;
	
	private Date dateResolved;
	
	public Long getId() {
		return id;
	}
	
	public void setId(Long id) {
		this.id = id;
	}
	
	public String getRecordUuid() {
		return recordUuid;
	}
	
	public void setRecordUuid(String recordUuid) {
		this.recordUuid = recordUuid;
	}
	
	public String getArtifact() {
		return artifact;
	}
	
	public void setArtifact(String artifact) {
		this.artifact = artifact;
	}
	
	public String getTermText() {
		return termText;
	}
	
	public void setTermText(String termText) {
		this.termText = termText;
	}
	
	public String getLookupFile() {
		return lookupFile;
	}
	
	public void setLookupFile(String lookupFile) {
		this.lookupFile = lookupFile;
	}
	
	public String getLookupType() {
		return lookupType;
	}
	
	public void setLookupType(String lookupType) {
		this.lookupType = lookupType;
	}
	
	public String getObsUuid() {
		return obsUuid;
	}
	
	public void setObsUuid(String obsUuid) {
		this.obsUuid = obsUuid;
	}
	
	public String getEncounterUuid() {
		return encounterUuid;
	}
	
	public void setEncounterUuid(String encounterUuid) {
		this.encounterUuid = encounterUuid;
	}
	
	public String getPatientUuid() {
		return patientUuid;
	}
	
	public void setPatientUuid(String patientUuid) {
		this.patientUuid = patientUuid;
	}
	
	public Integer getConceptId() {
		return conceptId;
	}
	
	public void setConceptId(Integer conceptId) {
		this.conceptId = conceptId;
	}
	
	public Date getFirstSeen() {
		return firstSeen;
	}
	
	public void setFirstSeen(Date firstSeen) {
		this.firstSeen = firstSeen;
	}
	
	public Date getLastSeen() {
		return lastSeen;
	}
	
	public void setLastSeen(Date lastSeen) {
		this.lastSeen = lastSeen;
	}
	
	public int getOccurrences() {
		return occurrences;
	}
	
	public void setOccurrences(int occurrences) {
		this.occurrences = occurrences;
	}
	
	public boolean isResolved() {
		return resolved;
	}
	
	public void setResolved(boolean resolved) {
		this.resolved = resolved;
	}
	
	public Date getDateResolved() {
		return dateResolved;
	}
	
	public void setDateResolved(Date dateResolved) {
		this.dateResolved = dateResolved;
	}
	
}

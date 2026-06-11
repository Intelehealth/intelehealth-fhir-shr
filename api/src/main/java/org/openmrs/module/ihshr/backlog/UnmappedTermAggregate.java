package org.openmrs.module.ihshr.backlog;

import java.util.Date;

/**
 * Aggregated backlog row for curation reports (doc §8.6 top-N by occurrence).
 */
public class UnmappedTermAggregate {
	
	private String artifact;
	
	private String termText;
	
	private String lookupFile;
	
	private String lookupType;
	
	private long totalOccurrences;
	
	private long distinctObsCount;
	
	private Date firstSeen;
	
	private Date lastSeen;
	
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
	
	public long getTotalOccurrences() {
		return totalOccurrences;
	}
	
	public void setTotalOccurrences(long totalOccurrences) {
		this.totalOccurrences = totalOccurrences;
	}
	
	public long getDistinctObsCount() {
		return distinctObsCount;
	}
	
	public void setDistinctObsCount(long distinctObsCount) {
		this.distinctObsCount = distinctObsCount;
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
	
}

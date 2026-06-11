package org.openmrs.module.ihshr.backlog;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.ihshr.utils.IhshrPropertyResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service("ihshrUnmappedTermService")
public class IhshrUnmappedTermServiceImpl implements IhshrUnmappedTermService {
	
	private static final Log LOG = LogFactory.getLog(IhshrUnmappedTermServiceImpl.class);
	
	private static final int DEFAULT_TOP_LIMIT = 50;
	
	private static final int MAX_TERM_LENGTH = 2000;
	
	@Autowired
	private IhshrUnmappedTermRepository repository;
	
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordOccurrence(UnmappedTermArtifact artifact, String lookupFile, UnmappedTermLookupType lookupType,
	        String termText) {
		if (!isBacklogEnabled()) {
			return;
		}
		if (artifact == null || artifact == UnmappedTermArtifact.UNKNOWN || lookupType == null
		        || StringUtils.isBlank(termText)) {
			return;
		}
		UnmappedTermBacklogContext.Holder ctx = UnmappedTermBacklogContext.get();
		if (ctx == null || StringUtils.isBlank(ctx.getObsUuid())) {
			return;
		}
		try {
			IhshrUnmappedTerm row = new IhshrUnmappedTerm();
			row.setRecordUuid(UUID.randomUUID().toString());
			row.setArtifact(artifact.getCode());
			row.setTermText(truncate(termText));
			row.setLookupFile(StringUtils.isNotBlank(lookupFile) ? lookupFile : artifact.getDefaultLookupFile());
			row.setLookupType(lookupType.code());
			row.setObsUuid(ctx.getObsUuid());
			row.setEncounterUuid(ctx.getEncounterUuid());
			row.setPatientUuid(ctx.getPatientUuid());
			row.setConceptId(ctx.getConceptId());
			repository.recordOccurrence(row);
		}
		catch (Exception ex) {
			LOG.warn("Unable to record unmapped term backlog: artifact=" + artifact + " term=" + termText + ": "
			        + ex.getMessage());
		}
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<UnmappedTermAggregate> getTopUnmapped(UnmappedTermArtifact artifact, int limit, boolean unresolvedOnly) {
		String artifactCode = artifact == null || artifact == UnmappedTermArtifact.UNKNOWN ? null : artifact.getCode();
		int capped = limit <= 0 ? DEFAULT_TOP_LIMIT : Math.min(limit, 500);
		return repository.findTopUnmapped(artifactCode, capped, unresolvedOnly);
	}
	
	@Override
	@Transactional
	public int markResolved(UnmappedTermArtifact artifact, String termText) {
		if (artifact == null || artifact == UnmappedTermArtifact.UNKNOWN || StringUtils.isBlank(termText)) {
			return 0;
		}
		return repository.markResolved(artifact.getCode(), termText);
	}
	
	@Override
	@Transactional(readOnly = true)
	public String exportTopUnmappedCsv(UnmappedTermArtifact artifact, int limit) {
		List<UnmappedTermAggregate> rows = getTopUnmapped(artifact, limit, true);
		StringBuilder csv = new StringBuilder();
		csv.append("artifact,term_text,lookup_file,lookup_type,total_occurrences,distinct_obs,first_seen,last_seen\n");
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		for (UnmappedTermAggregate row : rows) {
			csv.append(csvCell(row.getArtifact())).append(',');
			csv.append(csvCell(row.getTermText())).append(',');
			csv.append(csvCell(row.getLookupFile())).append(',');
			csv.append(csvCell(row.getLookupType())).append(',');
			csv.append(row.getTotalOccurrences()).append(',');
			csv.append(row.getDistinctObsCount()).append(',');
			csv.append(csvCell(format.format(row.getFirstSeen()))).append(',');
			csv.append(csvCell(format.format(row.getLastSeen()))).append('\n');
		}
		return csv.toString();
	}
	
	@Override
	public void logWeeklyTopUnmappedReport() {
		if (!isBacklogEnabled()) {
			return;
		}
		LOG.info("========== IHSHR unmapped term backlog (top " + DEFAULT_TOP_LIMIT + " per artifact) ==========");
		for (UnmappedTermArtifact artifact : UnmappedTermArtifact.values()) {
			if (artifact == UnmappedTermArtifact.UNKNOWN) {
				continue;
			}
			List<UnmappedTermAggregate> top = getTopUnmapped(artifact, DEFAULT_TOP_LIMIT, true);
			if (top.isEmpty()) {
				continue;
			}
			LOG.info("--- artifact=" + artifact.getCode() + " lookup=" + artifact.getDefaultLookupFile() + " ---");
			int rank = 1;
			for (UnmappedTermAggregate row : top) {
				LOG.info(rank++ + ". \"" + row.getTermText() + "\" occurrences=" + row.getTotalOccurrences()
				        + " distinctObs=" + row.getDistinctObsCount() + " lastSeen=" + row.getLastSeen());
			}
		}
		LOG.info("========== end unmapped term backlog report ==========");
	}
	
	static boolean isBacklogEnabled() {
		String value = IhshrPropertyResolver.resolve("unmapped.backlog.enabled", "ihshr.unmapped.backlog.enabled");
		if (StringUtils.isBlank(value)) {
			return true;
		}
		return !"false".equalsIgnoreCase(value.trim()) && !"0".equals(value.trim());
	}
	
	private static String truncate(String term) {
		String trimmed = term.trim();
		return trimmed.length() <= MAX_TERM_LENGTH ? trimmed : trimmed.substring(0, MAX_TERM_LENGTH);
	}
	
	private static String csvCell(String value) {
		if (value == null) {
			return "";
		}
		String escaped = value.replace("\"", "\"\"");
		if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
			return "\"" + escaped + "\"";
		}
		return escaped;
	}
	
}

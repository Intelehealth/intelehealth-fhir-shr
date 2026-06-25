package org.openmrs.module.ihshr.pull;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

/**
 * Parsed pull request (doc §4.2 filter form).
 */
public class ShrHistoryRequest {
	
	private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
	
	private ShrPullView view = ShrPullView.DEFAULT;
	
	private LocalDate dateFrom;
	
	private LocalDate dateTo;
	
	private List<ShrPullRecordType> recordTypes;
	
	private String status;
	
	private String conditionCode;
	
	private String conditionCodeSystem = "http://snomed.info/sct";
	
	private String sourceOrg;
	
	private String freeText;
	
	private String sort = "desc";
	
	private boolean sortExplicitlySet;
	
	private String labCode;
	
	private String labCodeSystem = "http://loinc.org";
	
	private String labComparator = "gt";
	
	private String labValue;
	
	/** Optional full composite override, e.g. http://loinc.org|2339-0$gt180 (doc §9.7). */
	private String labComposite;
	
	private int count = 50;
	
	private String pageUrl;
	
	private boolean includeLocalEcho;
	
	private String format = "envelope";
	
	public static ShrHistoryRequest fromQueryParams(java.util.Map<String, String> params) {
		ShrHistoryRequest req = new ShrHistoryRequest();
		if (params == null) {
			req.applyDefaults();
			return req;
		}
		if (StringUtils.isNotBlank(params.get("pageUrl"))) {
			req.pageUrl = params.get("pageUrl").trim();
			return req;
		}
		req.view = ShrPullView.fromParam(params.get("view"));
		req.dateFrom = parseDate(params.get("dateFrom"));
		req.dateTo = parseDate(params.get("dateTo"));
		req.recordTypes = ShrPullRecordType.parseCsv(params.get("recordTypes"));
		req.status = trimOrNull(params.get("status"));
		req.conditionCode = trimOrNull(params.get("conditionCode"));
		if (StringUtils.isNotBlank(params.get("conditionCodeSystem"))) {
			req.conditionCodeSystem = params.get("conditionCodeSystem").trim();
		}
		req.sourceOrg = trimOrNull(params.get("sourceOrg"));
		req.freeText = trimOrNull(params.get("freeText"));
		if (StringUtils.isNotBlank(params.get("sort"))) {
			req.sort = params.get("sort").trim();
			req.sortExplicitlySet = true;
		}
		req.labCode = trimOrNull(params.get("labCode"));
		if (StringUtils.isNotBlank(params.get("labCodeSystem"))) {
			req.labCodeSystem = params.get("labCodeSystem").trim();
		}
		if (StringUtils.isNotBlank(params.get("labComparator"))) {
			req.labComparator = params.get("labComparator").trim();
		}
		req.labValue = trimOrNull(params.get("labValue"));
		req.labComposite = trimOrNull(params.get("labComposite"));
		if (StringUtils.isNotBlank(params.get("count"))) {
			req.count = parseCount(params.get("count"));
		}
		if (StringUtils.isNotBlank(params.get("includeLocalEcho"))) {
			req.includeLocalEcho = Boolean.parseBoolean(params.get("includeLocalEcho").trim());
		}
		if (StringUtils.isNotBlank(params.get("format"))) {
			req.format = ShrPullFormat.parse(params.get("format")).getParamValue();
		}
		req.applyDefaults();
		return req;
	}
	
	private void applyDefaults() {
		if (dateFrom == null) {
			dateFrom = LocalDate.now().minusMonths(defaultMonths());
		}
		if (!sortExplicitlySet && (view == ShrPullView.VITALS || view == ShrPullView.LABS)) {
			sort = "asc";
		}
		if (count <= 0) {
			count = defaultCount();
		}
		if (count > maxCount()) {
			throw new ShrPullException(ShrPullErrorCode.INVALID_FILTER, "count exceeds maximum " + maxCount());
		}
	}
	
	private static LocalDate parseDate(String value) {
		if (StringUtils.isBlank(value)) {
			return null;
		}
		return LocalDate.parse(value.trim(), ISO_DATE);
	}
	
	private static int parseCount(String value) {
		try {
			return Integer.parseInt(value.trim());
		}
		catch (NumberFormatException ex) {
			throw new ShrPullException(ShrPullErrorCode.INVALID_FILTER, "Invalid count: " + value);
		}
	}
	
	private static String trimOrNull(String value) {
		return StringUtils.isBlank(value) ? null : value.trim();
	}
	
	private static int defaultCount() {
		return ShrPullSettings.defaultCount();
	}
	
	private static int maxCount() {
		return ShrPullSettings.maxCount();
	}
	
	private static int defaultMonths() {
		return ShrPullSettings.defaultMonths();
	}
	
	public ShrPullView getView() {
		return view;
	}
	
	public LocalDate getDateFrom() {
		return dateFrom;
	}
	
	public LocalDate getDateTo() {
		return dateTo;
	}
	
	public List<ShrPullRecordType> getRecordTypes() {
		return recordTypes;
	}
	
	public String getStatus() {
		return status;
	}
	
	public String getConditionCode() {
		return conditionCode;
	}
	
	public String getConditionCodeSystem() {
		return conditionCodeSystem;
	}
	
	public String getSourceOrg() {
		return sourceOrg;
	}
	
	public String getFreeText() {
		return freeText;
	}
	
	public String getSort() {
		return sort;
	}
	
	public int getCount() {
		return count;
	}
	
	public String getPageUrl() {
		return pageUrl;
	}
	
	public boolean isIncludeLocalEcho() {
		return includeLocalEcho;
	}
	
	public String getFormat() {
		return format;
	}
	
	void setFormat(String format) {
		this.format = format;
	}
	
	void setCount(int count) {
		this.count = count;
	}
	
	void setIncludeLocalEcho(boolean includeLocalEcho) {
		this.includeLocalEcho = includeLocalEcho;
	}
	
	public boolean isDescendingSort() {
		return !"asc".equalsIgnoreCase(sort);
	}
	
	public String formattedConditionToken() {
		if (StringUtils.isBlank(conditionCode)) {
			return null;
		}
		return conditionCodeSystem + "|" + conditionCode.trim();
	}
	
	public String formattedLabCompositeToken() {
		if (StringUtils.isNotBlank(labComposite)) {
			return labComposite.trim();
		}
		if (StringUtils.isBlank(labCode) || StringUtils.isBlank(labValue)) {
			return null;
		}
		String comparator = StringUtils.defaultIfBlank(labComparator, "gt").trim();
		String system = StringUtils.defaultIfBlank(labCodeSystem, "http://loinc.org").trim();
		return system + "|" + labCode.trim() + "$" + comparator + labValue.trim();
	}
	
	public String getLabCode() {
		return labCode;
	}
	
	public String getLabCodeSystem() {
		return labCodeSystem;
	}
	
	public String getLabComparator() {
		return labComparator;
	}
	
	public String getLabValue() {
		return labValue;
	}
	
	public String getLabComposite() {
		return labComposite;
	}
}

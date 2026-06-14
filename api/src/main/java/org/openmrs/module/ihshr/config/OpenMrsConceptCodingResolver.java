package org.openmrs.module.ihshr.config;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.Query;
import org.hl7.fhir.r4.model.Coding;
import org.openmrs.api.context.Context;
import org.openmrs.module.ihshr.fhir.ClinicalTermNormalizer;
import org.openmrs.module.ihshr.utils.IhshrDbSessionFactory;
import org.openmrs.module.ihshr.utils.IhshrPropertyResolver;

/**
 * Layer 1: resolve SNOMED CT / LOINC codings from the OpenMRS concept dictionary (CIEL concepts
 * with reference maps).
 */
public final class OpenMrsConceptCodingResolver {
	
	private static final String SNOMED_SOURCE = "SNOMED CT";
	
	private static final String LOINC_SOURCE = "LOINC";
	
	private static final String SNOMED_SYSTEM = "http://snomed.info/sct";
	
	private static final String LOINC_SYSTEM = "http://loinc.org";
	
	private static final Map<String, Coding> CACHE = new ConcurrentHashMap<String, Coding>();
	
	private static final Coding ABSENT = new Coding();
	
	private OpenMrsConceptCodingResolver() {
	}
	
	public static void clearCache() {
		CACHE.clear();
	}
	
	public static Coding lookupByTerm(String term) {
		if (!isEnabled() || StringUtils.isBlank(term) || !Context.isSessionOpen()) {
			return null;
		}
		for (String variant : ClinicalTermNormalizer.lookupVariants(term)) {
			Coding cached = getCached(variant);
			if (cached != null) {
				return copyCoding(cached);
			}
			Coding resolved = queryConceptDictionary(variant);
			putCached(variant, resolved);
			if (resolved != null) {
				return copyCoding(resolved);
			}
		}
		return null;
	}
	
	static boolean isEnabled() {
		String value = IhshrPropertyResolver.resolve("concept.coding.enabled", "ihshr.concept.coding.enabled");
		if (StringUtils.isBlank(value)) {
			return true;
		}
		return !"false".equalsIgnoreCase(value.trim()) && !"0".equals(value.trim());
	}
	
	private static Coding getCached(String variant) {
		String key = cacheKey(variant);
		if (!CACHE.containsKey(key)) {
			return null;
		}
		Coding cached = CACHE.get(key);
		return cached == ABSENT ? null : cached;
	}
	
	private static void putCached(String variant, Coding coding) {
		CACHE.put(cacheKey(variant), coding == null ? ABSENT : coding);
	}
	
	private static String cacheKey(String variant) {
		return variant.trim().toLowerCase(Locale.ROOT);
	}
	
	private static Coding queryConceptDictionary(String term) {
		try {
			List<String> sources = configuredSources();
			Query query = IhshrDbSessionFactory.get().getCurrentSession().createSQLQuery(buildLookupSql(sources.size()));
			query.setString("term", term.trim());
			for (int i = 0; i < sources.size(); i++) {
				query.setString("source" + i, sources.get(i));
			}
			query.setMaxResults(1);
			Object row = query.uniqueResult();
			if (!(row instanceof Object[])) {
				return null;
			}
			Object[] values = (Object[]) row;
			if (values.length < 2 || values[0] == null) {
				return null;
			}
			String code = values[0].toString();
			String sourceName = values[1] != null ? values[1].toString() : SNOMED_SOURCE;
			String display = values.length > 2 && values[2] != null ? values[2].toString() : term.trim();
			Coding coding = new Coding();
			coding.setSystem(mapSourceToSystem(sourceName));
			coding.setCode(code);
			coding.setDisplay(display);
			return coding;
		}
		catch (Exception ignored) {
			return null;
		}
	}
	
	private static String buildLookupSql(int sourceCount) {
		StringBuilder sourceClause = new StringBuilder("cs.name IN (");
		for (int i = 0; i < sourceCount; i++) {
			if (i > 0) {
				sourceClause.append(", ");
			}
			sourceClause.append(":source").append(i);
		}
		sourceClause.append(")");
		return "SELECT crt.code, cs.name AS source_name, COALESCE(crt.name, crt.code) AS display "
		        + "FROM concept_name cn "
		        + "JOIN concept c ON c.concept_id = cn.concept_id AND c.retired = 0 "
		        + "JOIN concept_reference_map crm ON crm.concept_id = c.concept_id "
		        + "JOIN concept_reference_term crt ON crt.concept_reference_term_id = crm.concept_reference_term_id AND crt.retired = 0 "
		        + "JOIN concept_source cs ON cs.concept_source_id = crt.concept_source_id "
		        + "LEFT JOIN concept_map_type cmt ON cmt.concept_map_type_id = crm.concept_map_type "
		        + "WHERE cn.voided = 0 AND LOWER(TRIM(cn.name)) = LOWER(TRIM(:term)) AND " + sourceClause + " "
		        + "ORDER BY CASE WHEN cn.concept_name_type = 'FULLY_SPECIFIED' THEN 0 ELSE 1 END, "
		        + "CASE WHEN cn.locale_preferred = 1 THEN 0 ELSE 1 END, " + "CASE cs.name WHEN '" + SNOMED_SOURCE
		        + "' THEN 0 WHEN '" + LOINC_SOURCE + "' THEN 1 ELSE 2 END, "
		        + "CASE cmt.name WHEN 'SAME-AS' THEN 0 WHEN 'NARROWER-THAN' THEN 1 ELSE 2 END " + "LIMIT 1";
	}
	
	private static List<String> configuredSources() {
		String raw = IhshrPropertyResolver.resolve("concept.coding.sources", "ihshr.concept.coding.sources");
		if (StringUtils.isBlank(raw)) {
			return Arrays.asList(SNOMED_SOURCE, LOINC_SOURCE);
		}
		String[] parts = raw.split(",");
		List<String> sources = new java.util.ArrayList<String>();
		for (String part : parts) {
			if (StringUtils.isNotBlank(part)) {
				sources.add(part.trim());
			}
		}
		return sources.isEmpty() ? Arrays.asList(SNOMED_SOURCE, LOINC_SOURCE) : sources;
	}
	
	static String mapSourceToSystem(String sourceName) {
		if (SNOMED_SOURCE.equalsIgnoreCase(StringUtils.trimToEmpty(sourceName))) {
			return SNOMED_SYSTEM;
		}
		if (LOINC_SOURCE.equalsIgnoreCase(StringUtils.trimToEmpty(sourceName))) {
			return LOINC_SYSTEM;
		}
		return SNOMED_SYSTEM;
	}
	
	private static Coding copyCoding(Coding coding) {
		if (coding == null) {
			return null;
		}
		Coding copy = new Coding();
		copy.setSystem(coding.getSystem());
		copy.setCode(coding.getCode());
		copy.setDisplay(coding.getDisplay());
		return copy;
	}
	
}

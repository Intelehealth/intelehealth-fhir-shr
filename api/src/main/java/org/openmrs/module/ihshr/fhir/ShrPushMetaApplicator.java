package org.openmrs.module.ihshr.fhir;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Resource;
import org.openmrs.module.ihshr.utils.IhshrPropertyResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies doc §5.3 {@code meta.source} and {@code meta.tag} on every resource pushed to SHR
 * (no-echo filter on doctor portal pull).
 */
public final class ShrPushMetaApplicator {
	
	public static final String LEGACY_GP_SOURCE_URI = "intelehealth.shr.source.uri";
	
	private static final String PROPERTY_KEY_SOURCE_URI = "shr.source.uri";
	
	private static final String TAG_ORIGIN_SYSTEM = "https://intelehealth.org/origin";
	
	private static final String TAG_ORIGIN_CODE = "intelehealth";
	
	private static final String TAG_ORIGIN_DISPLAY = "Originated from Intelehealth";
	
	private static final String TAG_LIFECYCLE_SYSTEM = "https://intelehealth.org/lifecycle";
	
	private static final String TAG_LIFECYCLE_CODE = "frozen-at-source";
	
	private static final String TAG_LIFECYCLE_DISPLAY = "Source system does not allow further edits";
	
	private static final Logger LOG = LoggerFactory.getLogger(ShrPushMetaApplicator.class);
	
	private ShrPushMetaApplicator() {
	}
	
	public static String resolveInstallationSourceUri() {
		return IhshrPropertyResolver.resolve(PROPERTY_KEY_SOURCE_URI, LEGACY_GP_SOURCE_URI);
	}
	
	public static void applyPushMeta(Resource resource) {
		applyPushMeta(resource, resolveInstallationSourceUri());
	}
	
	public static void applyPushMeta(Resource resource, String installationSourceUri) {
		if (resource == null) {
			return;
		}
		String sourceUri = StringUtils.trimToNull(installationSourceUri);
		if (StringUtils.isBlank(sourceUri)) {
			LOG.warn("SHR push meta skipped for {}: configure global property {} (unique per IH installation)",
			    resource.fhirType(), LEGACY_GP_SOURCE_URI);
			return;
		}
		Meta meta = resource.getMeta();
		if (meta == null) {
			meta = new Meta();
			resource.setMeta(meta);
		}
		meta.setSource(sourceUri);
		meta.getTag().clear();
		meta.addTag(buildTag(TAG_ORIGIN_SYSTEM, TAG_ORIGIN_CODE, TAG_ORIGIN_DISPLAY));
		meta.addTag(buildTag(TAG_LIFECYCLE_SYSTEM, TAG_LIFECYCLE_CODE, TAG_LIFECYCLE_DISPLAY));
	}
	
	public static void applyPushMeta(Bundle transactionBundle) {
		if (transactionBundle == null || !transactionBundle.hasEntry()) {
			return;
		}
		for (Bundle.BundleEntryComponent entry : transactionBundle.getEntry()) {
			if (entry.hasResource()) {
				applyPushMeta(entry.getResource());
			}
		}
	}
	
	private static Coding buildTag(String system, String code, String display) {
		Coding coding = new Coding();
		coding.setSystem(system);
		coding.setCode(code);
		coding.setDisplay(display);
		return coding;
	}
}

package org.openmrs.module.ihshr.pull;

import org.openmrs.module.ihshr.pull.timeline.ShrTimelineFormatter;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Maps {@link ShrPullResult} to HTTP responses by {@link ShrPullFormat}.
 */
public final class ShrPullResponseSupport {
	
	private ShrPullResponseSupport() {
	}
	
	public static ResponseEntity<?> toResponse(ShrPullResult result, ShrPullService service) {
		ShrPullFormat format = ShrPullFormat.fromRequest(result.getRequest());
		if (format.isFhirBundle()) {
			String body = service.encodeBundle(result.getMerged());
			return ResponseEntity.ok().cacheControl(CacheControl.noStore())
			        .contentType(MediaType.parseMediaType("application/fhir+json")).body(body);
		}
		if (format == ShrPullFormat.TIMELINE) {
			return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ShrTimelineFormatter.format(result));
		}
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.toEnvelope(result));
	}
}

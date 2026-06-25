package org.openmrs.module.ihshr.capability;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.openmrs.module.ihshr.pull.ShrPullRecordType;
import org.openmrs.module.ihshr.pull.ShrPullView;

@SuppressWarnings("unchecked")
public class ShrCapabilityStatementServiceTest {
	
	@Test
	public void getIntegrationRequirements_loadsTemplateAndPatchesMetadata() {
		ShrCapabilityStatementService service = new ShrCapabilityStatementService();
		Map<String, Object> doc = service.getIntegrationRequirements();
		
		assertEquals("CapabilityStatement", doc.get("resourceType"));
		assertEquals("requirements", doc.get("kind"));
		assertNotNull(doc.get("date"));
		
		Map<String, Object> software = (Map<String, Object>) doc.get("software");
		assertEquals("1.0.0-SNAPSHOT", software.get("version"));
		
		List<String> views = (List<String>) doc.get("ihshrPullViews");
		assertEquals(ShrPullView.values().length, views.size());
		assertTrue(views.contains("family-history"));
		
		List<String> recordTypes = (List<String>) doc.get("ihshrRecordTypes");
		assertEquals(ShrPullRecordType.values().length, recordTypes.size());
		assertTrue(recordTypes.contains("FamilyMemberHistory"));
		
		List<Map<String, Object>> rest = (List<Map<String, Object>>) doc.get("rest");
		assertNotNull(rest);
		assertTrue(!rest.isEmpty());
	}
}

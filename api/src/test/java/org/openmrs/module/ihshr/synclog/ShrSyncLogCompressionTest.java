package org.openmrs.module.ihshr.synclog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import org.junit.Test;

public class ShrSyncLogCompressionTest {
	
	@Test
	public void storeRoundTrip_preservesPlainUtf8Json() {
		String json = "{\"resourceType\":\"Bundle\",\"type\":\"transaction\"}";
		byte[] stored = ShrSyncLogCompression.gzip(json);
		assertEquals(json, new String(stored, StandardCharsets.UTF_8));
		assertEquals(json, ShrSyncLogCompression.gunzip(stored));
	}
	
	@Test
	public void gunzip_readsLegacyGzipRows() throws Exception {
		String json = "{\"resourceType\":\"Bundle\",\"type\":\"transaction\"}";
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		GZIPOutputStream gzip = new GZIPOutputStream(out);
		gzip.write(json.getBytes(StandardCharsets.UTF_8));
		gzip.close();
		assertEquals(json, ShrSyncLogCompression.gunzip(out.toByteArray()));
	}
	
	@Test
	public void storedPayload_isReadableJsonNotGzipMagic() {
		String json = "{\"resourceType\":\"Bundle\"}";
		byte[] stored = ShrSyncLogCompression.gzip(json);
		assertTrue(stored.length >= 2);
		assertTrue(stored[0] == '{' || stored[0] == '[');
	}
}

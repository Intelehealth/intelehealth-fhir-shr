package org.openmrs.module.ihshr.synclog;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Sync log bundle encoding for {@code request_bundle} / {@code response_bundle} (doc §13.1). New
 * rows store plain UTF-8 JSON so the columns are readable in SQL clients; legacy gzip rows are
 * still decoded on read.
 */
public final class ShrSyncLogCompression {
	
	private ShrSyncLogCompression() {
	}
	
	public static byte[] gzip(String plainText) {
		if (plainText == null) {
			return null;
		}
		return plainText.getBytes(StandardCharsets.UTF_8);
	}
	
	public static String gunzip(byte[] stored) {
		if (stored == null || stored.length == 0) {
			return null;
		}
		if (isGzip(stored)) {
			return gunzipLegacy(stored);
		}
		return new String(stored, StandardCharsets.UTF_8);
	}
	
	private static boolean isGzip(byte[] data) {
		return data.length >= 2 && (data[0] & 0xFF) == 0x1F && (data[1] & 0xFF) == 0x8B;
	}
	
	private static String gunzipLegacy(byte[] compressed) {
		try {
			GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed));
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buffer = new byte[4096];
			int read;
			while ((read = gzip.read(buffer)) >= 0) {
				out.write(buffer, 0, read);
			}
			gzip.close();
			return out.toString(StandardCharsets.UTF_8.name());
		}
		catch (IOException ex) {
			throw new IllegalStateException("Unable to gunzip legacy sync log payload", ex);
		}
	}
}

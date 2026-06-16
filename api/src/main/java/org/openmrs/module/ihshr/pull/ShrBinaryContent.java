package org.openmrs.module.ihshr.pull;

/**
 * Binary payload for streaming proxy (doc §11.4).
 */
public class ShrBinaryContent {
	
	private final String id;
	
	private final String contentType;
	
	private final byte[] data;
	
	public ShrBinaryContent(String id, String contentType, byte[] data) {
		this.id = id;
		this.contentType = contentType;
		this.data = data;
	}
	
	public String getId() {
		return id;
	}
	
	public String getContentType() {
		return contentType;
	}
	
	public byte[] getData() {
		return data;
	}
}

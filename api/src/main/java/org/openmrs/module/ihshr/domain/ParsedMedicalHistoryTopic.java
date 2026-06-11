package org.openmrs.module.ihshr.domain;

public class ParsedMedicalHistoryTopic {
	
	private final String topicKey;
	
	private final String topicLabel;
	
	private final String value;
	
	public ParsedMedicalHistoryTopic(String topicKey, String topicLabel, String value) {
		this.topicKey = topicKey;
		this.topicLabel = topicLabel;
		this.value = value;
	}
	
	public String getTopicKey() {
		return topicKey;
	}
	
	public String getTopicLabel() {
		return topicLabel;
	}
	
	public String getValue() {
		return value;
	}
	
}

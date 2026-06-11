package org.openmrs.module.ihshr.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.openmrs.module.ihshr.config.ShrLookupLoader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Loads {@code patient-history-topics.json} from {@code shr-config/lookups/} (doc §7.4, §16.3).
 */
public class MedicalHistoryTopicConfig {
	
	public static final String FILE_NAME = "patient-history-topics.json";
	
	private static volatile MedicalHistoryTopicConfig instance;
	
	private final List<String> negativePatterns;
	
	private final Map<String, JsonObject> topicsByKey;
	
	private MedicalHistoryTopicConfig(List<String> negativePatterns, Map<String, JsonObject> topicsByKey) {
		this.negativePatterns = negativePatterns;
		this.topicsByKey = topicsByKey;
	}
	
	public static MedicalHistoryTopicConfig getInstance() {
		if (instance == null) {
			synchronized (MedicalHistoryTopicConfig.class) {
				if (instance == null) {
					instance = load();
				}
			}
		}
		return instance;
	}
	
	public static void reload() {
		synchronized (MedicalHistoryTopicConfig.class) {
			ShrLookupLoader.clearCache();
			instance = load();
		}
	}
	
	public List<String> getNegativePatterns() {
		return negativePatterns;
	}
	
	public JsonObject getTopicConfig(String topicKey) {
		if (topicKey == null) {
			return null;
		}
		return topicsByKey.get(topicKey.toLowerCase());
	}
	
	public Map<String, JsonObject> getAllTopics() {
		return Collections.unmodifiableMap(topicsByKey);
	}
	
	private static MedicalHistoryTopicConfig load() {
		JsonObject root = ShrLookupLoader.load(FILE_NAME);
		List<String> negatives = Collections.emptyList();
		if (root.has("negative_patterns") && root.get("negative_patterns").isJsonArray()) {
			JsonArray arr = root.getAsJsonArray("negative_patterns");
			negatives = new ArrayList<String>();
			for (JsonElement el : arr) {
				negatives.add(el.getAsString());
			}
		}
		Map<String, JsonObject> topics = new LinkedHashMap<String, JsonObject>();
		if (root.has("topics") && root.get("topics").isJsonObject()) {
			JsonObject topicsObj = root.getAsJsonObject("topics");
			for (Map.Entry<String, JsonElement> entry : topicsObj.entrySet()) {
				if (entry.getValue().isJsonObject()) {
					topics.put(entry.getKey().toLowerCase(), entry.getValue().getAsJsonObject());
				}
			}
		}
		return new MedicalHistoryTopicConfig(negatives, topics);
	}
	
}

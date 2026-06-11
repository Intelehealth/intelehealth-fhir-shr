package org.openmrs.module.ihshr.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Coding;
import org.openmrs.module.ihshr.utils.IhshrPropertyResolver;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Loads SNOMED / LOINC lookup tables from {@code /opt/intelehealth/shr-config/lookups/} (doc §8,
 * §16.3) with classpath fallback {@code shr-config/lookups/}.
 */
public final class ShrLookupLoader {
	
	public static final String DEFAULT_LOOKUP_DIR = "/opt/intelehealth/shr-config";
	
	public static final String LOOKUP_SUBDIR = "lookups";
	
	public static final String CLASSPATH_LOOKUP_PREFIX = "shr-config/lookups/";
	
	private static final Gson GSON = new Gson();
	
	private static final Map<String, JsonObject> CACHE = new ConcurrentHashMap<String, JsonObject>();
	
	private ShrLookupLoader() {
	}
	
	public static void clearCache() {
		CACHE.clear();
	}
	
	public static JsonObject load(String fileName) {
		if (StringUtils.isBlank(fileName)) {
			return new JsonObject();
		}
		String key = fileName.trim();
		JsonObject cached = CACHE.get(key);
		if (cached != null) {
			return cached;
		}
		JsonObject loaded = readJson(key);
		CACHE.put(key, loaded);
		return loaded;
	}
	
	public static Coding lookupMapping(String fileName, String term) {
		if (StringUtils.isBlank(term)) {
			return null;
		}
		JsonObject root = load(fileName);
		if (!root.has("mappings") || !root.get("mappings").isJsonObject()) {
			return null;
		}
		JsonObject mappings = root.getAsJsonObject("mappings");
		String system = root.has("default_system") ? root.get("default_system").getAsString() : "http://snomed.info/sct";
		JsonElement hit = mappings.get(normalizeKey(term));
		if (hit == null) {
			hit = findCaseInsensitive(mappings, term);
		}
		if (hit == null || !hit.isJsonObject()) {
			return null;
		}
		JsonObject coding = hit.getAsJsonObject();
		if (!coding.has("code")) {
			return null;
		}
		Coding result = new Coding();
		result.setSystem(coding.has("system") ? coding.get("system").getAsString() : system);
		result.setCode(coding.get("code").getAsString());
		if (coding.has("display")) {
			result.setDisplay(coding.get("display").getAsString());
		}
		return result;
	}
	
	public static Coding lookupCategory(String fileName, String categoryName) {
		if (StringUtils.isBlank(categoryName)) {
			return null;
		}
		JsonObject root = load(fileName);
		if (!root.has("categories") || !root.get("categories").isJsonObject()) {
			return null;
		}
		JsonObject categories = root.getAsJsonObject("categories");
		String key = normalizeCategoryKey(categoryName);
		JsonElement hit = categories.get(key);
		if (hit == null) {
			hit = findCaseInsensitive(categories, key);
		}
		if (hit == null || !hit.isJsonObject()) {
			return null;
		}
		JsonObject coding = hit.getAsJsonObject();
		String system = root.has("default_system") ? root.get("default_system").getAsString() : "http://snomed.info/sct";
		Coding result = new Coding();
		result.setSystem(system);
		result.setCode(coding.get("code").getAsString());
		if (coding.has("display")) {
			result.setDisplay(coding.get("display").getAsString());
		}
		return result;
	}
	
	private static JsonObject readJson(String fileName) {
		File file = resolveFile(fileName);
		if (file != null && file.isFile()) {
			try (InputStream in = new FileInputStream(file)) {
				return GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
			}
			catch (Exception ignored) {
				// fall through to classpath
			}
		}
		String classpath = CLASSPATH_LOOKUP_PREFIX + fileName;
		InputStream in = ShrLookupLoader.class.getClassLoader().getResourceAsStream(classpath);
		if (in == null) {
			return new JsonObject();
		}
		try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
			return GSON.fromJson(reader, JsonObject.class);
		}
		catch (Exception ignored) {
			return new JsonObject();
		}
	}
	
	private static File resolveFile(String fileName) {
		String baseDir = resolveLookupDir();
		if (StringUtils.isBlank(baseDir)) {
			return null;
		}
		File lookups = new File(baseDir, LOOKUP_SUBDIR);
		File candidate = new File(lookups, fileName);
		if (candidate.isFile()) {
			return candidate;
		}
		File direct = new File(baseDir, fileName);
		return direct.isFile() ? direct : null;
	}
	
	public static String resolveLookupDir() {
		String dir = System.getProperty("ihshr.shr.lookup.dir");
		if (StringUtils.isBlank(dir)) {
			dir = IhshrPropertyResolver.resolve("shr.lookup.dir", "intelehealth.shr.lookup_dir");
		}
		if (StringUtils.isBlank(dir)) {
			dir = DEFAULT_LOOKUP_DIR;
		}
		return dir.trim();
	}
	
	private static String normalizeKey(String term) {
		return term.trim().toLowerCase(Locale.ROOT);
	}
	
	private static String normalizeCategoryKey(String categoryName) {
		String key = categoryName == null ? "" : categoryName.trim().toLowerCase(Locale.ROOT);
		if (key.endsWith(":")) {
			key = key.substring(0, key.length() - 1).trim();
		}
		return key;
	}
	
	private static JsonElement findCaseInsensitive(JsonObject object, String term) {
		String needle = normalizeKey(term);
		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			if (entry.getKey().trim().toLowerCase(Locale.ROOT).equals(needle)) {
				return entry.getValue();
			}
		}
		return null;
	}
	
}

package org.openmrs.module.ihshr.utils;

import java.io.InputStream;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads {@code ihshr.properties} from the classpath (merged in order when multiple files are
 * passed).
 */
public final class ModuleClasspathPropertiesLoader {
	
	private static final Logger log = LoggerFactory.getLogger(ModuleClasspathPropertiesLoader.class);
	
	private ModuleClasspathPropertiesLoader() {
	}
	
	public static Properties loadMergedInOrder(String... resourceNames) {
		Properties merged = loadMergedInOrder(Thread.currentThread().getContextClassLoader(), resourceNames);
		if (merged != null) {
			return merged;
		}
		return loadMergedInOrder(ModuleClasspathPropertiesLoader.class.getClassLoader(), resourceNames);
	}
	
	static Properties loadMergedInOrder(ClassLoader classLoader, String... resourceNames) {
		if (classLoader == null || resourceNames == null || resourceNames.length == 0) {
			return null;
		}
		Properties merged = new Properties();
		boolean any = false;
		for (String resource : resourceNames) {
			if (StringUtils.isBlank(resource)) {
				continue;
			}
			try (InputStream in = classLoader.getResourceAsStream(resource)) {
				if (in == null) {
					continue;
				}
				Properties fragment = new Properties();
				fragment.load(in);
				merged.putAll(fragment);
				any = true;
				log.info("Merged SHR module classpath properties from '{}'", resource);
			}
			catch (Exception ex) {
				log.warn("Unable to load SHR module classpath properties '{}': {}", resource, ex.getMessage());
			}
		}
		return any ? merged : null;
	}
}

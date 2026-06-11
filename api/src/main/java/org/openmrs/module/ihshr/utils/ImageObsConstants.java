package org.openmrs.module.ihshr.utils;

public final class ImageObsConstants {
	
	public static final int COMPLEX_IMAGE_CONCEPT_1 = 163371;
	
	public static final int COMPLEX_IMAGE_CONCEPT_2 = 163372;
	
	public static final String IDENTIFIER_SYSTEM = "urn:intelehealth:openmrs:obs";
	
	public static final String DEFAULT_COMPLEX_OBS_DIR = System.getProperty("user.home") + "/.OpenMRS/complex_obs";
	
	public static final String COMPLEX_OBS_DIR_PROPERTY = "ihshr.image.obs.dir";
	
	private ImageObsConstants() {
	}
}

package org.openmrs.module.ihshr.fhir;

import java.util.Locale;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Observation.ObservationComponentComponent;
import org.hl7.fhir.r4.model.Observation.ObservationStatus;
import org.openmrs.module.ihshr.backlog.UnmappedTermArtifact;
import org.openmrs.module.ihshr.backlog.UnmappedTermBacklog;
import org.openmrs.module.ihshr.backlog.UnmappedTermLookupType;
import org.openmrs.module.ihshr.config.ClinicalTermCodingResolver;
import org.openmrs.module.ihshr.domain.ParsedExamCategory;
import org.openmrs.module.ihshr.domain.ParsedFinding;
import org.openmrs.module.ihshr.parser.PhysicalExamValueTexts;
import org.openmrs.module.ihshr.utils.PhysicalExamConstants;

public class PhysicalExamObservationBuilder {
	
	private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
	
	private static final String EXAM_BODYSITE_MAPPINGS = "exam-bodysite-mappings.json";
	
	private static final String PHYSICAL_EXAM_MAPPINGS = "physical-exam-mappings.json";
	
	public Observation build(Observation source, String obsUuid, ParsedExamCategory category, String sharedNote) {
		Observation observation = source.copy();
		observation.setId((String) null);
		observation.setValue(null);
		observation.getIdentifier().clear();
		
		Identifier identifier = new Identifier();
		identifier.setSystem(PhysicalExamConstants.OBS_IDENTIFIER_SYSTEM);
		identifier.setValue(obsUuid + "::cat-" + slugify(category.getCategoryName()));
		observation.addIdentifier(identifier);
		
		observation.setStatus(ObservationStatus.FINAL);
		
		CodeableConcept categoryConcept = new CodeableConcept();
		categoryConcept.addCoding(new Coding().setSystem(PhysicalExamConstants.EXAM_CATEGORY_SYSTEM).setCode(
		    PhysicalExamConstants.EXAM_CATEGORY_CODE));
		observation.getCategory().clear();
		observation.addCategory(categoryConcept);
		
		CodeableConcept code = new CodeableConcept();
		code.addCoding(new Coding().setSystem(PhysicalExamConstants.EXAM_PROCEDURE_SYSTEM)
		        .setCode(PhysicalExamConstants.EXAM_PROCEDURE_CODE).setDisplay(PhysicalExamConstants.EXAM_PROCEDURE_DISPLAY));
		code.setText(category.getCategoryName());
		observation.setCode(code);
		
		observation.setBodySite(bodySiteFor(category.getCategoryName()));
		
		observation.getComponent().clear();
		for (ParsedFinding finding : category.getFindings()) {
			ObservationComponentComponent component = new ObservationComponentComponent();
			CodeableConcept componentCode = new CodeableConcept();
			componentCode.setText(finding.getItem());
			String componentKey = finding.getItem();
			if (StringUtils.isNotBlank(finding.getFinding())) {
				componentKey = finding.getItem() + ": " + finding.getFinding();
			}
			Coding componentSnomed = ClinicalTermCodingResolver.lookupMapping(componentKey, PHYSICAL_EXAM_MAPPINGS);
			if (componentSnomed == null) {
				componentSnomed = ClinicalTermCodingResolver.lookupMapping(finding.getItem(), PHYSICAL_EXAM_MAPPINGS);
			}
			if (componentSnomed != null) {
				componentCode.addCoding(componentSnomed);
			} else {
				UnmappedTermBacklog.recordMiss(UnmappedTermArtifact.PHYSICAL_EXAM, PHYSICAL_EXAM_MAPPINGS,
				    UnmappedTermLookupType.MAPPING, componentKey);
			}
			component.setCode(componentCode);
			if (StringUtils.isNotBlank(finding.getFinding())) {
				component.setValue(new org.hl7.fhir.r4.model.StringType(finding.getFinding()));
			}
			observation.addComponent(component);
		}
		
		observation.getNote().clear();
		if (StringUtils.isNotBlank(sharedNote)) {
			observation.addNote().setText(sharedNote);
		}
		
		return observation;
	}
	
	public static String stripHtmlForNote(String raw) {
		if (StringUtils.isBlank(raw)) {
			return "";
		}
		String clinical = PhysicalExamValueTexts.normalizeHtml(PhysicalExamValueTexts.extractClinicalHtml(raw));
		String text = HTML_TAG.matcher(clinical).replaceAll(" ");
		text = text.replace("&nbsp;", " ").replace("<br/>", "\n").replace("<br>", "\n");
		text = text.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
		text = text.replaceAll("[•\\u2022●\\u25CF]\\s*", " ");
		text = PhysicalExamValueTexts.stripPictureTakenMarkers(text);
		return text.replaceAll("\\s+", " ").trim();
	}
	
	private static CodeableConcept bodySiteFor(String categoryName) {
		CodeableConcept bodySite = new CodeableConcept();
		Coding site = ClinicalTermCodingResolver.resolveCategory(categoryName, EXAM_BODYSITE_MAPPINGS,
		    UnmappedTermArtifact.PHYSICAL_EXAM_BODYSITE);
		if (site != null) {
			bodySite.addCoding(site);
			bodySite.setText(site.getDisplay());
		} else {
			bodySite.setText(categoryName);
		}
		return bodySite;
	}
	
	private static String slugify(String categoryName) {
		if (categoryName == null) {
			return "unknown";
		}
		String slug = categoryName.trim().toLowerCase(Locale.ROOT);
		slug = slug.replaceAll("[^a-z0-9]+", "-");
		slug = slug.replaceAll("^-+|-+$", "");
		return slug.isEmpty() ? "unknown" : slug;
	}
	
}

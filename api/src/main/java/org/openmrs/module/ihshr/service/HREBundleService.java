package org.openmrs.module.ihshr.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Dosage;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.openmrs.module.ihshr.config.FhirConfig;
import org.openmrs.module.ihshr.datatype.OrderType;
import org.openmrs.module.ihshr.domain.MedicationRequestDTO;
import org.openmrs.module.ihshr.domain.ObservationDTO;
import org.openmrs.module.ihshr.exp.InvalidParamException;
import org.openmrs.module.ihshr.utils.ReqParam;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openmrs.api.context.Context;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;

@Service("ihshrHreBundleService")
public class HREBundleService {
	
	public String getBundle(String resourceType, Map<String, String> reqParam) {
		
		String mpiId = reqParam.getOrDefault("mpiId", "");
		
		if (mpiId.isEmpty()) {
			throw new InvalidParamException("Patient Identifier (mpiId) parameter is missing");
		}
		
		reqParam.remove("mpiId");
		
		reqParam.put("patient.identifier", mpiId);
		reqParam.put("_count", "200");
		
		Bundle results = Context.getRegisteredComponent("ihshrFhirConfig", FhirConfig.class).getOpenCRFhirContext().search()
		        .byUrl(resourceType + "?" + ReqParam.toQueryParam(reqParam)).returnBundle(Bundle.class).execute();
		
		if (resourceType.equals("Observation")) {
			return parseObservation(results);
		} else if (resourceType.equals("MedicationRequest")) {
			return parseMedicationRequest(results);
		} else if (resourceType.equals("ServiceRequest")) {
			return parseServiceRequest(results);
		} else {
			
			Bundle filterData = removeIHData(resourceType, results, mpiId);
			
			String response = Context.getRegisteredComponent("ihshrFhirConfig", FhirConfig.class).newJsonParser()
			        .setPrettyPrint(true).encodeResourceToString(filterData);
			
			return response;
		}
	}
	
	private Bundle removeIHData(String resourceType, Bundle bundle, String mpiId) {
		
		Bundle newBundle = new Bundle();
		newBundle.setType(Bundle.BundleType.COLLECTION);
		
		newBundle.setEntry(bundle.getEntry());
		Iterator<BundleEntryComponent> iterator = newBundle.getEntry().iterator();
		
		HashSet<String> keys = new HashSet<String>();
		
		if (resourceType.equals("Encounter")) {
			keys = getCommonOperationService().getEncountersByMpi(mpiId);
		} else if (resourceType.equals("Observation")) {
			keys = getCommonOperationService().getObservationsByMpi(mpiId);
		} else if (resourceType.equals("ServiceRequest")) {
			keys = getCommonOperationService()
			        .getServiceRequestByMpiAndOrderType(mpiId, OrderType.LAB_ORDER.getValue() + "");
		} else if (resourceType.equals("MedicationRequest")) {
			keys = getCommonOperationService().getServiceRequestByMpiAndOrderType(mpiId,
			    OrderType.DRUG_ORDER.getValue() + "");
		}
		
		while (iterator.hasNext()) {
			BundleEntryComponent bundleEntry = iterator.next();
			
			Resource resource = bundleEntry.getResource();
			
			String id = resource.getIdElement().getIdPart();
			
			if (keys.contains(id)) {
				iterator.remove();
			}
		}
		return newBundle;
	}
	
	private String parseObservation(Bundle bundle) {

		Iterator<BundleEntryComponent> iterator = bundle.getEntry().iterator();

		ArrayList<ObservationDTO> observations = new ArrayList<>();

		HashMap<String, ArrayList> vitalMap = new HashMap<>();
		HashMap<String, ArrayList> complainMap = new HashMap<>();
		HashMap<String, ArrayList> physicalMap = new HashMap<>();
		HashMap<String, ArrayList> familyHistoryMap = new HashMap<>();
		HashMap<String, ArrayList> medicalHistoryMap = new HashMap<>();
		HashMap<String, ArrayList> mentalFormMap = new HashMap<>();

		ArrayList<Object> referral = new ArrayList<>();

		while (iterator.hasNext()) {
			BundleEntryComponent bundleEntry = iterator.next();
			Observation obs = (Observation) bundleEntry.getResource();
			HashMap<String, Object> obsItem = new HashMap<>();
			try {
				if ((obs.getCode()!=null && obs.getCode().getText()!=null && obs.getCode().getText().toUpperCase().contains("COMPLAINT"))
						|| hasSnomedCode(obs.getCode(), "422843007")
						|| hasCodeDisplay(obs.getCode(), "COMPLAINT")) {
					String encounter = obs.getEncounter().getReference();
					ArrayList<String> listItem = complainMap.getOrDefault(encounter, new ArrayList<>());
					listItem.addAll(parseHTML(obs.getValueStringType().getValueAsString(), "CURRENT COMPLAINT"));
					complainMap.put(encounter, listItem);
				} else if ((obs.getCode()!=null && obs.getCode().getText()!=null && obs.getCode().getText().toUpperCase().contains("PHYSICAL EXAMINATION"))
						|| hasSnomedCode(obs.getCode(), "425044008")
						|| hasCodeDisplay(obs.getCode(), "PHYSICAL EXAMINATION")) {
					String encounter = obs.getEncounter().getReference();
					ArrayList<String> listItem = physicalMap.getOrDefault(encounter, new ArrayList<>());
					listItem.addAll(parseHTML(obs.getValueStringType().getValueAsString(), "PHYSICAL EXAMINATION"));
					physicalMap.put(encounter, listItem);
				} else if ((obs.getCode()!=null && obs.getCode().getText()!=null && obs.getCode().getText().toUpperCase().contains("FAMILY HISTORY"))
						|| hasSnomedCode(obs.getCode(), "422432008")
						|| hasCodeDisplay(obs.getCode(), "FAMILY HISTORY")) {
					String encounter = obs.getEncounter().getReference();
					ArrayList<String> listItem = familyHistoryMap.getOrDefault(encounter, new ArrayList<>());
					listItem.addAll(parseHTML(obs.getValueStringType().getValueAsString(), "FAMILY HISTORY"));
					familyHistoryMap.put(encounter, listItem);
				} else if ((obs.getCode()!=null && obs.getCode().getText()!=null && obs.getCode().getText().toUpperCase().contains("MEDICAL HISTORY"))
						|| hasSnomedCode(obs.getCode(), "371529009")
						|| hasCodeDisplay(obs.getCode(), "MEDICAL HISTORY")) {

					String encounter = obs.getEncounter().getReference();
					ArrayList<String> listItem = medicalHistoryMap.getOrDefault(encounter, new ArrayList<>());
					listItem.addAll(parseHTML(obs.getValueStringType().getValueAsString(), "MEDICAL HISTORY"));
					medicalHistoryMap.put(encounter, listItem);
				} else if ((obs.getCode()!=null && obs.getCode().getText()!=null && obs.getCode().getText().contains("Referral"))) {
//					referral.addAll(parseHTML(obs.getValueStringType().getValueAsString(),"Referral"));
				} else if (obs.getCategory() != null && !obs.getCategory().isEmpty()
						&& obs.getCategory().get(0).getCoding().get(0).getCode().equals("exam")) {

					String encounter = obs.getEncounter().getReference();
					ArrayList<String> vitalSample = vitalMap.getOrDefault(encounter, new ArrayList<>());
					if (obs.getValueQuantity() != null) {
						String vital="";
						if(obs.getCode()!=null && obs.getCode().getText()!=null) {
							 vital = obs.getCode().getText() + " : " + obs.getValueQuantity().getValue();
						}else {
							 vital = obs.getCode().getCoding().get(0).getDisplay() + " : " + obs.getValueQuantity().getValue();
						}
						
						if(!vital.equals("")) {
							vitalSample.add(vital);
							vitalMap.put(encounter, vitalSample);
						}
					}
				} else {
					String encounter = obs.getEncounter().getReference();
					String question = obs.getCode().getText();
					if (question.endsWith("?")) {
						String answer = obs.getValueStringType().getValue();
						System.err.println(question + " " + answer);
						ArrayList<String> mentalFormSample = mentalFormMap.getOrDefault(encounter,
								new ArrayList<String>());
						mentalFormSample.add(question + " : " + answer);
						mentalFormMap.put(encounter, mentalFormSample);
					}

				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		ObservationDTO vitalObs = new ObservationDTO();
		vitalObs.setTitle("Vitals");
		vitalObs.setData(vitalMap.values());
		observations.add(vitalObs);

		ObservationDTO currComplaintObs = new ObservationDTO();
		currComplaintObs.setTitle("Current Complaint");
		currComplaintObs.setData(complainMap.values());
		observations.add(currComplaintObs);

		ObservationDTO physicalExamObs = new ObservationDTO();
		physicalExamObs.setTitle("Physical Examination");
		physicalExamObs.setData(physicalMap.values());
		observations.add(physicalExamObs);

		ObservationDTO familyObs = new ObservationDTO();
		familyObs.setTitle("Family History");
		familyObs.setData(familyHistoryMap.values());
		observations.add(familyObs);

		ObservationDTO medicalHistoryObs = new ObservationDTO();
		medicalHistoryObs.setTitle("Medical History");
		medicalHistoryObs.setData(medicalHistoryMap.values());
		observations.add(medicalHistoryObs);

		ObservationDTO mentalFormObs = new ObservationDTO();
		mentalFormObs.setTitle("Patient Mental Health");
		mentalFormObs.setData(mentalFormMap.values());
		observations.add(mentalFormObs);

		Gson gson = new Gson();
		String response = gson.toJson(observations);
		return response;
	}
	
	private ArrayList<String> parseVitals(ArrayList<String> vitals) {
		if (vitals == null || vitals.isEmpty())
			return new ArrayList<>();

		String high = "0.0";
		String low = "0.0";
		String temp = "";

		Iterator<String> iterator = vitals.iterator();

		while (iterator.hasNext()) {
			String token = iterator.next(); // Properly define pressure inside loop

			if (token.toLowerCase().contains("systolic")) {
				high = token.split(":")[1].trim();
				iterator.remove();
			} else if (token.toLowerCase().contains("diastolic")) { // Ensure exact case match
				low = token.split(":")[1].trim();
				iterator.remove();
			} else if (token.toLowerCase().contains("temperature")) {
				temp = token.toLowerCase();
				temp = "T" + temp.substring(1);
				iterator.remove();
			}
		}

		vitals.add(temp);
		vitals.add("BP : " + high + " / " + low);
		return vitals;
	}
	
	private ArrayList<String> parseHTML(String json, String key) {

		try {
			ObjectMapper objectMapper = new ObjectMapper();
			JsonNode rootNode = objectMapper.readTree(json);
			String enHtml = rootNode.has("en") ? rootNode.get("en").asText() : "";
			if (enHtml.isEmpty()) {
				System.out.println("No 'en' field found for key: " + key);
				return new ArrayList<>();
			}
			return lineParser(enHtml);
		} catch (Exception e) {
			e.printStackTrace();
			return new ArrayList<>();
		}
	}
	
	private ArrayList<String> lineParser(String html) {
		String newHTML = html.replaceAll("<br>", "").replaceAll("<b>", "").replaceAll("</b>", "").replaceAll("•", "")
				.replaceAll("►", "");

		String[] lines = newHTML.split("<br/>");

		ArrayList<String> lineItems = new ArrayList<>();

		for (String line : lines) {
			String newLine = line.replaceAll("^\\?", "").trim();
			if (newLine.isEmpty())
				continue;
			if (newLine.endsWith(":"))
				continue;
			lineItems.add(newLine);
			System.out.println(newLine);
		}

		return lineItems;
	}
	
	private String parseMedicationRequest(Bundle bundle) {

		Iterator<BundleEntryComponent> iterator = bundle.getEntry().iterator();

		ArrayList<MedicationRequestDTO> medications = new ArrayList<>();

		while (iterator.hasNext()) {
			BundleEntryComponent bundleEntry = iterator.next();
			try {
				MedicationRequest medReq = (MedicationRequest) bundleEntry.getResource();
				MedicationRequestDTO medReqDTO = new MedicationRequestDTO();

				String medicationName = medReq.getMedicationReference().getDisplay();
				String dosage = medReq.getDosageInstructionFirstRep().getText();
				if (dosage == null) {
					String json = Context.getRegisteredComponent("ihshrFhirConfig", FhirConfig.class).newJsonParser()
					        .setPrettyPrint(true).encodeResourceToString(medReq);
					JSONObject jo = new JSONObject(json);
					JSONArray doIns = jo.getJSONArray("dosageInstruction");
					JSONObject timing = doIns.getJSONObject(0).getJSONObject("timing");
					JSONObject code = timing.getJSONObject("code");
					dosage = (String) code.get("text");
				}
				BigDecimal days = medReq.getDosageInstructionFirstRep().getTiming().getRepeat().getDuration();
				String doctorName = medReq.getRequester().getDisplay();

				System.out.println(medicationName + ", " + dosage + ", " + days + ", " + doctorName);

				medReqDTO.setMedicationName(medicationName);
				medReqDTO.setDays(days);
				medReqDTO.setDoctorName(doctorName.split(" \\(")[0]);
				medReqDTO.setDosage(dosage.split(":")[0]);
				medications.add(medReqDTO);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		Gson gson = new Gson();
		String response = gson.toJson(medications);
		return response;
	}
	
	private String parseServiceRequest(Bundle bundle) {
		Iterator<BundleEntryComponent> iterator = bundle.getEntry().iterator();
		ArrayList<HashMap> testList = new ArrayList<>();
		while (iterator.hasNext()) {
			BundleEntryComponent bundleEntry = iterator.next();
			try {
				ServiceRequest serReq = (ServiceRequest) bundleEntry.getResource();
				HashMap<String, Object> map = new HashMap<>();
				map.put("testName", serReq.getCode().getText());
				map.put("doctorName", serReq.getRequester().getDisplay().split(" \\(")[0]);
				map.put("testGivenDate", serReq.getOccurrencePeriod().getStart());
				testList.add(map);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		Gson gson = new Gson();
		String response = gson.toJson(testList);
		return response;
	}
	
	private boolean hasSnomedCode(CodeableConcept code, String snomedCTCode) {
		if (code == null)
			return false;
		
		for (Coding coding : code.getCoding()) {
			if (coding.getCode().equals(snomedCTCode))
				return true;
		}
		
		return false;
	}
	
	private boolean hasCodeDisplay(CodeableConcept code, String displayName) {
		if (code == null)
			return false;
		
		for (Coding coding : code.getCoding()) {
			if (coding.getDisplay() != null && coding.getDisplay().equals(displayName))
				return true;
		}
		
		return false;
	}
	
	private CommonOperationService getCommonOperationService() {
		return Context.getRegisteredComponent("ihshrCommonOperationService", CommonOperationService.class);
	}
}

package org.openmrs.module.ihshr.service;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.r4.model.Base64BinaryType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.openmrs.module.ihmodule.api.patientexchange.config.FhirContextHolder;
import org.openmrs.module.ihshr.domain.DiagnosticReportDTO;
import org.openmrs.module.ihshr.utils.HttpWebClient;
import org.openmrs.module.ihshr.utils.IHConstant;
import org.openmrs.module.ihshr.utils.ReqParam;
import org.springframework.stereotype.Service;

@Service("ihshrDiagnosticReportService")
public class DiagnosticReportService extends IHConstant {
	
	public List<DiagnosticReportDTO> getReport(Map<String, String> reqParam) throws UnsupportedEncodingException {
		String[] credentials = getOpencrOpenhimAuthentication().split(":", 2);
		String param = ReqParam.toQueryParam(reqParam);
		String response = HttpWebClient.get(getOpencrOpenhimURL(), "/DiagnosticReport?" + param, credentials[0],
		    credentials[1]);
		
		Bundle theBundle = FhirContextHolder.R4.newJsonParser().parseResource(Bundle.class, response);
		
		return parseBundle(theBundle);
	}
	
	private List<DiagnosticReportDTO> parseBundle(Bundle bundle) {
		List<DiagnosticReportDTO> reports = new ArrayList<>();
		for (BundleEntryComponent bundleEntry : bundle.getEntry()) {

			DiagnosticReport dReport = (DiagnosticReport) bundleEntry.getResource();
			DiagnosticReportDTO dto = new DiagnosticReportDTO();

			String resourceId = dReport.getIdElement().getIdPart();
			dto.setResourceId(resourceId);

			if (dReport.hasSubject()) {
				dto.setPatientId(dReport.getSubject().getReference().split("/")[1]);
			}

			if (dReport.hasPresentedForm()) {
				String contentType = dReport.getPresentedForm().get(0).getContentType();
				Base64BinaryType base64data = dReport.getPresentedForm().get(0).getDataElement();
				dto.setContentType(contentType);
				dto.setFileData(base64data.getValueAsString());
				dto.setTitle(dReport.getPresentedForm().get(0).getTitle());
				reports.add(dto);
			} 
		}

		return reports;
	}
}

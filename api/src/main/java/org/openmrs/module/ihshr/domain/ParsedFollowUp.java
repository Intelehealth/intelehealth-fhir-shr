package org.openmrs.module.ihshr.domain;

import java.util.Date;

public class ParsedFollowUp {
	
	private Date scheduledDateTime;
	
	private String dateText;
	
	private String timeText;
	
	private String remark;
	
	private String visitType;
	
	public Date getScheduledDateTime() {
		return scheduledDateTime;
	}
	
	public void setScheduledDateTime(Date scheduledDateTime) {
		this.scheduledDateTime = scheduledDateTime;
	}
	
	public String getDateText() {
		return dateText;
	}
	
	public void setDateText(String dateText) {
		this.dateText = dateText;
	}
	
	public String getTimeText() {
		return timeText;
	}
	
	public void setTimeText(String timeText) {
		this.timeText = timeText;
	}
	
	public String getRemark() {
		return remark;
	}
	
	public void setRemark(String remark) {
		this.remark = remark;
	}
	
	public String getVisitType() {
		return visitType;
	}
	
	public void setVisitType(String visitType) {
		this.visitType = visitType;
	}
	
}

package org.openmrs.module.ihshr.domain;

public class ConfigDataSync {
	
	private int id;
	
	private String name;
	
	private boolean status;
	
	public int getId() {
		return id;
	}
	
	public void setId(int id) {
		this.id = id;
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public boolean getStatus() {
		return status;
	}
	
	public void setStatus(boolean status) {
		this.status = status;
	}
	
	@Override
	public String toString() {
		return "ConfigDataSync [id=" + id + ", name=" + name + ", status=" + status + "]";
	}
	
}

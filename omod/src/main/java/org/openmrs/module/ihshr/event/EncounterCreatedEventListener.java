package org.openmrs.module.ihshr.event;

import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.Encounter;
import org.openmrs.api.context.Context;
import org.openmrs.module.ihshr.datatype.EncounterType;
import org.openmrs.api.context.Daemon;
import org.openmrs.event.Event;
import org.openmrs.event.EventListener;
import org.openmrs.module.DaemonToken;
import org.openmrs.module.ihshr.IhshrModuleActivator;
import org.openmrs.module.ihshr.scheduler.DataSendToSHR;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles {@link Encounter} {@link Event.Action#CREATED} events (push flow step 2) and triggers
 * visit-wide SHR sync when a visit-complete encounter is created.
 */
public class EncounterCreatedEventListener implements EventListener {
	
	private static final Logger LOG = LoggerFactory.getLogger(EncounterCreatedEventListener.class);
	
	private static final String DATA_SEND_TO_SHR_BEAN = "healthRecordDataSendToSHR";
	
	@Override
	public void onMessage(Message message) {
		if (!(message instanceof MapMessage)) {
			LOG.warn("[IHSHR] Encounter event ignored: expected MapMessage but got {}", message == null ? null : message
			        .getClass().getName());
			return;
		}
		MapMessage mapMessage = (MapMessage) message;
		try {
			String action = mapMessage.getString("action");
			if (!Event.Action.CREATED.toString().equals(action)) {
				return;
			}
			String uuid = mapMessage.getString("uuid");
			String className = mapMessage.getString("classname");
			if (StringUtils.isNotBlank(className) && !Encounter.class.getName().equals(className)) {
				return;
			}
			if (StringUtils.isBlank(uuid)) {
				LOG.warn("[IHSHR] Encounter.CREATED skipped SHR push: missing encounter uuid");
				return;
			}
			final String encounterUuid = uuid.trim();
			DaemonToken daemonToken = IhshrModuleActivator.getDaemonToken();
			if (daemonToken == null) {
				LOG.error("[IHSHR] Encounter.CREATED skipped SHR push: daemon token is not available for uuid={}",
				    encounterUuid);
				return;
			}
			Daemon.runInDaemonThread(new Runnable() {
				
				@Override
				public void run() {
					processEncounterCreated(encounterUuid, action, className);
				}
			}, daemonToken);
		}
		catch (JMSException ex) {
			LOG.error("[IHSHR] Unable to read Encounter.CREATED event message", ex);
		}
	}
	
	private void processEncounterCreated(String uuid, String action, String className) {
		boolean openedSession = false;
		try {
			if (!Context.isSessionOpen()) {
				Context.openSession();
				openedSession = true;
			}
			Encounter encounter = resolveEncounter(uuid);
			Integer encounterId = encounter == null ? null : encounter.getEncounterId();
			LOG.info("[IHSHR] Encounter.CREATED received: encounterId={}, uuid={}, action={}, class={}", encounterId, uuid,
			    action, className);
			if (encounter == null || encounterId == null) {
				LOG.warn("[IHSHR] Encounter.CREATED skipped SHR push: unable to resolve encounter id for uuid={}", uuid);
				return;
			}
			if (encounter.getEncounterType() == null
			        || encounter.getEncounterType().getEncounterTypeId() != EncounterType.VISIT_COMPLETE.getValue()) {
				LOG.info("[IHSHR] Encounter.CREATED ignored for SHR push (not visit-complete): encounterId={}, type={}",
				    encounterId, encounter.getEncounterType() != null ? encounter.getEncounterType().getEncounterTypeId()
				            : null);
				return;
			}
			syncHealthRecordsByEncounterId(encounterId);
		}
		catch (Exception ex) {
			LOG.error("[IHSHR] SHR visit push failed for encounter uuid={}: {}", uuid, ex.getMessage(), ex);
		}
		finally {
			if (openedSession) {
				Context.closeSession();
			}
		}
	}
	
	private void syncHealthRecordsByEncounterId(Integer encounterId) {
		DataSendToSHR dataSendToSHR = resolveDataSendToSHR();
		if (dataSendToSHR == null) {
			LOG.error("[IHSHR] DataSendToSHR component is not available; aborting encounter push for encounterId={}",
			    encounterId);
			return;
		}
		LOG.info("[IHSHR] Starting SHR visit push for encounterId={}", encounterId);
		try {
			dataSendToSHR.syncHealthRecordsByEncounterId(encounterId);
			LOG.info("[IHSHR] Completed SHR visit push for encounterId={}", encounterId);
		}
		catch (Exception ex) {
			LOG.error("[IHSHR] SHR visit push failed for encounterId={}: {}", encounterId, ex.getMessage(), ex);
		}
	}
	
	private DataSendToSHR resolveDataSendToSHR() {
		try {
			return Context.getRegisteredComponent(DATA_SEND_TO_SHR_BEAN, DataSendToSHR.class);
		}
		catch (Exception ex) {
			LOG.warn("[IHSHR] Falling back to type-based component lookup for DataSendToSHR: {}", ex.getMessage());
			java.util.List<DataSendToSHR> components = Context.getRegisteredComponents(DataSendToSHR.class);
			return (components == null || components.isEmpty()) ? null : components.get(0);
		}
	}
	
	private Encounter resolveEncounter(String uuid) {
		if (StringUtils.isBlank(uuid)) {
			return null;
		}
		try {
			return Context.getEncounterService().getEncounterByUuid(uuid.trim());
		}
		catch (Exception ex) {
			LOG.warn("[IHSHR] Unable to resolve encounter for uuid={}: {}", uuid, ex.getMessage());
			return null;
		}
	}
}

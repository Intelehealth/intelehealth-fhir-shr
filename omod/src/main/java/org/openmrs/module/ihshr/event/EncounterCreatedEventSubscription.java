package org.openmrs.module.ihshr.event;

import org.openmrs.Encounter;
import org.openmrs.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers / unregisters the {@link EncounterCreatedEventListener} on the OpenMRS Event bus.
 */
public final class EncounterCreatedEventSubscription {
	
	private static final Logger LOG = LoggerFactory.getLogger(EncounterCreatedEventSubscription.class);
	
	private static EncounterCreatedEventListener listener;
	
	private EncounterCreatedEventSubscription() {
	}
	
	public static synchronized void register() {
		if (listener != null) {
			return;
		}
		listener = new EncounterCreatedEventListener();
		Event.subscribe(Encounter.class, Event.Action.CREATED.toString(), listener);
		LOG.info("Subscribed to {} topic with action {}", Encounter.class.getSimpleName(), Event.Action.CREATED);
	}
	
	public static synchronized void unregister() {
		if (listener == null) {
			return;
		}
		Event.unsubscribe(Encounter.class, Event.Action.CREATED, listener);
		LOG.info("Unsubscribed from {} topic with action {}", Encounter.class.getSimpleName(), Event.Action.CREATED);
		listener = null;
	}
}

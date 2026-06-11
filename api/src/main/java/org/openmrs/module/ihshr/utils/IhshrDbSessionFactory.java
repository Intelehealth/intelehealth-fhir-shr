package org.openmrs.module.ihshr.utils;

import java.util.List;

import org.openmrs.api.context.Context;
import org.openmrs.api.db.hibernate.DbSessionFactory;

/**
 * Resolves OpenMRS {@link DbSessionFactory} for native SQL. Do not use
 * {@code Context.getRegisteredComponent("sessionFactory", DbSessionFactory.class)} — that bean name
 * refers to Hibernate's {@code SessionFactoryImpl}, not {@link DbSessionFactory}.
 */
public final class IhshrDbSessionFactory {
	
	private IhshrDbSessionFactory() {
	}
	
	public static DbSessionFactory get() {
		try {
			List<DbSessionFactory> factories = Context.getRegisteredComponents(DbSessionFactory.class);
			if (factories != null && !factories.isEmpty()) {
				return factories.get(0);
			}
		}
		catch (Exception ignored) {
			// fall through
		}
		throw new IllegalStateException(
		        "DbSessionFactory is not available. Ensure OpenMRS context session is open (Context.openSession()).");
	}
}

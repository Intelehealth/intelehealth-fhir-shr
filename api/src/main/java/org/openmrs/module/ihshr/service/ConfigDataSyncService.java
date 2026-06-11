package org.openmrs.module.ihshr.service;

import java.util.List;

import org.hibernate.Query;
import org.openmrs.module.ihshr.utils.IhshrDbSessionFactory;
import org.openmrs.module.ihshr.datatype.ConfigFacilityDataType;
import org.openmrs.module.ihshr.domain.ConfigDataSync;
import org.springframework.stereotype.Service;

@Service("ihshrConfigDataSyncService")
public class ConfigDataSyncService {
	
	public ConfigDataSync getConfigDataSync(ConfigFacilityDataType type) {
		String sql = "select c.id, c.name, c.status from config_data_sync_module c where id=:id";
		
		Query q = IhshrDbSessionFactory.get().getCurrentSession().createSQLQuery(sql);
		q.setParameter("id", type.getValue());
		
		@SuppressWarnings("unchecked")
		List<Object[]> rows = q.list();
		
		ConfigDataSync dto = new ConfigDataSync();
		for (Object[] row : rows) {
			dto.setId(Integer.parseInt(row[0].toString()));
			dto.setName(row[1].toString());
			dto.setStatus(Boolean.parseBoolean(row[2].toString()));
			break;
		}
		return dto;
	}
}

package com.kisf.sqlquery.core.repo;

import com.kisf.sqlquery.core.entity.DatasourceConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DatasourceConfigRepository extends JpaRepository<DatasourceConfig, String> {
}

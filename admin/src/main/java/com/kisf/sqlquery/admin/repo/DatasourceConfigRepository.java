package com.kisf.sqlquery.admin.repo;

import com.kisf.sqlquery.admin.entity.DatasourceConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DatasourceConfigRepository extends JpaRepository<DatasourceConfig, String> {
}
